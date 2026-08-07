package com.truevault.core.database

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empties the vault database for the "delete all my data" flow.
 *
 * A thin wrapper on purpose. The database itself is a Room type, and exposing it to a feature module
 * would drag Room onto that module's classpath — this keeps the seam at `deleteEverything()`, which
 * mentions nothing Room owns, and matches how `VaultFileSystemReset` already works.
 */
@Singleton
class VaultDatabaseReset @Inject constructor(
    private val database: TrueVaultDatabase,
) {
    /** Blocking disk I/O. Call it off the main thread. */
    fun deleteEverything() = database.wipe()
}
