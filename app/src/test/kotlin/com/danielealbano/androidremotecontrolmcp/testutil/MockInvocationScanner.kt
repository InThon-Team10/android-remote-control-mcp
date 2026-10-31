package com.danielealbano.androidremotecontrolmcp.testutil

import io.mockk.MockKGateway
import io.mockk.clearMocks
import io.mockk.impl.JvmMockKGateway
import kotlin.math.abs

/** Reads the calls recorded by MockK mocks and reports the ones that received a [Sentinel] value. */
internal object MockInvocationScanner {
    private const val RELATIVE_TOLERANCE = 1e-6
    private const val ABSOLUTE_TOLERANCE = 1e-9
    private const val ARG_PREVIEW = 60

    /** Forgets the recorded calls of [mocks], keeping their stubbed answers. */
    fun clear(mocks: List<Any>) {
        mocks.forEach { clearMocks(it, answers = false, recordedCalls = true, childMocks = false) }
    }

    fun recordedCallCount(mocks: List<Any>): Int {
        val repository = (MockKGateway.implementation() as JvmMockKGateway).stubRepo
        return mocks.sumOf { repository.stubFor(it).allRecordedCalls().size }
    }

    /** One description per (mock method, sentinel) pair that received a contract-breaking value. */
    fun hits(
        mocks: List<Any>,
        sentinels: List<Sentinel>,
    ): List<Pair<Sentinel, String>> {
        val repository = (MockKGateway.implementation() as JvmMockKGateway).stubRepo
        val found = mutableListOf<Pair<Sentinel, String>>()
        for (mock in mocks) {
            for (invocation in repository.stubFor(mock).allRecordedCalls()) {
                val leaves = mutableListOf<Any?>()
                invocation.args.forEach { flatten(it, leaves) }
                for (sentinel in sentinels.filter { s -> leaves.any { reaches(s, it) } }) {
                    val target = "${mock::class.java.simpleName}.${invocation.method.name}"
                    val args = invocation.args.map { it.toString().take(ARG_PREVIEW) }
                    found.add(sentinel to "${describe(sentinel)} reached $target args=$args")
                }
            }
        }
        return found
    }

    private fun describe(sentinel: Sentinel): String =
        when (sentinel) {
            is Sentinel.Number -> "param ${sentinel.param} out-of-range value ${sentinel.value}"
            is Sentinel.Text -> "param ${sentinel.param} invalid value ${sentinel.text}"
            is Sentinel.Length -> "param ${sentinel.param} string over ${sentinel.maxLength} chars"
        }

    private fun flatten(
        value: Any?,
        out: MutableList<Any?>,
    ) {
        when (value) {
            null, is kotlin.coroutines.Continuation<*> -> Unit
            is Number, is CharSequence, is Boolean -> out.add(value)
            is Iterable<*> -> value.forEach { flatten(it, out) }
            is Array<*> -> value.forEach { flatten(it, out) }
            is Map<*, *> -> value.forEach { (k, v) -> flatten(k, out).also { flatten(v, out) } }
            else -> out.add(value.toString())
        }
    }

    private fun reaches(
        sentinel: Sentinel,
        leaf: Any?,
    ): Boolean =
        when (sentinel) {
            is Sentinel.Number -> {
                val tolerance = abs(sentinel.value) * RELATIVE_TOLERANCE + ABSOLUTE_TOLERANCE
                leaf is Number && abs(leaf.toDouble() - sentinel.value) <= tolerance
            }

            is Sentinel.Text -> {
                leaf?.toString()?.contains(sentinel.text) == true
            }

            is Sentinel.Length -> {
                leaf is CharSequence && leaf.length > sentinel.maxLength
            }
        }
}
