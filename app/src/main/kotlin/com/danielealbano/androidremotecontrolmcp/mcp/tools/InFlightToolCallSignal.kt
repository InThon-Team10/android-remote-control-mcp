package com.danielealbano.androidremotecontrolmcp.mcp.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Balanced in-flight counter of MCP tool calls, observable by the on-device assistant. Unlike the visual indicator it
 * is never disabled by the "tool call indicator" setting. A finish without a matching start is ignored.
 */
@Singleton
class InFlightToolCallSignal
    @Inject
    constructor() : ToolCallIndicator {
        private val lock = Any()
        private var inFlight = 0
        private val _active = MutableStateFlow(false)

        /** True while at least one tool call is in flight. */
        val active: StateFlow<Boolean> = _active.asStateFlow()

        override fun onToolCallStarted(toolName: String) {
            synchronized(lock) {
                inFlight++
                _active.value = true
            }
        }

        override fun onToolCallFinished(toolName: String) {
            synchronized(lock) {
                if (inFlight > 0) inFlight--
                _active.value = inFlight > 0
            }
        }
    }
