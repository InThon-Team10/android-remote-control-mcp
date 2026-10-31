package com.danielealbano.androidremotecontrolmcp.ui

import com.danielealbano.androidremotecontrolmcp.ui.theme.PrimaryContainerLight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@DisplayName("Branding resources")
class BrandingResourcesTest {
    @Test
    fun `launcher foreground is an inset wrapping the tap bitmap`() {
        val root = xml("src/main/res/drawable/ic_launcher_foreground.xml")

        assertEquals("inset", root.tagName)
        assertEquals("17.5%", root.getAttribute("android:inset"))
        val bitmap = root.getElementsByTagName("bitmap").item(0) as Element
        assertEquals("@drawable/helper_launcher_tap", bitmap.getAttribute("android:src"))
        assertEquals("true", bitmap.getAttribute("android:mipMap"))
        assertEquals("fill", bitmap.getAttribute("android:gravity"))
    }

    @Test
    fun `adaptive icons use the launcher background and foreground`() {
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            val root = xml("src/main/res/mipmap-anydpi-v26/$name")
            val background = drawableOf(root, "background")
            val foreground = drawableOf(root, "foreground")
            assertEquals("@color/ic_launcher_background", background)
            assertEquals("@drawable/ic_launcher_foreground", foreground)
        }
    }

    @Test
    fun `launcher background equals the primary container`() {
        val color = xml("src/main/res/values/ic_launcher_background.xml").getElementsByTagName("color").item(0)
        val expected = "#%06X".format(PrimaryContainerLight.value.shr(32).toInt() and 0xFFFFFF)

        assertEquals("#DEE0FF", expected)
        assertEquals(expected, color.textContent.trim())
    }

    @Test
    fun `every painterResource drawable resolves to a raster or a vector`() {
        val pattern = Regex("""painterResource\(\s*(?:id\s*=\s*)?R\.drawable\.(\w+)""")
        val names = ktFiles().flatMap { f -> pattern.findAll(f.readText()).map { it.groupValues[1] } }.toSet()

        assertTrue(names.isNotEmpty())
        names.forEach { name ->
            val files =
                File(file("src/main/res"), "")
                    .listFiles { f -> f.name.startsWith("drawable") }
                    .orEmpty()
                    .flatMap { dir -> dir.listFiles { f -> f.nameWithoutExtension == name }.orEmpty().toList() }
            assertTrue(files.isNotEmpty()) { "drawable $name not found" }
            files.forEach { f ->
                when (f.extension) {
                    "png", "webp" -> Unit
                    "xml" -> assertEquals("vector", xmlOf(f).tagName) { "$name is not a vector" }
                    else -> fail("unexpected drawable ${f.name}")
                }
            }
        }
    }

    @Test
    fun `the four webps are lossless metadata free and sized`() {
        val expected =
            mapOf(
                "helper_phone_mascot" to 800,
                "helper_phone_mascot_sleeping" to 800,
                "helper_phone_mascot_thinking" to 800,
                "helper_launcher_tap" to 512,
            )
        expected.forEach { (name, size) ->
            val bytes = file("src/main/res/drawable-nodpi/$name.webp").readBytes()
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WEBP", String(bytes, 8, 4))
            assertEquals("VP8L", String(bytes, 12, 4))
            val b = bytes.map { it.toInt() and 0xFF }
            assertEquals(0x2F, b[20])
            val bits = b[21] or (b[22] shl 8) or (b[23] shl 16) or (b[24] shl 24)
            assertEquals(size, (bits and 0x3FFF) + 1)
            assertEquals(size, ((bits shr 14) and 0x3FFF) + 1)
            val text = String(bytes, Charsets.ISO_8859_1)
            listOf("EXIF", "XMP ", "ICCP", "c2pa", "OpenAI", "ChatGPT", "gpt-image").forEach {
                assertFalse(text.contains(it)) { "$name contains $it" }
            }
        }
    }

    @Test
    fun `no png shadows a webp resource`() {
        val dir = file("src/main/res/drawable-nodpi")
        dir.listFiles { f -> f.extension == "webp" }.orEmpty().forEach { webp ->
            assertFalse(File(dir, "${webp.nameWithoutExtension}.png").exists())
        }
    }

    @Test
    fun `approved original has only the image chunks`() {
        val bytes = repoFile("docs/branding/approved-phone-helper.png").readBytes()
        var i = 8
        val chunks = mutableSetOf<String>()
        while (i < bytes.size) {
            val len = bytes.copyOfRange(i, i + 4).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            chunks += String(bytes, i + 4, 4)
            i += 12 + len
        }
        assertEquals(setOf("IHDR", "IDAT", "IEND"), chunks)
    }

    private fun xml(path: String): Element = xmlOf(file(path))

    private fun xmlOf(f: File): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(f)
            .documentElement

    private fun drawableOf(
        root: Element,
        tag: String,
    ): String = (root.getElementsByTagName(tag).item(0) as Element).getAttribute("android:drawable")

    private fun ktFiles(): List<File> = file("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun file(relative: String): File =
        listOf(File(relative), File("app", relative)).firstOrNull { it.exists() }
            ?: fail("$relative not found from ${File(".").absolutePath}")

    private fun repoFile(name: String): File =
        listOf(File("..", name), File(name)).firstOrNull { it.isFile }
            ?: fail("$name not found from ${File(".").absolutePath}")
}
