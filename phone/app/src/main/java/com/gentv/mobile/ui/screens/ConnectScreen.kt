package com.gentv.mobile.ui.screens

import androidx.compose.runtime.Composable
import com.gentv.mobile.TvSummary
import com.gentv.core.flow.ConnectEvent
import com.gentv.core.flow.ConnectState

/** The discovery and connection states share the same visual shell; their state machine is unchanged. */
@Composable
fun ConnectScreen(state: ConnectState, onEvent: (ConnectEvent) -> Unit, onShareLogs: () -> Unit,
    currentTv: TvSummary? = null, onNavigate: (Int) -> Unit = {}, onBack: () -> Unit = {}) {
    when (state) {
        is ConnectState.Scanning, is ConnectState.NothingFound -> DiscoveryScreen(state, currentTv, onEvent, onNavigate, onBack)
        else -> ConnectionFlowScreen(state, onEvent, onShareLogs, onNavigate, onBack)
    }
}
