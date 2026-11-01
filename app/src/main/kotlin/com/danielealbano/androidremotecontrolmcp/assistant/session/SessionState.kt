package com.danielealbano.androidremotecontrolmcp.assistant.session

sealed interface SessionState {
    data object Idle : SessionState

    data object Running : SessionState

    data object AwaitingConfirmation : SessionState
}
