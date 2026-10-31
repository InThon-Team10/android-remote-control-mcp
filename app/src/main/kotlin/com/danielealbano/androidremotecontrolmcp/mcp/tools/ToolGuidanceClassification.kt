package com.danielealbano.androidremotecontrolmcp.mcp.tools

/** How a tool behaves under elder guidance. */
enum class ToolGuidanceClass {
    /** Reads only; runs unchanged. */
    READ_ONLY,

    /** Targeted action: highlights the target for the user to act on, never acts. */
    HIGHLIGHT,

    /** Untargeted device change: refused with a "guide the user instead" error. */
    REFUSE,
}

/**
 * Elder-guidance classification of every MCP tool by its un-prefixed name. Anything not listed (including tools
 * added later) is [ToolGuidanceClass.REFUSE], so the app never acts on the device by default.
 */
object ToolGuidanceClassification {
    val READ_ONLY_TOOLS: Set<String> =
        setOf(
            "get_screen_state",
            "find_nodes",
            "get_node_details",
            "get_accessibility_status",
            "get_clipboard",
            "wait_for_node",
            "wait_for_idle",
            "list_apps",
            "list_storage_locations",
            "list_files",
            "read_file",
            "list_cameras",
            "list_camera_photo_resolutions",
            "list_camera_video_resolutions",
            "notification_list",
            "get_location",
            "get_shared_content",
        )

    val HIGHLIGHT_TOOLS: Set<String> =
        setOf(
            "tap",
            "long_press",
            "double_tap",
            "click_node",
            "long_click_node",
            "tap_node",
            "type_append_text",
            "type_insert_text",
            "type_replace_text",
            "type_clear_text",
            "scroll_node",
            "scroll_to_node",
            "swipe",
        )

    /** Classifies the un-prefixed [toolName]; unknown names are REFUSE. */
    fun classify(toolName: String): ToolGuidanceClass =
        when (toolName) {
            in READ_ONLY_TOOLS -> ToolGuidanceClass.READ_ONLY
            in HIGHLIGHT_TOOLS -> ToolGuidanceClass.HIGHLIGHT
            else -> ToolGuidanceClass.REFUSE
        }
}
