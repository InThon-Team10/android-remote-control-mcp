package com.danielealbano.androidremotecontrolmcp.ui.helper

/** Practice navigation cannot grant permissions, start services, or send requests. */
enum class HelperEntryPage { WELCOME, PRACTICE, INVITE, CONSENT, PERMISSION, MICROPHONE, HOME }

enum class HelperPracticeStep { OPEN, ASK, FOLLOW, DONE }

data class HelperEntryModel(
    val page: HelperEntryPage = HelperEntryPage.WELCOME,
    val step: HelperPracticeStep = HelperPracticeStep.OPEN,
) {
    fun practice() = copy(page = HelperEntryPage.PRACTICE, step = HelperPracticeStep.OPEN)

    fun advance() =
        if (page != HelperEntryPage.PRACTICE) {
            this
        } else {
            when (step) {
                HelperPracticeStep.OPEN -> copy(step = HelperPracticeStep.ASK)
                HelperPracticeStep.ASK -> copy(step = HelperPracticeStep.FOLLOW)
                HelperPracticeStep.FOLLOW -> copy(step = HelperPracticeStep.DONE)
                HelperPracticeStep.DONE -> copy(page = HelperEntryPage.INVITE)
            }
        }
}

/** Ignore spacing and punctuation differences from dictation, but require the practice sentence. */
internal fun practiceQuestionMatches(
    draft: String,
    expected: String,
): Boolean =
    draft.filter { it.isLetterOrDigit() }.let { it.isNotEmpty() && it == expected.filter { character -> character.isLetterOrDigit() } }

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
