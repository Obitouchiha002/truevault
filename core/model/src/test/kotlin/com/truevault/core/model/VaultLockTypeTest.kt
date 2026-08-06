package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VaultLockTypeTest {

    @Test
    fun `pin lengths are exactly four and six`() {
        assertThat(VaultLockType.PIN_4.pinLength).isEqualTo(4)
        assertThat(VaultLockType.PIN_6.pinLength).isEqualTo(6)
        assertThat(VaultLockType.PASSPHRASE.pinLength).isNull()
    }

    @Test
    fun `only four and six digit pins are offered`() {
        assertThat(VaultLockType.forPinLength(4)).isEqualTo(VaultLockType.PIN_4)
        assertThat(VaultLockType.forPinLength(6)).isEqualTo(VaultLockType.PIN_6)
        assertThat(VaultLockType.forPinLength(5)).isNull()
        assertThat(VaultLockType.forPinLength(8)).isNull()
    }

    @Test
    fun `isPin distinguishes the two families`() {
        assertThat(VaultLockType.PIN_4.isPin).isTrue()
        assertThat(VaultLockType.PIN_6.isPin).isTrue()
        assertThat(VaultLockType.PASSPHRASE.isPin).isFalse()
    }
}
