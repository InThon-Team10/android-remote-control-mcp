package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus

enum class HelperEntryPage { WELCOME, SETUP, HOME, HELP, SETTINGS }

/** Navigation state of the helper entry flow. Pure: every transition returns a new model. */
data class HelperEntryModel(
    val page: HelperEntryPage = HelperEntryPage.WELCOME,
    val returnPage: HelperEntryPage = HelperEntryPage.HOME,
) {
    /** The system back button leaves the screen only from the two root pages. */
    val exitsOnBack: Boolean get() = page == HelperEntryPage.WELCOME || page == HelperEntryPage.HOME

    fun setup(): HelperEntryModel = copy(page = HelperEntryPage.SETUP)

    fun home(): HelperEntryModel = copy(page = HelperEntryPage.HOME)

    fun settings(): HelperEntryModel = copy(page = HelperEntryPage.SETTINGS)

    fun intro(): HelperEntryModel = copy(page = HelperEntryPage.WELCOME)

    fun help(): HelperEntryModel {
        val opened = copy(page = HelperEntryPage.HELP, returnPage = page)
        return if (page == HelperEntryPage.HELP) this else opened
    }

    fun back(): HelperEntryModel =
        when {
            exitsOnBack -> this
            page == HelperEntryPage.HELP -> copy(page = returnPage)
            else -> home()
        }

    companion object {
        fun initial(introSeen: Boolean): HelperEntryModel =
            HelperEntryModel(page = if (introSeen) HelperEntryPage.HOME else HelperEntryPage.WELCOME)
    }
}

/** Runtime permissions as reported by the activity on every resume. */
data class PermissionSnapshot(
    val notifications: Boolean = false,
    val microphone: Boolean = false,
)

/** The setup checklist; accessibility, consent and the AI key are required, notifications and microphone are not. */
data class SetupChecklist(
    val accessibility: Boolean,
    val notifications: Boolean,
    val microphone: Boolean,
    val consent: Boolean,
    val aiConnected: Boolean,
) {
    val ready: Boolean get() = accessibility && consent && aiConnected
}

enum class HomeStatus { MISSING, OFF, STARTING, ON, FAILED }

enum class HomeAction { CONTINUE_SETUP, START, STARTING, OPEN, RETRY }

/** Everything the entry screens render. [loaded] is false until the stored settings were read once. */
data class HelperEntryUiState(
    val loaded: Boolean = false,
    val model: HelperEntryModel = HelperEntryModel(),
    val config: AssistantConfig = AssistantConfig(),
    val bubble: HelperBubbleStatus = HelperBubbleStatus.OFF,
    val server: ServerStatus = ServerStatus.Stopped,
    val accessibilityReady: Boolean = false,
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
) {
    val setup: SetupChecklist
        get() =
            SetupChecklist(
                accessibility = accessibilityReady,
                notifications = permissions.notifications,
                microphone = permissions.microphone,
                consent = config.privacyNoticeAccepted,
                aiConnected = config.apiKeyConfigured,
            )

    val homeStatus: HomeStatus
        get() =
            when {
                !setup.ready -> HomeStatus.MISSING
                busy || bubble == HelperBubbleStatus.STARTING -> HomeStatus.STARTING
                bubble == HelperBubbleStatus.ON -> HomeStatus.ON
                bubble == HelperBubbleStatus.FAILED || errorRes != null -> HomeStatus.FAILED
                else -> HomeStatus.OFF
            }

    val homeAction: HomeAction
        get() =
            when (homeStatus) {
                HomeStatus.MISSING -> HomeAction.CONTINUE_SETUP
                HomeStatus.STARTING -> HomeAction.STARTING
                HomeStatus.ON -> HomeAction.OPEN
                HomeStatus.FAILED -> HomeAction.RETRY
                HomeStatus.OFF -> HomeAction.START
            }

    val serverRunning: Boolean get() = server is ServerStatus.Running
}
