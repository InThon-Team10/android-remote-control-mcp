package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus

enum class HelperEntryPage { WELCOME, PRACTICE, INVITE, CONSENT, PERMISSION, MICROPHONE, HOME, SETTINGS }

enum class HelperPracticeStep { OPEN, ASK, FOLLOW, DONE }

/**
 * Navigation state of the helper entry flow. Pure: every transition returns a new model. Practice navigation cannot
 * grant permissions, start services, or send requests.
 */
data class HelperEntryModel(
    val page: HelperEntryPage = HelperEntryPage.WELCOME,
    val step: HelperPracticeStep = HelperPracticeStep.OPEN,
) {
    /** The system back button leaves the screen only from the two root pages. */
    val exitsOnBack: Boolean get() = page == HelperEntryPage.WELCOME || page == HelperEntryPage.HOME

    fun practice(): HelperEntryModel = copy(page = HelperEntryPage.PRACTICE, step = HelperPracticeStep.OPEN)

    /** Moves the practice forward; its last step leads to the invitation. Does nothing outside the practice. */
    fun advance(): HelperEntryModel =
        when {
            page != HelperEntryPage.PRACTICE -> this
            step == HelperPracticeStep.DONE -> copy(page = HelperEntryPage.INVITE)
            else -> copy(step = HelperPracticeStep.entries[step.ordinal + 1])
        }

    fun page(next: HelperEntryPage): HelperEntryModel = copy(page = next)

    fun home(): HelperEntryModel = copy(page = HelperEntryPage.HOME)

    fun settings(): HelperEntryModel = copy(page = HelperEntryPage.SETTINGS)

    fun intro(): HelperEntryModel = copy(page = HelperEntryPage.WELCOME)

    fun back(): HelperEntryModel = if (exitsOnBack) this else home()

    fun skip(): HelperEntryModel = back()

    companion object {
        fun initial(introSeen: Boolean): HelperEntryModel =
            HelperEntryModel(page = if (introSeen) HelperEntryPage.HOME else HelperEntryPage.WELCOME)
    }
}

/** True when [draft] is [expected] ignoring case, whitespace and punctuation; empty or partial drafts never match. */
internal fun practiceQuestionMatches(
    draft: String,
    expected: String,
): Boolean {
    val wanted = normalizeQuestion(expected)
    return wanted.isNotEmpty() && normalizeQuestion(draft) == wanted
}

private fun normalizeQuestion(text: String): String = text.filter { it.isLetterOrDigit() }.lowercase()

/** The next page of the setup chain: consent, then accessibility, then the microphone, then home. */
internal fun helperSetupPage(
    consented: Boolean,
    accessible: Boolean,
    microphoneDecided: Boolean,
): HelperEntryPage =
    when {
        !consented -> HelperEntryPage.CONSENT
        !accessible -> HelperEntryPage.PERMISSION
        !microphoneDecided -> HelperEntryPage.MICROPHONE
        else -> HelperEntryPage.HOME
    }

/** Title and body of the coach card of a practice [step]. */
internal fun practiceCoach(step: HelperPracticeStep): Pair<Int, Int> =
    when (step) {
        HelperPracticeStep.OPEN -> R.string.helper_tour_open_title to R.string.helper_tour_open_body
        HelperPracticeStep.ASK -> R.string.helper_tour_ask_title to R.string.helper_tour_ask_body
        HelperPracticeStep.FOLLOW -> R.string.helper_tour_follow_title to R.string.helper_tour_follow_body
        HelperPracticeStep.DONE -> R.string.helper_tour_done_title to R.string.helper_tour_done_body
    }

/** The practice has its own dictation, which must never run while the live conversation holds the microphone. */
internal fun practiceMicEnabled(conversationActive: Boolean): Boolean = !conversationActive

/** What the home status shows: [awake] draws the bright, breathing 콕콕이; [bodyRes] is null when there is no body. */
internal data class HomeStatusVisual(
    val awake: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int?,
)

internal fun homeStatusVisual(
    status: HomeStatus,
    aiConnected: Boolean,
): HomeStatusVisual =
    when (status) {
        HomeStatus.ON -> {
            HomeStatusVisual(true, R.string.helper_home_active_title, null)
        }

        HomeStatus.OFF -> {
            HomeStatusVisual(false, R.string.helper_home_inactive_title, null)
        }

        HomeStatus.MISSING -> {
            HomeStatusVisual(
                awake = false,
                titleRes = R.string.entry_status_missing,
                bodyRes = if (aiConnected) R.string.entry_body_missing else R.string.helper_home_ai_missing,
            )
        }

        HomeStatus.STARTING -> {
            HomeStatusVisual(false, R.string.entry_status_starting, R.string.entry_body_starting)
        }

        HomeStatus.FAILED -> {
            HomeStatusVisual(false, R.string.entry_status_failed, R.string.entry_body_failed)
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

enum class HomeAction { START, STARTING, OPEN, RETRY }

/** Everything the entry screens render. [loaded] is false until the stored settings were read once. */
data class HelperEntryUiState(
    val loaded: Boolean = false,
    val model: HelperEntryModel = HelperEntryModel(),
    val config: AssistantConfig = AssistantConfig(),
    val server: ServerStatus = ServerStatus.Stopped,
    val accessibilityReady: Boolean = false,
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val conversationActive: Boolean = false,
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
                busy || server is ServerStatus.Starting -> HomeStatus.STARTING
                errorRes != null || server is ServerStatus.Error -> HomeStatus.FAILED
                config.enabled && server is ServerStatus.Running -> HomeStatus.ON
                else -> HomeStatus.OFF
            }

    val homeAction: HomeAction
        get() =
            when (homeStatus) {
                HomeStatus.STARTING -> HomeAction.STARTING
                HomeStatus.ON -> HomeAction.OPEN
                HomeStatus.FAILED -> HomeAction.RETRY
                HomeStatus.OFF, HomeStatus.MISSING -> HomeAction.START
            }
}
