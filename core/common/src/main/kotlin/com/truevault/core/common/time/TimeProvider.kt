package com.truevault.core.common.time

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable time source.
 *
 * Session expiry and auto-lock depend on elapsed time, and a lock that can be defeated by changing
 * the device clock is not a lock — so [elapsedRealtimeMillis] (monotonic, survives clock changes)
 * is what security decisions use. [currentTimeMillis] is only for user-visible timestamps.
 */
interface TimeProvider {
    /** Wall-clock time, for display and stored metadata. Can move backwards. */
    fun currentTimeMillis(): Long

    /** Monotonic time since boot, including deep sleep. Used for every timeout decision. */
    fun elapsedRealtimeMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindsTimeProvider(impl: SystemTimeProvider): TimeProvider
}
