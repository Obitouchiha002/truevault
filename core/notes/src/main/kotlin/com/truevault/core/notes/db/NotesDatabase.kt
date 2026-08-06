package com.truevault.core.notes.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Ordinary notes. **A different database from the vault, on purpose.**
 *
 * These notes are not encrypted and are not private. They are the app's visible, useful half — a
 * place to write things down — and keeping them in their own database is what guarantees that
 * opening, searching or backing up notes can never reach vault content. The two share no table, no
 * connection and no key.
 *
 * Deleting a note can never touch a vault item, because nothing here can address one.
 *
 * Deliberately small for now: text notes, pinning, archive and a trash. No OCR, no voice, no AI, no
 * attachments. Each of those brings a permission, a model download or a network call, and a notes
 * feature is not worth any of them until the basics are solid.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isInTrash: Boolean = false,
    val trashedAt: Long? = null,
)

@Dao
interface NoteDao {

    /** Pinned first, then most recently edited. Trash and archive are separate views. */
    @Query(
        """
        SELECT * FROM notes
        WHERE isInTrash = 0 AND isArchived = 0
        ORDER BY isPinned DESC, updatedAt DESC
        """,
    )
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isInTrash = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isInTrash = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    /**
     * Title and body only, and only outside the trash.
     *
     * There is no vault content in this table to find, which is the point: notes search physically
     * cannot surface a vault item, rather than being trusted not to.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE isInTrash = 0
          AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET isInTrash = 1, trashedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: String, now: Long)

    @Query("UPDATE notes SET isInTrash = 0, trashedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: String, now: Long)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("DELETE FROM notes WHERE isInTrash = 1")
    suspend fun emptyTrash()

    /** Trash retention. Called at startup; deletes nothing that is not already in the trash. */
    @Query("DELETE FROM notes WHERE isInTrash = 1 AND trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun purgeTrashOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE isInTrash = 0 AND isArchived = 0")
    fun observeActiveCount(): Flow<Int>
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        /** Its own file. The vault database is `truevault.db`, and the two never meet. */
        const val NAME = "nexa-notes.db"
    }
}
