package com.truevault.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A privacy-safe activity entry.
 *
 * There is deliberately no column for a file name, a path or a URI. Activity history is the one
 * screen a user might show someone else — "look what this app does" — so it says *what happened* and
 * *how many items* it happened to, and nothing that identifies the files.
 */
@Entity(
    tableName = "activity_events",
    indices = [Index("created_at"), Index("kind")],
)
data class ActivityEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "item_count")
    val itemCount: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
