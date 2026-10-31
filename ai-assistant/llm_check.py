"""
1단계: DeepSeek(Lablup) API 연결 및 성능 점검. 폰 없이 PC에서만 실행.

  python llm_check.py            # 기본 (각 항목 3회)
  python llm_check.py -n 5       # 반복 횟수 변경

확인 항목
  [1] 모델 목록 조회
  [2] 기본 응답 + 지연시간/토큰 처리량
  [3] 스트리밍 첫 토큰 시간(TTFT) — 음성 비서 체감 속도와 직결
  [4] Function/Tool calling 지원 여부 — 폰 제어 에이전트의 필수 조건
  [5] 한국어 + 고령자 말투 의도 파악
"""
import argparse
import json
import statistics as st

from common import LLMClient, LLMError, load_config, make_llm


def hr(t):
    print("\n" + "=" * 70 + f"\n{t}\n" + "=" * 70)


def fmt(xs):
    if not xs:
        return "-"
    return f"평균 {st.mean(xs):.2f}s / 최소 {min(xs):.2f}s / 최대 {max(xs):.2f}s"


DEMO_TOOLS = [
    {"type": "function", "function": {
        "name": "android_open_uri",
        "description": "Open a URI (web URL, tel:, geo:, etc.) on the phone with the default app.",
        "parameters": {"type": "object", "properties": {
            "uri": {"type": "string", "description": "URI to open"}}, "required": ["uri"]}}},
    {"type": "function", "function": {
        "name": "android_open_app",
        "description": "Launch an installed app by package name.",
        "parameters": {"type": "object", "properties": {
            "package_id": {"type": "string"}}, "required": ["package_id"]}}},
    {"type": "function", "function": {
        "name": "android_tap",
        "description": "Tap at screen coordinates.",
        "parameters": {"type": "object", "properties": {
            "x": {"type": "number"}, "y": {"type": "number"}}, "required": ["x", "y"]}}},
]

TOOL_PROMPTS = [
    "네이버에서 오늘 서울 날씨 좀 찾아줘",
    "유튜브 앱 좀 켜줘",
    "손주한테 전화 걸고 싶은데 번호가 010-1234-5678이야",
]

INTENT_PROMPTS = [
    "아 그 뭐냐 노란 거 카톡 말고 그 길 찾는 거 있잖아 그거 좀 열어봐",
    "인터넷에서 기차표 예매하는 거 하고 싶은데 어떻게 해",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-n", type=int, default=3, help="반복 횟수")
    ap.add_argument("--provider", default="deepseek", help="deepseek(기본) / gemini")
    ap.add_argument("--model", help="모델 이름 덮어쓰기")
    ap.add_argument("--effort", help="생각 강도: minimal / low / medium / high")
    args = ap.parse_args()

    cfg = load_config(args.provider)
    if args.model:
        cfg["LLM_MODEL"] = args.model
    if args.effort:
        cfg["LLM_REASONING_EFFORT"] = args.effort
    if not cfg["LLM_API_KEY"]:
        print(f"{cfg['PROVIDER']} API 키가 없습니다. ai-assistant/.env 파일을 확인하세요 (Gemini는 GEMINI_API_KEY).")
        return
    llm = make_llm(cfg)
    print(f"엔드포인트: {llm.url}\n모델: {llm.model}  (reasoning_effort={llm.reasoning_effort or '기본값'})")
    report = {"model": llm.model}

    # [1]
    hr("[1] 모델 목록")
    try:
        models = llm.list_models()
        ids = [m.get("id") for m in models.get("data", [])]
        print(ids if ids else json.dumps(models, ensure_ascii=False)[:500])
        report["models"] = ids
    except Exception as e:
        print(f"(모델 목록 엔드포인트 미지원 또는 실패: {e}) — 무시하고 진행")

    # [2]
    hr("[2] 기본 응답 / 지연시간")
    lat, tps = [], []
    for i in range(args.n):
        try:
            msg, usage, dt = llm.chat([{"role": "user", "content": "안녕하세요. 한 문장으로 자기소개 해주세요."}],
                                      max_tokens=200)
        except LLMError as e:
            print(f"  실패: {e}")
            print("\n→ API 키/모델명/네트워크를 확인하세요. 여기서 막히면 이후 단계는 의미가 없습니다.")
            return
        ct = usage.get("completion_tokens") or 0
        lat.append(dt)
        if ct:
            tps.append(ct / dt)
        has_reason = bool(msg.get("reasoning_content"))
        print(f"  #{i+1} {dt:.2f}s  usage={usage}  reasoning={'있음' if has_reason else '없음'}")
        if i == 0:
            print(f"     응답: {(msg.get('content') or '').strip()[:200]}")
    print(f"  → 지연시간 {fmt(lat)}")
    if tps:
        print(f"  → 출력 처리량 평균 {st.mean(tps):.1f} tok/s (지연 포함)")
    report["basic_latency_mean"] = st.mean(lat)

    # [3]
    hr("[3] 스트리밍 TTFT (첫 토큰까지 시간)")
    ttfts = []
    for i in range(args.n):
        try:
            ttft, total, text, chunks = llm.stream_ttft(
                [{"role": "user", "content": "스마트폰으로 버스 시간 확인하는 방법을 두 문장으로 알려줘."}])
            print(f"  #{i+1} TTFT={ttft if ttft is None else round(ttft, 2)}s  전체={total:.2f}s  청크={chunks}")
            if ttft:
                ttfts.append(ttft)
        except Exception as e:
            print(f"  스트리밍 실패: {e}")
            break
    print(f"  → TTFT {fmt(ttfts)}")
    report["ttft_mean"] = st.mean(ttfts) if ttfts else None

    # [4]
    hr("[4] Tool calling 지원 여부 (가장 중요)")
    sys_msg = {"role": "system", "content": "너는 사용자의 안드로이드 폰을 대신 조작하는 비서다. 반드시 도구를 호출해서 작업을 수행해라."}
    ok, tl = 0, []
    for p in TOOL_PROMPTS:
        try:
            msg, usage, dt = llm.chat([sys_msg, {"role": "user", "content": p}], tools=DEMO_TOOLS, max_tokens=512)
        except LLMError as e:
            print(f"  '{p}' → 오류: {e}")
            continue
        tl.append(dt)
        calls = msg.get("tool_calls") or []
        if calls:
            ok += 1
            for c in calls:
                f = c.get("function", {})
                print(f"  '{p}'\n     → {f.get('name')}({f.get('arguments')})  [{dt:.2f}s]")
        else:
            print(f"  '{p}'\n     → 도구 호출 없음. 텍스트: {(msg.get('content') or '')[:150]}  [{dt:.2f}s]")
    print(f"  → 네이티브 tool calling 성공 {ok}/{len(TOOL_PROMPTS)}  ({fmt(tl)})")
    report["native_tool_calls"] = f"{ok}/{len(TOOL_PROMPTS)}"
    if ok == 0:
        print("  ※ 네이티브 tool calling이 안 되면 phone_agent.py 를 --json-mode 로 실행하세요.")

    # [5]
    hr("[5] 한국어 고령자 말투 의도 파악")
    for p in INTENT_PROMPTS:
        try:
            msg, usage, dt = llm.chat([
                {"role": "system", "content": "디지털 기기에 익숙하지 않은 어르신의 말을 듣고, 하려는 작업을 한 줄로 요약하고 폰에서 할 단계 3개 이내로 적어라."},
                {"role": "user", "content": p}], max_tokens=300)
            print(f"  '{p}'  [{dt:.2f}s]\n     → {(msg.get('content') or '').strip()[:300]}")
        except LLMError as e:
            print(f"  오류: {e}")

    hr("요약")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
