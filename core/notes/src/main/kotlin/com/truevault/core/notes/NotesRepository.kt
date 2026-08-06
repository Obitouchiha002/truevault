package com.truevault.core.notes

import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.notes.db.NoteDao
import com.truevault.core.notes.db.NoteEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** How long a deleted note stays recoverable. */
private const val TRASH_RETENTION_DAYS = 30L
private const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000

/** A note as the UI sees it. */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isInTrash: Boolean,
    val colour: Int = 0,
    val isChecklist: Boolean = false,
) {
    /**
     * A checklist stored as text.
     *
     * "[ ] milk" / "[x] bread" rather than a second table: one migration instead of two, the
     * checklist stays searchable as plain text, and turning a note into a list and back never
     * loses a line.
     */
    val checklistItems: List<ChecklistItem>
        get() = body.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val done = line.startsWith(DONE_PREFIX, ignoreCase = true)
                val open = line.startsWith(OPEN_PREFIX)
                ChecklistItem(
                    text = when {
                        done -> line.drop(DONE_PREFIX.length)
                        open -> line.drop(OPEN_PREFIX.length)
                        else -> line
                    }.trim(),
                    isDone = done,
                )
            }
            .toList()

    val openCount: Int get() = checklistItems.count { !it.isDone }
    val doneCount: Int get() = checklistItems.count { it.isDone }
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()

    /** First non-empty line, for a note the user never titled. */
    val displayTitle: String
        get() = title.ifBlank {
            body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty()
        }

    val preview: String
        get() = body.lineSequence()
            .drop(if (title.isBlank()) 1 else 0)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(120)
            .orEmpty()
}

/**
 * Notes, kept entirely apart from the vault.
 *
 * This class has no reference to the vault database, the crypto service or the file system, and it
 * is the only way the notes UI reaches storage. That is the separation guarantee, expressed as a
 * dependency graph rather than as a promise in a comment.
 */
@Singleton
class NotesRepository @Inject constructor(
    private val dao: NoteDao,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeNotes(): Flow<List<Note>> = dao.observeActive().map { it.map(NoteEntity::toNote) }

    fun observeArchived(): Flow<List<Note>> = dao.observeArchived().map { it.map(NoteEntity::toNote) }

    fun observeTrash(): Flow<List<Note>> = dao.observeTrash().map { it.map(NoteEntity::toNote) }

    fun observeCount(): Flow<Int> = dao.observeActiveCount()

    fun search(query: String): Flow<List<Note>> =
        dao.search(query.trim()).map { it.map(NoteEntity::toNote) }

    suspend fun note(id: String): Note? = withContext(ioDispatcher) { dao.byId(id)?.toNote() }

    /**
     * Creates or updates.
     *
     * A note with no title and no body is discarded rather than saved: opening the editor and
     * changing your mind should leave nothing behind, not an empty row the user has to tidy up.
     */
    suspend fun save(id: String?, title: String, body: String): String? =
        withContext(ioDispatcher) {
            val now = timeProvider.currentTimeMillis()
            val trimmedTitle = title.trim()
            val trimmedBody = body.trimEnd()

            if (trimmedTitle.isBlank() && trimmedBody.isBlank()) {
                id?.let { existing -> dao.byId(existing)?.let { dao.delete(it) } }
                return@withContext null
            }

            val existing = id?.let { dao.byId(it) }
            val entity = existing?.copy(
                title = trimmedTitle,
                body = trimmedBody,
                updatedAt = now,
            ) ?: NoteEntity(
                id = UUID.randomUUID().toString(),
                title = trimmedTitle,
                body = trimmedBody,
                createdAt = now,
                updatedAt = now,
            )

            dao.upsert(entity)
            entity.id
        }

    suspend fun setColour(id: String, colour: Int) = withContext(ioDispatcher) {
        dao.setColour(id, colour, timeProvider.currentTimeMillis())
    }

    /**
     * Turns a note into a checklist and back without losing a line.
     *
     * Going in, every non-empty line becomes an unticked item. Coming out, the markers are stripped
     * and the text is left exactly as the user would have typed it.
     */
    suspend fun setChecklist(id: String, checklist: Boolean) = withContext(ioDispatcher) {
        val note = dao.byId(id)?.toNote() ?: return@withContext
        val body = if (checklist) {
            note.body.lineSequence().filter { it.isNotBlank() }
                .map { ChecklistItem(it.trim(), isDone = false) }
                .toList().toChecklistBody()
        } else {
            note.checklistItems.joinToString("\n") { it.text }
        }
        dao.setChecklist(id, checklist, body, timeProvider.currentTimeMillis())
    }

    /** Ticks or unticks one line, leaving the rest of the note untouched. */
    suspend fun toggleChecklistItem(id: String, index: Int) = withContext(ioDispatcher) {
        val note = dao.byId(id)?.toNote() ?: return@withContext
        val items = note.checklistItems.toMutableList()
        if (index !in items.indices) return@withContext

        items[index] = items[index].copy(isDone = !items[index].isDone)
        dao.setChecklist(id, true, items.toChecklistBody(), timeProvider.currentTimeMillis())
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(ioDispatcher) {
        dao.setPinned(id, pinned, timeProvider.currentTimeMillis())
    }

    suspend fun setArchived(id: String, archived: Boolean) = withContext(ioDispatcher) {
        dao.setArchived(id, archived, timeProvider.currentTimeMillis())
    }

    /** Deleting a note moves it to the trash. Nothing here can reach a vault item. */
    suspend fun moveToTrash(id: String) = withContext(ioDispatcher) {
        dao.moveToTrash(id, timeProvider.currentTimeMillis())
    }

    suspend fun restore(id: String) = withContext(ioDispatcher) {
        dao.restoreFromTrash(id, timeProvider.currentTimeMillis())
    }

    suspend fun emptyTrash() = withContext(ioDispatcher) { dao.emptyTrash() }

    /**
     * Removes trashed notes past the retention window. Called once at startup.
     *
     * Only rows already in the trash are eligible, so a bug in the date arithmetic can cost a user
     * a note they deleted a month ago — never one they still have.
     */
    suspend fun purgeExpiredTrash() = withContext(ioDispatcher) {
        dao.purgeTrashOlderThan(timeProvider.currentTimeMillis() - TRASH_RETENTION_MILLIS)
    }
}

/** One line of a checklist. */
data class ChecklistItem(val text: String, val isDone: Boolean)

private const val OPEN_PREFIX = "[ ] "
private const val DONE_PREFIX = "[x] "

/** Renders checklist items back to the stored text form. */
fun List<ChecklistItem>.toChecklistBody(): String =
    joinToString("\n") { (if (it.isDone) DONE_PREFIX else OPEN_PREFIX) + it.text }

private fun NoteEntity.toNote() = Note(
    id = id,
    title = title,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    isArchived = isArchived,
    isInTrash = isInTrash,
    colour = colour,
    isChecklist = isChecklist,
)
