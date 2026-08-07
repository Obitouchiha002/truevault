package com.truevault.feature.admin.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.remote.InstallRecord

/**
 * Asked once, on first launch, when a backend is configured.
 *
 * There is no account here and no verification. It is a label so the person running the backend can
 * tell one install from another, and the screen says exactly that rather than implying a sign-up.
 */
@Composable
fun NamePromptScreen(
    onDone: () -> Unit,
    viewModel: NamePromptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("What should we call you?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "Just a name for this device — no account, no email, nothing to remember. " +
                    "You can type anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue") }
        }
    }
}

/**
 * Shown when the backend says this install is blocked.
 *
 * The vault is untouched — every file is still encrypted on the device, and a block does not delete
 * anything. The screen says so, because a person locked out of their own photos deserves to know
 * whether they have lost them.
 */
@Composable
fun BlockedScreen(
    appVersion: String,
    viewModel: BlockedViewModel = hiltViewModel(),
) {
    val status by viewModel.uiState.collectAsStateWithLifecycle()
    val checking by viewModel.isChecking.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("This app is suspended", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                status.reason ?: "Access to this app has been suspended on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status.code?.let {
                Spacer(Modifier.height(8.dp))
                Text("Code $it", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Your files have not been deleted. They are still encrypted on this device and " +
                    "will open again if the suspension is lifted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { viewModel.retry(appVersion) }, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check again")
            }
        }
    }
}
