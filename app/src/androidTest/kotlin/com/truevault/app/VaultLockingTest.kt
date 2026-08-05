package com.truevault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.truevault.core.crypto.session.VaultLockState
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.model.AutoLockDuration
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The locking contract, against the real Android Keystore.
 *
 * The unit tests use an in-memory stand-in for the Keystore because it does not exist on the JVM.
 * This runs the same guarantees against the real thing: a wrong password is refused, and
 * backgrounding drops the key.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VaultLockingTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var keyManager: VaultKeyManager

    @Inject
    lateinit var session: VaultSession

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun aVaultCreatedOnDeviceOpensWithItsPasswordAndNotWithAnother() = runTest {
        val password = "instrumented-test-passphrase"
        keyManager.createLock(password.toCharArray())
        session.lock()

        assertThat(session.state.value).isEqualTo(VaultLockState.Locked)

        keyManager.unlockWithPassword("wrong-passphrase".toCharArray())
        assertThat(session.isUnlocked).isFalse()

        keyManager.unlockWithPassword(password.toCharArray())
        assertThat(session.isUnlocked).isTrue()
    }

    @Test
    fun backgroundingWithTheDefaultSettingLocksImmediately() = runTest {
        keyManager.createLock("another-instrumented-passphrase".toCharArray())

        session.onAppBackgrounded(AutoLockDuration.IMMEDIATE)

        assertThat(session.isUnlocked).isFalse()
    }
}
