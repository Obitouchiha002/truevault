package com.truevault.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.truevault.core.database.entity.ImportTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: ImportTransactionEntity)

    @Update
    suspend fun update(transaction: ImportTransactionEntity)

    @Query("SELECT * FROM import_transactions WHERE transaction_id = :id")
    suspend fun findById(id: String): ImportTransactionEntity?

    @Query("SELECT * FROM import_transactions WHERE vault_item_id = :vaultItemId")
    suspend fun findByVaultItemId(vaultItemId: String): ImportTransactionEntity?

    /** Everything that was mid-flight when the process died. The input to crash recovery. */
    @Query("SELECT * FROM import_transactions WHERE state IN (:states)")
    suspend fun findByStates(states: List<String>): List<ImportTransactionEntity>

    @Query("SELECT * FROM import_transactions WHERE state = :state ORDER BY created_at DESC")
    fun observeByState(state: String): Flow<List<ImportTransactionEntity>>

    @Query(
        """
        UPDATE import_transactions
        SET state = :state, bytes_processed = :bytesProcessed, updated_at = :updatedAt
        WHERE transaction_id = :id
        """,
    )
    suspend fun updateProgress(id: String, state: String, bytesProcessed: Long, updatedAt: Long)

    @Query(
        """
        UPDATE import_transactions
        SET state = :state, failure_code = :failureCode, retry_allowed = :retryAllowed, updated_at = :updatedAt
        WHERE transaction_id = :id
        """,
    )
    suspend fun markTerminal(
        id: String,
        state: String,
        failureCode: String?,
        retryAllowed: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM import_transactions WHERE transaction_id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM import_transactions WHERE state IN (:states) AND updated_at < :before")
    suspend fun deleteOlderThan(states: List<String>, before: Long)
}
