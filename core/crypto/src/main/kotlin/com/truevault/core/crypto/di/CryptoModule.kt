package com.truevault.core.crypto.di

import com.truevault.core.crypto.keystore.AndroidHardwareKeyStore
import com.truevault.core.crypto.keystore.HardwareKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindsHardwareKeyStore(impl: AndroidHardwareKeyStore): HardwareKeyStore
}
