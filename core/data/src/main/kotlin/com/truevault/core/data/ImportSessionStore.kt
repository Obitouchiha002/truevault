package com.truevault.core.data

import com.truevault.core.data.model.ImportSession
import com.truevault.core.model.ImportMode
import com.truevault.core.model.SelectedSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the files a user picked, in memory, for the length of one import flow.
 *
 * This is why the import routes carry only a session id. A picked URI can reveal a file name, an
 * album, or a folder path — putting one in a navigation argument would write it into the back stack
 * and into saved state. The id here is opaque and the URIs never leave this process.
 *
 * Sessions are dropped when the flow finishes or the vault locks; nothing is persisted.
 */
@Singleton
class ImportSessionStore @Inject constructor() {

    private val sessions = MutableStateFlow<Map<String, ImportSession>>(emptyMap())
    val activeSessions: StateFlow<Map<String, ImportSession>> = sessions.asStateFlow()

    fun create(sources: List<SelectedSource>): ImportSession {
        val session = ImportSession(sessionId = UUID.randomUUID().toString(), sources = sources)
        sessions.update { it + (session.sessionId to session) }
        return session
    }

    fun find(sessionId: String): ImportSession? = sessions.value[sessionId]

    fun setMode(sessionId: String, mode: ImportMode): ImportSession? {
        val updated = sessions.value[sessionId]?.copy(mode = mode) ?: return null
        sessions.update { it + (sessionId to updated) }
        return updated
    }

    fun discard(sessionId: String) {
        sessions.update { it - sessionId }
    }

    /** Called when the vault locks. Picked URIs must not survive a lock. */
    fun clear() {
        sessions.value = emptyMap()
    }
}
