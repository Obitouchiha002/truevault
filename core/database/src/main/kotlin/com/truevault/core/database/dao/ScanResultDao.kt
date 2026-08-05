package com.truevault.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.truevault.core.database.entity.ScanResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<ScanResultEntity>)

    @Query("SELECT * FROM scan_results WHERE scan_id = :scanId ORDER BY confidence DESC")
    suspend fun findByScan(scanId: String): List<ScanResultEntity>

    @Query("SELECT * FROM scan_results WHERE resolved = 0 ORDER BY created_at DESC")
    fun observeUnresolved(): Flow<List<ScanResultEntity>>

    @Query("SELECT COUNT(*) FROM scan_results WHERE resolved = 0 AND match_type = :matchType")
    fun observeUnresolvedCount(matchType: String): Flow<Int>

    @Query("UPDATE scan_results SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: String)

    @Query("UPDATE scan_results SET resolved = 1 WHERE vault_item_id = :vaultItemId")
    suspend fun markAllResolvedForItem(vaultItemId: String)

    @Query("DELETE FROM scan_results WHERE scan_id = :scanId")
    suspend fun deleteScan(scanId: String)
}
