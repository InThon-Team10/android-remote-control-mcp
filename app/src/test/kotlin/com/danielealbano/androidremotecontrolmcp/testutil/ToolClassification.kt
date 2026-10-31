package com.danielealbano.androidremotecontrolmcp.testutil

/**
 * Classification of every tool for the anti-prompt-injection rule: device-content tools put the untrusted-content
 * warning first in a successful answer, action-only tools return server-generated text and need none. A tool that is
 * in neither set fails the fuzz suite, forcing whoever adds a tool to classify it.
 */
internal object ToolClassification {
    val DEVICE_CONTENT =
        setOf(
            "android_get_screen_state",
            "android_find_nodes",
            "android_get_node_details",
            "android_get_clipboard",
            "android_wait_for_node",
            "android_wait_for_idle",
            "android_type_append_text",
            "android_type_insert_text",
            "android_type_replace_text",
            "android_type_clear_text",
            "android_list_storage_locations",
            "android_list_files",
            "android_read_file",
            "android_list_apps",
            "android_list_cameras",
            "android_list_camera_photo_resolutions",
            "android_list_camera_video_resolutions",
            "android_take_camera_photo",
            "android_notification_list",
            "android_get_location",
            "android_get_shared_content",
            "android_share_file_via_web",
        )

    val ACTION_ONLY =
        setOf(
            "android_press_back",
            "android_press_home",
            "android_press_recents",
            "android_open_notifications",
            "android_open_quick_settings",
            "android_dismiss_keyboard",
            "android_tap",
            "android_long_press",
            "android_double_tap",
            "android_swipe",
            "android_scroll",
            "android_pinch",
            "android_custom_gesture",
            "android_click_node",
            "android_long_click_node",
            "android_tap_node",
            "android_scroll_to_node",
            "android_scroll_node",
            "android_press_key",
            "android_get_accessibility_status",
            "android_set_clipboard",
            "android_write_file",
            "android_append_file",
            "android_file_replace",
            "android_download_from_url",
            "android_delete_file",
            "android_close_app",
            "android_open_app",
            "android_save_camera_photo",
            "android_save_camera_video",
            "android_send_intent",
            "android_open_uri",
            "android_notification_open",
            "android_notification_dismiss",
            "android_notification_snooze",
            "android_notification_action",
            "android_notification_reply",
        )
}
