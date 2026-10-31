package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Production implementation of [AccessibilityServiceProvider] that delegates
 * to [McpAccessibilityService.instance] (the system-managed singleton).
 *
 * This class is Hilt-injectable and stateless.
 */
class AccessibilityServiceProviderImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AccessibilityServiceProvider {
        override fun getRootNode(): AccessibilityNodeInfo? = McpAccessibilityService.instance?.getRootNode()

        override fun getAccessibilityWindows(): List<AccessibilityWindowInfo> =
            McpAccessibilityService.instance?.getAccessibilityWindows() ?: emptyList()

        override fun getCurrentPackageName(): String? = McpAccessibilityService.instance?.getCurrentPackageName()

        override fun getCurrentActivityName(): String? = McpAccessibilityService.instance?.getCurrentActivityName()

        override fun getScreenInfo(): ScreenInfo =
            McpAccessibilityService.instance?.getScreenInfo()
                ?: throw McpToolException.PermissionDenied("Accessibility service not available")

        override fun isReady(): Boolean = McpAccessibilityService.instance?.isReady() == true

        override suspend fun getStatus(): AccessibilityServiceStatus =
            withContext(Dispatchers.Main) {
                val enabled =
                    PermissionUtils.isAccessibilityServiceEnabled(context, McpAccessibilityService::class.java)
                val service = McpAccessibilityService.instance
                if (service == null || !service.isReady()) {
                    return@withContext AccessibilityServiceStatus(enabled = enabled)
                }
                val info = service.serviceInfo
                val capabilities = info?.capabilities ?: 0
                val flags = info?.flags ?: 0
                val canRetrieveWindowContent =
                    capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
                val (activeWindowAvailable, interactiveWindowCount) =
                    synchronized(AccessibilityTreeLock.monitor) {
                        (service.getRootNode() != null) to service.getAccessibilityWindows().size
                    }
                AccessibilityServiceStatus(
                    enabled = enabled,
                    connected = true,
                    capabilities =
                        AccessibilityCapabilities(
                            canRetrieveWindowContent = canRetrieveWindowContent,
                            canPerformGestures =
                                capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0,
                            canTakeScreenshot =
                                capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0,
                            canRetrieveInteractiveWindows =
                                canRetrieveWindowContent &&
                                    flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0,
                            canReportViewIds = flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0,
                        ),
                    activeWindowAvailable = activeWindowAvailable,
                    interactiveWindowCount = interactiveWindowCount,
                )
            }

        override fun getContext(): Context? = McpAccessibilityService.instance

        override fun clearFrameworkNodeCache() {
            McpAccessibilityService.instance?.clearFrameworkNodeCache()
        }
    }
