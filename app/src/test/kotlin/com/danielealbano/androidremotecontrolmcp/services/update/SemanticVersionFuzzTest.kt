package com.danielealbano.androidremotecontrolmcp.services.update

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.math.sign
import kotlin.random.Random

@DisplayName("SemanticVersion property tests")
class SemanticVersionFuzzTest {
    private class Generated(
        val text: String,
        val core: List<BigInteger>,
        val pre: List<String>,
    )

    private fun randomNumber(rnd: Random): BigInteger =
        when (rnd.nextInt(6)) {
            0 -> BigInteger.valueOf(rnd.nextLong(0, 4))
            1 -> BigInteger.valueOf(rnd.nextLong(0, 1000))
            2 -> BigInteger.valueOf(Int.MAX_VALUE.toLong() + rnd.nextLong(-2, 3))
            3 -> BigInteger(rnd.nextInt(1, MAX_BITS), java.util.Random(rnd.nextLong()))
            else -> BigInteger.valueOf(rnd.nextLong(0, 12))
        }

    private fun digits(
        rnd: Random,
        value: BigInteger,
    ): String = "0".repeat(if (rnd.nextInt(4) == 0) rnd.nextInt(1, 4) else 0) + value.toString()

    private fun randomIdentifier(rnd: Random): String =
        when (rnd.nextInt(5)) {
            0 -> {
                digits(rnd, randomNumber(rnd))
            }

            1 -> {
                ""
            }

            2 -> {
                "-"
            }

            else -> {
                val alphabet = "0123456789abcAB-"
                String(CharArray(rnd.nextInt(1, 5)) { alphabet[rnd.nextInt(alphabet.length)] })
            }
        }

    private fun generate(rnd: Random): Generated {
        val core = List(3) { randomNumber(rnd) }
        val pre = if (rnd.nextInt(3) == 0) emptyList() else List(rnd.nextInt(1, 4)) { randomIdentifier(rnd) }
        val sb = StringBuilder()
        if (rnd.nextBoolean()) sb.append('v')
        sb.append(core.joinToString(".") { digits(rnd, it) })
        if (pre.isNotEmpty()) sb.append('-').append(pre.joinToString("."))
        if (rnd.nextInt(4) == 0) sb.append('+').append("abc1234.7")
        val padded = if (rnd.nextInt(5) == 0) " $sb\n" else sb.toString()
        return Generated(padded, core, pre)
    }

    private fun expectedParse(g: Generated): SemanticVersion? {
        val max = BigInteger.valueOf(Int.MAX_VALUE.toLong())
        val joined = g.pre.joinToString(".")
        val parsable = g.core.all { it <= max } && !(g.pre.isNotEmpty() && joined.isEmpty())
        return if (parsable) SemanticVersion(g.core[0].toInt(), g.core[1].toInt(), g.core[2].toInt(), joined) else null
    }

    private fun isNumeric(id: String) = id.isNotEmpty() && id.all { it in '0'..'9' }

    private fun compareIdentifiers(
        x: String,
        y: String,
    ): Int =
        when {
            isNumeric(x) && isNumeric(y) -> BigInteger(x).compareTo(BigInteger(y))
            isNumeric(x) -> -1
            isNumeric(y) -> 1
            else -> x.compareTo(y)
        }

    private fun referenceCompare(
        a: SemanticVersion,
        b: SemanticVersion,
    ): Int {
        val core = compareValuesBy(a, b, { it.major }, { it.minor }, { it.patch })
        val x = a.preRelease.split('.')
        val y = b.preRelease.split('.')
        return when {
            core != 0 -> {
                core.sign
            }

            a.preRelease == b.preRelease -> {
                0
            }

            a.preRelease.isEmpty() -> {
                1
            }

            b.preRelease.isEmpty() -> {
                -1
            }

            else -> {
                x.zip(y).map { (p, q) -> compareIdentifiers(p, q).sign }.firstOrNull { it != 0 }
                    ?: x.size.compareTo(y.size).sign
            }
        }
    }

    @Test
    fun `parse of generated versions matches the reference and the core string round-trips`() {
        val seed = 0x5E3B01L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val g = generate(rnd)
            val ctx = "seed=$seed iteration=$i input='${FuzzStrings.escape(g.text)}'"
            val parsed = SemanticVersion.parse(g.text)
            assertEquals(expectedParse(g), parsed, ctx)
            if (parsed != null) {
                assertEquals(parsed.copy(preRelease = ""), SemanticVersion.parse(parsed.toCoreString()), ctx)
            }
        }
    }

    @Test
    fun `parse never throws on arbitrary text`() {
        val seed = 0x5E3B02L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val text = FuzzStrings.random(rnd, 40)
            val result = runCatching { SemanticVersion.parse(text) }
            assertNull(result.exceptionOrNull(), "seed=$seed iteration=$i input='${FuzzStrings.escape(text)}'")
        }
    }

    @Test
    fun `compare is antisymmetric transitive and agrees with the reference over a pool`() {
        val seed = 0x5E3B03L
        val rnd = Random(seed)
        val pool = generateSequence { expectedParse(generate(rnd)) }.take(POOL_SIZE).toList()
        for (a in pool) {
            assertEquals(0, a.compareTo(a), "seed=$seed reflexive $a")
            for (b in pool) {
                val ab = a.compareTo(b).sign
                assertEquals(-ab, b.compareTo(a).sign, "seed=$seed antisymmetry $a vs $b")
                assertEquals(referenceCompare(a, b), ab, "seed=$seed reference $a vs $b")
            }
        }
        for (a in pool) {
            for (b in pool) {
                for (c in pool) {
                    if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0) {
                        assertEquals(true, a.compareTo(c) <= 0, "seed=$seed transitivity $a <= $b <= $c")
                    }
                }
            }
        }
    }

    private companion object {
        const val ITERATIONS = 4000
        const val POOL_SIZE = 70
        const val MAX_BITS = 100
    }
}
