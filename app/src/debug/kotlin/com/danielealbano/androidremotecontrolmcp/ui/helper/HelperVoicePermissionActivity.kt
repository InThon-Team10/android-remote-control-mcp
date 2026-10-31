package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ResultReceiver

/** Only Android's permission UI is shown; recognition stays in the overlay input field. */
class HelperVoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            complete(true)
        } else if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            complete(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun complete(granted: Boolean) {
        val receiver = intent.getParcelableExtra(EXTRA_RESULT, ResultReceiver::class.java)
        finish()
        receiver?.send(if (granted) Activity.RESULT_OK else Activity.RESULT_CANCELED, null)
    }

    companion object {
        const val EXTRA_RESULT = "voice_permission_result"
        private const val REQUEST_MICROPHONE = 1
    }
}
