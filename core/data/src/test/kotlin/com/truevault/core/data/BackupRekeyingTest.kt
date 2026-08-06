package com.truevault.core.data

import com.google.common.truth.Truth.assertThat
import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.file.VaultFileCipher
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultCryptoService
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.testing.FakeTimeProvider
import com.truevault.core.testing.crypto.FakeHardwareKeyStore
import com.truevault.core.testing.crypto.FakeVaultLockStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The property that makes a backup worth having: **an item exported from one vault must open in a
 * different vault.**
 *
 * This is a regression test for a defect found during release verification. Backup format v1 stored
 * each container together with the file key *as wrapped by the source vault's master key*. After a
 * reinstall the destination vault has a different master key, so every restored item was
 * permanently unopenable — the archive looked complete and could never be restored, which is the
 * exact failure the design set out to avoid.
 *
 * These tests work at the crypto layer rather than through `BackupRepository`, because the
 * repository needs `android.util.Base64`, a `ContentResolver` and a real file system. What is
 * verified here is the re-keying rule the repository is built on.
 */
class BackupRekeyingTest {

    private val dispatcher = StandardTestDispatcher()
    private val time = FakeTimeProvider()
    private val plaintext = "the contents of a secured file".toByteArray()

    /**
     * Builds an independent vault: its own lock store, its own Keystore, its own master key.
     *
     * Suspending on purpose. `createLock` hops onto the injected dispatcher, so blocking the test
     * thread while waiting for it would deadlock against the very scheduler that has to run it.
     */
    private suspend fun newVault(password: String): VaultCryptoService {
        val session = VaultSession(time, TestScope(dispatcher))
        val keyManager = VaultKeyManager(
            keyStore = FakeHardwareKeyStore(),
            lockStore = FakeVaultLockStore(),
            session = session,
            timeProvider = time,
            defaultDispatcher = dispatcher,
        )
        // createLock opens the session, which is what makes the master key available.
        keyManager.createLock(password.toCharArray())
        val service = VaultCryptoService(keyManager)
        check(service.isUnlocked) { "vault should be unlocked after creation" }
        return service
    }

    private fun writeContainer(crypto: VaultCryptoService, fileKey: javax.crypto.SecretKey): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = ByteArrayInputStream(plaintext),
            destination = out,
            fileKey = fileKey,
            wrappedFileKey = crypto.wrapFileKey(fileKey),
            sealedMetadata = crypto.sealMetadata("{}".toByteArray(), fileKey),
            plaintextSize = plaintext.size.toLong(),
            chunkSize = com.truevault.core.crypto.file.VaultContainer.MIN_CHUNK_SIZE,
        )
        return out.toByteArray()
    }

    @Test
    fun `an item re-keyed through an archive key opens in a different vault`() = runTest(dispatcher) {
        val source = newVault("source-vault-passphrase")
        val destination = newVault("destination-vault-passphrase")
        val archiveKey = javax.crypto.spec.SecretKeySpec(AesGcm.randomBytes(32), "AES")

        val fileKey = source.generateFileKey()
        val container = writeContainer(source, fileKey)

        // Export: unwrap with the source master key, re-wrap under the archive key.
        val archiveWrapped = source.wrapFileKeyWith(archiveKey, fileKey)

        // Restore: unwrap with the archive key, re-wrap under the destination master key.
        val recovered = destination.unwrapFileKeyWith(archiveKey, archiveWrapped)
        val destinationWrapped = destination.wrapFileKey(recovered)

        val out = ByteArrayOutputStream()
        destination.decryptFile(
            source = ByteArrayInputStream(container),
            destination = out,
            wrappedFileKey = destinationWrapped,
        )

        assertThat(out.toByteArray()).isEqualTo(plaintext)
    }

    @Test
    fun `without re-keying the same container is unopenable in another vault`() = runTest(dispatcher) {
        // This is exactly what format v1 produced, and why it had to change.
        val source = newVault("source-vault-passphrase")
        val destination = newVault("destination-vault-passphrase")

        val fileKey = source.generateFileKey()
        val container = writeContainer(source, fileKey)

        assertThrows(GeneralSecurityException::class.java) {
            destination.decryptFile(
                source = ByteArrayInputStream(container),
                destination = ByteArrayOutputStream(),
            )
        }
    }

    @Test
    fun `the archive-wrapped key is useless without the archive key`() = runTest(dispatcher) {
        val source = newVault("source-vault-passphrase")
        val archiveKey = javax.crypto.spec.SecretKeySpec(AesGcm.randomBytes(32), "AES")
        val wrongArchiveKey = javax.crypto.spec.SecretKeySpec(AesGcm.randomBytes(32), "AES")

        val fileKey = source.generateFileKey()
        val archiveWrapped = source.wrapFileKeyWith(archiveKey, fileKey)

        assertThrows(GeneralSecurityException::class.java) {
            source.unwrapFileKeyWith(wrongArchiveKey, archiveWrapped)
        }
    }

    @Test
    fun `the row's wrapped key takes precedence over the container header`() = runTest(dispatcher) {
        val vault = newVault("a-vault-passphrase")
        val fileKey = vault.generateFileKey()

        // A container whose header carries a key this vault cannot unwrap, standing in for one that
        // arrived from a restore.
        val foreignVault = newVault("a-different-vault-passphrase")
        val out = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = ByteArrayInputStream(plaintext),
            destination = out,
            fileKey = fileKey,
            wrappedFileKey = foreignVault.wrapFileKey(fileKey),
            sealedMetadata = vault.sealMetadata("{}".toByteArray(), fileKey),
            plaintextSize = plaintext.size.toLong(),
            chunkSize = com.truevault.core.crypto.file.VaultContainer.MIN_CHUNK_SIZE,
        )

        val decrypted = ByteArrayOutputStream()
        vault.decryptFile(
            source = ByteArrayInputStream(out.toByteArray()),
            destination = decrypted,
            wrappedFileKey = vault.wrapFileKey(fileKey),
        )

        assertThat(decrypted.toByteArray()).isEqualTo(plaintext)
    }
}
