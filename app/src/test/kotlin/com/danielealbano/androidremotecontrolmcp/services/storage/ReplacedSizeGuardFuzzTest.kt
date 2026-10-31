package com.danielealbano.androidremotecontrolmcp.services.storage

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.random.Random

@DisplayName("requireReplacedSizeWithinLimit property tests")
class ReplacedSizeGuardFuzzTest {
    private fun utf8Size(value: String) = value.toByteArray(Charsets.UTF_8).size.toLong()

    @Test
    fun `guard agrees with BigInteger arithmetic and only throws ActionFailed`() {
        val seed = 0x51AE01L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val limitMb = rnd.nextInt(1, 2000)
            val config = ServerConfig(fileSizeLimitMb = limitMb)
            val limit = BigInteger.valueOf(limitMb.toLong()).shiftLeft(20)
            val oldString = FuzzStrings.random(rnd, 12)
            val newString = FuzzStrings.random(rnd, 12)
            val count = if (rnd.nextBoolean()) rnd.nextInt(0, 100) else rnd.nextInt(0, Int.MAX_VALUE)
            // Around the boundary half of the time, otherwise anywhere up to 4 TiB.
            val growth = utf8Size(newString) - utf8Size(oldString)
            val projectedDelta = BigInteger.valueOf(count.toLong()).multiply(BigInteger.valueOf(growth))
            val fileSize =
                if (rnd.nextBoolean()) {
                    (limit - projectedDelta + BigInteger.valueOf(rnd.nextLong(-3, 4)))
                        .max(BigInteger.ZERO)
                        .min(BigInteger.valueOf(MAX_FILE))
                        .toLong()
                } else {
                    rnd.nextLong(0, MAX_FILE)
                }
            val expectedThrow = BigInteger.valueOf(fileSize).add(projectedDelta) > limit
            val ctx =
                "seed=$seed iteration=$i fileSize=$fileSize count=$count growth=$growth limitMb=$limitMb"
            val thrown =
                try {
                    requireReplacedSizeWithinLimit(fileSize, count, oldString, newString, config)
                    null
                } catch (e: McpToolException.ActionFailed) {
                    e
                }
            assertEquals(expectedThrow, thrown != null, ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 20000
        const val MAX_FILE = 1L shl 42
    }
}
