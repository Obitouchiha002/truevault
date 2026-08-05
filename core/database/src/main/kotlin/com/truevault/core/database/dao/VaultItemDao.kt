package com.truevault.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.truevault.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Vault item access.
 *
 * Listing is paged: a vault with 10,000 items must never load 10,000 rows, and it certainly must
 * never decrypt 10,000 metadata blobs to draw one screen.
 *
 * Ordering happens in SQL on the plaintext columns (date, size, category). Ordering or searching by
 * file name cannot happen here, because the name is encrypted — that is handled a layer up, against
 * an in-memory index built after unlock.
 */
@Dao
interface VaultItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity)

    @Update
    suspend fun update(item: VaultItemEntity)

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun findById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<VaultItemEntity>

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM vault_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT mime_category AS category, COUNT(*) AS count FROM vault_items GROUP BY mime_category")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM vault_items WHERE privacy_status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(original_size), 0) FROM vault_items")
    fun observeTotalOriginalBytes(): Flow<Long>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE (:category IS NULL OR mime_category = :category)
        ORDER BY created_at DESC
        """,
    )
    fun pagingSourceByNewest(category: String?): PagingSource<Int, VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE (:category IS NULL OR mime_category = :category)
        ORDER BY created_at ASC
        """,
    )
    fun pagingSourceByOldest(category: String?): PagingSource<Int, VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE (:category IS NULL OR mime_category = :category)
        ORDER BY original_size DESC
        """,
    )
    fun pagingSourceByLargest(category: String?): PagingSource<Int, VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE (:category IS NULL OR mime_category = :category)
        ORDER BY original_size ASC
        """,
    )
    fun pagingSourceBySmallest(category: String?): PagingSource<Int, VaultItemEntity>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE (:category IS NULL OR mime_category = :category)
        ORDER BY mime_category ASC, created_at DESC
        """,
    )
    fun pagingSourceByType(category: String?): PagingSource<Int, VaultItemEntity>

    /** Backs the in-memory name index and name-ordered listing. Metadata only, no file contents. */
    @Query("SELECT id, encrypted_metadata, mime_category, original_size, created_at FROM vault_items")
    suspend fun allMetadata(): List<VaultItemMetadataRow>

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    fun pagingSourceByIds(ids: List<String>): PagingSource<Int, VaultItemEntity>

    /** Items sharing a fingerprint — the exact-duplicate check, done without any plaintext. */
    @Query("SELECT * FROM vault_items WHERE content_fingerprint = :fingerprint")
    suspend fun findByFingerprint(fingerprint: ByteArray): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE privacy_status = :status")
    suspend fun findByStatus(status: String): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE last_integrity_check_at IS NULL OR last_integrity_check_at < :before LIMIT :limit")
    suspend fun findStaleIntegrityChecks(before: Long, limit: Int): List<VaultItemEntity>

    @Transaction
    @Query("UPDATE vault_items SET privacy_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePrivacyStatus(id: String, status: String, updatedAt: Long)

    @Transaction
    @Query(
        """
        UPDATE vault_items
        SET original_deletion_state = :state, privacy_status = :privacyStatus, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateDeletionState(
        id: String,
        state: String,
        privacyStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE vault_items
        SET verification_status = :status, last_integrity_check_at = :checkedAt, updated_at = :checkedAt
        WHERE id = :id
        """,
    )
    suspend fun updateVerification(id: String, status: String, checkedAt: Long)
}

data class CategoryCount(
    val category: String,
    val count: Int,
)

data class VaultItemMetadataRow(
    val id: String,
    @androidx.room.ColumnInfo(name = "encrypted_metadata") val encryptedMetadata: ByteArray,
    @androidx.room.ColumnInfo(name = "mime_category") val mimeCategory: String,
    @androidx.room.ColumnInfo(name = "original_size") val originalSize: Long,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean = other is VaultItemMetadataRow &&
        id == other.id && encryptedMetadata.contentEquals(other.encryptedMetadata)

    override fun hashCode(): Int = 31 * id.hashCode() + encryptedMetadata.contentHashCode()
}
