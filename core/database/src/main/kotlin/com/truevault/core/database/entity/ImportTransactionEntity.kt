package com.truevault.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One in-flight import.
 *
 * This row is what makes Secure Move recoverable. It is written *before* any bytes are encrypted and
 * updated as the transaction advances, so a process killed at any point leaves behind a record that
 * says exactly how far it got — which is the difference between a resumable import and an orphaned
 * `.vault.part` file nobody can explain.
 */
@Entity(
    tableName = "import_transactions",
    indices = [Index("state"), Index("vault_item_id"), Index("created_at")],
)
data class ImportTransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,

    @ColumnInfo(name = "vault_item_id")
    val vaultItemId: String,

    /** Sealed source URI. Kept only while the transaction may still need to read or delete it. */
    @ColumnInfo(name = "encrypted_source_token", typeAffinity = ColumnInfo.BLOB)
    val encryptedSourceToken: ByteArray,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "import_mode")
    val importMode: String,

    @ColumnInfo(name = "bytes_processed")
    val bytesProcessed: Long,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /** A stable code, never a raw exception message. */
    @ColumnInfo(name = "failure_code")
    val failureCode: String?,

    @ColumnInfo(name = "retry_allowed")
    val retryAllowed: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is ImportTransactionEntity &&
        transactionId == other.transactionId &&
        vaultItemId == other.vaultItemId &&
        encryptedSourceToken.contentEquals(other.encryptedSourceToken) &&
        state == other.state &&
        importMode == other.importMode &&
        bytesProcessed == other.bytesProcessed &&
        totalBytes == other.totalBytes &&
        createdAt == other.createdAt &&
        updatedAt == other.updatedAt &&
        failureCode == other.failureCode &&
        retryAllowed == other.retryAllowed

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + vaultItemId.hashCode()
        result = 31 * result + state.hashCode()
        return result
    }

    override fun toString(): String =
        "ImportTransactionEntity(id=$transactionId, state=$state, mode=$importMode)"
}
