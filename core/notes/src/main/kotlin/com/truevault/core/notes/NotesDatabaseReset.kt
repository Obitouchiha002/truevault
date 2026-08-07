package com.truevault.core.notes

import com.truevault.core.notes.db.NotesDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empties the notes database for the "delete all my data" flow.
 *
 * Kept separate from the vault's own reset because the two databases are deliberately unrelated —
 * nothing in the notes half can reach the vault — and a single reset object spanning both would be
 * the first thread joining them.
 */
@Singleton
class NotesDatabaseReset @Inject constructor(
    private val database: NotesDatabase,
) {
    /** Blocking disk I/O. Call it off the main thread. */
    fun deleteEverything() = database.wipe()
}
