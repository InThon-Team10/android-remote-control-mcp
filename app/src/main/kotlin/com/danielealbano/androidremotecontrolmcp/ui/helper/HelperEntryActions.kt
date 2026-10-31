package com.danielealbano.androidremotecontrolmcp.ui.helper

/** Setup-only user actions; each opens a system screen or reports a choice. */
interface HelperSetupActions {
    fun openAccessibilitySettings()

    fun openAppInfo()

    fun requestNotifications()

    fun requestMicrophone()

    fun acceptConsent()

    fun setVoice(enabled: Boolean)

    fun saveKey(key: String)

    fun clearKey()
}

/** Entry points of the settings page that leave the app's own screens. */
interface HelperSettingsActions {
    fun setSpeech(enabled: Boolean)

    fun showIntroAgain()
}

/** Navigation and home actions of the entry flow. */
interface HelperEntryActions : HelperSettingsActions {
    fun startSetup()

    fun navigate(transition: (HelperEntryModel) -> HelperEntryModel)

    fun turnOn()

    fun turnOff()

    fun openHelper()
}
