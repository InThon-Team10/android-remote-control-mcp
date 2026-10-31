# 사용자 조작 안내 기능

실제 앱 코드는 `android-remote-control-mcp` 프로젝트에 있습니다. 외부 AI가 기존 MCP 연결로
화면을 분석하고, 접근성 오버레이가 대상의 화면 좌표에 주황색 네모 테두리를 표시합니다.
오버레이는 터치와 키보드 입력을 가로채지 않습니다. 배경은 어둡게 하지 않습니다.

## UI 연결

`services/guidance/GuidanceController`가 UI와 독립적인 진입점입니다.

- `start(goal)` — 시작. 인자를 생략하면 `카카오톡 켜는법`.
- `state: StateFlow<GuidanceState>` — 목표, 상태, 화면 revision, 안내 문구, 대상 좌표, 단계 수.
- `show(revision, instruction, HighlightBounds(...))` — 해당 화면을 분석한 결과를 표시.
- `complete(revision)` — 화면 분석으로 목표 달성을 확인한 뒤 완료.
- `cancel()` — 세션 취소와 표시 제거.

suspend 함수는 ViewModel의 coroutine에서 호출할 수 있습니다. 내부에서 Main 스레드로 전환합니다.
접근성 서비스를 사용자가 활성화해야 합니다. AI 분석 결과가 없는 상태에서 임의의 버튼을 선택하지 않습니다.

## DeepSeek 직접 연결

Hilt로 `DeepSeekGuidanceAgent`를 주입하고 `agent.start(apiKey, goal)`을 호출하면 화면 분석,
네모박스 표시, 사용자 조작 대기, 다음 화면 재분석을 자동으로 반복합니다.
기본 목표는 `카카오톡 켜는법`입니다. `agent.stop()`으로 HTTP 요청까지 취소합니다.
UI에서는 `GuidanceController.state`와 `agent.status`를 구독하면 됩니다.

- 주소: `https://api.console.lablup.ai/v1/chat/completions`
- 모델: `DeepSeek-V4.1-Flash-B300`
- API 키: 실행 시 전달. 소스, APK 상수, DataStore, 로그에 저장하지 않음.
- 전송 정보: 목표, 직전 안내, 현재 화면의 접근성 텍스트/좌표. 기존 Privacy Mode 처리를 거침.
- 모델 출력: `guide` + 안내/대상, `complete`, `blocked`만 허용. 기기 조작 도구를 제공하지 않음.
- 분석 중 화면이 바뀌거나 취소하면 HTTP 요청을 취소하며 이전 화면의 응답을 적용하지 않음.
- 통신/응답 형식/대상 오류는 표시를 지우고 `ERROR`로 중단. 재시작은 start 호출.

UI 없는 테스트는 **debug APK만** 포함하는 `GuidanceTestReceiver`로 실행할 수 있습니다.
ADB shell만 가진 `android.permission.DUMP`로 제한되며 release APK에는 포함되지 않습니다.
키는 로컬 PowerShell의 `LABLUP_API_KEY` 환경변수에 설정하고 다음을 실행합니다.

```powershell
adb shell am broadcast -n com.danielealbano.androidremotecontrolmcp.foss.debug/com.danielealbano.androidremotecontrolmcp.debug.GuidanceTestReceiver --es operation agent_start --es api_key "$env:LABLUP_API_KEY"
adb shell am broadcast -n com.danielealbano.androidremotecontrolmcp.foss.debug/com.danielealbano.androidremotecontrolmcp.debug.GuidanceTestReceiver --es operation status
adb shell am broadcast -n com.danielealbano.androidremotecontrolmcp.foss.debug/com.danielealbano.androidremotecontrolmcp.debug.GuidanceTestReceiver --es operation agent_stop
```

테스트 시작 후 휴대폰에서 네모박스와 instruction에 따라 사용자가 직접 조작합니다.
안내 문구는 현재 status 결과에서 확인할 수 있으며 화면에 문구를 표시하는 UI는 아직 추가하지 않았습니다.

## MCP로 `카카오톡 켜는법` 테스트

기존 서버를 시작하고 MCP 클라이언트에서 다음 순서로 호출합니다. 이름의 `android_`는 기기 slug에 따라 달라집니다.

1. `android_guidance_start({})` — 테스트 목표로 시작.
2. `android_guidance_status({})`에서 revision을 얻은 **뒤** `android_get_screen_state`로 현재 화면 분석.
3. 다음 사용자 행동을 정하고 아래처럼 표시. node_id는 방금 분석한 화면의 실제 값으로 교체합니다.

```json
{
  "name": "android_guidance_show",
  "arguments": {
    "revision": 1,
    "instruction": "카카오톡 아이콘을 누르세요.",
    "node_id": "현재_화면에서_얻은_node_id"
  }
}
```

노드가 없는 제스처/시스템 탐색 안내는 node_id 대신 `left`, `top`, `right`, `bottom`을
실제 화면 픽셀 좌표로 지정합니다. 예를 들어 앱 서랍을 여는 스와이프는 시작 영역을 표시하고
instruction으로 방향을 설명합니다. 앱 실행 intent나 홈 이동을 AI가 대신 수행하지 않습니다.

4. `WAITING_USER`에서는 기다립니다. 사용자가 직접 터치/입력/스크롤하거나 화면이 바뀌면
   표시가 제거되고 revision이 증가하며 `NEEDS_ANALYSIS`가 됩니다.
5. 다시 2–4를 반복합니다. 카카오톡이 없거나 다른 화면으로 이동했으면 새 화면에서 판단합니다.
6. 카카오톡이 실제로 전면에 열린 것을 화면 분석으로 확인한 뒤
   `android_guidance_complete({"revision": 현재값})`를 호출합니다.
   테스트 목표에서는 서비스가 전면 패키지 `com.kakao.talk`도 검사합니다.
7. 중단은 `android_guidance_cancel({})`입니다.

화면 변경은 성공 신호가 아닙니다. 잘못 누르거나 대상 노드의 위치/내용이 바뀌면 재분석합니다.
대기 중 시계 등 다른 위젯만 갱신되면 대상 노드를 다시 확인하고 유효한 하이라이트는 유지합니다.
명시적인 좌표 안내는 고정 영역으로 취급하며 클릭/스크롤/입력/화면 전환 때 해제합니다.
이전 revision의 제안/완료, 대기 중 다음 단계 덮어쓰기, 화면 밖 좌표는 거부합니다.
중복된 동일 제안은 단계 수를 늘리지 않습니다. 회전/서비스 종료 시 표시를 제거합니다.

기존 tap/click 계열 MCP 호출도 실행 직전에 안내로 전환됩니다. node_id를 사용한 노드 클릭과
좌표 탭을 지원하며, 선택자 호출은 guidance_show에 명시적인 node_id를 전달해 사용합니다.
그 외 조작 도구는 오류와 함께 안내 API 사용을 요청합니다. 취소/완료 뒤에도 자동 조작은 허용되지 않습니다.
이 정책은 production `McpToolRegistration`에서 모든 도구 핸들러 앞에 설치되며,
새로 추가되는 조작 도구도 읽기 허용 목록에 없으면 실행할 수 없습니다.

네모박스 렌더러는 `GuidanceOverlay`에 분리했습니다. 추후 대상 밖을 어둡게 하는 효과로
바꾸어도 목표/단계/AI 연결 API를 유지할 수 있습니다.
