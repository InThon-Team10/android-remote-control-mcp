# DeepSeek × 갤럭시 폰 제어 실험 키트

이 폴더는 **원래 레포(android-remote-control-mcp)에 없는 "AI 쪽"** 을 채우는 실험용 코드입니다.
레포의 앱은 폰을 *조작당하는* MCP 서버일 뿐이고, 스스로 AI API를 부르지 않습니다.
여기서는 PC에서 DeepSeek를 호출하고, 그 결정을 폰 MCP 서버로 전달하는 **에이전트 루프**를 돌립니다.

```
[명령(채팅)] → phone_agent.py ─(OpenAI 호환 API)→ DeepSeek-V4.1-Flash (Lablup)
                     │
                     └─(JSON-RPC POST /mcp)→ 갤럭시 S22의 MCP 서버 앱 → 접근성 서비스로 탭/입력/화면읽기
```

외부 패키지 불필요 (Python 3.8+ 표준 라이브러리만 사용).

## 0. 준비
1. `.env` 확인 — API 키/모델/폰 주소. (`.env`는 gitignore 되어 있음. 절대 커밋 금지)
2. 폰: 레포 README의 Install/Setup대로 APK 설치 → **접근성 서비스 켜기**
   (S22에서 토글이 회색이면: 설정 → 애플리케이션 → 앱 정보 → ⋮ → *제한된 설정 허용*) → Server 탭에서 **Start**.
   Server 탭에 표시되는 **토큰**을 `.env`의 `MCP_TOKEN`에 넣기.
3. PC ↔ 폰 연결 (둘 중 하나)
   - **USB(권장)**: 개발자 옵션의 USB 디버깅 켜기 → `adb forward tcp:8080 tcp:8080` → `MCP_URL=http://127.0.0.1:8080/mcp`
   - **같은 Wi-Fi**: 앱 Settings에서 바인딩을 `0.0.0.0`으로 → `MCP_URL=http://<폰IP>:8080/mcp`

## 1단계 — DeepSeek API만 점검 (폰 불필요)
```
python llm_check.py
```
기본 응답 지연, 스트리밍 TTFT(첫 토큰 시간), **tool calling 지원 여부**, 한국어 고령자 말투 이해를 확인합니다.
tool calling이 0/3이면 2단계에서 `--json-mode`를 붙이세요.

## 2단계 — 폰 연결 확인
```
python phone_agent.py --check
```
현재 폰 화면의 UI 노드 목록이 출력되면 성공.

## 3단계 — 직접 명령해 보기
```
python phone_agent.py                         # 대화형
python phone_agent.py --task "유튜브에서 임영웅 노래 찾아줘"
```

## 4단계 — 벤치마크 (성능 검증)
```
python phone_agent.py --bench bench_tasks.json            # 과제마다 사람 판정(y/n) 입력
python phone_agent.py --bench bench_tasks.json --repeat 3 # 반복 측정
```
`runs/<시각>/summary.csv`, `runs.jsonl`에 과제별 기록이 남습니다.

| 지표 | 의미 |
|---|---|
| success / auto_check / human_success | 모델 자체 판정 / 화면 문자열 자동 확인 / 사람 판정 (논문엔 사람 판정 기준 권장) |
| steps, llm_calls | 과제 완료까지 단계 수 |
| llm_time, tool_time, wall_time | LLM 대기 / 폰 조작 / 전체 시간 → 병목 파악 |
| prompt/completion_tokens | 과제당 비용 |
| tool_errors, format_errors | 잘못된 노드·인자, 도구 미호출 등 |
| ask_user | 확인 질문 횟수 (B8 애매함, B9 결제는 질문해야 정답) |

`--auto-yes`는 무인 실행용이며, 확인 질문에 항상 "진행 중지"로 답합니다 (실수로 결제되지 않도록).

## 설계 메모
- 도구 58개 중 브라우저/일상 작업용 20개만 노출 → 토큰 절약, 선택 오류 감소 (`ALLOWED`).
- 화면을 바꾸는 동작 뒤에는 자동으로 `wait_for_idle` + 새 화면 상태를 붙여 LLM 왕복을 줄임.
- 과거 화면 TSV는 지워 컨텍스트 폭증 방지. 스크린샷은 사용 안 함 (텍스트 전용 모델 가정).
- 입력 속도 기본 250ms/글자 → 30ms로 낮춤 (`--typing-speed`).
- 결제·전송·삭제·로그인 직전 `ask_user` 확인을 시스템 프롬프트로 강제.

## Gemini로 바꿔서 실행하기
`.env`에 아래를 추가 (DeepSeek 설정은 그대로 둬도 됨):
```
GEMINI_API_KEY=<Google AI Studio 키>
GEMINI_MODEL=gemini-3.8-flash
```
모든 명령 뒤에 `--provider gemini`만 붙이면 됩니다.
```
python llm_check.py --provider gemini              # [1]에서 사용 가능한 모델 이름 확인
python phone_agent.py --provider gemini --check
python phone_agent.py --provider gemini --task "유튜브에서 임영웅 노래 찾아줘"
python phone_agent.py --provider gemini --bench bench_tasks.json
python phone_agent.py --provider gemini --model <다른 모델명> ...   # 모델만 바꿔 보기
```
결과 폴더 이름 끝에 `_gemini` / `_deepseek`가 붙고, CSV에 `model` 열이 있어 두 모델을 바로 비교할 수 있습니다.
