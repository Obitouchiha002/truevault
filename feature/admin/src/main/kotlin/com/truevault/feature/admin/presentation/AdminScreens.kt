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

/**
 * The admin panel. Reached only by tapping a corner five times and entering the PIN.
 *
 * The PIN is never stored on the device — it is held in this screen's state for as long as the
 * panel is open and sent with each call, exactly as StreamGarden does it. An extracted APK
 * therefore yields the public anon key and nothing that grants admin.
 */
@Composable
fun AdminPanelScreen(
    onClose: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (!state.authorised) {
                Spacer(Modifier.height(60.dp))
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::onPinChanged,
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::unlock,
                        enabled = state.canSubmitPin,
                    ) { Text(if (state.isBusy) "…" else "Enter") }
                    TextButton(onClick = onClose) { Text("Back") }
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.installs.size} install(s)", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Named for what it does. On a backend shared with StreamGarden this flag lives in one
                    // `app_config` row, so flipping it here suspends that app's users too.
                    Text("Kill ALL apps", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = state.killSwitch, onCheckedChange = viewModel::setKillSwitch)
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.installs, key = { it.id }) { install ->
                    InstallCard(
                        install = install,
                        onBlockToggle = { blocked ->
                            viewModel.setBlocked(
                                id = install.id,
                                blocked = blocked,
                                reason = if (blocked) "Access suspended by the developer." else null,
                                minutes = null,
                                code = if (blocked) "403" else null,
                            )
                        },
                        onPremiumToggle = { viewModel.setPremium(install.id, it) },
                        showPremium = state.premiumSupported,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallCard(
    install: InstallRecord,
    onBlockToggle: (Boolean) -> Unit,
    onPremiumToggle: (Boolean) -> Unit,
    showPremium: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                install.name?.takeIf { it.isNotBlank() } ?: "(no name)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${install.platform ?: "?"} · v${install.version ?: "?"} · last seen ${install.lastSeen?.take(16) ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blocked", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = install.blocked, onCheckedChange = onBlockToggle)
                }
                if (showPremium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Premium", style = MaterialTheme.typography.labelMedium)
                        Switch(checked = install.premium, onCheckedChange = onPremiumToggle)
                    }
                }
            }
        }
    }
}
