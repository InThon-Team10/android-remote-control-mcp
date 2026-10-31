# 프런트엔드 시작하기

프로젝트 루트는 dev/android-remote-control-mcp 입니다. 현재 브랜치는 feat/helper-ui 입니다.

## 편집할 파일

공통 경로: app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/ui/

| 파일 | 수정할 내용 |
| --- | --- |
| helper/HelperRequestScreen.kt | 요청 입력창, 마이크 버튼, 준비 중/메시지 상태 |
| helper/HelperComponents.kt | 도움 버튼과 안내 카드 |
| helper/SpotlightOverlay.kt | 강조 영역 외 화면을 어둡게 그리는 방식 |
| theme/Color.kt | 기존 앱의 공통 색상 |
| theme/Type.kt | 기존 앱의 공통 글자 스타일 |

문구: app/src/main/res/values/helper_strings.xml
테마 파일을 수정하면 기존 MCP 화면에도 반영됩니다.

## 미리보기와 실행

1. Android Studio에서 이 저장소 루트를 엽니다.
2. Build Variants에서 app의 gmsDebug를 선택합니다.
3. app/src/debug/kotlin/com/danielealbano/androidremotecontrolmcp/ui/helper/HelperPreviewActivity.kt를 엽니다.
4. Split 또는 Design에서 요청/큰 글씨/안내/다크 준비 중 Preview를 확인합니다.
5. debug APK 설치 후 별도 아이콘 **도우미 UI 개발**을 실행합니다.

개발 화면은 기존 MCP 런처와 별개이며 release APK에는 포함되지 않습니다.
글을 입력해 안내 시작하기 → 연습 버튼 누르기 → 안내 닫기 흐름을 테스트할 수 있습니다.
마이크와 다시 듣기는 미연결 안내를 표시합니다. 녹음이나 다른 앱 조작은 수행하지 않습니다.
회전과 뒤로가기를 확인하고, 입력한 요청이 안내 화면에서 돌아왔을 때 유지되는지 확인합니다.

## 기능 담당자와 연결

UI 함수는 상태를 외부에서 받고 이벤트를 콜백으로 내보냅니다.
- onSubmit: 현재 request를 이용해 안내 요청. 준비 중에는 busy=true.
- onMicrophoneClick: 권한 확인 및 음성 인식 시작. 결과는 request에 반영.
- instruction: AI가 만든 현재 단계의 설명.
- onRepeat: instruction을 음성 합성에 전달.
- onClose: 안내 종료 및 오버레이 제거.
- target: SpotlightOverlay Canvas 기준 px Rect. 화면 절대 좌표를 그대로 넣지 마세요.
- HelperBubble: 클릭 UI만 제공. 실제 창 배치·드래그 확장·서비스 생명주기는 네이티브 호스트 담당.

개발 예제의 Canvas 터치 통과는 같은 Activity 안에서만 확인하는 것입니다.
다른 앱 위 터치 통과, 시스템 권한, 화면 위치 추적의 검증을 대체하지 않습니다.

## 로컬 빌드 조건

저장소의 현재 설정이 문서의 오래된 요구사항보다 우선합니다.
compileSdk=37, Gradle wrapper=9.7.1. 이번 설정에서 WSL SDK API 37을 설치했습니다.
local.properties는 WSL SDK 경로입니다. Windows Android Studio로 빌드한다면 SDK Manager에서
API 37 설치 후 local.properties의 sdk.dir을 Windows SDK 경로로 바꿔야 합니다.
이 파일은 Git에서 제외됩니다. Linux/Windows SDK 경로를 섞지 마세요.

WSL에서:
```sh
./gradlew :app:compileGmsDebugKotlin
./gradlew :app:ktlintCheck
./gradlew :app:testGmsDebugUnitTest
```

ngrok Java JAR는 로컬 Maven으로 준비했습니다. 터널을 실제 사용하는 경우에는 원본 프로젝트의 native tunnel 라이브러리 빌드도 필요합니다.
make build 조건은 CONTRIBUTING.md 및 Makefile을 참고하세요.
현재 셋업의 검증 결과는 채팅의 완료 메시지를 참고하세요.

## 이번 설정에서 확인한 결과

- Kotlin 컴파일, ktlint, detekt, gmsDebug APK 빌드 통과.
- SpotlightOverlayTest 통과: 화면 경계 clipping, 누락·화면 밖·역전·NaN·무한대 좌표.
- 원본 전체 테스트는 ngrok-java-native-host.jar가 추가로 필요합니다. 이번 UI 단위 테스트는
  임시 Gradle init 설정(/tmp/inthon-ui-test.init.gradle)에서 그 파일 의존성만 제외하고 실행했습니다.
  원본 build.gradle.kts와 다른 테스트 설정은 변경하지 않았습니다.
- 전체 테스트 스위트 및 실제 시스템 오버레이 동작은 검증하지 않았습니다.
- 빌드 도구에서 JDK deprecated API 및 일부 라이브러리 debug symbol 제거 불가 경고가 나왔으나 APK 생성은 성공했습니다.

### Manual QA Steps — Android API 35 에뮬레이터에서 확인

- 빈 입력에서 시작 버튼 비활성화.
- 마이크 버튼: 미연결 안내 표시, 실제 녹음 없음.
- 요청 입력 → 안내 화면 전환 → 강조 영역 안 연습 버튼 클릭 → 성공 문구 표시.
- 뒤로가기 후 입력한 요청 유지.
- 기본 글씨와 글자 크기 200%에서 화면 캡처 확인.
- Compose Preview 자체와 실제 음성·다른 앱 조작은 별도 확인이 필요합니다.

## 실제 오버레이 디자인 확인

1. '도우미 UI 개발'에서 **다른 앱 위에 띄워보기**를 누릅니다.
2. Android 접근성 설정의 다운로드한 앱에서 **도우미 오버레이 디자인**을 켭니다.
3. 홈이나 다른 앱으로 이동하면 오른쪽의 웃는 얼굴 캐릭터가 남아 있습니다.
4. 캐릭터를 누르거나 왼쪽으로 밀면 말풍선이 열립니다.
5. **강조 화면 보기**는 임의 위치를 밝게 남기는 디자인 예시입니다. 실제 화면을 분석하지 않습니다.
6. **버블로 돌아가기**는 현재 안내를 끝내고 캐릭터로 돌아갑니다. 다시 누르면 작성한 질문을 보존한 입력 화면을 엽니다. 메뉴의 **도우미 종료**는 서비스를 종료합니다.

디자인 편집:
- app/src/debug/kotlin/com/danielealbano/androidremotecontrolmcp/ui/helper/HelperOverlayDesign.kt
- 문구: app/src/debug/res/values/helper_overlay_strings.xml
- 창 배치/터치/생명주기: 같은 패키지 HelperOverlayPreviewService.kt

이 디자인 서비스는 화면 내용을 읽거나 대신 조작하지 않습니다.
실제 권한 안내는 Android 설정에서 확인할 수 있으며 서비스는 release에 포함되지 않습니다.

### Overlay verification

- assembleGmsDebug 및 ktlintCheck 통과.
- Android API 35 에뮬레이터에서 접근성 설정을 통해 디자인 서비스 활성화.
- 홈 화면 위 측면 캐릭터와 Chrome 위 말풍선 및 텍스트 입력 표시 확인.
- 전체 타입 기반 detektGmsDebug에는 원본 코드의 분석 오류가 남아 있다. 새 ui/helper 파일 관련 지적은 수정 후 재검사에서 검출되지 않았다.
- 실제 화면 인식/AI 실행은 미연결이며, 강조 영역의 실제 다른 앱 터치 전달 및 회전/키보드 경계 동작은 추가 검증 대상이다.


## A/B 실제 Gemini 연결 (2026-11-01)

버블 → 입력창에서 A/B 선택 → AI 연결 설정에서 전송 안내 확인 및 API 키 저장 → 안내받을 앱으로 돌아가 요청 보내기.
Android Remote Control MCP 접근성 서비스, MCP 서버의 Bearer 인증과 get_screen_state 도구가 필요합니다.
두 버전은 같은 HelperAssistantConnection을 쓰며 화면 텍스트/버튼 정보와 질문만 Gemini로 보냅니다. 실제 탭은 사용자가 수행합니다.
입력 ‘테스트’는 선택한 A/B의 로컬 예시로 실행됩니다. 마이크 예시는 아직 실제 음성 입력에 연결되지 않았습니다.
연결 코드는 debug ui/helper의 HelperAssistantConnection.kt, 설정 화면은 HelperConnectionSettingsActivity.kt입니다.
기존 Gemini 자동 실행/음성 테스트 화면은 별개로 유지됩니다. A/B에서는 자동 실행 세션을 시작하지 않습니다.

이번 연결 검증: assembleGmsDebug·ktlintCheck 및 HelperAssistantConnectionTest 4개 통과. 기존 native ngrok host JAR 누락을 제외하는 로컬 테스트 init 설정을 사용했습니다. 실제 Gemini 응답과 에뮬레이터 전체 A/B 흐름 검증은 사용자 요청으로 다음에 진행합니다.


## 첫 실행·재방문 공통 화면과 A/B 브랜치

- `feat/helper-version-a`: 설명 패널을 먼저 표시하는 A 안내.
- `feat/helper-version-b`: 말풍선을 먼저 표시하는 B 안내.
- 두 브랜치의 소개·시작 설정·재방문 홈은 동일합니다. 차이는 debug `ui/helper/HelperBuildVariant.kt`의 안내 버전 값입니다.
- **도우미 UI 개발** 아이콘을 열면 최초 소개가 표시됩니다. 소개를 지나간 다음에는 홈이 표시됩니다.
- 시작 설정과 버블 켜기는 “목표 화면 · 기능 미연결”로 표시되는 모의 동작입니다. 실제 접근성 권한·서비스·AI 준비 상태를 변경하거나 보증하지 않습니다.
- 홈 → 설정에서 설정 완료/권한 해제, 다음 실행 실패, 최초 안내 재진입을 확인할 수 있습니다.
- 홈 → 설정 → 기존 오버레이 개발 화면에서 실제 오버레이 및 AI 연결 설정으로 이동합니다. 실제 연결 기능의 범위와 동의 조건은 DESIGN.md의 Shared A/B Gemini connection 절을 따릅니다.
- `scripts/check-helper-entry.sh`는 컴파일된 목업 상태의 최초 진입·권한 거절·중복 실행·종료/권한 해제 후 지연 완료·실패/재시도를 검사합니다.
- 사용자 요청으로 추가 화면 검증을 중단했습니다. 새 첫 화면의 200% 글씨·가로 화면·보조 기술 검증은 남아 있습니다.
