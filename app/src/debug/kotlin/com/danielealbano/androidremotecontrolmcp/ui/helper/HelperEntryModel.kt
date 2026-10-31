package com.danielealbano.androidremotecontrolmcp.ui.helper

/** Local simulation only. Never grants a permission or starts a service. */
enum class HelperEntryPage { WELCOME, SETUP, PERMISSION, HOME, HELP, SETTINGS, REQUEST }

enum class HelperEntryBubble { OFF, STARTING, ON, FAILED }

data class HelperEntryModel(
    val page: HelperEntryPage = HelperEntryPage.WELCOME,
    val returnPage: HelperEntryPage = HelperEntryPage.WELCOME,
    val introSeen: Boolean = false,
    val permission: Boolean = false,
    val bubble: HelperEntryBubble = HelperEntryBubble.OFF,
    val failNextStart: Boolean = false,
) {
    fun primary(): HelperEntryModel =
        when {
            page == HelperEntryPage.WELCOME -> {
                copy(page = HelperEntryPage.SETUP, introSeen = true)
            }

            !permission -> {
                copy(
                    page = if (page == HelperEntryPage.SETUP) HelperEntryPage.PERMISSION else HelperEntryPage.SETUP,
                )
            }

            bubble == HelperEntryBubble.ON -> {
                copy(page = HelperEntryPage.REQUEST)
            }

            bubble == HelperEntryBubble.STARTING -> {
                this
            }

            else -> {
                copy(page = HelperEntryPage.HOME, bubble = HelperEntryBubble.STARTING)
            }
        }

    fun permissionResult(granted: Boolean): HelperEntryModel =
        copy(page = HelperEntryPage.SETUP, permission = granted, bubble = HelperEntryBubble.OFF)

    fun finishStart(): HelperEntryModel =
        if (bubble != HelperEntryBubble.STARTING || !permission) {
            this
        } else {
            copy(
                bubble = if (failNextStart) HelperEntryBubble.FAILED else HelperEntryBubble.ON,
                failNextStart = false,
            )
        }

    fun stop(): HelperEntryModel = copy(bubble = HelperEntryBubble.OFF, page = HelperEntryPage.HOME)

    fun revoke(): HelperEntryModel = copy(permission = false, bubble = HelperEntryBubble.OFF)

    fun help(): HelperEntryModel = copy(page = HelperEntryPage.HELP, returnPage = page)

    fun back(): HelperEntryModel =
        copy(
            page =
                when (page) {
                    HelperEntryPage.PERMISSION -> HelperEntryPage.SETUP
                    HelperEntryPage.HELP -> returnPage
                    else -> HelperEntryPage.HOME
                },
        )
}
