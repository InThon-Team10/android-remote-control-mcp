@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Uses the same encrypted key store and consent gate as the team's assistant. */
@AndroidEntryPoint
class HelperConnectionSettingsActivity : ComponentActivity() {
    @Inject lateinit var settings: AssistantSettings

    @Inject lateinit var starter: AssistantServerStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidRemoteControlMcpTheme(dynamicColor = false) {
                val config by settings.config.collectAsState(initial = AssistantConfig())
                var key by remember { mutableStateOf("") }
                var accepted by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                        Text("AI 연결 설정", style = MaterialTheme.typography.headlineSmall)
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("콕콕이가 안내하면 버튼은 내가 직접 눌러요.")
                            Text("요청을 보내면 질문과 현재 화면의 텍스트·버튼 이름·좌표가 Google Gemini로 전송돼요. 화면에 개인정보가 있으면 함께 전송될 수 있어요. 스크린샷은 보내지 않아요.")
                            Text(stringResource(R.string.helper_voice_privacy))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = accepted || config.privacyNoticeAccepted,
                                    enabled = !busy && !config.privacyNoticeAccepted,
                                    onCheckedChange = { accepted = it },
                                )
                                Text("화면 정보 전송을 이해하고 동의해요")
                            }
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it },
                                enabled = !busy,
                                label = { Text(if (config.apiKeyConfigured) "새 API 키 (비워두면 기존 키 유지)" else "Gemini API 키") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("키는 앱의 암호화 저장소에 보관해요. 채팅에 붙여 넣지 않아도 돼요.")
                            Text("사용 모델: " + config.model)
                            message?.let { Text(it) }
                            Button(
                                enabled =
                                    !busy && (accepted || config.privacyNoticeAccepted) && (key.isNotBlank() || config.apiKeyConfigured),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            if (key.isNotBlank()) {
                                                settings.setApiKey(key)
                                                key = ""
                                            }
                                            if (!config.privacyNoticeAccepted) settings.acceptPrivacyNotice()
                                            settings.setEnabled(true)
                                            starter.ensureRunning(this@HelperConnectionSettingsActivity).getOrThrow()
                                            message = "연결 준비가 끝났어요. 안내받을 앱으로 돌아가 도움 버튼에서 요청해 주세요."
                                        } catch (
                                            cancelled: CancellationException,
                                        ) {
                                            throw cancelled
                                        } catch (
                                            _: Exception,
                                        ) {
                                            message = "연결 준비에 실패했어요. MCP 앱의 서버 설정과 권한을 확인해 주세요."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text(if (busy) "연결 준비 중…" else "저장하고 연결 준비") }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        settings.setEnabled(false)
                                        message = "AI 연결을 껐어요."
                                    }
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) { Text("AI 연결 끄기") }
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("돌아가기") }
                    }
                }
            }
        }
    }
}
