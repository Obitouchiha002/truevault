package com.truevault.core.capabilities.di

import android.content.Context
import android.os.Build
import com.truevault.core.capabilities.privateapps.Android15PrivateAppsController
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.core.capabilities.privateapps.UnsupportedPrivateAppsController
import com.truevault.core.capabilities.provider.LauncherRoleProvider
import com.truevault.core.capabilities.provider.PrivateSpaceCapabilityProvider
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object PrivateAppsModule {

    /**
     * Picks the implementation at runtime.
     *
     * The API 35 class is constructed only when `SDK_INT` is at least 35, so its class is never
     * loaded — let alone verified — on an older device. This is what keeps Android 8 free of
     * `NoClassDefFoundError` without a single try/catch around version detection.
     */
    @Provides
    @Singleton
    fun providesPrivateAppsController(
        @ApplicationContext context: Context,
        privateSpaceProvider: PrivateSpaceCapabilityProvider,
        launcherRoleProvider: LauncherRoleProvider,
        @Dispatcher(TrueVaultDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
    ): PrivateAppsController = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        Android15PrivateAppsController(
            context = context,
            privateSpaceProvider = privateSpaceProvider,
            launcherRoleProvider = launcherRoleProvider,
            defaultDispatcher = defaultDispatcher,
        )
    } else {
        UnsupportedPrivateAppsController()
    }
}
