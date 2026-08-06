package com.truevault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.truevault.app.ui.TrueVaultApp
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.data.PendingShareBuffer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * It extends [FragmentActivity] because `BiometricPrompt` requires one — the prompt is hosted as a
 * fragment, and no Compose-only alternative can bind a biometric result to a Keystore key.
 *
 * Two things happen here that cannot be done from Compose:
 *  - [WindowManager.LayoutParams.FLAG_SECURE] is applied to the window itself. Hiding content at
 *    the Compose level would not stop a screenshot or a screen recording; the window flag does,
 *    and it also blanks the app's entry in the recent-apps switcher.
 *  - The splash screen is held until preferences have been read once, so the app never draws its
 *    first frame in the wrong theme.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var pendingShares: PendingShareBuffer

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        handleShare(intent)

        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value is MainActivityUiState.Loading
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    applyScreenshotProtection(state.blockScreenshots)
                }
            }
        }

        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences = state.settingsOrDefault()

            TrueVaultTheme(
                themePreference = preferences.theme,
                useDynamicColor = preferences.useDynamicColor,
            ) {
                TrueVaultApp(
                    startDestination = state.startDestination(),
                    lockState = state.lockState(),
                )
            }
        }
    }

    /**
     * A share can arrive while the activity is already running, because the launch mode is
     * `singleTask`. Without this, the second share of a session would be silently dropped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /**
     * Parks incoming files until the vault is unlocked.
     *
     * Nothing is read, decrypted or imported here. The URIs go into an in-memory buffer and the
     * navigation layer picks them up once there is a key to encrypt with — a share that arrives at a
     * locked vault must neither be lost nor acted on.
     */
    private fun handleShare(intent: Intent?) {
        if (intent == null || !SharedIntentReader.isShare(intent)) return

        val uris = SharedIntentReader.read(intent)
        SharedIntentReader.takeReadPermission(intent, uris, contentResolver)
        pendingShares.offer(uris.map(Uri::toString))
    }

    private fun applyScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
