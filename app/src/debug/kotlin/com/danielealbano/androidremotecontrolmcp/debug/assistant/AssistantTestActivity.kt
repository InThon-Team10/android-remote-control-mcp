package com.danielealbano.androidremotecontrolmcp.debug.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Debug-only launcher activity to exercise the headless assistant on a device. */
@AndroidEntryPoint
class AssistantTestActivity : ComponentActivity() {
    private val viewModel: AssistantTestViewModel by viewModels()

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startVoice()
            } else {
                viewModel.session.reportError(AssistantError.MicrophonePermissionMissing)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { viewModel.session.collapseRequests.collect { moveTaskToBack(true) } }
        setContent {
            AndroidRemoteControlMcpTheme {
                AssistantTestScreen(
                    viewModel = viewModel,
                    onSend = { text -> viewModel.send(this, text) },
                    onRecord = ::startRecording,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.session.setChatVisible(true)
    }

    override fun onStop() {
        viewModel.session.setChatVisible(false)
        super.onStop()
    }

    private fun startRecording() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
