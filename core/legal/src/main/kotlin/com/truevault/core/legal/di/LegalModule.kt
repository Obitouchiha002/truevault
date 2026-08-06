package com.truevault.core.legal.di

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.truevault.core.legal.AppVersionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LegalModule {

    /**
     * Reads the app's own version from the package manager.
     *
     * Bound here rather than injected from `:app` so `:core:legal` stays independent of the
     * application module, and so a missing package (which cannot happen for the running app, but
     * the API insists it might) degrades to zero rather than to a crash on the first screen a user
     * ever sees.
     */
    @Provides
    @Singleton
    fun provideAppVersionProvider(
        @ApplicationContext context: Context,
    ): AppVersionProvider = object : AppVersionProvider {

        private val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        override val versionCode: Long =
            info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

        override val versionName: String = info?.versionName.orEmpty()
    }
}
