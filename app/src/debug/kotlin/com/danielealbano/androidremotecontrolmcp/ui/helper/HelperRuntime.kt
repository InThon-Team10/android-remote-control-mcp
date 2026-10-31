package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Main-thread state published by the actual overlay host, never by tutorial preferences. */
internal object HelperRuntime {
    var visible by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var suppressed = false
        private set
    var show: (() -> Unit)? = null
    var hide: (() -> Unit)? = null
    var ask: (() -> Unit)? = null
    private var restoreAfterPractice = false

    fun suppress(value: Boolean) {
        if (value == suppressed) return
        if (value) {
            restoreAfterPractice = visible
            suppressed = true
            hide?.invoke()
        } else {
            suppressed = false
            if (restoreAfterPractice) show?.invoke()
            restoreAfterPractice = false
        }
    }

    fun connect() {
        connected = true
        if (suppressed) restoreAfterPractice = true
    }

    fun disconnect() {
        connected = false
        visible = false
        show = null
        hide = null
        ask = null
    }
}
