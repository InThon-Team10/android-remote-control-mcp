package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

/** The app's launcher: explains the helper, walks through the setup and shows its real status. */
@AndroidEntryPoint
class HelperActivity : ComponentActivity() {
    private val viewModel: HelperEntryViewModel by viewModels()

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reportResume() }
    private val microphoneLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reportResume() }

    private val setupActions =
        object : HelperSetupActions {
            override fun openAccessibilitySettings() = PermissionUtils.openAccessibilitySettings(this@HelperActivity)

            override fun openAppInfo() =
                startSafely(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )

            override fun requestNotifications() = notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            override fun requestMicrophone() = microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)

            override fun acceptConsent() = viewModel.editor.acceptConsent()

            override fun setVoice(enabled: Boolean) = viewModel.editor.setVoice(enabled)

            override fun saveKey(key: String) = viewModel.editor.saveKey(key)

            override fun clearKey() = viewModel.editor.clearKey()
        }

    private val actions =
        object : HelperEntryActions {
            override fun startSetup() = viewModel.startSetup()

            override fun navigate(transition: (HelperEntryModel) -> HelperEntryModel) = viewModel.navigate(transition)

            override fun turnOn() = viewModel.turnOn(this@HelperActivity)

            override fun turnOff() = viewModel.turnOff()

            override fun openHelper() {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                startSafely(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Handler(Looper.getMainLooper()).post { viewModel.openInput() }
            }

            override fun setSpeech(enabled: Boolean) = viewModel.editor.setSpeech(enabled)

            override fun showIntroAgain() = viewModel.showIntroAgain()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.uiState.value.model.exitsOnBack) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        viewModel.navigate(HelperEntryModel::back)
                    }
                }
            },
        )
        setContent {
            val state by viewModel.uiState.collectAsState()
            AndroidRemoteControlMcpTheme {
                HelperEntryScreen(state, actions, setupActions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reportResume()
    }

    private fun reportResume() {
        val permissions =
            PermissionSnapshot(
                notifications = notificationsGranted(),
                microphone = PermissionUtils.isMicrophonePermissionGranted(this),
            )
        viewModel.onResume(this, permissions)
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || PermissionUtils.isNotificationPermissionGranted(this)

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No activity for ${intent.action ?: intent.component?.shortClassName}", e)
        }
    }

    private companion object {
        const val TAG = "MCP:HelperActivity"
    }
}
