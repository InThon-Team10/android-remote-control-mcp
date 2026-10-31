package com.danielealbano.androidremotecontrolmcp.services.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.danielealbano.androidremotecontrolmcp.data.model.AppFilter
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("AppManagerImpl.listInstalledApps property tests")
class AppListFuzzTest {
    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private lateinit var manager: AppManagerImpl

    @BeforeEach
    fun setUp() {
        mockkStatic(PackageInfoCompat::class)
        every { PackageInfoCompat.getLongVersionCode(any()) } returns 1L
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns PackageInfo()
        manager = AppManagerImpl(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(PackageInfoCompat::class)
    }

    // Independent of String.contains(ignoreCase): char-wise comparison by upper then lower case folding
    private fun referenceContainsIgnoreCase(
        haystack: String,
        needle: String,
    ): Boolean {
        fun same(
            a: Char,
            b: Char,
        ) = a == b || a.uppercaseChar() == b.uppercaseChar() || a.lowercaseChar() == b.lowercaseChar()
        return needle.length <= haystack.length &&
            (0..haystack.length - needle.length).any { start ->
                needle.indices.all { same(haystack[start + it], needle[it]) }
            }
    }

    @Test
    fun `listing filters by system flag and case-insensitive name, keeps every match and sorts by lowercase name`() =
        runBlocking {
            val seed = 0xA99001L
            val rnd = Random(seed)
            repeat(ITERATIONS) { i ->
                val labels = mutableMapOf<ApplicationInfo, String>()
                val apps =
                    List(rnd.nextInt(0, 12)) { index ->
                        val info = ApplicationInfo()
                        info.packageName = "pkg.app$index"
                        val systemBit = if (rnd.nextBoolean()) ApplicationInfo.FLAG_SYSTEM else 0
                        info.flags = systemBit or (rnd.nextInt() and ApplicationInfo.FLAG_SYSTEM.inv())
                        labels[info] = FuzzStrings.random(rnd, 12)
                        info
                    }
                every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns apps
                apps.forEach { every { packageManager.getApplicationLabel(it) } returns labels.getValue(it) }
                val filter = AppFilter.entries[rnd.nextInt(AppFilter.entries.size)]
                val anyLabel = labels.values.randomOrNull(rnd).orEmpty()
                val query =
                    when (rnd.nextInt(4)) {
                        0 -> null
                        1 -> ""
                        2 -> anyLabel.substring(0, rnd.nextInt(anyLabel.length + 1)).uppercase()
                        else -> FuzzStrings.random(rnd, 3)
                    }
                val ctx = "seed=$seed iteration=$i filter=$filter query='${FuzzStrings.escape(query.orEmpty())}'"
                val expected =
                    apps.filter { app ->
                        val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val flagOk = filter == AppFilter.ALL || (filter == AppFilter.SYSTEM) == system
                        flagOk && (query == null || referenceContainsIgnoreCase(labels.getValue(app), query))
                    }
                val actual = manager.listInstalledApps(filter, query)
                assertEquals(expected.map { it.packageName }.sorted(), actual.map { it.packageId }.sorted(), ctx)
                val sortKeys = actual.map { it.name.lowercase() }
                assertEquals(sortKeys.sorted(), sortKeys, "$ctx not sorted by lowercase name")
                val systemById = apps.associate { it.packageName to ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
                assertTrue(actual.all { it.isSystemApp == systemById[it.packageId] }, ctx)
            }
        }

    private companion object {
        const val ITERATIONS = 3000
    }
}
