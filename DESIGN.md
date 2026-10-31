---
version: alpha
name: "InThon Helper UI"
description: "실제 화면을 한 단계씩 안내하는 한국어 안드로이드 도우미의 개발용 UI"
colors:
  primary: "#4A5FE0"
  surface: "#FBF8FF"
  onSurface: "#1B1B21"
typography:
  sans:
    fontFamily: "Android system default with Korean fallback"
spacing:
  page: "24dp"
  gap: "16dp"
rounded:
  button: "MaterialTheme.shapes default"
components:
  request: "HelperRequestScreen"
  guidance: "GuidanceCard"
  bubble: "HelperBubble"
  spotlight: "SpotlightOverlay"
---

# Design context

현재 범위는 화면 개발용 구성요소와 debug 전용 실행 화면이다. 앱 아이콘은 2026-11-01 사용자가 승인한 손을 든 휴대폰 캐릭터로 확정했다. 아래 확정 앱 아이콘 절을 따른다.
사용자 요구: 정보 취약 계층을 위한 쉬운 설명, 텍스트/음성 입력, 현재 화면 강조와 안내.

## 지속 디자인 원칙: 유니버설 디자인과 포용적 UI/UX

이 프로젝트의 모든 화면, 오버레이, 문구, 상호작용은 아래 원칙을 기본 설계 기준으로 삼는다. 일시적·상황적 제약을 포함해 시각, 청각, 운동, 인지, 언어, 디지털 경험의 차이를 고려한다. 정보 취약 계층을 하나의 능력이나 연령으로 단정하지 않는다. 이 절은 기존 시안의 장식·배치 지침보다 우선하며, 현재 구현의 접근성 인증을 뜻하지 않는다.

### 핵심 규칙

1. **UD-01 동등한 사용과 존중.** 주요 기능에 동등하게 접근할 수 있게 한다. 특정 사용자를 열등하게 표현하거나 유아화하지 않는다. 쉬운 표현과 친근함은 유지하되 선택권을 빼앗지 않는다.
2. **UD-02 사용 방법의 유연성.** 음성과 텍스트를 상호 대안으로 제공한다. 말하기·듣기·드래그·정밀 터치를 유일한 방법으로 강요하지 않는다. 음성 안내에는 텍스트를 제공하고, 제스처에는 탭 대안을 둔다. 미구현 기능은 사용 가능한 것처럼 표시하지 않는다.
3. **UD-03 단순하고 직관적인 안내.** 한 번에 한 행동을 설명한다. 쉬운 한국어와 일관된 명칭을 쓰며 전문용어·기억 의존·숨은 제스처를 줄인다. 아이콘만으로 의미가 불분명한 행동은 텍스트 라벨을 함께 표시한다.
4. **UD-04 알아볼 수 있는 정보.** 색상·빛·위치·소리 하나에만 의미를 의존하지 않는다. 실행·듣는 중·처리 중·완료·실패를 실제 상태에 맞는 문구와 함께 구분한다. 안내 대상, 설명, 종료 수단이 장식보다 먼저 눈에 들어와야 한다.
5. **UD-05 실수 허용과 복구.** 잘못 눌러도 되돌아오게 하고 입력값을 보존한다. 오류는 이유와 다음 행동을 알려준다. 결제·전송·삭제 같은 결과가 큰 행동은 실행 전에 대상과 결과를 확인할 수 있어야 한다. 안내와 자동 실행을 구분한다.
6. **UD-06 적은 신체·인지 부담.** 반복 입력, 오래 누르기, 정교한 움직임, 불필요한 단계와 강제 시간 제한을 줄인다. 안내를 사용자의 속도로 읽고 다시 확인할 수 있게 한다. 깜빡임을 피하고 시스템의 애니메이션 감소 설정을 존중한다.
7. **UD-07 충분한 크기와 공간.** 모든 터치 영역은 최소 48×48dp, 주요 행동과 닫기는 56×56dp 이상을 프로젝트 기준으로 한다. 인접 터치 영역이 겹치지 않게 하고 좁은 화면·키보드·가로 화면에서도 잘리지 않게 한다. 아이콘 그림 크기와 실제 터치 영역을 구분한다.
8. **IN-01 읽기와 보조 기술.** 주요 설명은 기본 18sp 이상을 프로젝트 기준으로 하며 현재 더 큰 안내 글씨를 이유 없이 축소하지 않는다. sp와 시스템 글자 확대를 지원하고 200% 확대에서 핵심 내용·행동이 잘리거나 겹치지 않게 검증한다. 프로젝트 텍스트 대비는 크기와 관계없이 4.5:1 이상을 목표로 한다. TalkBack 이름·역할·상태·읽기 순서와 외부 키보드/Switch Access 접근을 검증한다. 장식 이미지는 읽기에서 제외한다.
9. **IN-02 사용자 통제와 맥락 보존.** 도우미가 무엇을 하고 있는지 알리고, 언제든 쉽게 접거나 종료하게 한다. 복귀 수단은 스크롤 없이 보이는 ‘도움 버튼으로 돌아가기’ 텍스트 버튼으로 제공한다. 표시 문구와 접근성 이름을 일치시킨다. 버블 복귀와 전체 서비스 종료를 구분한다. 설명은 대상 근처에 배치하되 대상·닫기·시스템 조작을 가리지 않는다. 밝은 강조 영역과 안내 위치는 실제 화면 좌표에 맞아야 하며 예시 좌표는 예시로 명시한다.
10. **IN-03 다양한 사용자와 함께 검증.** 개발자 취향이나 스크린샷만으로 사용성을 확정하지 않는다. 디지털 경험·시각·운동·청각·인지 조건이 다양한 사용자의 피드백을 반영한다. 사용자의 실수를 탓하지 않고 디자인의 장벽을 찾아 개선한다. 작은 기기, 큰 글씨, 밝고 어두운 배경, 한 손 조작, 음성 사용 불가 상황을 검토한다.

### 디자인 작업 때 적용하는 절차

- 구현 전 이 절을 읽고 변경이 영향을 주는 규칙을 확인한다. 기존 컴포넌트를 재사용하되 기존 구현을 원칙 준수의 증거로 간주하지 않는다.
- 구현 후 해당 변경의 글자 잘림·대비·터치 영역·라벨·상태·복귀 동작을 확인한다. 보조 기술과 실제 사용자 테스트를 하지 않았다면 미검증으로 기록하며 통과했다고 말하지 않는다.
- 장식을 줄일 때 인지 가능성과 조작 가능성을 함께 검토한다. 작고 흐리게 만드는 것을 단순화와 혼동하지 않는다.
- 디자인 전용 작업에서 위반을 발견하더라도 요청과 무관한 기능을 임의로 대규모 변경하지 않는다. 관련 위반과 보완안을 알린다.

### 사용자 요청과 원칙이 충돌할 때

사용자 결정(2026-10-31): **요청한 변경은 우선 구현하고, 충돌하는 원칙과 영향을 반드시 알린다.** 디자인 원칙 충돌만을 이유로 작업을 중단하거나 재승인을 요구하지 않는다. 취향이 다르다는 이유만으로 위반이라고 하지 않는다.

- 요청 범위대로 구현하면서 함께 충족할 수 있는 접근성 조치는 유지한다.
- 최종 응답에 `원칙 충돌: [규칙 ID/이름] — [요청한 변경]으로 [영향]이 있습니다. 보완안: [구체적인 대안].` 형식으로 짧게 알린다.
- 측정하지 않아 확정할 수 없는 경우 `위반 가능성 / 미검증`으로 구분한다. 미검증을 확정 위반으로 단정하지 않는다.
- 지속되는 예외는 이 문서의 예외 기록에 요청·영향·대안·검증 상태를 남긴다. 일회성 예외를 전체 원칙 변경으로 확대하지 않는다. 원칙 자체를 바꾸라는 명시적 요청이 있을 때만 기준을 수정한다.
- 실제 구현이 다른 상위 안전·권한 제한으로 불가능한 경우에는 완료했다고 말하지 않고 구체적 제한을 설명한다.

### 예외 및 검증 기록

현재 새로 승인된 원칙 예외는 없음. 이전 시안 전체의 준수 검토는 아직 하지 않았다. 실제 음성/AI 연결, 동적 타깃 추적, TalkBack·Switch Access·200% 글자 확대·다양한 사용자 테스트는 이 문서 작성만으로 완료되지 않는다.

### 기준 참고

- [Android 접근성 가이드](https://developer.android.com/guide/topics/ui/accessibility/apps): 최소 48dp 터치 영역과 텍스트 대비. 주요 버튼 56dp, 주요 설명 18sp, 모든 텍스트 4.5:1 목표는 이 프로젝트가 정한 추가 기준이다.
- [W3C 대비 기준 설명](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html): 일반 텍스트 4.5:1, 큰 텍스트 3:1의 대비 기준 참고. 웹의 pt/px 기준을 Android sp와 무조건 동일시하지 않는다.
- [NC State 유니버설 디자인 센터](https://design.ncsu.edu/research/center-for-universal-design/): 유니버설 디자인 배경. 위 규칙은 우리 앱을 위한 적용 규칙이며 공식 표준의 전문 인용이 아니다.

## Visual direction and runtime ownership

기존 Material 3 테마를 유지한다. 별도 폰트·아이콘 라이브러리를 추가하지 않는다.
한 번에 한 행동을 알려주는 안내 카드와 밝은 목표 영역이 시각적 중심이다.
장식보다 큰 글씨, 명확한 행동 문구, 여유 있는 터치 영역을 우선한다.

런타임 토큰이 기준이다:
- 색상: app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/ui/theme/Color.kt
- 글꼴/크기: 같은 폴더 Type.kt (headlineMedium 28sp/36sp, bodyLarge 16sp/24sp)
- 테마 연결: Theme.kt → MaterialTheme → ui/helper 구성요소.
- 개발 화면은 dynamicColor=false로 고정하며 기존 앱의 테마 설정은 변경하지 않는다.
- 컴포넌트 간격은 각 ui/helper 파일에 정의한다. 새 전역 토큰 시스템은 만들지 않는다.

## Canonical UI map

| Capability | Owner | Contract |
| --- | --- | --- |
| Form | Material 3 OutlinedTextField / Button | 빈 요청·준비 중에는 제출 불가, 입력값은 호출자가 관리 |
| Guidance | GuidanceCard | 안내 문자열과 다시 듣기/닫기 콜백을 받음 |
| Overlay drawing | SpotlightOverlay | Canvas 로컬 px 좌표, 터치 이벤트를 가로채지 않는 시각 레이어 |
| Bubble | HelperBubble | 텍스트가 있는 클릭 가능한 도움 버튼. 창 생성·드래그는 호스트 책임 |
| Status | Text + polite live region | 입력 화면 메시지와 안내 문장 상태를 음성 접근성에 전달 |

## Layout and accessibility

요청 화면과 안내 예제는 세로 스크롤을 지원한다. 요청 화면은 IME 여백을 적용한다.
개발 Activity는 safeDrawingPadding을 적용한다. 주요 버튼 최소 높이는 56dp이며 고정 높이로 글자를 자르지 않는다.
안내 카드의 버튼은 세로 배치하여 큰 글씨와 좁은 화면에 대응한다.
동작은 표준 Material 버튼으로 제공하고 사용자 설정을 무시하는 애니메이션은 추가하지 않는다.

## Locale and scope

새 도우미 UI는 한국어 프로토타입이며 문구는 helper_strings.xml에 모은다.
기존 앱 번역은 변경하지 않는다. 타 언어 배포 전에 새 문자열 번역을 추가해야 한다.
개발 화면은 샘플임을 항상 표시한다. 실제 음성 인식/음성 합성/다른 앱 조작/결제는 연결하지 않는다.
시스템 오버레이 권한·접근성 서비스·터치 전달은 별도 네이티브 호스트에서 구현하고 실제 기기에서 검증해야 한다.

## Overlay-first design revision

사용자 스케치와 Siri + Bixby 참조에 따른 투명 오버레이. 내부 개발 화면은 유지한다.
- 버블을 펼친 즉시 배경 앱이 보이되 검정 60% 딤으로 낮춘다. 사용자의 후속 요청에 따라 하단 외곽 상자를 제거한다.
- HelperOverlayFrame: 청록 #62D8FF → 보라 #7B78FF → 분홍 #F29BD5 → 연보라 #987BFF의 정적인 그라데이션 테두리. HelperOverlayFrame이 해당 장식 색의 런타임 소유자다. 흰색 경계선 없이 7dp 핵심 선에서 안쪽 약 28dp까지 48개의 미세한 선을 겹치고 불투명도를 제곱 곡선으로 낮춰 빛이 부드럽게 사라지도록 표현한다. 중앙에는 검정 60% 딤을 그리며 터치를 받지 않는다.
- HelperOverlaySpeech: 외곽 배경 없는 하단 입력 바. 입력창만 LightColorScheme.surface / onSurface로 밝게 고정하고 24dp 모서리를 사용한다. 상태·접기·닫기는 어두운 배경 위 흰색으로 표시한다. 상태는 titleMedium으로 도우미 실행 중을 명시하며 미리보기 표기를 유지한다.
- 입력창 옆 마이크는 56dp 원형 버튼. 음성 입력 예시를 열고 다시 닫을 수 있다. 실제 녹음·AI 요청은 연결하지 않았으며 미리보기임을 표시한다.
- 입력 문자열은 서비스가 보관하여 접었다 펼쳐도 유지한다. V 접기 버튼은 제거하고 X를 누르면 키보드를 닫고 버블로 돌아간다. 서비스는 종료하지 않는다.
- HelperOverlayPreviewService가 비터치 테두리 창을 먼저, 입력 가능한 하단 창을 나중에 붙인다. 하단 창 바깥은 기존 앱 터치가 가능하다.
- 색상과 글꼴은 기존 Color.kt / Type.kt → MaterialTheme에서 가져온다. 별도 컬러나 애니메이션 체계는 추가하지 않는다.
- 한국어 문구는 debug helper_overlay_strings.xml에서 관리한다. 접근성 이름은 기존 표준 Compose 버튼에 제공한다.

## Soft edge revision
사용자 피드백에 따라 선 형태의 테두리와 겹친 스트로크를 제거했다. HelperOverlayFrame은 가장자리 네 지점의 넓은 radialGradient 빛만 그린다. 최대 불투명도 42%에서 22%, 6%, 0%로 연속 감쇠한다. 어두운 배경과 실행 중 문구는 유지한다. 이 결정이 앞선 테두리 두께 명세를 대체한다.

## Visible edge glow correction
너무 옅은 radial 빛을 폐기한다. 네 가장자리에서 95% → 85% → 50% → 15% → 0%로 44dp에 걸쳐 감쇠하는 연속 linearGradient를 사용한다. 별도 실선 없이 가장자리의 밝기를 확보하고 중앙 화면은 딤만 유지한다.

## Guidance layout
가이드 예시 시작 시 입력 창을 제거하고 강조 대상 바로 아래에 꼬리가 있는 밝은 말풍선을 표시한다. 오른쪽 위의 56dp 이상 밝은 X 닫기 버튼은 버블 복귀 동작이다. 강조 영역과 말풍선은 터치 불가 창에, 닫기 버튼은 별도 작은 터치 창에 배치한다. 현재 타깃은 유튜브 검색 버튼의 고정 예시 좌표이며 자동 탐지·추적은 미연결이다.

## Restrained guidance revision
사용자가 승인한 정리: 프레임은 파랑 #819DFF와 연보라 #B3A7F2 두 색으로 제한한다. 빛 번짐 폭은 22dp, 강조선은 1.5dp. 말풍선 titleLarge 글씨는 유지하되 안쪽 여백은 가로 18dp/세로 14dp, 꼬리는 16x8dp, 그림자는 3dp로 축소한다. 닫기 버튼의 보라 경계선은 제거하고 56dp 이상 터치 영역과 텍스트는 유지한다. 런타임 소유자는 HelperOverlayDesign.kt이며 위의 이전 장식 수치를 대체한다.

## Inclusive guidance revision
가이드 중 입력창은 계속 숨기되 하단 조작 패널에 다시 질문하기, 안내 읽어주기, 작게 접기, 도우미 종료를 제공한다. 다시 질문하기는 입력값을 보존하고 입력 화면으로 복귀한다. 작게 접기는 버블로 복귀하며 종료는 접근성 미리보기 서비스를 비활성화한다. 입력 화면의 X도 작게 접기라는 텍스트 버튼으로 변경했다.
Android TTS의 한국어 사용 가능 여부를 확인하고 읽기 버튼을 활성화한다. 미지원 시 이유를 텍스트로 표시한다. 음성 인식 및 AI 판단은 미연결이다.
검증: 빌드·ktlint 통과, 세로 에뮬레이터에서 200% 글자 확대 시 말풍선과 버튼의 잘림·겹침 없음, 다시 질문하기 복귀 확인. 기본 글자 크기 1.0으로 원복. TalkBack/Switch Access 실사용, 실제 음성 청취, 가로/다른 기기와 사용자 테스트는 미검증. 고정된 예시 타깃은 화면 변화에 맞춰 추적되지 않아 IN-02 충족 전이며 실제 서비스에서 동적 타깃 좌표와 변경 시 안내 무효화가 필요하다. 캡처는 디자인 예시이며 원칙 전체 준수의 증거가 아니다.

## Compact guidance menu
하단 전체 폭 패널을 기본으로 표시하지 않는다. 가이드에서는 최소 높이 56dp의 도움 메뉴 버튼만 오른쪽 아래에 두고, 탭하면 기존 재질문/읽기/접기/종료와 메뉴 접기/안내 숨기기/위치 옮기기를 표시한다. 접힌 창은 WRAP_CONTENT이므로 나머지 하단은 터치를 가로채지 않는다. 안내 숨기기는 딤·프레임·말풍선·강조 창을 제거하고 안내 다시 보기 버튼만 남긴다. 메뉴 위치 옮기기는 네 모서리를 순환하는 탭 대안이다. 현재 상단 타깃 예시에 맞춰 초기 메뉴는 아래에 배치하며 임의 타깃을 자동 감지해 위치를 바꾸는 기능은 아직 없다. 키보드나 글자 확대에 따라 메뉴 내용은 제한된 높이 안에서 스크롤한다.

메뉴 진입 버튼은 기능을 예고하도록 ‘다시 질문·더 보기’로 표시한다. 탭하면 재질문을 즉시 실행하는 대신 기존 도움 메뉴를 펼친다. 터치 크기와 위치 이동·숨기기 동작은 유지한다.

## X restoration and consistent menu
사용자 결정: 작게 접기 텍스트를 X로 복원. 입력·가이드·메뉴의 HelperCollapseButton은 56dp 밝은 원형 X이며 버블 복귀로 통일한다. 접근성 이름은 도우미 화면 닫고 버블로 돌아가기. 펼친 메뉴는 24dp 모서리 표면 안에 동일한 56dp 이상 행, 24dp 아이콘, titleMedium 왼쪽 정렬로 통일한다. 메뉴 접기는 메뉴만 접고, X는 버블 복귀, 도우미 종료는 서비스 종료다. UD-03 예외: 시각적 X만으로 버블 복귀를 예측하기 어려울 수 있다. 접근성 이름은 제공하며 필요 시 최초 사용 안내로 보완한다. 메뉴 자동 이동 및 타깃에 따른 말풍선 재배치는 아직 미구현이다.
검증: assembleGmsDebug·ktlintCheck·디자인 정적 audit 통과. 에뮬레이터에서 입력 X, 통일된 메뉴 렌더링 및 메뉴 X → 버블 복귀 확인. 변경 후 200% 글씨·TalkBack·Switch Access는 미검증.

## Centered menu
펼친 메뉴는 어느 모서리에서 열어도 화면 중앙, 전체 너비에서 좌우 16dp 여백으로 표시한다. 최대 높이는 화면의 85%이며 내용은 스크롤 가능하다. 열기 클릭 시 실제 접힌 메뉴 버튼 위치를 캡처해 240ms 확대/이동/페이드로 중앙 패널을 표시한다. 닫기는 즉시 처리하고 위치 이동한 접힌 버튼은 그대로 유지한다. 시스템 애니메이션 비활성화 시 모션을 생략한다. 메뉴가 열린 동안 화면 일부를 가리는 것은 사용자가 요청한 일시적 메뉴이며 메뉴 접기/X로 복귀한다.

## Bottom-centered menu correction
사용자 정정: 펼친 메뉴는 화면 하단 중앙(좌우 16dp 여백)에 고정한다. 메뉴 내부 X는 메뉴만 접으며 접근성 이름도 메뉴 접기이다. 접힌 메뉴 옆 및 입력 화면 X는 버블 복귀를 유지한다. 이전 중앙 배치 및 메뉴 내부 X의 버블 복귀 규칙을 대체한다. 열기 모션만 기존 버튼 위치에서 하단 중앙으로 이동한다.

말풍선 꼬리와 본체 접합부의 그림자 제거: 안내 말풍선 Surface의 shadowElevation은 0dp. 꼬리와 본체는 동일한 surface 색을 유지한다. 다른 메뉴/버튼의 그림자는 유지한다.

## Target-aware caption placement
HelperOverlayFrame의 targetBounds는 오버레이 내부 px Rect이며 강조 구멍과 말풍선이 동일한 좌표를 사용한다. null이면 기존 유튜브 검색 예시 좌표를 사용한다. instruction 문자열도 외부에서 전달 가능하다. HelperAnchoredCaption은 SubcomposeLayout으로 실제 sp 확대/줄바꿈 결과를 측정하고 아래 공간이 충분하면 아래, 부족하면 위로 배치한다. 좌우 16dp 여백 안으로 본체를 제한하고 꼬리는 타깃 중심을 향하도록 기울이며 위 배치 시 아래로 뒤집는다. 그림자는 0dp로 유지한다.
실제 AI 탐지·좌표 추적은 아직 연결되지 않았다. 위아래 어느 쪽에도 전체 문장이 들어가지 않을 정도로 큰 콘텐츠는 여유가 큰 쪽을 택하고 화면 안으로 이동하므로 대상과 겹칠 수 있다. 이 극단 조건은 IN-02 한계이며 실제 연동 전에 짧은 단계별 문장 또는 별도 상세 안내가 필요하다.
검증: 앱 빌드 및 ktlint 통과. JUnit 실행은 기존 ngrok-java-native-host.jar 누락으로 차단. scripts/check-helper-caption.sh는 컴파일된 실제 Kotlin 함수를 직접 호출하여 아래·위·큰 글씨·좌우 경계 5조건 통과. TalkBack/Switch Access 및 실제 사용자 검증은 미완료.


## Consequence explanation A/B prototypes
Current owner: ConsequenceGuideModel / ConsequenceGuideHost, hosted by HelperOverlayPreviewService; entry is HelperPreviewActivity → 결과 설명 A/B 비교 → ConsequencePreviewActivity (debug, not exported).
Both variants share LightColorScheme, titleLarge explanation, 18sp facts, 24dp panel corners, 16dp horizontal safe margins, 56dp actions and the shared X/menu-row components. Fixtures intentionally contain example Korean copy; they are not real transaction data.

A always starts important confirmed steps with explanation. B measures the complete result/facts/action caption first, falling back to explanation when it cannot fit. In explanation the concise result+action is measured independently; the location action stays disabled if even that cannot fit. Unknown results never expose location navigation. No truncation, font shrinking, automatic click or success inference is used. Explanation body/actions scroll; the heading and X remain visible.

State is INPUT / EXPLANATION / TARGET / RECONFIRM. Step data includes ID, action, result, required facts, importance, confirmed status, screen-coordinate target and version. New coordinates/information synchronously close the previous target aperture; remeasurement cannot bypass reconfirmation. Lifecycle exit/interruption removes restrictions. Orientation changes return to the test selector rather than reopening stale coordinates.

Important explanation screens block underlying touch; TARGET opens only the verified target rectangle using four separate accessibility windows. Ordinary search has no masks. Explicit screen coordinates and system/cutout insets keep masks and controls out of system navigation. Touch is delivered naturally, never injected. This is limited to the owned inert Activity through an in-process bridge, not arbitrary external apps. The owned screen also suppresses background accessibility semantics during explanation and guards test button callbacks. This does not guarantee restrictions on external-app TalkBack/Switch Access actions.

X inside explanation exits to bubble. X inside the help menu closes only that menu; target-guide X exits to bubble. Result reread returns to explanation. The menu contains an explicitly test-only screen/info mutation action. Real financial/payment app integration remains disabled.

Verification:
- assembleGmsDebug, ktlintCheck and independent compiled-code checks pass.
- scripts/check-helper-caption.sh covers above/below/scaled height/left/right.
- scripts/check-consequence-guide.sh covers A/B entry, measurement gate, stale snapshot invalidation, long full/core caption fallback, unknown result and ordinary guide routing.
- Emulator: A explanation rejects target tap; target mode records target=1/other=0; B full caption, long-caption fallback at mid-screen, changed amount/position reconfirmation, unknown-result no-location action, 200% text, 320dp width and landscape scrolling/target alignment verified.
- TalkBack live inspection found background focus during explanation, prompting the owned-screen semantics fix. Complete spoken order, Switch Access and diverse-user validation are not certified. Native full JUnit execution remains subject to missing vendor/ngrok-java/ngrok-java-native/target/ngrok-java-native-host.jar; independent checks do not substitute for the full suite.
최종 일반 검색 실기 검증: 타깃 1회·다른 버튼 1회로 비중요 안내의 터치 허용 확인. 검증 후 화면 크기 720×1600, 글씨 배율 1.0, 자동 회전 1 및 기존 접근성 서비스 목록으로 복원. 최종 APK 에뮬레이터 설치 완료.

## Automatic consequence narration
A/B 결과 안내는 새 안내/변경된 단계마다 기본적으로 fullText를 한 번 자동으로 읽는다. 메뉴·측정·위치 안내 전환 같은 재렌더링은 같은 step을 중복 재생하지 않는다. 다시 읽어주기는 수동 재생이며 기존 재생을 대체한다. 설명 자동으로 읽기 스위치는 기본 켜짐이며 선택을 기기에 저장한다. 끄기·닫기·안내 변경 시 이전 음성을 중지한다. TalkBack 터치 탐색 중에는 중복 발화를 피하도록 자체 자동 읽기를 생략한다. 텍스트와 버튼 위치 보기 동작은 음성 완료를 기다리지 않는다. 한국어 TTS 준비 전에는 텍스트를 유지하고 준비되면 현재 활성 단계만 읽는다.

## Explicit bubble return and fresh request (2026-10-31)

This decision supersedes all earlier X/collapse navigation descriptions above. All debug helper overlays use the shared HelperReturnToBubbleButton with the visible/accessibility label ‘도움 버튼으로 돌아가기’. No X remains. Input and explanation panels keep return controls outside scrolling content. The shared HelperControlActions measures the actual labels at the user's font scale and stacks them when a row does not fit. Minimum action height is 56dp; labels wrap without ellipsis. Existing LightColorScheme and titleMedium own the appearance.

Menu entry is ‘도우미 메뉴’. An expanded menu keeps ‘메뉴 닫기’ and ‘도움 버튼으로 돌아가기’ visible outside its scroll region. Closing the menu returns to the same guide; returning to the bubble ends the guide. ‘도우미 종료’ retains its distinct service-stop behavior. Controls avoid the highlighted target, reserve caption space, and fall back to an explanation panel when there is insufficient space.

Bubble return stops TTS, hides the keyboard, clears overlay windows and touch restrictions, discards consequence step/measurement permission, and invalidates pending layout callbacks. The underlying app is then available for direct interaction. It does not execute or cancel a transaction. Opening the bubble shows the request input with the draft preserved and no automatic submission. Previous guidance is never resumed; auto-read preference survives. Real AI/request execution remains unconnected in this debug prototype.

Verification for this revision is recorded after implementation below; previous revision checks do not certify this change.

Verification — explicit bubble return:
- assembleGmsDebug, ktlintCheck, strict premium static audit, check-consequence-guide.sh and check-helper-caption.sh passed. The new compiled-code checks cover exit invalidation, late measurement after finish, a fresh request, opposite-side controls and insufficient-space fallback.
- API 35 emulator: A explanation/target/menu, B target, ordinary guide/menu/hidden guide, reconfirmation, input draft preservation, keyboard-visible return, bubble re-entry and service stop exercised. Returning from B left target/other counters at 0/0; a subsequent direct background tap incremented the target counter to 1, confirming release of restrictions without a forwarded exit tap.
- 320dp width with 200% font: A explanation and menu controls stack, remain visible while the body scrolls, and can return to the bubble. Landscape: long transmission explanation, menu, reconfirmation and fresh input are visible and operable. These are representative cases, not a complete device/font matrix.
- TalkBack enabled: return button receives focus; double-tap returns to the bubble, and bubble activation reopens input. Full spoken content/order, audio-stop listening, Switch Access and diverse-user validation remain unverified. No Switch Access service was installed on this emulator.
- Existing inert background test fixture buttons overlap at 320dp/200% font; that separate fixture layout was not redesigned. Overlay return controls themselves did not overlap. No universal accessibility certification is claimed.
- Gradle testGmsDebugUnitTest (HelperCaptionPlacementTest filter) is blocked by the pre-existing missing vendor/ngrok-java/ngrok-java-native/target/ngrok-java-native-host.jar. Independent checks do not replace the full suite.
- Keyboard placement uses native IME fitting plus explicit system-navigation clearance. Service disconnection invalidates callbacks; a revoked overlay token is handled without recreating windows. Final APK installed; font scale, screen size, rotation and enabled accessibility services restored to their original values.

## Direct test entry (2026-11-01)
‘테스트 화면 열기’ now opens the selected fixture and starts its chosen A/B guide once, after the target has been laid out and the foreground Activity and helper service are ready. The fixture no longer displays a second ‘선택한 A/B 안으로 안내 시작’ button or an A/B heading; the selector retains its A/B choices. Bubble return does not retrigger the one-shot launch.

In the overlay request field, the exact trimmed text ‘테스트’ reveals a 56dp-minimum ‘테스트 실행’ button. This button or the keyboard Go action opens a fresh owned short-payment fixture directly in variant A, skipping the selector. The request draft is preserved. The explicit internal intent is debug-only and the Activity remains non-exported. Rotation/recreation does not replay the launch intent. No payment or external-app action is connected.

Verification: assembleGmsDebug and ktlintCheck passed; the compiled-code script checks exact/trimmed test matching and rejects partial/empty requests. Emulator screenshots confirm automatic entry into selected consequence explanations with no second start button. Full keyboard-to-A end-to-end interaction and refreshed assistive-technology checks remain unverified for this revision.


### Consistent preview explanations and input controls (2026-11-01)

This revision supersedes prior Enter-to-submit, unknown-result blocking, and B overflow-to-panel rules for the owned inert debug fixture only. A always starts with the same explanation panel, including ordinary and unknown-result examples; its fixed “버튼 위치 보기” action opens location guidance when coordinates and space are valid. Unknown results retain their uncertainty text and never become confirmed results. B uses the same scrollable caption for every fixture and changed information; insufficient space retains that caption without opening the target. Reread is inside the caption for A location guidance and all B guidance, never in the menu. Menu header controls have no shadow. Automatic reading remains configurable.

Leaving the fixture keeps the overlay visible but removes target highlighting and touch restrictions. Returning requires fresh layout eligibility; A shows reconfirmation. Accessibility feedback interruption stops speech without dismissing the UI. Input Enter inserts a newline; only the fixed “요청 보내기” button submits. Exact trimmed “테스트” launches the inert A demo; unsupported requests show an honest preview limitation.

User-requested exception: input alone restores a 56dp X with accessibility name “버블로 돌아가기”. UD-03 and IN-02 text-label requirements remain the default elsewhere. Impact: sighted users may not infer bubble return from X. Compensation: accessible name, large target, retained draft, and explicit return text in guide/menu; a visible first-use hint is a possible further improvement. This exception does not imply accessibility certification.

Validation for this revision: assembleGmsDebug, ktlintCheck, direct consequence-state/narration checks, caption geometry checks and strict design audit passed. Emulator verified A unknown-result explanation to location, B long caption, shadow-free menu without reread, Enter newline with exact test request submitted only by the explicit button, overlay retention on Home, and 320dp/200%/landscape scrollable caption with fixed reading/return actions. Recreating the fixture invalidates the old target until a fresh selected fixture reports its layout. Font scale, display size and rotation were restored. TalkBack, Switch Access, actual audio listening and the complete cross-product of fixtures/orientations remain unverified; full JUnit remains blocked by the pre-existing missing ngrok native host jar.


### Shared A/B Gemini connection (2026-11-01)

User approved integration of upstream/feat/on-device-gemini-assistant and explicitly approved sending the typed request and current screen text/button labels/bounds to Google Gemini, without screenshots. Both variants use HelperAssistantConnection, reusing AssistantSettings (encrypted key and consent), AssistantMcpConnection (existing authentication/permissions), AssistantServerStarter and GeminiModelGateway. HelperConnectionSettingsActivity is the Korean settings entry point; it states the payload/destination and requires consent before enabling. Empty API-key input preserves the existing key. Configuration is shared with the team's assistant.

A/B remain user-operated guidance, not the branch's automatic action agent: this adapter only calls the registered get_screen_state tool and gives Gemini no executable tools. Results are explicitly labeled AI guidance and never treated as verified transaction consequences. Targets must match visible, enabled, clickable APPLICATION nodes in the current MCP snapshot; model coordinates are never accepted. A starts with an explanation, B uses the same scrollable caption. No target means text-only guidance with no touch restriction. A fresh screen read gates response presentation and A's location transition. Accessibility events invalidate highlighting immediately; a local polling read additionally catches missing events. Changes release background restrictions and require an explicit next request. Closing cancels work and ignores late replies. No screenshots, microphone audio or automated taps are sent/executed by this A/B adapter.

Canonical ownership: existing HelperOverlaySpeech input/variant controls, ConsequenceGuideHost output and narration, and the team's settings/MCP/Gemini clients. The old inert “테스트” request stays available and now honors selected A/B. The real connection is debug-only like the existing helper. It has clear loading, cancellation, missing-key, disabled-service, auth, quota, unavailable-network and stale-screen states.

Connection verification: assembleGmsDebug and ktlintCheck passed; HelperAssistantConnectionTest passed 4 tests (both variants, read-only tools/no screenshots, missing-consent gate, stale reply rejection, invalid/offscreen/disabled/overlay node rejection). Tests used the existing /tmp/inthon-ui-test.init.gradle workaround excluding the missing native ngrok host jar; the full native-dependent suite was not run. Strict design audit and git diff --check passed. APK was installed; A/B input controls and settings launch were observed, but another task reinstalled/operated the shared emulator during verification. At the user’s explicit request, further emulator validation is deferred. Real Gemini network responses, actual target-following, TalkBack/Switch Access, and the new connection UI at large text/landscape remain unverified. No API keys were extracted or supplied by this task. The pre-integration helper changes remain backed up in stash helper-ui-before-gemini-integration-20261101; integrated code and frontend changes remain uncommitted.


## Shared entry and separate A/B branches (2026-11-01)

The first-run welcome, setup simulation and returning-user home are shared unchanged between `feat/helper-version-a` and `feat/helper-version-b`. HelperEntryPreview owns debug-only preferences (`intro_seen` and `mock_permission`), distinct from real AssistantSettings. All entry screens say “목표 화면 · 기능 미연결”; simulated permission never grants Android access and simulated bubble state never starts a service. The old overlay/AI development flow remains accessible from the mock home settings. These entry screens do not certify that the real assistant is ready.

HelperEntryScreen reuses Color.kt → MaterialTheme and Android system typography; main explanations use 18sp/26sp, titles use headlineMedium, page margins 24dp and main actions at least 56dp. Body content scrolls independently from the bottom action. Text buttons inherit the surrounding content color, avoiding the measured 4.05:1 primary-on-primaryContainer combination. The welcome example is illustrative, not a live screen target.

Each branch fixes its guide mode in HelperBuildVariant.kt. A starts with explanation; B starts with the scrollable caption. The overlay request and inert test fixture use that same mode. Runtime A/B switching is removed from these branch builds; common onboarding/home source and settings storage are identical. The base MCP launcher is unchanged.

Entry verification before the user requested a stop: assembleGmsDebug, ktlintCheck, strict static audit, compiled state checks, and emulator first-entry/skip/return/relaunch/mock grant/start/request-draft/stop/failure-retry/revocation flows passed. A contrast correction was subsequently rebuilt. New entry screens at 320dp/200% text, landscape, TalkBack/Switch Access and diverse-user testing remain unverified. No additional builds or UI tests were run for branch separation, following the user's request to prioritize Git publication.

## 확정 앱 아이콘 — 손을 든 휴대폰 (2026-11-01)

사용자가 웃는 얼굴과 손을 든 휴대폰 시안(v3-03)을 앱 아이콘으로 확정했다. 기존 유사 캐릭터 사례를 확인한 뒤 선택한 디자인이다. 이는 독점성이나 상표 조사 완료를 의미하지 않는다. 다른 로고 시안은 채택하지 않는다.

승인 원본: docs/branding/approved-phone-helper.png. 내장 image_gen으로 원본의 배경만 제거한 런타임 이미지: app/src/main/res/drawable-nodpi/helper_phone_mascot.png. 표정·기울기·손을 든 자세를 유지한다. 새 시안으로 임의 교체하지 않는다.

런타임 소유 경로: AndroidManifest.xml의 icon/roundIcon → mipmap-anydpi-v26/ic_launcher.xml 및 ic_launcher_round.xml → drawable/ic_launcher_foreground.xml → helper_phone_mascot.png. 두 아이콘 모두 같은 캐릭터와 18dp foreground inset을 사용한다. 배경은 values/ic_launcher_background.xml의 #DEE0FF이며 기존 PrimaryContainerLight와 동일한 팔레트다. 별도 폰트나 라이브러리는 추가하지 않았다.

UD-01/03/04에 따라 친근한 도우미를 표현하고 얼굴·휴대폰 실루엣으로 색상 외 단서를 제공한다. 앱 이름/런처 접근성 라벨은 유지하며 이 캐릭터를 기능 버튼의 텍스트 라벨 대체물로 사용하지 않는다. 원칙 충돌은 확인되지 않았다.

검증: :app:processGmsDebugResources 통과. 실제 런처에서 작은 크기·다양한 마스크·TalkBack 및 다양한 사용자의 인지 검증은 미완료다. 기존 설치 앱에는 아직 재설치하지 않았다.

## A 단일 안내로 확정 — 결제·전송만 사전 설명 (2026-11-01)

사용자가 A를 최종 선택하고 B를 폐기했다. 이 결정은 위 A/B 비교 및 "모든 A 사례는 설명 먼저" 기록을 대체한다. 활성 작업 브랜치는 feat/helper-version-a이다. 로컬 feat/helper-version-b 브랜치는 삭제했으며 원격 브랜치는 변경하지 않았다. GuideVariant와 HelperBuildVariant 및 B 표시·분기를 제거한다.

ConsequenceStep.kind의 PAYMENT(결제·송금 실행), TRANSMISSION(메시지·사진·파일 전송 실행)만 긴 사전 설명을 연다. OTHER(검색·화면 이동·삭제·결과 미확인)는 A의 위치 안내 단계로 바로 진입한다. 이는 B 모드 유지가 아니다. 일반 안내도 측정 전에는 타깃을 열지 않으며, 화면 변경·종료 시 이전 타깃 권한을 무효화한다. 결제·전송의 화면 변경은 사전 재확인으로 돌아간다.

Gemini 응답 category는 필수이며 허용 값 밖이나 누락된 응답은 오류 처리한다. category는 설명 화면 선택용 AI 분류일 뿐, 거래 안전성이나 실행 결과를 검증한 값이 아니다. 자동 조작 권한은 추가하지 않는다. 미확인 결과는 미확인으로 유지한다. 삭제 등 OTHER의 주의사항은 짧은 안내 안에 계속 표시하여 UD-05의 결과 확인 정보를 보존한다.

Impeccable polish를 사용하되 AGENTS.md의 유니버설 디자인·포용적 UI/UX 기준을 우선한다. 사전 설명은 "결제 전에 확인해 주세요"/"보내기 전에 확인해 주세요" → 결과 → 확인할 사실 묶음 → 버튼 위치 보기 순서다. 18sp/26sp 사실 설명, 24dp 패널 모서리, 56dp 주요 행동, 스크롤 밖 복귀/메뉴와 주 행동을 유지한다. 자동 읽기 설정만 메뉴로 이동하고 다시 읽기는 안내 안에 유지한다. 재확인 시 상태 문구를 유지하며 실제 실행은 사용자가 선택함을 밝힌다.

검증: assembleGmsDebug 및 ktlintCheck 통과. HelperAssistantConnectionTest 5개 통과(기존 누락 ngrok host jar를 제외하는 /tmp/inthon-ui-test.init.gradle 사용, 전체 native 의존 테스트 아님). check-consequence-guide.sh, check-helper-caption.sh, check-helper-entry.sh 통과. strict premium 정적 감사 오류/경고 0. category 필수화 후 최종 assembleGmsDebug·ktlintCheck·관련 테스트 5개를 다시 통과했다. git diff --check 통과.
실기 한계: WSL 에뮬레이터는 /dev/kvm 부재, Windows 가상 기기는 실행 잠금/ADB 미연결로 이번 변경 화면을 새로 촬영하지 못했다. 실제 작은 아이콘, 200% 글자, TalkBack/Switch Access, 실제 Gemini 응답 및 다양한 사용자 검증은 미완료다. 기존 캡처를 이번 변경의 검증 증거로 사용하지 않는다.


## 첫 사용 리메이크 · 2026-11-01
사용자 확정 명칭: 캐릭터 **콕콕이**, 기능 **도움 버튼**. A 단일 버전. 기존 모의 권한/켜짐 화면을 교체한다.
- 환영 → 격리된 연습용 휴대폰(도움 버튼 / 예시 질문 / 직접 목표 누르기) → 사용 초대 → 권한 설명 → 실제 Android 접근성 설정 → 홈.
- 따뜻한 밝은 바탕, 승인한 손을 든 휴대폰, 큰 환영 문장과 한 단계당 한 행동. 네이티브 Material 구성과 기존 토큰 유지. 전화기 프레임은 연습 영역에만 사용한다.
- 연습이라는 표시는 스크롤 바깥에 항상 보인다. 외부 앱·사진·마이크·AI 요청·권한 변경과 연결하지 않는다. 중단/재연습을 제공한다.
- 온보딩 중 실제 도움 버튼을 숨겨 가짜 버튼과 혼동하지 않게 하며, 홈 복귀나 앱 이탈 시 기존 켜짐 상태를 복원한다. 홈 상태는 실제 서비스 창의 부착/제거를 관찰한다. 모의 권한 preferences는 사용하지 않는다.
- 권한 화면은 Android의 실제 허용/사용/확인 문구 차이를 설명하며 사용자가 읽고 선택한다. 새 권한을 앱이 임의로 허용하지 않는다.
- 본문 18sp 이상, 주요 터치 56dp 이상, 세로 스크롤, 장식 캐릭터 읽기 제외. 보조 기술의 실제 탐색 및 다양한 사용자 검증은 별도 미검증이다.
검증 기록: assembleGmsDebug / ktlintCheck 통과. 격리된 연습 전환, 기존 결과 안내·캡션 회귀 스크립트 통과. HelperRuntimeTest 3개와 HelperAssistantConnectionTest 5개 통과. API 35 에뮬레이터에서 환영·연습 전 단계·완료·초대·홈 및 켜짐/꺼짐 실제 일치 확인. 글자 200%에서 연습 고정 표시와 스크롤 후 도움 버튼 노출 확인, 설정 1.0으로 복원. 정적 디자인 감사 오류 0건은 네이티브 접근성 인증이 아니다. 권한 미허용 상태의 실제 허용/거절 전 과정, TalkBack/Switch Access, 가로·320dp 화면, 다양한 사용자 테스트는 미검증.


## 홈 상태와 음성 입력 정리 · 2026-11-01
사용자 항목 1·2는 home_status, 4·6은 overlay_simplify, 5는 voice_input 서브에이전트가 담당했고 반환 후 메인이 3번 정렬을 통합했다. 각 담당은 AGENTS.md·지속 유니버설/포용적 원칙과 Impeccable 지침을 읽었다.
- 첫 안내는 완료 이력이 없을 때 표시하고 재실행은 홈으로 간다. 사용자 지시에 따라 이 정책은 유지한다. 홈의 사용법 다시 연습하기는 유지한다.
- 홈은 승인 캐릭터 + 체크/일시정지 그래픽 + 실제 켜짐 문구로 상태를 전달한다. 색만으로 구분하지 않는다. 상태·설명·주요 행동은 중심선을 맞추고 도움 버튼이 뜨는 옆 공간을 피하도록 상태/설명 폭을 제한한다.
- 입력은 중복 안내/상시 설정/긴 개인정보 설명/가짜 음성 팝업을 제거한다. 개인정보 안내는 AI 연결 설정에서 확인한다. 입력과 마이크는 한 행, 입력·전송은 같은 양끝, X와 마이크는 같은 오른쪽 축이다. X 56dp와 입력 사이 최소 24dp. 비활성 전송도 글자를 읽을 수 있는 대비를 유지한다. 입력 배경의 앱 내용은 검정 78%로 낮춰 입력에 집중한다.
- 음성은 Android SpeechRecognizer로 기존 입력창에 부분·최종 결과를 반영한다. 기기 내 인식을 우선하며 없으면 시스템 인식기를 쓴다. 부분 결과 제공 여부는 기기 음성 서비스에 달려 있다. 발화 종료나 수동 중지 즉시 녹음 아이콘을 해제하고 결과를 기다리며 자동 전송하지 않는다. 재시작/종료 후 이전 콜백은 무시한다. 최초 마이크 권한은 시스템 화면으로 요청한다.
- X-only는 기존 사용자 승인 예외(UD-03/IN-02)를 유지한다. 시각적으로 복귀 의미가 모호할 수 있으므로 접근성 이름 ‘도움 버튼으로 돌아가기’와 처음 사용 연습으로 보완한다.

검증 기록(1차 수정): assembleGmsDebug 및 ktlintCheck 통과. HelperVoiceInputTest 4개(부분/최종 결과, 자동 종료, 수동 중지 후 늦은 준비 콜백, 오래된 콜백, 시스템 인식기 대체)와 HelperRuntimeTest 3개 통과. 연습/결과 안내/캡션 회귀 스크립트 통과. API35 에뮬레이터에서 상태 그래픽, 간결한 입력 UI, 최초 시스템 마이크 권한창, 200% 입력 UI를 확인했다. 실제 마이크 권한은 부여하지 않았고 실음성 전사 및 TalkBack/Switch Access는 미검증이다. 글자 배율은 1.0으로 복원했다.
사용자 후속 지적: 입력창과 마이크 버튼 높이가 달라 보임. 같은 행의 intrinsic 높이를 공유하고 기본 최소 높이 64dp, 동일한 24dp 모서리를 적용했다. 이 최종 크기 수정은 빌드/스타일 검사 통과, 최종 실화면 재확인은 PR 이후 후속 검토 대상으로 남긴다.


## 2차 시각 보완 · 2026-11-01
사용자 항목별 네 서브에이전트 작업 뒤 메인이 연결·정렬을 통합했다.
- 홈의 실제 ON은 승인된 손든 휴대폰 + 밝은 동심원과 2.8초의 부드러운 호흡 효과. OFF는 image_gen으로 만든 투명 배경 수면모자/감은 눈 캐릭터. 애니메이션 감소 설정에서는 정적 원을 사용하고 항상 텍스트 상태도 제공한다.
- 제목은 ‘콕콕이가’ / ‘켜져 있어요·꺼져 있어요’라는 의미 단위 두 줄, 실제 가용 폭에 맞는 24/28/32sp. 시스템 글자 확대를 역으로 상쇄하지 않는다. 이전 임의260dp 제목 폭 제한을 없앤다.
- 홈의 ‘도움 버튼을 눌러…’ 중복 본문을 제거하고 단일 주요 행동을 유지한다.
- 연습은 카드 속 휴대폰을 폐기하고 화면 전체가 연습용 설정 앱이 된다. 상단의 연습 표시와 종료는 항상 보이고, 설정 목록 → 도움 요청 → 강조된 화면 메뉴 → 연습 완료 순서로 직접 조작한다. 실제 설정·마이크·네트워크와 연결되지 않는다. 완료 후 기존 사용 초대 단계로 이어진다.

2차 통합 검증: assembleGmsDebug·ktlintCheck 및 기존 온보딩/안내/캡션 회귀 스크립트 통과. 720×1600 에뮬레이터에서 켜짐·꺼짐과 연습 진입 → 질문 → 화면 메뉴 강조 → 완료 → 활성화 초대 흐름을 확인했다. 연습 질문은 가상 설정 화면 위의 로컬 입력 레이어로 표시하며 실제 요청을 보내지 않는다. 홈 버튼은 중복 이름을 제거한 ‘물어보기’로 정리했다. 320dp 폭·200% 글자 확대에서 홈 제목과 주요 버튼의 잘림 없이 표시됨을 확인했고 에뮬레이터 크기와 글자 설정을 원복했다. 모든 연습 단계의 200% 확대, TalkBack·Switch Access와 다양한 실제 사용자 검증은 아직 미검증이다.

### 콕콕이 그림체 통일
사용자 피드백에 따라 꺼짐 이미지의 3D 수면모자를 제거하고 기존 켜짐 원본과 같은 평면 2D 몸체·얼굴·팔로 교체했다. 감은 눈과 내려간 팔로 휴식 상태를 표현한다. 이후 상태별 자산도 같은 2D 스타일을 유지한다. 내장 image_gen으로 원본 PNG를 참조해 수정했다. 생성 프롬프트: 원본의 기울어진 평면 휴대폰 실루엣, 파란 테두리, 흰 화면, 남색 미소와 비율을 유지하고 눈을 감은 곡선과 내려간 팔만 변경. 3D, 입체 조명, 그림자, 모자, 추가 장식 금지. 투명 배경.

수면모자 복원: 사용자 요청은 모자 제거가 아닌 그림체 통일이다. 최종 꺼짐 자산에는 같은 2D 스타일의 파란 수면모자·흰 띠·방울을 유지한다. 이전 모자 제거 결정을 대체한다. 내장 image_gen 편집 프롬프트: 현재 2D 잠자는 원본의 몸체·감은 눈·미소·내려간 팔을 유지하고, 파란 테두리가 있는 흰 띠와 방울의 평면 수면모자만 추가. 음영·입체감·그림자 없이 투명 배경과 여백 유지.

### 행동을 따라 하는 연습 튜토리얼
사용자 요청에 따라 콕콕이의 단계별 설명·진행 표시와 밝은 조작 대상을 배치하고 배경 설정 앱은 어둡게 낮췄다. 도움 버튼 → 미리 준비된 연습 질문 보내기 → 화면 메뉴 누르기 → 완료 순서이며 현재 대상의 조작만 다음 단계로 진행한다. 배경은 비활성 장식이며 접근성 탐색에서 제외한다. 실제 전송·설정 변경·권한 요청은 없다. 긴 설명과 큰 글씨는 스크롤로 읽으며 종료는 상단에 고정하고 시스템 뒤로가기도 허용한다.
원칙 충돌: UD-02 사용 방법의 유연성 — 요청된 순서 강제는 연습 안에서 자유 탐색을 제한한다. 보완: 연습 끝내기와 뒤로가기를 상시 허용하고 홈에서 언제든 재연습할 수 있다. 실제 앱 사용에는 이 제한을 적용하지 않는다.
검증: assembleGmsDebug·ktlintCheck·온보딩 상태 전이 회귀 스크립트 통과. 에뮬레이터에서 도움 버튼 → 질문 → 강조 메뉴 → 활성화 초대까지 진행 확인. 이번 변경의 200% 글씨·TalkBack·Switch Access와 실제 사용자 검증은 미검증.

### 연습 질문 직접 입력
ASK 단계는 빈 입력창에서 시작하며 ‘화면 설정은 어디에 있어요?’를 직접 쓰거나 명시적으로 마이크를 눌러 입력한다. 띄어쓰기·문장부호 차이는 허용하고 문장이 일치한 뒤에만 질문 보내기를 활성화한다. 입력은 연습 단계 전이에만 사용하며 AI 요청을 보내지 않는다. 기존 HelperVoiceInput을 재사용해 중간 인식 결과·듣는 중·처리 중·완료·오류를 같은 화면에서 표시한다. 마이크 권한은 버튼을 눌렀을 때만 요청하고 거절 시 타이핑을 유지한다. 연습 이탈·앱 백그라운드 시 녹음을 취소한다. 장치 음성 인식기는 실제로 사용하므로 이전의 전면 로컬 연습 설명 중 음성 인식에 관한 부분은 이 기록으로 대체한다.
검증: 빈값/미완성 입력 차단·문장부호/공백 허용 회귀 검사 통과. 에뮬레이터에서 빈 입력·직접 타이핑·음성 입력 버튼 표시 확인. 실제 마이크 녹음, TalkBack와 200% 글자 크기 검증은 미실시.

### 실제 조작부와 연습 조작부 통일
연습 OPEN은 실제 오버레이의 HelperEdgeCharacter를 그대로 사용한다. 64×80dp 버튼·44dp 캐릭터·비대칭 모서리·도움 라벨·색상·그림자를 공유하고 연습 강조선은 바깥에 분리한다. ASK도 실제 HelperOverlaySpeech를 재사용해 입력창·마이크·X·요청 보내기 버튼과 가로 여백을 통일했다. 실제 컴포넌트의 submitAllowed 기본값은 true이며 연습에서만 예문 일치 조건을 전달한다. X는 연습 첫 도움 버튼 단계로 돌아간다. 연습 설명·음성 처리·로컬 단계 전이만 별도로 유지한다. 빌드·ktlint·온보딩 입력 회귀 검사 통과 및 에뮬레이터 두 단계 렌더링 확인. 실제 음성·보조 기술 실사용은 미검증.

### 요청 처리 중 중앙 상태 화면
실제 요청의 busy 상태에서 기존 입력창 대신 전체 사용 가능 영역을 덮는 HelperThinkingOverlay를 표시한다. 검정 84% 배경, 중앙 200dp 2D 생각하는 콕콕이와 생각 중 문구를 사용하며 배경 터치를 차단한다. 상단 56dp 이상 중단 버튼은 기존 showBubble을 통해 요청을 취소하고 입력값을 유지한 채 도움 버튼으로 돌아간다. 성공·오류는 기존 요청 수명주기로 해제한다. 큰 글씨와 짧은 화면에는 본문 스크롤을 제공한다.
생성 자산: helper_phone_mascot_thinking.png. 내장 image_gen 편집 프롬프트: 기존 2D 원본의 실루엣·색·비율 유지, 손을 턱에 댄 생각하는 자세와 점 세 개 생각 말풍선 추가, 입체·그림자·질감 없이 투명 배경.
검증: assembleGmsDebug·ktlintCheck 통과. debug preview_thinking 인텐트로 에뮬레이터 렌더링 확인(실제 요청을 보내지 않는 시각 미리보기). 실제 서버 응답·오류·취소 전 과정과 TalkBack·200% 글자 검증은 이번 변경에서 미실시.

### 연습 이후 동의와 권한 연결
연습 완료 후 활성화 초대의 예 → 화면 정보 전송 동의 → Android 접근성 권한 → 선택적 마이크 권한 → 도움 버튼 활성화 순서로 연결한다. 동의는 실제 AssistantSettings.acceptPrivacyNotice에 저장하고 활성화 상태도 동일 저장소에 반영한다. 이미 동의했거나 허용한 항목은 건너뛰며 마이크를 거절·나중에 선택해도 글 입력으로 시작한다. 동의하지 않아도 연습과 홈에는 접근할 수 있다. API 키 입력은 이 흐름에서 요구하지 않고 홈의 AI 연결 설정 링크를 제거했다. API 키 자체의 앱 내 사전 구성은 별도 구현 사항이며 이 변경으로 연결 완료를 주장하지 않는다.
검증: 빌드·ktlint·동의 우선/접근성 다음/선택 마이크/완료 항목 건너뛰기 회귀 검사 통과. 에뮬레이터 동의 화면 표시 확인. 사용자 대신 실제 동의·권한을 승인하지 않았으며 Android 승인 이후 전체 실사용 및 TalkBack은 미검증.

### 생각 말풍선 점 모션
생각 이미지의 고정 점만 내장 image_gen으로 제거하고, 같은 말풍선 안에 Compose Canvas 점을 그린다. 프롬프트는 기존 실루엣·말풍선·투명 배경을 유지하고 말풍선 속 점 세 개만 흰색으로 제거하도록 지정했다. 0.6초 간격으로 1 → 2 → 3 → 0개를 2.4초 주기로 반복한다. 처리 화면이 제거되면 Compose 애니메이션도 폐기한다. 시스템 애니메이션 비활성 상태에서는 세 점을 고정 표시한다. 버튼 문구는 사용자 요청대로 ‘생각을 끝내고 도움 버튼으로’로 변경했다. 빌드·ktlint 통과.

점 위치 미세 조정: 오른쪽 4dp 이동에서 왼쪽 1dp 복귀(기본 200dp 그림 기준 최종 +3dp). 점 중심·반경·간격은 동일한 정사각형 이미지 영역의 비율로 계산하며, 200dp보다 좁아질 때 이미지와 Canvas가 함께 축소되도록 aspectRatio(1f)를 적용했다. 400×1100px 에뮬레이터에서 그림 축소 후 말풍선 내부 점 정렬 확인, 화면 크기 원복. 빌드·ktlint 통과.

### 앱 아이콘만 콕 누르는 시안으로 교체
사용자가 새로 승인한 검지로 콕 누르는 2D 시안(exec-e19aaa1b)을 앱 아이콘에만 적용한다. ic_launcher_foreground.xml은 전용 helper_launcher_tap.png를 참조하며 일반·원형 adaptive icon 모두 동일하게 적용된다. 기존 18dp inset과 배경은 유지한다. 앱 내부 helper_phone_mascot.png 및 수면·생각 상태 이미지는 변경하지 않는다. 이전 손을 든 휴대폰 앱 아이콘 확정 기록은 이 결정으로 대체한다.

### 도움 버튼 C안 및 세로 이동
사용자가 C안을 선택했다. 오른쪽에 밀착한 연보라(#ECE8FF) 64×144dp 탭, 왼쪽 20dp 둥근 모서리, 오른쪽 직각, 그림자 없이 기존 2D 캐릭터(투명 여백 포함 76dp)와 20sp SemiBold 남색 도움 라벨을 사용한다. 연습도 HelperEdgeCharacter를 공유한다. 앱 아이콘은 변경하지 않는다. 실제 버튼은 서비스 창에서 rawY 기준으로 세로 드래그하며 짧은 탭은 입력창을 연다. 화면 시스템 영역을 제외한 범위로 제한하고 상대 세로 위치를 저장한다. TalkBack 위·아래 이동 액션도 제공한다. 빌드·ktlint 통과. 에뮬레이터에서 위아래 이동, 이동 후 탭 열기, 닫기 확인. TalkBack 실사용은 미검증.


### 도움 버튼 원래 모양 복원
사용자 요청으로 C안을 폐기하고 기존 64×80dp 둥근 도움 버튼, 6dp 외부 여백과 그림자, 44dp 원본 캐릭터와 테마 도움 라벨을 복원한다. 서비스 창은 76×92dp로 되돌리고 세로 이동·위치 저장·접근성 이동 액션은 유지한다. 연습 화면도 같은 컴포넌트를 공유한다.
