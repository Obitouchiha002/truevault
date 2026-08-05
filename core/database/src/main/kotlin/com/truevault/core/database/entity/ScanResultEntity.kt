package com.truevault.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One match found by a privacy scan.
 *
 * Deleting a vault item cascades here: a finding about an item that no longer exists is noise, and
 * leaving it behind would let the privacy score keep counting a problem the user already solved.
 */
@Entity(
    tableName = "scan_results",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vault_item_id"), Index("resolved"), Index("scan_id")],
)
data class ScanResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Groups every finding produced by one scan run. */
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    @ColumnInfo(name = "vault_item_id")
    val vaultItemId: String,

    @ColumnInfo(name = "match_type")
    val matchType: String,

    /** Sealed URI of the copy that was found. */
    @ColumnInfo(name = "encrypted_matched_token", typeAffinity = ColumnInfo.BLOB)
    val encryptedMatchedToken: ByteArray,

    /** 0..100, always shown to the user rather than hidden behind a verdict. */
    @ColumnInfo(name = "confidence")
    val confidence: Int,

    @ColumnInfo(name = "matched_size")
    val matchedSize: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "resolved")
    val resolved: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is ScanResultEntity &&
        id == other.id &&
        scanId == other.scanId &&
        vaultItemId == other.vaultItemId &&
        matchType == other.matchType &&
        encryptedMatchedToken.contentEquals(other.encryptedMatchedToken) &&
        confidence == other.confidence &&
        matchedSize == other.matchedSize &&
        createdAt == other.createdAt &&
        resolved == other.resolved

    override fun hashCode(): Int = 31 * id.hashCode() + scanId.hashCode()

    override fun toString(): String =
        "ScanResultEntity(id=$id, type=$matchType, confidence=$confidence)"
}
