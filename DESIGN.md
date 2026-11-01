---
version: alpha
name: "여기콕"
description: "지금 화면에서 누를 곳 하나를 보여 주고 사용자가 직접 하게 돕는 한국어 안드로이드 도우미"
colors:
  primary: "#4A5FE0"
  primaryContainer: "#DEE0FF"
  onPrimaryContainer: "#00105C"
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
  panel: "24dp"
components:
  character: "HelperCharacter (helper_phone_mascot, helper_phone_mascot_sleeping, helper_phone_mascot_thinking, 콕콕이)"
  bubble: "BubbleCharacterWindow / HelpButtonFace (도움 버튼)"
  input: "HelperRequestInput"
  conversation: "BubblePanelWindow"
  thinking: "BubbleThinking"
  practice: "HelperPracticeScreen"
  home: "HelperMascotStateVisual"
  caption: "CaptionBubble"
  spotlight: "SpotlightBand + SpotlightRingPart"
  swipe: "SwipeArrow"
---

# Design context

현재 범위는 여기콕 앱의 실제 화면(main 소스)이다: 시작 화면(HelperActivity), 떠 있는 도움 버튼과 대화 창, 생각 중 화면, 안내 오버레이. 디버그·AI 테스트·고급 MCP 설정·키 입력 화면은 사용자 화면에서 열 수 없다. 이름은 앱 여기콕, 캐릭터 콕콕이, 떠 있는 버튼 도움 버튼이다(사용자 결정, 2026-11-01). 앱 아이콘은 콕 누르는 콕콕이, 화면 속 캐릭터는 승인된 손을 든 휴대폰과 그 상태 그림이며 「캐릭터와 앱 아이콘」 절을 따른다. 제품 결정은 PRODUCT.md가 기준이고 아래 UI 팀 프로토타입 기록과 다르면 PRODUCT.md와 「현재 런타임」 절이 우선한다.
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
9. **IN-02 사용자 통제와 맥락 보존.** 도우미가 무엇을 하고 있는지 알리고, 언제든 쉽게 접거나 종료하게 한다. 복귀 수단은 스크롤 없이 보이는 ‘도움 버튼으로 돌아가기’ 텍스트 버튼으로 제공한다. 표시 문구와 접근성 이름을 일치시킨다. 도움 버튼 복귀와 도우미 종료(도움 버튼과 MCP 서버를 함께 멈춤)를 구분한다. 설명은 대상 근처에 배치하되 대상·닫기·시스템 조작을 가리지 않는다. 밝은 강조 영역과 안내 위치는 실제 화면 좌표에 맞아야 하며 예시 좌표는 예시로 명시한다.
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

UI 팀 승인 예외: (1) 입력 줄 닫기를 X 아이콘만으로 표시(UD-03·IN-02, PR #18). 보완: 56dp 터치, 접근성 이름 ‘도움 버튼으로 돌아가기’, 첫 사용 연습. (2) 연습 튜토리얼은 정해진 순서(도움 버튼 → 질문 → 강조된 ‘화면’ → 완료)만 진행한다(UD-02 — 연습 안 자유 탐색 제한, PR #32). 보완: ‘연습 끝내기’와 시스템 뒤로 가기 상시 허용, 홈에서 언제든 다시 연습, 실제 앱 사용에는 적용하지 않음. 이전 시안 전체의 준수 검토는 아직 하지 않았다. 실제 음성/AI 연결, 동적 타깃 추적, TalkBack·Switch Access·200% 글자 확대·다양한 사용자 테스트는 이 문서 작성만으로 완료되지 않는다.

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
- 글꼴/크기: 같은 폴더 Type.kt (headlineMedium 28sp/36sp, titleLarge 22sp/28sp, bodyLarge 16sp/24sp). 주요 설명은 18sp/26sp를 직접 지정한다(IN-01).
- 테마 연결: Theme.kt → MaterialTheme → ui/helper 구성요소.
- 도우미 화면(시작 화면·도움 버튼·대화 창·생각 중 화면·안내 오버레이)은 dynamicColor=false로 UI 팀 팔레트를 유지하고 밝음/어두움은 시스템 설정을 따른다.
- 컴포넌트 간격은 각 ui/helper 파일에 정의한다. 새 전역 토큰 시스템은 만들지 않는다.

## Canonical UI map

| Capability | Owner | Contract |
| --- | --- | --- |
| Form | BubblePanelWindow input row (field, mic, X, send) | 실제: 빈 요청 불가, 실행 중 전송 불가. 연습: 문장이 일치할 때만 전송 |
| Guidance | GuidanceSceneRenderer → CaptionBubble / TypeHintCaption | 확인 단계 없이 바로 강조, 설명은 말풍선 둘째 줄, 한 번에 한 단계 |
| Overlay drawing | SpotlightBand (72% 딤 + 구멍), SpotlightRingPart (흰 테두리), 8dp 입력 칸 테두리, SwipeArrow (검정 82% 알약 + 흰 화살표 3개) | 절대 px 창, MCP에서 숨김, 구멍·스와이프 시작 지점 위에 창 없음 |
| Bubble | BubbleCharacterWindow + HelperBubbleOverlayHost | 64x80dp 둥근 버튼(창 76x92dp, 그림자 여백 6dp), 콕콕이 44dp와 ‘도움’/‘생각 중’, 오른쪽 가장자리 세로 끌기, permille 위치 저장, TalkBack 위/아래 이동, 안내 중 숨김, 연습과 공유 |
| Thinking | 생각 중 화면 | 첫 안내 전까지만, 어떤 강조보다 먼저 제거 |
| Character | HelperCharacter | 장식용, 상태 그림 3종 |
| Status | Text + polite live region | 입력 화면 메시지와 안내 문장 상태를 음성 접근성에 전달 |

## Layout and accessibility

요청 화면과 안내 예제는 세로 스크롤을 지원한다. 요청 화면은 IME 여백을 적용한다.
개발 Activity는 safeDrawingPadding을 적용한다. 주요 버튼 최소 높이는 56dp이며 고정 높이로 글자를 자르지 않는다.
안내 카드의 버튼은 세로 배치하여 큰 글씨와 좁은 화면에 대응한다.
동작은 표준 Material 버튼으로 제공하고 사용자 설정을 무시하는 애니메이션은 추가하지 않는다.

## Locale and scope

도우미 UI는 한국어이며 문구는 main res의 helper_*_strings.xml에 모은다.
기존 앱 번역은 변경하지 않는다. 타 언어 배포 전에 새 문자열 번역을 추가해야 한다.
연습 화면은 ‘연습 중 · 실제 앱이 아니에요’를 늘 표시한다. 도우미는 다른 앱을 조작하지 않고 결제·전송도 사용자가 직접 한다. 오버레이는 접근성 서비스 창으로 띄우고 실제 기기에서 검증한다.

## 현재 런타임: 여기콕 (2026-11-01)

- 도움 버튼: 64x80dp 둥근 버튼(HelpButtonFace). 오른쪽 가장자리에서 세로로 끌어 옮기고 위치를 저장한다. 끌 수 없는 사용자를 위해 TalkBack 동작 ‘도움 버튼 위로 이동’·‘도움 버튼 아래로 이동’을 제공한다. 접근성 이름은 보이는 글 ‘도움’(처리 중 ‘생각 중’)이다.
- 대화 창(BubblePanelWindow): 도움 버튼을 누르면 열리고 입력 줄, 답변 말풍선, 멈추기·새 대화를 보인다. 입력 줄의 X(‘도움 버튼으로 돌아가기’)로 닫는다.
- 안내: 확인 단계 없이 바로 강조한다. 안내 단계가 보이는 동안 도움 버튼과 대화 창은 숨는다.
- 도우미 종료는 도움 버튼과 MCP 서버를 함께 멈추고, 도우미가 켜진 채 부팅하면 서버가 다시 시작된다.
- 키 입력·디버그·AI 테스트·고급 MCP 설정 화면은 사용자 화면에서 열 수 없다.

## 캐릭터와 앱 아이콘 — 콕콕이

- 원본: UI 팀이 승인한 손을 든 휴대폰(docs/branding/approved-phone-helper.png). 다시 그리거나 다른 시안으로 바꾸지 않는다.
- 상태 그림은 drawable-nodpi의 800x800 무손실 WebP이다: helper_phone_mascot(기본), helper_phone_mascot_sleeping(파란 테두리 흰 띠와 방울 달린 수면모자), helper_phone_mascot_thinking(빈 생각 말풍선, 점은 코드가 그린다).
- 앱 아이콘 helper_launcher_tap은 512x512 무손실이며 ic_launcher_foreground.xml이 17.5% 안쪽 여백으로 쓰고 배경은 #DEE0FF이다. 누르는 고리는 66dp 안전 영역 안(최대 32.9dp)에 둔다.
- 모양 규칙: 둥근 2D 그림체, 검지로 콕 누르는 손짓이 아이콘의 중심이다.
- 몸의 파랑은 그림마다 다르며 전달된 그대로 쓴다: 원본 #475EE1, 기본 #3851E2, 아이콘 #204AF6, 수면·생각 약 #0A48F8.
- 크기: 홈 176dp(208dp 원 안), 환영·초대 156dp, 생각 중 화면 최대 200dp 정사각형, 도움 버튼과 연습 44dp(#DEE0FF 받침 위), 정보 화면 70dp(72dp 원 안).
- 장식이므로 contentDescription은 null이다. 48dp 이하이거나 파란 바탕 위에서는 받침(plate)을 둔다.
- 움직임은 홈의 숨 쉬는 원과 생각 중 점뿐이며 시스템의 애니메이션 제거 설정을 따른다.
- 미검증: 작은 런처 크기, 마스크 모양, TalkBack, 200% 글꼴, 다양한 사용자.

## 첫 사용 — 환영 → 연습 → 초대 → 동의 → 권한 → 마이크 → 홈

- 동의 화면은 화면 정보를 받는 곳(OpenAI)과 실제로 보내는 정보(목소리, 입력한 글, 화면 글자·버튼 이름·위치, 화면 사진)를 적는다. 키 이야기는 없다.
- 이미 끝낸 단계는 건너뛴다. 순서는 동의, 접근성, 마이크이다.
- 마이크 화면은 대화에 마이크가 필요하다고 안내하며 건너뛰기는 ‘나중에 하기’이다.
- 접근성 서비스는 앱이 대신 켜지 않는다. 사용자가 시스템 설정에서 직접 켠다.
- 동의하지 않아도 연습과 홈은 쓸 수 있다.

## 연습 — 연습용 설정 앱

- 화면 전체가 가짜 설정 앱이다. 위쪽 줄에 ‘연습 중 · 실제 앱이 아니에요’와 ‘연습 끝내기’를 보이고 뒤로 가기가 항상 된다.
- 4단계(도움 버튼 → 질문 → 강조된 ‘화면’ → 완료)이며 질문은 정해진 문장과 일치할 때만 전송된다.
- 확인 눌러 보기 단계 없이 바로 강조한다.
- 연습에 들어갈 때 진행 중인 실시간 대화를 먼저 끝낸다.
- 받아쓰기는 기기 안에서만 처리하며 실시간 대화와 겹치지 않고, 화면을 떠날 때 취소한다.

## 홈 상태

- 켜짐: 밝은 원이 2.8초 주기로 숨 쉰다. 꺼짐: 수면모자를 쓴 콕콕이.
- 두 줄 제목은 24/28/32sp로 화면 너비에 맞춘다.
- 도우미와 서버의 실제 상태만 보인다. 동작: 물어보기, 콕콕이 켜기, 도우미 종료.

## 생각 중 화면

- 검정 84% 바탕 위에 200dp 이하의 생각 중 콕콕이. 점은 1, 2, 3, 0개 순서로 0.6초마다 바뀌고 애니메이션이 꺼져 있으면 점 3개를 고정해 보인다.
- 오른쪽 위 ‘생각을 끝내고 도움 버튼으로’(56dp 이상)로 요청을 멈추고 입력을 되살린다.
- 첫 안내 강조가 나오기 전에 사라지고, 안내가 시작된 뒤에는 다른 앱의 터치를 막지 않는다. 안내 사이에는 도움 버튼의 ‘생각 중’ 글만 보인다.

## 안내는 바로 강조 — 확인 단계 없음 (사용자 결정, 2026-11-01)

- 안내 단계는 확인 눌러 보기 없이 곧바로 대상을 강조한다. 결과가 큰 행동(결제·전송·삭제·통화·공유)의 설명은 안내 말풍선의 둘째 줄에 쉬운 말 한 줄로 함께 보인다.
- 강조 구멍과 스와이프 시작 지점 위에는 어떤 창도 두지 않는다. 스와이프는 화살표만 쓴다.

## 입력과 음성

- 입력 칸에는 실시간 대화의 받아쓴 글과 사용자가 입력한 글이 보이고, 마이크는 대화 상태(듣는 중·말하는 중·음소거)를 보인다.
- 마이크는 한 번에 한 곳만 쓴다.
- 입력 줄: 입력 칸과 마이크는 64dp 이상에 24dp 모서리, X는 별도 56dp 버튼(‘도움 버튼으로 돌아가기’)이며 닫기와 입력 사이는 12dp 띄운다. 안내 중에는 78% 딤을 쓰지 않는다.

## UI 팀 프로토타입 기록 (PR #18·#32, 참고용)

아래 절은 UI 팀의 디버그 프로토타입 기록이며 여기콕에는 들어 있지 않다. 위의 절과 PRODUCT.md가 우선한다. PR #32 2차까지의 변경: B안 폐기와 A안만 유지, 아이콘이 손 든 휴대폰에서 콕 누르는 아이콘으로 변경, 수면모자 제거 후 복원, 도움 버튼 C안 시도 후 폐기, 결제·전송 사전 설명을 사용자 결정으로 즉시 강조로 대체.

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

This decision supersedes all earlier X/collapse navigation descriptions above. All debug helper overlays use the shared HelperReturnToBubbleButton with the visible/accessibility label ‘버블로 돌아가기’. No X remains. Input and explanation panels keep return controls outside scrolling content. The shared HelperControlActions measures the actual labels at the user's font scale and stacks them when a row does not fit. Minimum action height is 56dp; labels wrap without ellipsis. Existing LightColorScheme and titleMedium own the appearance.

Menu entry is ‘도우미 메뉴’. An expanded menu keeps ‘메뉴 닫기’ and ‘버블로 돌아가기’ visible outside its scroll region. Closing the menu returns to the same guide; returning to the bubble ends the guide. ‘도우미 종료’ retains its distinct service-stop behavior. Controls avoid the highlighted target, reserve caption space, and fall back to an explanation panel when there is insufficient space.

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


### Shared A/B OpenAI connection (2026-11-01)

User approved integration of the team assistant branch and explicitly approved sending the typed request and current screen text/button labels/bounds to OpenAI, without screenshots. Both variants use HelperAssistantConnection, reusing AssistantSettings (encrypted key and consent), AssistantMcpConnection (existing authentication/permissions), AssistantServerStarter and the OpenAI text gateway. HelperConnectionSettingsActivity is the Korean settings entry point; it states the payload/destination and requires consent before enabling. Empty API-key input preserves the existing key. Configuration is shared with the team's assistant.

A/B remain user-operated guidance, not the branch's automatic action agent: this adapter only calls the registered get_screen_state tool and gives OpenAI no executable tools. Results are explicitly labeled AI guidance and never treated as verified transaction consequences. Targets must match visible, enabled, clickable APPLICATION nodes in the current MCP snapshot; model coordinates are never accepted. A starts with an explanation, B uses the same scrollable caption. No target means text-only guidance with no touch restriction. A fresh screen read gates response presentation and A's location transition. Accessibility events invalidate highlighting immediately; a local polling read additionally catches missing events. Changes release background restrictions and require an explicit next request. Closing cancels work and ignores late replies. No screenshots, microphone audio or automated taps are sent/executed by this A/B adapter.

Canonical ownership: existing HelperOverlaySpeech input/variant controls, ConsequenceGuideHost output and narration, and the team's settings/MCP/OpenAI clients. The old inert “테스트” request stays available and now honors selected A/B. The real connection is debug-only like the existing helper. It has clear loading, cancellation, missing-key, disabled-service, auth, quota, unavailable-network and stale-screen states.

Connection verification: assembleGmsDebug and ktlintCheck passed; HelperAssistantConnectionTest passed 4 tests (both variants, read-only tools/no screenshots, missing-consent gate, stale reply rejection, invalid/offscreen/disabled/overlay node rejection). Tests used the existing /tmp/inthon-ui-test.init.gradle workaround excluding the missing native ngrok host jar; the full native-dependent suite was not run. Strict design audit and git diff --check passed. APK was installed; A/B input controls and settings launch were observed, but another task reinstalled/operated the shared emulator during verification. At the user’s explicit request, further emulator validation is deferred. Real OpenAI network responses, actual target-following, TalkBack/Switch Access, and the new connection UI at large text/landscape remain unverified. No API keys were extracted or supplied by this task. The pre-integration helper changes remain backed up in stash helper-ui-before-gemini-integration-20261101; integrated code and frontend changes remain uncommitted.


## Shared entry and separate A/B branches (2026-11-01)

The first-run welcome, setup simulation and returning-user home are shared unchanged between `feat/helper-version-a` and `feat/helper-version-b`. HelperEntryPreview owns debug-only preferences (`intro_seen` and `mock_permission`), distinct from real AssistantSettings. All entry screens say “목표 화면 · 기능 미연결”; simulated permission never grants Android access and simulated bubble state never starts a service. The old overlay/AI development flow remains accessible from the mock home settings. These entry screens do not certify that the real assistant is ready.

HelperEntryScreen reuses Color.kt → MaterialTheme and Android system typography; main explanations use 18sp/26sp, titles use headlineMedium, page margins 24dp and main actions at least 56dp. Body content scrolls independently from the bottom action. Text buttons inherit the surrounding content color, avoiding the measured 4.05:1 primary-on-primaryContainer combination. The welcome example is illustrative, not a live screen target.

Each branch fixes its guide mode in HelperBuildVariant.kt. A starts with explanation; B starts with the scrollable caption. The overlay request and inert test fixture use that same mode. Runtime A/B switching is removed from these branch builds; common onboarding/home source and settings storage are identical. The base MCP launcher is unchanged.

Entry verification before the user requested a stop: assembleGmsDebug, ktlintCheck, strict static audit, compiled state checks, and emulator first-entry/skip/return/relaunch/mock grant/start/request-draft/stop/failure-retry/revocation flows passed. A contrast correction was subsequently rebuilt. New entry screens at 320dp/200% text, landscape, TalkBack/Switch Access and diverse-user testing remain unverified. No additional builds or UI tests were run for branch separation, following the user's request to prioritize Git publication.
