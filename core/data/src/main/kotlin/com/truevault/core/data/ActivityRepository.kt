package com.truevault.core.data

import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.database.dao.ActivityEventDao
import com.truevault.core.database.entity.ActivityEventEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The kinds of thing worth telling the user about. Deliberately coarse. */
enum class ActivityKind {
    FILES_SECURED,
    ORIGINAL_DELETED,
    DUPLICATE_DETECTED,
    BACKUP_COMPLETED,
    IMPORT_FAILED,
}

data class ActivityEvent(
    val id: String,
    val kind: ActivityKind,
    val itemCount: Int,
    val timestampMillis: Long,
)

private const val MAX_RETAINED_EVENTS = 100

/**
 * Recent activity.
 *
 * Every entry says what happened and to how many items — never which ones. Activity is the screen a
 * user is most likely to have visible while someone else is looking, so a file name here would
 * defeat the point of the app.
 */
@Singleton
class ActivityRepository @Inject constructor(
    private val dao: ActivityEventDao,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeRecent(limit: Int = 20): Flow<List<ActivityEvent>> =
        dao.observeRecent(limit).map { rows -> rows.map(ActivityEventEntity::toDomain) }

    suspend fun record(kind: ActivityKind, itemCount: Int): Unit = withContext(ioDispatcher) {
        dao.insert(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                kind = kind.name,
                itemCount = itemCount,
                createdAt = timeProvider.currentTimeMillis(),
            ),
        )
        dao.trimTo(MAX_RETAINED_EVENTS)
    }
}

private fun ActivityEventEntity.toDomain() = ActivityEvent(
    id = id,
    kind = runCatching { ActivityKind.valueOf(kind) }.getOrDefault(ActivityKind.FILES_SECURED),
    itemCount = itemCount,
    timestampMillis = createdAt,
)
