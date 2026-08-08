package com.truevault.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.truevault.core.remote.InstallStatus
import com.truevault.core.remote.RemoteGateRepository
import com.truevault.feature.admin.presentation.BlockedScreen
import com.truevault.feature.admin.presentation.NamePromptScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the gate has decided about this launch.
 *
 * [Deciding] exists for a single frame or two while the cached answer is read off disk. It is not a
 * network wait: the app never blocks on the network to open, because a vault that will not open
 * without a connection is not a vault the owner controls.
 */
sealed interface GateState {
    data object Deciding : GateState
    data object NeedsName : GateState
    data object Blocked : GateState
    data object Open : GateState
}

@HiltViewModel
class RemoteGateViewModel @Inject constructor(
    private val gate: RemoteGateRepository,
) : ViewModel() {

    /**
     * The version, supplied by the host on its first composition.
     *
     * A `Deferred` rather than a plain field because `init` runs at construction, before any
     * composable can set anything — a plain field would have sent the very first check-in, the one
     * that registers the install, with an empty version string.
     */
    private val appVersion = CompletableDeferred<String>()

    private val _state = MutableStateFlow<GateState>(GateState.Deciding)
    val state: StateFlow<GateState> = _state.asStateFlow()

    private val _premium = MutableStateFlow(false)
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    init {
        viewModelScope.launch {
            // Cached first, always. This is what makes the app hybrid rather than online-only: the
            // last answer the backend gave is on disk, so the decision is instant and identical
            // whether or not there is a connection right now.
            gate.restore()

            when {
                !gate.isEnabled -> _state.value = GateState.Open

                // No name yet — a fresh install, or a reinstall whose storage was wiped. Ask for the
                // name BEFORE the first check-in, never after. Checking in first registered the
                // install with an empty name, which is exactly the "(no name)" rows showing up in
                // the admin panel. onNameEntered() does the check-in once there is a name to send.
                !gate.hasDisplayName() -> _state.value = GateState.NeedsName

                // Name already on file. A reinstall wipes app-private storage, so a blocked device
                // comes back with an empty cache and "no decision" looks like "not blocked". Opening
                // straight away would hand it a working app until the background check-in landed —
                // and forever, if it stayed offline. So wait for a real answer, with a timeout, then
                // open anyway: a genuine user on a train must not be shut out of an offline app.
                else -> {
                    if (!gate.hasEverCheckedIn()) {
                        withTimeoutOrNull(FIRST_CHECK_IN_TIMEOUT_MS) { gate.checkIn(appVersion.await()) }
                    }
                    _state.value = decide(gate.currentStatus(), gate.hasDisplayName())
                }
            }
        }
        viewModelScope.launch {
            gate.status.collect { status ->
                _premium.value = status.premium
                // A name already given is never asked for twice, even if a check-in later blocks.
                _state.value = decide(status, gate.hasDisplayName())
            }
        }
    }

    /**
     * Fired after the cached decision, and again whenever the app returns to the foreground. It runs
     * in the background and nothing waits on it: if it succeeds the state updates through the flow
     * above, and if there is no connection the cached decision simply stands. The user sees no
     * spinner and no difference either way.
     */
    fun checkIn(version: String) {
        viewModelScope.launch { gate.checkIn(version) }
    }

    fun onNameEntered(version: String) {
        viewModelScope.launch {
            _state.value = decide(gate.currentStatus(), gate.hasDisplayName())
            gate.checkIn(version)
        }
    }

    fun setAppVersion(version: String) { appVersion.complete(version) }

    private fun decide(status: InstallStatus, hasName: Boolean): GateState = when {
        !gate.isEnabled -> GateState.Open
        status.blocked -> GateState.Blocked
        !hasName -> GateState.NeedsName
        else -> GateState.Open
    }
}

/**
 * Wraps the whole app.
 *
 * Everything past this point is the app exactly as it was before the backend existed. The gate adds
 * one screen on first launch and one when an install is suspended, and is otherwise invisible —
 * which is the whole requirement: a user who is online and not blocked should never be able to tell
 * this layer is here.
 */
@Composable
fun RemoteGateHost(
    appVersion: String,
    viewModel: RemoteGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handed over before anything is drawn, which is what releases the first check-in.
    LaunchedEffect(appVersion) { viewModel.setAppVersion(appVersion) }

    when (state) {
        // Nothing is drawn for the frame it takes to read a cached boolean. Drawing the app and
        // then snatching it away would be worse than a blank frame.
        GateState.Deciding -> Unit
        GateState.NeedsName -> NamePromptScreen(onDone = { viewModel.onNameEntered(appVersion) })
        GateState.Blocked -> BlockedScreen(appVersion = appVersion)
        GateState.Open -> content()
    }
}

/** Long enough for a slow connection, short enough not to look like a hang. */
private const val FIRST_CHECK_IN_TIMEOUT_MS = 6_000L
