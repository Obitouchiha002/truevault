package com.truevault.feature.authentication.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.PasswordStrength
import com.truevault.core.testing.FakeTimeProvider
import com.truevault.core.testing.MainDispatcherRule
import com.truevault.core.testing.crypto.FakeHardwareKeyStore
import com.truevault.core.testing.crypto.FakeVaultLockStore
import com.truevault.feature.authentication.domain.BiometricCapability
import com.truevault.feature.authentication.domain.BiometricCapabilityChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/**
 * Turbine's three-second default is not enough here.
 *
 * These tests run real Argon2id rather than a stub, deliberately — the KDF parameters are a security
 * property and a test that mocked them would prove nothing about them. But a deliberately slow
 * function on a machine running six Gradle tasks at once is exactly the shape that fails once in
 * twenty runs and passes on the retry, which is how a suite stops being believed.
 */
private val KDF_TIMEOUT = 30.seconds

class CreateVaultLockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = StandardTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)
    private val time = FakeTimeProvider()
    private val lockStore = FakeVaultLockStore()
    private val session = VaultSession(time, TestScope(dispatcher))

    private val keyManager = VaultKeyManager(
        keyStore = FakeHardwareKeyStore(),
        lockStore = lockStore,
        session = session,
        timeProvider = time,
        defaultDispatcher = dispatcher,
    )

    private val preferences = mockk<UserPreferencesDataSource>(relaxed = true) {
        coEvery { setBiometricUnlockEnabled(any()) } returns Unit
    }

    private val capabilityChecker = mockk<BiometricCapabilityChecker> {
        every { capability() } returns BiometricCapability.AVAILABLE
    }

    private fun viewModel() = CreateVaultLockViewModel(keyManager, preferences, capabilityChecker)

    @Test
    fun `a short password blocks submission`() = runTest {
        val vm = viewModel()

        vm.onAction(
            CreateVaultLockAction.PasswordChanged("short".toCharArray(), "short".toCharArray()),
        )

        assertThat(vm.uiState.value.strength).isEqualTo(PasswordStrength.TOO_SHORT)
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `mismatched confirmation blocks submission even for a strong password`() = runTest {
        val vm = viewModel()

        vm.onAction(
            CreateVaultLockAction.PasswordChanged(
                password = "river stone lantern".toCharArray(),
                confirmation = "river stone lanterm".toCharArray(),
            ),
        )

        assertThat(vm.uiState.value.passwordsMatch).isFalse()
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `a matching strong password enables submission`() = runTest {
        val vm = viewModel()

        vm.onAction(
            CreateVaultLockAction.PasswordChanged(
                password = "river stone lantern".toCharArray(),
                confirmation = "river stone lantern".toCharArray(),
            ),
        )

        assertThat(vm.uiState.value.canSubmit).isTrue()
    }

    @Test
    fun `submitting without biometrics creates the vault and finishes`() = runTest {
        val vm = viewModel()

        vm.effects.test(timeout = KDF_TIMEOUT) {
            vm.onAction(CreateVaultLockAction.PasswordChanged("river stone lantern".toCharArray(), "river stone lantern".toCharArray()))
            vm.onAction(CreateVaultLockAction.BiometricToggled(false))
            vm.onAction(CreateVaultLockAction.Submit("river stone lantern".toCharArray()))

            assertThat(awaitItem()).isEqualTo(CreateVaultLockEffect.VaultCreated)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(lockStore.read()).isNotNull()
        assertThat(session.isUnlocked).isTrue()
    }

    @Test
    fun `opting into biometrics asks for enrolment before finishing`() = runTest {
        val vm = viewModel()

        vm.effects.test(timeout = KDF_TIMEOUT) {
            vm.onAction(CreateVaultLockAction.PasswordChanged("river stone lantern".toCharArray(), "river stone lantern".toCharArray()))
            vm.onAction(CreateVaultLockAction.BiometricToggled(true))
            vm.onAction(CreateVaultLockAction.Submit("river stone lantern".toCharArray()))

            assertThat(awaitItem())
                .isInstanceOf(CreateVaultLockEffect.RequestBiometricEnrolment::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `declining the biometric prompt still leaves a working vault`() = runTest {
        val vm = viewModel()

        vm.effects.test(timeout = KDF_TIMEOUT) {
            vm.onAction(CreateVaultLockAction.PasswordChanged("river stone lantern".toCharArray(), "river stone lantern".toCharArray()))
            vm.onAction(CreateVaultLockAction.BiometricToggled(true))
            vm.onAction(CreateVaultLockAction.Submit("river stone lantern".toCharArray()))
            awaitItem() // enrolment request

            vm.onAction(CreateVaultLockAction.BiometricEnrolmentDeclined)

            assertThat(awaitItem()).isEqualTo(CreateVaultLockEffect.VaultCreated)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(requireNotNull(lockStore.read()).biometricSealedMasterKey).isNull()
        assertThat(session.isUnlocked).isTrue()
    }

    @Test
    fun `biometrics cannot be offered on a device that does not support them`() = runTest {
        every { capabilityChecker.capability() } returns BiometricCapability.UNSUPPORTED
        val vm = viewModel()

        vm.effects.test(timeout = KDF_TIMEOUT) {
            vm.onAction(CreateVaultLockAction.PasswordChanged("river stone lantern".toCharArray(), "river stone lantern".toCharArray()))
            vm.onAction(CreateVaultLockAction.BiometricToggled(true))
            vm.onAction(CreateVaultLockAction.Submit("river stone lantern".toCharArray()))

            assertThat(awaitItem()).isEqualTo(CreateVaultLockEffect.VaultCreated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the password is never held in UI state`() = runTest {
        val vm = viewModel()

        vm.onAction(
            CreateVaultLockAction.PasswordChanged(
                "river stone lantern".toCharArray(),
                "river stone lantern".toCharArray(),
            ),
        )

        assertThat(vm.uiState.value.toString()).doesNotContain("river")
    }
}
