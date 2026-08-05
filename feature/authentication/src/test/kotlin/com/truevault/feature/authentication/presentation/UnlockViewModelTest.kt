package com.truevault.feature.authentication.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.VaultError
import com.truevault.core.testing.FakeTimeProvider
import com.truevault.core.testing.MainDispatcherRule
import com.truevault.core.testing.crypto.FakeHardwareKeyStore
import com.truevault.core.testing.crypto.FakeVaultLockStore
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class UnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = StandardTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)
    private val time = FakeTimeProvider()
    private val keyStore = FakeHardwareKeyStore()
    private val lockStore = FakeVaultLockStore()
    private val session = VaultSession(time, TestScope(dispatcher))

    private val keyManager = VaultKeyManager(
        keyStore = keyStore,
        lockStore = lockStore,
        session = session,
        timeProvider = time,
        defaultDispatcher = dispatcher,
    )

    private val preferences = mockk<UserPreferencesDataSource>(relaxed = true)

    private val password = "river stone lantern"

    private suspend fun createVault() {
        keyManager.createLock(password.toCharArray())
        session.lock()
    }

    @Test
    fun `the correct password emits Unlocked`() = runTest {
        createVault()
        val vm = UnlockViewModel(keyManager, preferences)

        vm.effects.test {
            vm.onAction(UnlockAction.Submit(password.toCharArray()))

            assertThat(awaitItem()).isEqualTo(UnlockEffect.Unlocked)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(session.isUnlocked).isTrue()
    }

    @Test
    fun `a wrong password reports an error and counts the attempt`() = runTest {
        createVault()
        val vm = UnlockViewModel(keyManager, preferences)

        vm.onAction(UnlockAction.Submit("wrong-password".toCharArray()))
        testScheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(VaultError.AuthenticationRequired)
        assertThat(vm.uiState.value.failedAttempts).isEqualTo(1)
        assertThat(session.isUnlocked).isFalse()
    }

    @Test
    fun `repeated wrong passwords never destroy the vault`() = runTest {
        createVault()
        val vm = UnlockViewModel(keyManager, preferences)

        repeat(10) { vm.onAction(UnlockAction.Submit("wrong-password".toCharArray())) }
        testScheduler.advanceUntilIdle()

        // The record survives: a "wipe after N attempts" rule would punish a user who mistypes far
        // more reliably than it would punish an attacker.
        assertThat(lockStore.read()).isNotNull()
        vm.onAction(UnlockAction.Submit(password.toCharArray()))
        testScheduler.advanceUntilIdle()
        assertThat(session.isUnlocked).isTrue()
    }

    @Test
    fun `the biometric shortcut is offered only when a biometric copy exists`() = runTest {
        createVault()
        val without = UnlockViewModel(keyManager, preferences)
        testScheduler.advanceUntilIdle()
        assertThat(without.uiState.value.biometricAvailable).isFalse()

        keyManager.unlockWithPassword(password.toCharArray())
        keyManager.enableBiometricUnlock(requireNotNull(keyManager.biometricEnrolCipher()))
        session.lock()

        val with = UnlockViewModel(keyManager, preferences)
        testScheduler.advanceUntilIdle()
        assertThat(with.uiState.value.biometricAvailable).isTrue()
    }

    @Test
    fun `an invalidated biometric key falls back to the password and says so`() = runTest {
        createVault()
        keyManager.unlockWithPassword(password.toCharArray())
        keyManager.enableBiometricUnlock(requireNotNull(keyManager.biometricEnrolCipher()))
        session.lock()

        val vm = UnlockViewModel(keyManager, preferences)
        testScheduler.advanceUntilIdle()
        keyStore.biometricInvalidated = true

        vm.onAction(UnlockAction.BiometricRequested)
        testScheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.biometricWasReset).isTrue()
        assertThat(vm.uiState.value.biometricAvailable).isFalse()
    }

    @Test
    fun `a successful unlock clears the failure count`() = runTest {
        createVault()
        val vm = UnlockViewModel(keyManager, preferences)

        vm.onAction(UnlockAction.Submit("wrong".toCharArray()))
        testScheduler.advanceUntilIdle()
        vm.onAction(UnlockAction.Submit(password.toCharArray()))
        testScheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.failedAttempts).isEqualTo(0)
    }
}
