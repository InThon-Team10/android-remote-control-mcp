package com.danielealbano.androidremotecontrolmcp.integration

import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderGuidanceTargets
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderToolGuidance
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceStatus
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceOutcome
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceState
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint
import com.danielealbano.androidremotecontrolmcp.services.guidance.UserActivity
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Elder guidance integration")
class ElderGuidanceIntegrationTest {
    /** Records the guided targets and answers each with [outcome]. */
    private class FakeGuidanceController(
        var outcome: GuidanceOutcome = GuidanceOutcome.USER_ACTED,
    ) : GuidanceController {
        val targets = mutableListOf<GuidanceTarget>()
        override val state: StateFlow<GuidanceState?> = MutableStateFlow(null)

        override suspend fun guide(target: GuidanceTarget): GuidanceOutcome {
            targets += target
            return outcome
        }

        override fun cancel() = Unit

        override fun onUserActivity(activity: UserActivity) = Unit

        override fun setRendererAttached(attached: Boolean) = Unit
    }

    private val button =
        AccessibilityNodeData(
            id = "node_btn",
            className = "android.widget.Button",
            text = "OK",
            bounds = BoundsData(50, 800, 250, 1000),
            clickable = true,
            enabled = true,
            visible = true,
        )
    private val tree =
        AccessibilityNodeData(
            id = "node_root",
            bounds = BoundsData(0, 0, 1080, 2400),
            visible = true,
            children = listOf(button),
        )
    private val screen = ScreenInfo(width = 1080, height = 2400, densityDpi = 420, orientation = "portrait")
    private val controller = FakeGuidanceController()

    @BeforeEach
    fun setUp() = McpIntegrationTestHelper.mockAndroidLog()

    @AfterEach
    fun tearDown() = McpIntegrationTestHelper.unmockAndroidLog()

    /** One window showing [root]; each of [nodes] is found by its id. */
    private fun depsWith(
        root: AccessibilityNodeData,
        vararg nodes: AccessibilityNodeData,
    ): MockDependencies {
        val deps = McpIntegrationTestHelper.createMockDependencies()
        McpIntegrationTestHelper.setupMultiWindowMock(deps, root, screen)
        for (node in nodes) {
            every { deps.elementFinder.findNodeById(any<List<WindowData>>(), node.id) } returns node
        }
        return deps
    }

    private fun depsWithScreen(): MockDependencies = depsWith(tree, button)

    /** A full-screen root holding [children], later ones drawn on top. */
    private fun rootOf(vararg children: AccessibilityNodeData) =
        AccessibilityNodeData(
            id = "node_root",
            bounds = BoundsData(0, 0, 1080, 2400),
            visible = true,
            children = children.toList(),
        )

    private fun textOf(result: CallToolResult) = (result.content.single() as TextContent).text

    private fun hasProperty(
        tool: Tool,
        name: String,
    ) = tool.inputSchema.properties?.containsKey(name) == true

    @Test
    fun `a tool that changes the device by itself is refused with a guide-the-user error`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_press_home", emptyMap())

                assertEquals(true, result.isError)
                assertTrue(textOf(result).startsWith("Elder guidance mode: 'press_home'"))
                coVerify(exactly = 0) { deps.actionExecutor.pressHome() }
                assertTrue(controller.targets.isEmpty())
            }
        }

    @Test
    fun `a read-only tool is unaffected`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.accessibilityServiceProvider.getStatus() } returns AccessibilityServiceStatus()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_get_accessibility_status", emptyMap())

                assertNotEquals(true, result.isError)
                coVerify(exactly = 1) { deps.accessibilityServiceProvider.getStatus() }
            }
        }

    @Test
    fun `tap_node highlights the resolved bounds and never acts on the device`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool(
                        "android_tap_node",
                        mapOf("node_id" to "node_btn", "instruction" to "  Press OK  ", "explanation" to "It saves"),
                    )

                assertNotEquals(true, result.isError)
                assertEquals(
                    "The user did it. Call get_screen_state to see the new screen before the next step.",
                    textOf(result),
                )
                val target = controller.targets.single()
                assertEquals(BoundsData(50, 800, 250, 1000), target.bounds)
                assertEquals(GuidanceKind.TAP, target.kind)
                assertEquals("Press OK", target.instruction)
                assertEquals("It saves", target.explanation)
                coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any()) }
            }
        }

    @Test
    fun `a stale node id asks the model to read the screen again and shows nothing`() =
        runTest {
            val deps = depsWithScreen()
            every { deps.elementFinder.findNodeById(any<List<WindowData>>(), "node_gone") } returns null

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_click_node", mapOf("node_id" to "node_gone"))

                assertEquals(true, result.isError)
                assertEquals(ElderToolGuidance.NODE_GONE_TEXT, textOf(result))
                assertTrue(controller.targets.isEmpty())
                coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any()) }
            }
        }

    /** A visible, enabled node with no text. */
    private fun plain(
        id: String,
        bounds: BoundsData,
        clickable: Boolean = false,
        children: List<AccessibilityNodeData> = emptyList(),
    ) = AccessibilityNodeData(
        id = id,
        bounds = bounds,
        clickable = clickable,
        enabled = true,
        visible = true,
        children = children,
    )

    /** Samsung Settings: the text sits on a non-clickable label inside a clickable row without text. */
    private val settingsLabel =
        AccessibilityNodeData(
            id = "node_label",
            className = "android.widget.TextView",
            text = "연결",
            bounds = BoundsData(200, 1340, 280, 1405),
            enabled = true,
            visible = true,
        )

    /** The label's row; its sample points land on the icon, the summary and the switch, not on the label. */
    private val settingsRow =
        plain(
            "node_row",
            BoundsData(28, 1297, 1052, 1443),
            clickable = true,
            children =
                listOf(
                    plain("node_icon", BoundsData(28, 1297, 200, 1443)),
                    settingsLabel,
                    plain("node_summary", BoundsData(280, 1297, 780, 1443)),
                    plain("node_switch", BoundsData(780, 1297, 1052, 1443)),
                ),
        )

    /** Samsung Settings: the floating search bar drawn over the bottom rows of the list. */
    private val searchBar =
        plain("node_search", BoundsData(197, 2124, 883, 2270), clickable = true)
            .copy(resourceId = "com.android.settings:id/search_plate")

    /** Bounds of the bottom row, mostly under [searchBar]: 2 of its 5 sample points are clear. */
    private val underSearchBar = BoundsData(28, 2083, 1052, 2229)

    @Test
    fun `tap_node on a label highlights its clickable row`() =
        runTest {
            val deps = depsWith(rootOf(settingsRow), settingsLabel)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_tap_node", mapOf("node_id" to "node_label"))

                assertNotEquals(true, result.isError)
                val target = controller.targets.single()
                assertEquals(BoundsData(28, 1297, 1052, 1443), target.bounds)
                assertEquals(GuidanceKind.TAP, target.kind)
                coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any()) }
            }
        }

    @Test
    fun `a target mostly behind a floating search bar is refused and shows nothing`() =
        runTest {
            // Samsung Settings: the floating search bar is drawn over most of the bottom row
            val row = plain("node_sound", underSearchBar, clickable = true)
            val deps = depsWith(rootOf(row, searchBar), row)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_click_node", mapOf("node_id" to "node_sound"))

                assertEquals(true, result.isError)
                assertEquals(ElderToolGuidance.HIDDEN_TARGET_TEXT, textOf(result))
                assertTrue(controller.targets.isEmpty())
                coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any()) }
            }
        }

    @Test
    fun `a label that is clear inside a row mostly behind a floating bar is refused`() =
        runTest {
            // The label is left of the bar, but its row (what the user is shown) is mostly under it
            val label = plain("node_title", BoundsData(40, 2090, 180, 2220)).copy(text = "소리 및 진동")
            val row = plain("node_sound", underSearchBar, clickable = true, children = listOf(label))
            val deps = depsWith(rootOf(row, searchBar), label)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_tap_node", mapOf("node_id" to "node_title"))

                assertEquals(true, result.isError)
                assertEquals(ElderToolGuidance.HIDDEN_TARGET_TEXT, textOf(result))
                assertTrue(controller.targets.isEmpty())
            }
        }

    @Test
    fun `type_clear_text on a field mostly behind a floating bar is refused`() =
        runTest {
            val field = plain("node_field", underSearchBar, clickable = true).copy(editable = true)
            val deps = depsWith(rootOf(field, searchBar), field)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_type_clear_text", mapOf("node_id" to "node_field"))

                assertEquals(true, result.isError)
                assertEquals(ElderToolGuidance.HIDDEN_TARGET_TEXT, textOf(result))
                assertTrue(controller.targets.isEmpty())
                verify { deps.typeInputController wasNot Called }
            }
        }

    @Test
    fun `scroll_node on a list mostly behind a floating bar is still shown`() =
        runTest {
            // The user scrolls by swiping anywhere on the list, so a covered list is no reason to refuse
            val list = plain("node_list", underSearchBar).copy(scrollable = true)
            val deps = depsWith(rootOf(list, searchBar), list)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool("android_scroll_node", mapOf("node_id" to "node_list", "direction" to "up"))

                assertNotEquals(true, result.isError)
                val target = controller.targets.single()
                assertEquals(underSearchBar, target.bounds)
                assertEquals(GuidanceKind.SCROLL, target.kind)
                assertEquals(ScrollDirection.UP, target.scrollDirection)
            }
        }

    @Test
    fun `tap at a point on a label highlights the label, not its row`() =
        runTest {
            val deps = depsWith(rootOf(settingsRow))

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool("android_tap", mapOf("x" to 240, "y" to 1370))

                val target = controller.targets.single()
                assertEquals(BoundsData(200, 1340, 280, 1405), target.bounds)
                assertEquals(GuidanceKind.TAP, target.kind)
            }
        }

    @Test
    fun `a row reaching below the screen is checked and highlighted within the screen`() =
        runTest {
            // Sampling the row's full bounds would put every point below the 2400 px screen
            val row = plain("node_last", BoundsData(28, 2300, 1052, 2700), clickable = true)
            val deps = depsWith(rootOf(row), row)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_tap_node", mapOf("node_id" to "node_last"))

                assertNotEquals(true, result.isError)
                assertEquals(BoundsData(28, 2300, 1052, 2400), controller.targets.single().bounds)
            }
        }

    @Test
    fun `scroll_to_node on a node mostly behind a floating bar highlights its list`() =
        runTest {
            val row = plain("node_sound", underSearchBar, clickable = true)
            val list =
                plain("node_list", BoundsData(0, 300, 1080, 2400), children = listOf(row)).copy(scrollable = true)
            val deps = depsWith(rootOf(list, searchBar), row)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool("android_scroll_to_node", mapOf("node_id" to "node_sound", "direction" to "down"))

                assertNotEquals(true, result.isError)
                val target = controller.targets.single()
                assertEquals(BoundsData(0, 300, 1080, 2400), target.bounds)
                assertEquals(GuidanceKind.SCROLL, target.kind)
                assertEquals(ScrollDirection.DOWN, target.scrollDirection)
            }
        }

    @Test
    fun `scroll_to_node on a node the user can touch answers that it is already on screen`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool("android_scroll_to_node", mapOf("node_id" to "node_btn", "direction" to "down"))

                assertNotEquals(true, result.isError)
                assertEquals(ElderGuidanceTargets.ALREADY_ON_SCREEN_TEXT, textOf(result))
                assertTrue(controller.targets.isEmpty())
            }
        }

    @Test
    fun `a bare hex node id highlights the node with the node_ prefix`() =
        runTest {
            val hexButton = button.copy(id = "node_c0ffee")
            val deps = depsWith(rootOf(hexButton), hexButton)

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result = client.callTool("android_tap_node", mapOf("node_id" to "c0ffee"))

                assertNotEquals(true, result.isError)
                assertEquals(BoundsData(50, 800, 250, 1000), controller.targets.single().bounds)
            }
        }

    @Test
    fun `a blank instruction and explanation become an empty instruction and no explanation`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool(
                    "android_click_node",
                    mapOf("node_id" to "node_btn", "instruction" to "   ", "explanation" to " "),
                )

                val target = controller.targets.single()
                assertEquals("", target.instruction)
                assertNull(target.explanation)
            }
        }

    @Test
    fun `tap on a point with no node highlights a 72dp box around it`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool("android_tap", mapOf("x" to 500, "y" to 1500))

                val target = controller.targets.single()
                assertEquals(BoundsData(406, 1406, 594, 1594), target.bounds)
                assertEquals(GuidanceKind.TAP, target.kind)
                coVerify(exactly = 0) { deps.actionExecutor.tap(any(), any()) }
            }
        }

    @Test
    fun `tap on a node highlights that node`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool("android_long_press", mapOf("x" to 100, "y" to 900))

                val target = controller.targets.single()
                assertEquals(BoundsData(50, 800, 250, 1000), target.bounds)
                assertEquals(GuidanceKind.LONG_PRESS, target.kind)
            }
        }

    @Test
    fun `type_replace_text never echoes the text and never types`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool(
                        "android_type_replace_text",
                        mapOf("node_id" to "node_btn", "search" to "old-secret", "new_text" to "new-secret"),
                    )

                val text = textOf(result)
                assertFalse(text.contains("old-secret") || text.contains("new-secret"))
                assertFalse(
                    controller.targets
                        .single()
                        .instruction
                        .contains("secret"),
                )
                assertEquals(GuidanceKind.TYPE, controller.targets.single().kind)
                verify { deps.typeInputController wasNot Called }
                coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any()) }
            }
        }

    @Test
    fun `scroll_node highlights with the requested direction`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool("android_scroll_node", mapOf("node_id" to "node_btn", "direction" to "down"))

                val target = controller.targets.single()
                assertEquals(GuidanceKind.SCROLL, target.kind)
                assertEquals(ScrollDirection.DOWN, target.scrollDirection)
                assertNull(target.swipeStart)
            }
        }

    @Test
    fun `swipe highlights its path with the start point and never swipes`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val result =
                    client.callTool("android_swipe", mapOf("x1" to 1000, "y1" to 0, "x2" to 1000, "y2" to 1200))

                assertNotEquals(true, result.isError)
                val target = controller.targets.single()
                assertEquals(GuidanceKind.SCROLL, target.kind)
                assertEquals(ScrollDirection.DOWN, target.scrollDirection)
                assertEquals(ScreenPoint(1000, 0), target.swipeStart)
                // 48dp at 420 dpi = 126 px around the path, limited to the 1080 x 2400 screen.
                assertEquals(BoundsData(874, 0, 1080, 1326), target.bounds)
                coVerify(exactly = 0) { deps.actionExecutor.swipe(any(), any(), any(), any(), any()) }
            }
        }

    @Test
    fun `a swipe starting off screen carries its start point clamped to the screen`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                client.callTool("android_swipe", mapOf("x1" to 1500, "y1" to 1800, "x2" to 540, "y2" to 1800))

                val target = controller.targets.single()
                assertEquals(ScrollDirection.LEFT, target.scrollDirection)
                assertEquals(ScreenPoint(1079, 1800), target.swipeStart)
            }
        }

    @Test
    fun `instruction and explanation are in the schema and description only when enabled`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication(guidanceController = controller) { client, _ ->
                val tools = client.listTools().tools.associateBy { it.name }

                listOf("android_tap_node", "android_scroll_to_node").forEach {
                    val tool = tools.getValue(it)
                    assertTrue(hasProperty(tool, "instruction"), it)
                    assertTrue(hasProperty(tool, "explanation"), it)
                    assertTrue(tool.description.orEmpty().contains("Elder guidance"), it)
                }
                assertFalse(
                    tools
                        .getValue("android_find_nodes")
                        .inputSchema.properties
                        ?.containsKey("instruction") == true,
                )
            }
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val tool = client.listTools().tools.single { it.name == "android_tap_node" }

                assertFalse(hasProperty(tool, "instruction"))
                assertFalse(tool.description.orEmpty().contains("Elder guidance"))
            }
        }

    @Test
    fun `an over-long instruction or explanation is an invalid params error`() =
        runTest {
            val deps = depsWithScreen()

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                val tooLongInstruction = mapOf("node_id" to "node_btn", "instruction" to "a".repeat(201))
                val tooLongExplanation = mapOf("node_id" to "node_btn", "explanation" to "a".repeat(501))
                val longInstruction = client.callTool("android_tap_node", tooLongInstruction)
                val longExplanation = client.callTool("android_tap_node", tooLongExplanation)

                assertEquals(true, longInstruction.isError)
                assertEquals(true, longExplanation.isError)
                assertTrue(controller.targets.isEmpty())
            }
        }

    @Test
    fun `each outcome is answered with its server text and error flag`() =
        runTest {
            val deps = depsWithScreen()
            val expected =
                mapOf(
                    GuidanceOutcome.SCREEN_CHANGED to ("The screen changed" to false),
                    GuidanceOutcome.TIMED_OUT to ("The user has not acted yet" to false),
                    GuidanceOutcome.CANCELLED to ("The user closed the guidance" to true),
                    GuidanceOutcome.SUPERSEDED to ("A newer guidance step" to true),
                    GuidanceOutcome.UNAVAILABLE to ("The guidance overlay is not available" to true),
                )

            McpIntegrationTestHelper.withTestApplication(deps, guidanceController = controller) { client, _ ->
                expected.forEach { (outcome, answer) ->
                    controller.outcome = outcome

                    val result = client.callTool("android_click_node", mapOf("node_id" to "node_btn"))

                    assertTrue(textOf(result).startsWith(answer.first), outcome.name)
                    assertEquals(answer.second, result.isError == true, outcome.name)
                }
            }
        }
}
