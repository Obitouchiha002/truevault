package com.truevault.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.feature.launcher.presentation.SecureLauncherScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Secure Launcher Mode's Home activity.
 *
 * Separate from [MainActivity] on purpose: the Home role belongs to one activity, and the vault
 * must not become a launcher just because the user opened it. This activity shows app icons only —
 * it never displays vault content, so it does not set `FLAG_SECURE` and does not require an unlocked
 * session to draw. Editing which icons are hidden does require one, and that check lives in the
 * launcher's view model.
 *
 * TrueVault appearing in Android's "choose a Home app" list is unavoidable once this activity
 * exists; the feature is never enabled or requested without the user going to
 * Settings → Advanced Privacy first.
 */
@AndroidEntryPoint
class SecureLauncherActivity : FragmentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences = state.settingsOrDefault()

            TrueVaultTheme(
                themePreference = preferences.theme,
                useDynamicColor = preferences.useDynamicColor,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SecureLauncherScreen()
                }
            }
        }
    }
}
