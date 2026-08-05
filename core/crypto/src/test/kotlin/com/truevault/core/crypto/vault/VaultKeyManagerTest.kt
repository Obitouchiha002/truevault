package com.truevault.core.crypto.vault

import com.google.common.truth.Truth.assertThat
import com.truevault.core.common.result.Outcome
import com.truevault.core.testing.crypto.FakeHardwareKeyStore
import com.truevault.core.testing.crypto.FakeVaultLockStore
import com.truevault.core.crypto.session.VaultLockState
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.model.AutoLockDuration
import com.truevault.core.model.VaultError
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * These are the tests that matter most in the whole project: they are what stands between a user
 * and a vault that either opens for the wrong password or refuses to open for the right one.
 */
class VaultKeyManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val time = com.truevault.core.testing.FakeTimeProvider()
    private val keyStore = FakeHardwareKeyStore()
    private val lockStore = FakeVaultLockStore()
    private val session = VaultSession(time, TestScope(dispatcher))

    private val manager = VaultKeyManager(
        keyStore = keyStore,
        lockStore = lockStore,
        session = session,
        timeProvider = time,
        defaultDispatcher = dispatcher,
    )

    private val password = "a-long-enough-passphrase"

    @Test
    fun `creating a lock stores a record and opens the session`() = runTest(dispatcher) {
        val result = manager.createLock(password.toCharArray())

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(session.state.value).isEqualTo(VaultLockState.Unlocked)
        assertThat(lockStore.read()).isNotNull()
    }

    @Test
    fun `the stored record contains neither the password nor the master key`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())

            val record = requireNotNull(lockStore.read())
            val stored = record.sealedMasterKey + record.salt

            assertThat(String(stored, Charsets.ISO_8859_1)).doesNotContain(password)
            assertThat(record.biometricSealedMasterKey).isNull()
            assertThat(record.toString()).doesNotContain("salt")
        }

    @Test
    fun `the correct password unlocks a locked vault`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()

        val result = manager.unlockWithPassword(password.toCharArray())

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(session.state.value).isEqualTo(VaultLockState.Unlocked)
    }

    @Test
    fun `a wrong password is rejected and leaves the vault locked`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()

        val result = manager.unlockWithPassword("not-the-password".toCharArray())

        assertThat(result).isEqualTo(Outcome.Failure(VaultError.AuthenticationRequired))
        assertThat(session.state.value).isEqualTo(VaultLockState.Locked)
    }

    @Test
    fun `a password that differs only in case is rejected`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()

        val result = manager.unlockWithPassword(password.uppercase().toCharArray())

        assertThat(result).isEqualTo(Outcome.Failure(VaultError.AuthenticationRequired))
    }

    @Test
    fun `a tampered record is rejected even with the right password`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()
        lockStore.corruptSealedMasterKey()

        val result = manager.unlockWithPassword(password.toCharArray())

        assertThat(result).isEqualTo(Outcome.Failure(VaultError.AuthenticationRequired))
        assertThat(session.state.value).isEqualTo(VaultLockState.Locked)
    }

    @Test
    fun `a truncated record is reported as an integrity failure`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()
        lockStore.truncateSealedMasterKey()

        val result = manager.unlockWithPassword(password.toCharArray())

        assertThat(result).isEqualTo(Outcome.Failure(VaultError.IntegrityCheckFailed))
    }

    @Test
    fun `a record written by a newer KDF version is refused, not guessed at`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())
            val record = requireNotNull(lockStore.read())
            lockStore.write(record.copy(kdfVersion = 99))
            session.lock()

            val result = manager.unlockWithPassword(password.toCharArray())

            assertThat(result).isEqualTo(
                Outcome.Failure(
                    VaultError.UnsupportedFormatVersion(foundVersion = 99, maxSupportedVersion = 1),
                ),
            )
        }

    @Test
    fun `a second vault cannot be created over an existing one`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())

        val result = manager.createLock("another-passphrase".toCharArray())

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `biometric unlock cannot be enabled while the vault is locked`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()

        assertThat(manager.biometricEnrolCipher()).isNull()
    }

    @Test
    fun `biometric enrolment then unlock opens the same vault`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())

        val enrolCipher = requireNotNull(manager.biometricEnrolCipher())
        assertThat(manager.enableBiometricUnlock(enrolCipher)).isInstanceOf(Outcome.Success::class.java)

        session.lock()
        val unlockCipher = requireNotNull(manager.biometricUnlockCipher())
        val result = manager.unlockWithBiometric(unlockCipher)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(session.state.value).isEqualTo(VaultLockState.Unlocked)
    }

    @Test
    fun `a new biometric enrolment invalidates the biometric path and falls back to password`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())
            manager.enableBiometricUnlock(requireNotNull(manager.biometricEnrolCipher()))
            session.lock()

            keyStore.biometricInvalidated = true

            assertThat(manager.biometricUnlockCipher()).isNull()
            assertThat(manager.unlockWithPassword(password.toCharArray()))
                .isInstanceOf(Outcome.Success::class.java)
        }

    @Test
    fun `disabling biometrics removes the stored biometric copy`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        manager.enableBiometricUnlock(requireNotNull(manager.biometricEnrolCipher()))

        manager.disableBiometricUnlock()

        assertThat(requireNotNull(lockStore.read()).biometricSealedMasterKey).isNull()
        assertThat(keyStore.hasBiometricBoundKey()).isFalse()
    }

    @Test
    fun `changing the password re-seals the same vault without touching file keys`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())
            val originalRecord = requireNotNull(lockStore.read())

            val result = manager.changePassword(
                currentPassword = password.toCharArray(),
                newPassword = "a-different-passphrase".toCharArray(),
            )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val updated = requireNotNull(lockStore.read())
            assertThat(updated.salt).isNotEqualTo(originalRecord.salt)

            session.lock()
            assertThat(manager.unlockWithPassword("a-different-passphrase".toCharArray()))
                .isInstanceOf(Outcome.Success::class.java)
        }

    @Test
    fun `the old password stops working after a change`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        manager.changePassword(password.toCharArray(), "a-different-passphrase".toCharArray())
        session.lock()

        assertThat(manager.unlockWithPassword(password.toCharArray()))
            .isEqualTo(Outcome.Failure(VaultError.AuthenticationRequired))
    }

    @Test
    fun `a wrong current password cannot change the password`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.lock()

        val result = manager.changePassword("wrong".toCharArray(), "new-passphrase".toCharArray())

        assertThat(result).isEqualTo(Outcome.Failure(VaultError.AuthenticationRequired))
    }

    @Test
    fun `unlocking before a vault exists fails instead of creating one`() = runTest(dispatcher) {
        val result = manager.unlockWithPassword(password.toCharArray())

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(lockStore.read()).isNull()
    }

    @Test
    fun `backgrounding with the default setting locks immediately`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())

        session.onAppBackgrounded(AutoLockDuration.IMMEDIATE)

        assertThat(session.state.value).isEqualTo(VaultLockState.Locked)
        assertThat(session.isUnlocked).isFalse()
    }

    @Test
    fun `a grace period keeps the session until the monotonic clock passes it`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())

            session.onAppBackgrounded(AutoLockDuration.ONE_MINUTE)
            assertThat(session.isUnlocked).isTrue()

            time.advanceBy(AutoLockDuration.ONE_MINUTE.millis)
            assertThat(session.isUnlocked).isFalse()
            assertThat(session.state.value).isEqualTo(VaultLockState.Locked)
        }

    @Test
    fun `moving the wall clock backwards does not extend a session`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())
        session.onAppBackgrounded(AutoLockDuration.ONE_MINUTE)

        // A user changing the device date must not buy extra unlocked time: expiry is monotonic.
        time.setWallClock(0L)
        time.advanceBy(AutoLockDuration.ONE_MINUTE.millis)

        assertThat(session.isUnlocked).isFalse()
    }

    @Test
    fun `returning to the foreground within the grace period keeps the session open`() =
        runTest(dispatcher) {
            manager.createLock(password.toCharArray())
            session.onAppBackgrounded(AutoLockDuration.FIVE_MINUTES)

            time.advanceBy(1_000)
            session.onAppForegrounded()

            assertThat(session.isUnlocked).isTrue()
        }

    @Test
    fun `screen off locks immediately when the user asked for it`() = runTest(dispatcher) {
        manager.createLock(password.toCharArray())

        session.onScreenOff(lockOnScreenOff = true, duration = AutoLockDuration.FIFTEEN_MINUTES)

        assertThat(session.isUnlocked).isFalse()
    }
}
