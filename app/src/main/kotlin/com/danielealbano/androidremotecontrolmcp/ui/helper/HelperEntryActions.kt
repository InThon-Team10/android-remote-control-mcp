package com.danielealbano.androidremotecontrolmcp.ui.helper

/** Setup-only user actions; each opens a system screen or reports a choice. */
interface HelperSetupActions {
    fun openAccessibilitySettings()

    fun openAppInfo()

    fun requestNotifications()

    fun requestMicrophone()

    fun setVoice(enabled: Boolean)
}

/** Entry points of the settings page that leave the app's own screens. */
interface HelperSettingsActions {
    fun setSpeech(enabled: Boolean)

    fun showIntroAgain()
}

/** Navigation and home actions of the entry flow. */
interface HelperEntryActions : HelperSettingsActions {
    fun navigate(transition: (HelperEntryModel) -> HelperEntryModel)

    /** Moves on to the first setup step that is still missing, turning the helper on when none is. */
    fun continueSetup()

    fun acceptConsent()

    /** The microphone page's "later": the helper still turns on, talking asks for the permission again. */
    fun skipMicrophone()

    fun startPractice()

    fun turnOn()

    fun turnOff()

    fun openHelper()
}
