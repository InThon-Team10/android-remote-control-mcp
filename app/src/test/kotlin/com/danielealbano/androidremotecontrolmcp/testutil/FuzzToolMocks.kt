package com.danielealbano.androidremotecontrolmcp.testutil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.SurroundingText
import com.danielealbano.androidremotecontrolmcp.data.model.LocationData
import com.danielealbano.androidremotecontrolmcp.integration.McpIntegrationTestHelper
import com.danielealbano.androidremotecontrolmcp.integration.MockDependencies
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * Minimal mock returns for the tool fuzz suites: enough for calls to get past readiness checks and reach each tool's
 * own validation, behaving like the real services where it matters (unknown ids, nodes and paths are refused), so
 * that a value echoed in a success text is one a real provider would have accepted.
 */
internal object FuzzToolMocks {
    private const val SCREEN_W = 1080
    private const val SCREEN_H = 2400
    private const val DENSITY = 420
    private const val ELEMENT_SIZE = 100
    private const val SPACE = 0x20
    private const val DEL = 0x7F

    fun stub(deps: MockDependencies) {
        val tree =
            AccessibilityNodeData(
                id = FuzzToolArguments.KNOWN_NODE,
                className = "android.widget.FrameLayout",
                bounds = BoundsData(0, 0, SCREEN_W, SCREEN_H),
                visible = true,
            )
        val screen = ScreenInfo(SCREEN_W, SCREEN_H, DENSITY, "portrait")
        McpIntegrationTestHelper.setupMultiWindowMock(deps, tree, screen)
        stubGestures(deps)
        stubNodeActions(deps)
        stubNotifications(deps)
        stubStorageAndApps(deps)
        stubDeviceContent(deps, tree)
        stubTyping(deps)
        every { deps.cameraProvider.isCameraPermissionGranted() } returns true
        every { deps.cameraProvider.isMicrophonePermissionGranted() } returns true
    }

    /** The service mocks whose recorded calls are inspected. */
    fun serviceMocks(deps: MockDependencies): List<Any> =
        listOf(
            deps.actionExecutor,
            deps.accessibilityServiceProvider,
            deps.screenCaptureProvider,
            deps.treeParser,
            deps.elementFinder,
            deps.storageLocationProvider,
            deps.fileOperationProvider,
            deps.appManager,
            deps.typeInputController,
            deps.screenshotAnnotator,
            deps.screenshotEncoder,
            deps.cameraProvider,
            deps.nodeCache,
            deps.intentDispatcher,
            deps.notificationProvider,
            deps.locationProvider,
            deps.sharedContentInbox,
            deps.ephemeralFileLinkService,
        )

    private fun stubGestures(deps: MockDependencies) {
        val executor = deps.actionExecutor
        val ok = Result.success(Unit)
        coEvery { executor.tap(any(), any()) } returns ok
        coEvery { executor.longPress(any(), any(), any()) } returns ok
        coEvery { executor.doubleTap(any(), any()) } returns ok
        coEvery { executor.swipe(any(), any(), any(), any(), any()) } returns ok
        coEvery { executor.scroll(any(), any(), any()) } returns ok
        coEvery { executor.pinch(any(), any(), any(), any()) } returns ok
        coEvery { executor.customGesture(any()) } returns ok
        coEvery { executor.pressBack() } returns ok
        coEvery { executor.pressHome() } returns ok
        coEvery { executor.pressRecents() } returns ok
        coEvery { executor.openNotifications() } returns ok
        coEvery { executor.openQuickSettings() } returns ok
        coEvery { executor.dismissKeyboard() } returns Result.success(true)
    }

    /** Like the real executor: only a node of the current tree can be acted on. */
    private fun stubNodeActions(deps: MockDependencies) {
        val executor = deps.actionExecutor
        val noNode = Result.failure<Unit>(IllegalArgumentException("Node not found"))
        val known = FuzzToolArguments.KNOWN_NODE
        val ok = Result.success(Unit)
        coEvery { executor.clickNode(any(), any()) } returns noNode
        coEvery { executor.clickNode(known, any()) } returns ok
        coEvery { executor.longClickNode(any(), any()) } returns noNode
        coEvery { executor.longClickNode(known, any()) } returns ok
        coEvery { executor.scrollNode(any(), any(), any()) } returns noNode
        coEvery { executor.scrollNode(known, any(), any()) } returns ok
        coEvery { executor.setTextOnNode(any(), any(), any()) } returns noNode
        coEvery { executor.setTextOnNode(known, any(), any()) } returns ok
    }

    private fun stubNotifications(deps: MockDependencies) {
        val provider = deps.notificationProvider
        val ok = Result.success(Unit)
        every { provider.isReady() } returns true
        coEvery { provider.getNotifications(any(), any()) } returns emptyList()
        coEvery { provider.openNotification(any()) } returns ok
        coEvery { provider.dismissNotification(any()) } returns ok
        coEvery { provider.snoozeNotification(any(), any()) } returns ok
        coEvery { provider.executeAction(any()) } returns ok
        coEvery { provider.replyToAction(any(), any()) } returns ok
    }

    /** Like the real providers: only the known location, a path without ASCII controls, an installed package. */
    private fun stubStorageAndApps(deps: MockDependencies) {
        val files = deps.fileOperationProvider
        val unknown = McpToolException.InvalidParams("Unknown storage location or invalid path")
        // The real providers reject the regex class \p{Cntrl}: ASCII controls and DEL, not C1 or U+2028/U+2029.
        val badPath = { path: String -> path.any { it.code < SPACE || it.code == DEL } }
        val location = FuzzToolArguments.KNOWN_LOCATION
        coEvery { files.writeFile(neq(location), any(), any()) } throws unknown
        coEvery { files.writeFile(any(), match(badPath), any()) } throws unknown
        coEvery { files.appendFile(neq(location), any(), any()) } throws unknown
        coEvery { files.appendFile(any(), match(badPath), any()) } throws unknown
        coEvery { files.deleteFile(neq(location), any()) } throws unknown
        coEvery { files.deleteFile(any(), match(badPath)) } throws unknown
        coEvery { files.downloadFromUrl(neq(location), any(), any()) } throws unknown
        coEvery { files.downloadFromUrl(any(), match(badPath), any()) } throws unknown
        coEvery { files.replaceInFile(neq(location), any(), any(), any(), any()) } throws unknown
        coEvery { files.replaceInFile(any(), match(badPath), any(), any(), any()) } throws unknown
        val notInstalled = Result.failure<Unit>(IllegalArgumentException("Package not installed"))
        val installed = { pkg: String -> pkg.isNotEmpty() && pkg.all { it.isLetterOrDigit() || it in "._" } }
        coEvery { deps.appManager.openApp(match { !installed(it) }) } returns notInstalled
        coEvery { deps.appManager.closeApp(match { !installed(it) }) } returns notInstalled
        coEvery { deps.appManager.openApp(match(installed)) } returns Result.success(Unit)
        coEvery { deps.appManager.closeApp(match(installed)) } returns Result.success(Unit)
    }

    /** Device-content sources, so the tools that read them can succeed and be checked for the warning. */
    private fun stubDeviceContent(
        deps: MockDependencies,
        tree: AccessibilityNodeData,
    ) {
        val finder = deps.elementFinder
        // Like the real finder: only a node of the current tree resolves.
        every { finder.findNodeById(any<List<WindowData>>(), any()) } returns null
        every { finder.findNodeById(any<List<WindowData>>(), FuzzToolArguments.KNOWN_NODE) } returns tree
        every { finder.findElements(any<List<WindowData>>(), any(), any(), any()) } returns
            listOf(
                ElementInfo(
                    id = FuzzToolArguments.KNOWN_NODE,
                    text = "text",
                    className = "android.widget.Button",
                    bounds = BoundsData(0, 0, ELEMENT_SIZE, ELEMENT_SIZE),
                    clickable = true,
                    enabled = true,
                    visible = true,
                ),
            )
        val context = mockk<Context>()
        val clipboard = mockk<ClipboardManager>(relaxed = true)
        val clip = mockk<ClipData>()
        val item = mockk<ClipData.Item>()
        every { deps.accessibilityServiceProvider.getContext() } returns context
        every { context.getSystemService(ClipboardManager::class.java) } returns clipboard
        every { clipboard.primaryClip } returns clip
        every { clip.itemCount } returns 1
        every { clip.getItemAt(0) } returns item
        every { item.text } returns "clipboard text"
        coEvery { deps.locationProvider.getLocation(any()) } returns
            Result.success(LocationData(1.0, 2.0, 3.0f, "street"))
    }

    private fun stubTyping(deps: MockDependencies) {
        val input = deps.typeInputController
        every { input.isReady() } returns true
        every { input.commitText(any(), any()) } returns true
        every { input.setSelection(any(), any()) } returns true
        every { input.deleteSurroundingText(any(), any()) } returns true
        every { input.sendKeyEvent(any()) } returns true
        every { input.performContextMenuAction(any()) } returns true
        every { input.getSurroundingText(any(), any(), any()) } returns mockk<SurroundingText>(relaxed = true)
    }
}
