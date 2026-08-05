package com.truevault.app

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
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

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
