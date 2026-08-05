package com.truevault.core.datastore.di

import com.truevault.core.crypto.vault.VaultLockStore
import com.truevault.core.datastore.VaultLockDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultLockModule {

    @Binds
    @Singleton
    abstract fun bindsVaultLockStore(impl: VaultLockDataSource): VaultLockStore
}
