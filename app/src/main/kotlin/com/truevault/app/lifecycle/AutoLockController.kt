package com.truevault.app.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.truevault.core.common.dispatcher.ApplicationScope
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.AutoLockDuration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Locks the vault when TrueVault stops being on screen.
 *
 * Two independent signals are watched, because they are genuinely different events:
 *
 *  - **Process lifecycle** covers the user leaving the app — home button, app switcher, another app
 *    taking over. This is the one the default [AutoLockDuration.IMMEDIATE] responds to.
 *  - **`ACTION_SCREEN_OFF`** covers the screen turning off while TrueVault is still foregrounded.
 *    The process lifecycle does not move in that case, so without this receiver a vault left open on
 *    a pocketed phone would stay open.
 *
 * The receiver is registered for the life of the process. It carries no data, is not exported, and
 * responds only to a system broadcast.
 */
@Singleton
class AutoLockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: VaultSession,
    private val preferences: UserPreferencesDataSource,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val autoLockDuration = MutableStateFlow(AutoLockDuration.IMMEDIATE)
    private val lockOnScreenOff = MutableStateFlow(true)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                session.onScreenOff(
                    lockOnScreenOff = lockOnScreenOff.value,
                    duration = autoLockDuration.value,
                )
            }
        }
    }

    fun start() {
        preferences.userPreferences
            .onEach { prefs ->
                autoLockDuration.value = prefs.autoLockDuration
                lockOnScreenOff.value = prefs.lockOnScreenOff
            }
            .launchIn(applicationScope)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        ContextCompat.registerReceiver(
            context,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        session.onAppBackgrounded(autoLockDuration.value)
    }

    override fun onStart(owner: LifecycleOwner) {
        session.onAppForegrounded()
    }
}
