package com.truevault.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.truevault.core.database.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ActivityEventEntity)

    @Query("SELECT * FROM activity_events ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEventEntity>>

    /** Keeps history bounded; activity is a summary, not an audit log to be retained forever. */
    @Query(
        """
        DELETE FROM activity_events
        WHERE id NOT IN (SELECT id FROM activity_events ORDER BY created_at DESC LIMIT :keep)
        """,
    )
    suspend fun trimTo(keep: Int)
}
