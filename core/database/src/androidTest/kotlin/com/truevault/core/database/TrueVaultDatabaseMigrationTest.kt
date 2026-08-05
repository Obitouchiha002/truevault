package com.truevault.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "truevault-migration-test.db"

/**
 * Migration coverage.
 *
 * At schema version 1 there is nothing to migrate yet, so this test does the two things that are
 * actually useful today:
 *
 *  1. Proves the exported schema is present and openable, which is what every future migration test
 *     will build on. A missing schema export is otherwise discovered much later, when a migration
 *     needs it and cannot be written.
 *  2. Runs the full migration path with `validateMigration = true`, so the moment version 2 is added
 *     without a migration, this fails.
 *
 * Destructive migration is never enabled anywhere in this project: dropping the table would destroy
 * the user's vault index while leaving the encrypted files orphaned on disk.
 */
@RunWith(AndroidJUnit4::class)
class TrueVaultDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrueVaultDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun schemaVersionOneIsExportedAndOpenable() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            assertThat(db.version).isEqualTo(1)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migratesAllTheWayToTheCurrentVersion() {
        helper.createDatabase(TEST_DB, 1).close()

        // Passing every known migration and asking Room to validate the result is what makes a
        // forgotten migration a build failure rather than a crash on a user's device.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            TrueVaultDatabase.VERSION,
            true,
            *TRUEVAULT_MIGRATIONS,
        )
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun everyExpectedTableExistsAtVersionOne() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }

            assertThat(tables).containsAtLeast(
                "vault_items",
                "import_transactions",
                "scan_results",
                "activity_events",
            )
        }
    }
}
