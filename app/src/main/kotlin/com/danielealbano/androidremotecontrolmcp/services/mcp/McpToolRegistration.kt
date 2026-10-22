package com.danielealbano.androidremotecontrolmcp.services.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.LoggedToolRegistrar
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ReferenceCountedToolCallIndicator
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerAppManagementTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerCameraTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerFileTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerGestureTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerIntentTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerLocationTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerNodeActionTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerNotificationTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerScreenIntrospectionTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerSharingTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerSystemActionTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerTextInputTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerTouchActionTools
import com.danielealbano.androidremotecontrolmcp.mcp.tools.registerUtilityTools
import com.danielealbano.androidremotecontrolmcp.privacy.PlaceholderSubstitutor
import com.danielealbano.androidremotecontrolmcp.privacy.PrivacyToolGate
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityToolCallIndicator
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeParser
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ActionExecutor
import com.danielealbano.androidremotecontrolmcp.services.accessibility.CompactTreeFormatter
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementFinder
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenStateSnapshotCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.TypeInputController
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WebViewNodeMerger
import com.danielealbano.androidremotecontrolmcp.services.apps.AppManager
import com.danielealbano.androidremotecontrolmcp.services.camera.CameraProvider
import com.danielealbano.androidremotecontrolmcp.services.intents.IntentDispatcher
import com.danielealbano.androidremotecontrolmcp.services.location.LocationProvider
import com.danielealbano.androidremotecontrolmcp.services.notifications.NotificationProvider
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenCaptureProvider
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenshotAnnotator
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenshotEncoder
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenshotRedactor
import com.danielealbano.androidremotecontrolmcp.services.sharing.EphemeralFileLinkService
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedContentInbox
import com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider
import com.danielealbano.androidremotecontrolmcp.services.storage.StorageLocationProvider
import io.modelcontextprotocol.kotlin.sdk.server.Server
import javax.inject.Inject

/**
 * Registers every MCP tool on the SDK server with the tools' dependencies. Kept out of [McpServerService] so the
 * service stays focused on its lifecycle.
 */
class McpToolRegistration
    @Inject
    constructor() {
        @Inject lateinit var serverLogRepository: ServerLogRepository

        @Inject lateinit var screenStateSnapshotCache: ScreenStateSnapshotCache

        @Inject lateinit var cameraProvider: CameraProvider

        @Inject lateinit var notificationProvider: NotificationProvider

        @Inject lateinit var ephemeralFileLinkService: EphemeralFileLinkService

        @Inject lateinit var actionExecutor: ActionExecutor

        @Inject lateinit var accessibilityServiceProvider: AccessibilityServiceProvider

        @Inject lateinit var screenCaptureProvider: ScreenCaptureProvider

        @Inject lateinit var treeParser: AccessibilityTreeParser

        @Inject lateinit var elementFinder: ElementFinder

        @Inject lateinit var compactTreeFormatter: CompactTreeFormatter

        @Inject lateinit var screenshotAnnotator: ScreenshotAnnotator

        @Inject lateinit var screenshotEncoder: ScreenshotEncoder

        @Inject lateinit var storageLocationProvider: StorageLocationProvider

        @Inject lateinit var fileOperationProvider: FileOperationProvider

        @Inject lateinit var appManager: AppManager

        @Inject lateinit var typeInputController: TypeInputController

        @Inject lateinit var nodeCache: AccessibilityNodeCache

        @Inject lateinit var webViewNodeMerger: WebViewNodeMerger

        @Inject lateinit var intentDispatcher: IntentDispatcher

        @Inject lateinit var locationProvider: LocationProvider

        @Inject lateinit var sharedContentInbox: SharedContentInbox

        @Inject lateinit var privacyToolGate: PrivacyToolGate

        @Inject lateinit var placeholderSubstitutor: PlaceholderSubstitutor

        @Inject lateinit var screenshotRedactor: ScreenshotRedactor

        /** Registers every MCP tool on [server]; [currentBaseUrl] is the URL the sharing tools hand out. */
        fun registerAll(
            server: Server,
            toolNamePrefix: String,
            perms: ToolPermissionsConfig,
            fileSizeLimitMb: Int,
            toolCallIndicatorEnabled: Boolean,
            currentBaseUrl: () -> String,
        ) {
            val toolCallIndicator = ReferenceCountedToolCallIndicator(AccessibilityToolCallIndicator())
            toolCallIndicator.setEnabled(toolCallIndicatorEnabled)
            val registrar =
                LoggedToolRegistrar(
                    server,
                    serverLogRepository,
                    toolCallIndicator,
                )
            registerAccessibilityToolBundle(registrar, toolNamePrefix, perms)
            registerFileTools(registrar, storageLocationProvider, fileOperationProvider, toolNamePrefix, perms)
            registerAppManagementTools(registrar, appManager, privacyToolGate, toolNamePrefix, perms)
            registerCameraTools(registrar, cameraProvider, fileOperationProvider, toolNamePrefix, perms)
            registerIntentTools(registrar, intentDispatcher, toolNamePrefix, perms)
            registerNotificationTools(
                registrar,
                notificationProvider,
                privacyToolGate,
                placeholderSubstitutor,
                toolNamePrefix,
                perms,
            )
            registerLocationTools(registrar, locationProvider, privacyToolGate, toolNamePrefix, perms)
            registerSharingBundle(registrar, toolNamePrefix, perms, fileSizeLimitMb, currentBaseUrl)
        }

        private fun registerAccessibilityToolBundle(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
            perms: ToolPermissionsConfig,
        ) {
            registerScreenIntrospectionTools(
                registrar,
                treeParser,
                accessibilityServiceProvider,
                screenCaptureProvider,
                compactTreeFormatter,
                screenshotAnnotator,
                screenshotEncoder,
                nodeCache,
                screenStateSnapshotCache,
                webViewNodeMerger,
                privacyToolGate,
                screenshotRedactor,
                toolNamePrefix,
                perms,
            )
            registerSystemActionTools(registrar, actionExecutor, accessibilityServiceProvider, toolNamePrefix, perms)
            registerTouchActionTools(registrar, actionExecutor, toolNamePrefix, perms)
            registerGestureTools(registrar, actionExecutor, toolNamePrefix, perms)
            registerNodeActionTools(
                registrar,
                treeParser,
                elementFinder,
                actionExecutor,
                accessibilityServiceProvider,
                nodeCache,
                privacyToolGate,
                placeholderSubstitutor,
                toolNamePrefix,
                perms,
            )
            registerTextInputTools(
                registrar,
                treeParser,
                actionExecutor,
                accessibilityServiceProvider,
                typeInputController,
                nodeCache,
                privacyToolGate,
                placeholderSubstitutor,
                toolNamePrefix,
                perms,
            )
            registerUtilityTools(
                registrar,
                treeParser,
                elementFinder,
                accessibilityServiceProvider,
                nodeCache,
                privacyToolGate,
                placeholderSubstitutor,
                toolNamePrefix,
                perms,
            )
        }

        private fun registerSharingBundle(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
            perms: ToolPermissionsConfig,
            fileSizeLimitMb: Int,
            currentBaseUrl: () -> String,
        ) {
            registerSharingTools(
                registrar,
                sharedContentInbox,
                ephemeralFileLinkService,
                fileOperationProvider,
                fileSizeLimitMb,
                currentBaseUrl,
                privacyToolGate,
                toolNamePrefix,
                perms,
            )
        }
    }
