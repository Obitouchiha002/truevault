package com.truevault.core.capabilities.di

import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.DeviceCapabilityDetectorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CapabilitiesModule {

    @Binds
    @Singleton
    abstract fun bindsDetector(impl: DeviceCapabilityDetectorImpl): DeviceCapabilityDetector
}
