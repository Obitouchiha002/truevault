package com.truevault.core.testing.crypto

import com.truevault.core.crypto.vault.VaultLockRecord
import com.truevault.core.crypto.vault.VaultLockStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVaultLockStore : VaultLockStore {

    private val state = MutableStateFlow<VaultLockRecord?>(null)

    override val record: Flow<VaultLockRecord?> = state

    override suspend fun read(): VaultLockRecord? = state.value

    override suspend fun write(record: VaultLockRecord) {
        state.value = record
    }

    override suspend fun clear() {
        state.value = null
    }

    /** Lets a test corrupt the stored blob the way a damaged file or an attacker would. */
    fun corruptSealedMasterKey(byteIndex: Int = 20) {
        val current = state.value ?: return
        val damaged = current.sealedMasterKey.copyOf()
        damaged[byteIndex] = (damaged[byteIndex] + 1).toByte()
        state.value = current.copy(sealedMasterKey = damaged)
    }

    fun truncateSealedMasterKey() {
        val current = state.value ?: return
        state.value = current.copy(sealedMasterKey = current.sealedMasterKey.copyOf(8))
    }
}
