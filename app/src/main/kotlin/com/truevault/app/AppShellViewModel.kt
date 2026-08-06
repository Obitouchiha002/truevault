package com.truevault.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.data.PendingShareBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State the app shell needs that does not belong to any one screen.
 *
 * Only whether a share is waiting — not what was shared. The URIs are file locations on the user's
 * device, and nothing outside the import flow has a reason to hold them.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    pendingShareBuffer: PendingShareBuffer,
) : ViewModel() {

    val hasPendingShare: StateFlow<Boolean> = pendingShareBuffer.pending
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}
