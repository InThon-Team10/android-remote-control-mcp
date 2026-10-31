# 시작 화면 (도우미 진입 화면)

앱의 런처 화면은 `ui/helper/HelperActivity`입니다. 모든 빌드(debug/release)에서 같은 화면이 열립니다.
기존 MCP 서버 설정 화면(`MainActivity`)은 설정 → 고급 설정(MCP 서버)에서 열립니다.

## 흐름

| 페이지 | 내용 |
| --- | --- |
| WELCOME | 최초 안내. 안내를 아직 보지 않았을 때만 첫 화면입니다. "시작 설정하기"를 누르면 SETUP으로 가고 안내를 본 것으로 저장합니다. |
| SETUP | 실제 상태 체크리스트. 화면으로 돌아올 때마다(ON_RESUME) 다시 확인합니다. |
| HOME | 실제 상태(버블, MCP 서버, 접근성)와 "설정 이어하기 / 버블 켜기 / 켜는 중… / 도우미 열기 / 다시 시도" 버튼. |
| HELP | 사용 방법. 열었던 페이지로 돌아갑니다. |
| SETTINGS | 음성 안내, AI 연결 키, 최초 안내 다시 보기, 고급 설정, (debug) AI 테스트 화면. |

시스템 뒤로 가기는 WELCOME과 HOME에서만 화면을 닫고, 나머지 페이지는 이전 페이지로 돌아갑니다.

### 시작 설정 체크리스트

1. 접근성 서비스: 접근성 설정을 엽니다. Android 13 이상에서는 "제한된 설정 허용" 안내와 앱 정보 버튼도 보입니다.
2. 알림: Android 13 이상에서만 권한을 요청합니다. 그 아래 버전에서는 완료로 표시합니다.
3. 마이크(선택): 허용하면 "음성으로 대화하기" 스위치가 나타납니다.
4. AI 사용 동의: 요청 글, 화면 내용(스크린샷 포함), 음성(켠 경우)이 Google Gemini로 전송된다는 안내와 무료 이용 단계의 데이터 사용 안내.
5. AI 연결: 빌드에 앱의 OpenAI 키(`OPENAI_API_KEY`)가 들어 있으면 이 단계는 보이지 않고 완료로 처리됩니다.
   키가 없는 빌드에서만 키를 입력하는 화면이 나타납니다.

접근성(1), 동의(4), AI 연결(5)이 끝나면 "버블 켜기"가 활성화됩니다. 버튼은 보이는 화면에서 MCP 서버를 시작하고,
도우미를 켠 뒤 HOME으로 이동합니다. "도우미 열기"는 휴대폰 홈 화면을 연 다음 버블의 입력창을 펼칩니다.

## 파일

경로: `app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/ui/helper/`

| 파일 | 역할 |
| --- | --- |
| HelperActivity.kt | 권한 요청, 시스템 화면 열기, 상태 보고 |
| HelperEntryViewModel.kt | 설정·버블·서버·접근성·권한을 하나의 `HelperEntryUiState`로 합치고 화면 이동 상태를 저장 |
| HelperSettingsEditor.kt | 동의, 음성, 키 등 설정 변경 |
| HelperEntryModel.kt | 화면 이동과 상태 계산(순수 로직) |
| HelperEntryScreen.kt, HelperEntryPages.kt, HelperEntrySetup.kt, HelperEntryParts.kt | Compose 화면 |
| HelperEntryActions.kt | 화면이 내보내는 사용자 동작 인터페이스 |

문구: `app/src/main/res/values/helper_entry_strings.xml` (이름은 `entry_` 접두사).

## APK 만들기 (OpenAI 키 포함)

앱의 OpenAI 키는 저장소 루트의 `local.properties`(Git에서 제외됨)에 두거나 `OPENAI_API_KEY` 환경 변수로 넘깁니다.
키는 release와 debug 빌드 모두에 들어갑니다. 해커톤용 빌드이며 앱 스토어에 배포하지 않습니다.

```properties
OPENAI_API_KEY=여기에_키
```

그 뒤 `./gradlew :app:assembleGmsDebug`로 빌드하면 키가 빌드 시점에 앱에 들어갑니다.
이 파일과 키는 커밋하거나 GitHub에 올리지 마세요. 로그나 PR 설명에도 적지 않습니다.

## 검증

```sh
./gradlew :app:compileGmsDebugKotlin
./gradlew :app:testGmsDebugUnitTest --tests '*HelperEntryModelTest' --tests '*HelperEntryViewModelTest' --tests '*ExportedComponentsManifestTest'
./gradlew :app:ktlintCheck :app:detekt
```

### Manual QA Steps

- 첫 실행에서 WELCOME이 열리고, 안내를 본 뒤 다시 실행하면 HOME이 열립니다.
- 글자 크기 200%, 가로 화면, 어두운 테마, TalkBack에서 각 페이지를 확인합니다.
- 접근성을 켠 뒤 앱으로 돌아오면 체크리스트가 완료로 바뀝니다.
- 화면을 회전해도 현재 페이지가 유지됩니다.
