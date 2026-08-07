package com.truevault.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.truevault.core.remote.InstallStatus
import com.truevault.core.remote.RemoteGateRepository
import com.truevault.feature.admin.presentation.AdminPanelScreen
import com.truevault.feature.admin.presentation.BlockedScreen
import com.truevault.feature.admin.presentation.NamePromptScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            _state.value = decide(gate.currentStatus(), gate.hasDisplayName())
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
    var showAdmin by remember { mutableStateOf(false) }

    if (showAdmin) {
        AdminPanelScreen(onClose = { showAdmin = false })
        return
    }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            // Nothing is drawn for the frame it takes to read a cached boolean. Drawing the app and
            // then snatching it away would be worse than a blank frame.
            GateState.Deciding -> Unit
            GateState.NeedsName -> NamePromptScreen(onDone = { viewModel.onNameEntered(appVersion) })
            GateState.Blocked -> BlockedScreen(appVersion = appVersion)
            GateState.Open -> content()
        }

        AdminTapTarget(onOpen = { showAdmin = true })
    }
}

/**
 * The way into the admin panel: five taps in the top-left corner, within two seconds of each other.
 *
 * Deliberately awkward to find and impossible to reach by accident. It sits above every screen,
 * including the suspended one, so a blocked device is still administrable — otherwise the only way
 * to recover from a mistaken block would be a reinstall, and the Android ID survives that.
 *
 * The target is transparent and 48dp square, and it **counts without consuming**. That matters more
 * than it sounds: the top-left corner is where back arrows and menu buttons live, and a box that
 * swallowed touches there would break navigation on every screen in the app for the sake of a hidden
 * door. So it watches the Initial pointer pass and never marks anything consumed — the button
 * underneath still gets the tap, and the corner behaves normally for everyone who is not counting.
 */
@Composable
private fun BoxScope.AdminTapTarget(onOpen: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(48.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.any { it.changedToDownIgnoreConsumed() }
                        if (!pressed) continue

                        val now = SystemClock.elapsedRealtime()
                        // A gap resets the count rather than extending it, so stray taps across a
                        // long session never accumulate into an accidental unlock.
                        taps = if (now - lastTapAt <= TAP_WINDOW_MS) taps + 1 else 1
                        lastTapAt = now
                        if (taps >= TAPS_TO_OPEN) {
                            taps = 0
                            onOpen()
                        }
                    }
                }
            },
    )
}

private const val TAPS_TO_OPEN = 5
private const val TAP_WINDOW_MS = 2_000L
