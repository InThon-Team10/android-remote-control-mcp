"""
공통 모듈: 설정 로더, OpenAI 호환 LLM 클라이언트, 폰 MCP 클라이언트.
외부 패키지 없이 Python 표준 라이브러리만 사용 (Windows/Mac/Linux 그대로 실행).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))

# Windows 콘솔에서 한글 깨짐 방지
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass


# ---------------------------------------------------------------- 설정
PROVIDER_DEFAULTS = {
    # Gemini는 OpenAI 호환 엔드포인트를 제공 → 같은 코드로 호출 가능
    "GEMINI": {"BASE_URL": "https://generativelanguage.googleapis.com/v1beta/openai",
               "MODEL": "gemini-3.8-flash", "MAX_TOKENS": "8192",
               "TEMPERATURE": "1.0"},  # Gemini 3 계열은 temperature 1.0 권장(낮추면 반복/루프 위험)
}


def load_config(provider=None):
    """ai-assistant/.env 파일 → 환경변수 순으로 설정을 읽는다 (환경변수가 우선).
    provider="gemini" 이면 .env의 GEMINI_API_KEY / GEMINI_MODEL / GEMINI_BASE_URL ... 이
    LLM_* 값을 덮어쓴다. (DeepSeek ↔ Gemini 비교를 .env 수정 없이 옵션 하나로)"""
    cfg = {
        "LLM_BASE_URL": "https://api.console.lablup.ai/v1",
        "LLM_API_KEY": "",
        "LLM_MODEL": "DeepSeek-V4.1-Flash-B300",
        "MCP_URL": "http://127.0.0.1:8080/mcp",
        "MCP_TOKEN": "",
        "LLM_TEMPERATURE": "0.2",
        "LLM_MAX_TOKENS": "2048",      # 응답 최대 토큰 (생각(thinking) 모델은 넉넉히)
        "LLM_REASONING_EFFORT": "",    # 예: low / medium / high (지원 모델만, 비우면 미전송)
    }
    env_path = os.path.join(HERE, ".env")
    if os.path.exists(env_path):
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip().strip('"').strip("'")
    for k in list(cfg):
        if os.environ.get(k):
            cfg[k] = os.environ[k]
    if provider and provider.lower() not in ("deepseek", "default"):
        P = provider.upper()
        for key in ("BASE_URL", "API_KEY", "MODEL", "TEMPERATURE", "MAX_TOKENS", "REASONING_EFFORT"):
            v = os.environ.get(f"{P}_{key}") or cfg.get(f"{P}_{key}") or PROVIDER_DEFAULTS.get(P, {}).get(key)
            if v is not None:
                cfg[f"LLM_{key}"] = v
        if not os.environ.get(f"{P}_API_KEY") and not cfg.get(f"{P}_API_KEY"):
            cfg["LLM_API_KEY"] = ""  # 다른 공급자 키가 섞여 나가지 않도록
    cfg["PROVIDER"] = (provider or "deepseek").lower()
    return cfg


def make_llm(cfg):
    return LLMClient(cfg["LLM_BASE_URL"], cfg["LLM_API_KEY"], cfg["LLM_MODEL"],
                     temperature=float(cfg.get("LLM_TEMPERATURE") or 0.2),
                     max_tokens=int(cfg.get("LLM_MAX_TOKENS") or 2048),
                     reasoning_effort=cfg.get("LLM_REASONING_EFFORT") or None)


def _post_json(url, payload, headers, timeout):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    for k, v in headers.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace"), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), e.headers


# ---------------------------------------------------------------- LLM
class LLMError(RuntimeError):
    pass


class LLMClient:
    """OpenAI 호환 /chat/completions 클라이언트 (Lablup DeepSeek 엔드포인트용)."""

    def __init__(self, base_url, api_key, model, timeout=180, temperature=0.2,
                 max_tokens=2048, reasoning_effort=None):
        self.default_temperature = temperature
        self.max_tokens_floor = max_tokens   # 호출부가 더 작게 줘도 이 값 이상 사용
        self.reasoning_effort = reasoning_effort
        self.max_retries = int(os.environ.get("LLM_MAX_RETRIES", "4"))
        self.retries = 0
        self.url = base_url.rstrip("/") + "/chat/completions"
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    def _headers(self):
        return {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}

    def chat(self, messages, tools=None, temperature=None, max_tokens=None, extra=None):
        """반환: (assistant_message dict, usage dict, latency_sec)"""
        mt = max(max_tokens or 0, self.max_tokens_floor)
        payload = {"model": self.model, "messages": messages,
                   "temperature": self.default_temperature if temperature is None else temperature,
                   "max_tokens": mt}
        if self.reasoning_effort:
            payload["reasoning_effort"] = self.reasoning_effort
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        if extra:
            payload.update(extra)
        t0 = time.perf_counter()
        # 429(한도)/500/502/503(과부하)/504 는 잠시 후 재시도 (지수 백오프)
        for attempt in range(self.max_retries + 1):
            status, body, hdrs = _post_json(self.url, payload, self._headers(), self.timeout)
            if status not in (429, 500, 502, 503, 504) or attempt == self.max_retries:
                break
            wait = 2 ** (attempt + 1)
            try:
                wait = max(wait, int((hdrs or {}).get("Retry-After") or 0))
            except ValueError:
                pass
            self.retries += 1
            print(f"     ⏳ LLM HTTP {status} (서버 혼잡/한도) → {wait}초 후 재시도 {attempt+1}/{self.max_retries}", flush=True)
            time.sleep(wait)
        dt = time.perf_counter() - t0
        if status != 200:
            raise LLMError(f"HTTP {status}: {body[:800]}")
        try:
            obj = json.loads(body)
            msg = obj["choices"][0]["message"]
        except Exception:
            raise LLMError(f"응답 파싱 실패: {body[:800]}")
        return msg, obj.get("usage") or {}, dt

    def stream_ttft(self, messages, max_tokens=256):
        """스트리밍으로 첫 토큰까지 시간(TTFT)과 전체 시간 측정."""
        payload = {"model": self.model, "messages": messages, "stream": True,
                   "max_tokens": max(max_tokens, self.max_tokens_floor),
                   "temperature": self.default_temperature}
        if self.reasoning_effort:
            payload["reasoning_effort"] = self.reasoning_effort
        req = urllib.request.Request(self.url, data=json.dumps(payload).encode(), method="POST")
        for k, v in self._headers().items():
            req.add_header(k, v)
        t0 = time.perf_counter()
        ttft, text, chunks = None, [], 0
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            for raw in resp:
                line = raw.decode("utf-8", "replace").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    delta = json.loads(data)["choices"][0].get("delta", {})
                except Exception:
                    continue
                piece = delta.get("content") or delta.get("reasoning_content") or ""
                if piece:
                    if ttft is None:
                        ttft = time.perf_counter() - t0
                    text.append(delta.get("content") or "")
                    chunks += 1
        return ttft, time.perf_counter() - t0, "".join(text), chunks

    def list_models(self):
        req = urllib.request.Request(self.base_url + "/models")
        req.add_header("Authorization", f"Bearer {self.api_key}")
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode())


# ---------------------------------------------------------------- MCP (폰)
class MCPError(RuntimeError):
    pass


class MCPClient:
    """폰에서 실행 중인 android-remote-control-mcp 서버 (Streamable HTTP; 세션 방식/무상태 방식 모두 지원)."""

    def __init__(self, url, token="", timeout=60):
        self.url = url
        self.token = token
        self.timeout = timeout
        self._id = 0
        self.prefix = "android_"
        self.session_id = None
        self.protocol_version = None

    def _headers(self):
        h = {"Content-Type": "application/json",
             "Accept": "application/json, text/event-stream"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        if self.session_id:  # 세션 방식 서버(v1.12.0 등) 대응
            h["Mcp-Session-Id"] = self.session_id
        if self.protocol_version:
            h["MCP-Protocol-Version"] = self.protocol_version
        return h

    def _send(self, payload):
        try:
            status, body, headers = _post_json(self.url, payload, self._headers(), self.timeout)
        except urllib.error.URLError as e:
            raise MCPError(f"폰 MCP 서버에 연결 실패 ({self.url}): {e.reason}")
        sid = headers.get("Mcp-Session-Id") if headers else None
        if sid:
            self.session_id = sid
        return status, body

    def _rpc(self, method, params=None, _retry=True):
        self._id += 1
        payload = {"jsonrpc": "2.0", "id": self._id, "method": method}
        if params is not None:
            payload["params"] = params
        status, body = self._send(payload)
        if _retry and method != "initialize" and (
                status == 404 or (status == 400 and "not initialized" in body.lower())):
            # 세션이 없거나 만료됨 → 다시 initialize 후 재시도
            self.session_id = None
            self.initialize()
            return self._rpc(method, params, _retry=False)
        if status != 200:
            raise MCPError(f"MCP HTTP {status}: {body[:500]}")
        body = body.strip()
        if body.startswith("event:") or body.startswith("data:") or "\ndata:" in body:  # SSE 대비
            datas = [l[5:].strip() for l in body.splitlines() if l.startswith("data:")]
            body = datas[-1] if datas else "{}"
        obj = json.loads(body)
        if "error" in obj:
            raise MCPError(f"MCP 오류: {obj['error']}")
        return obj.get("result", {})

    def initialize(self):
        res = self._rpc("initialize", {
            "protocolVersion": "2025-06-18",
            "capabilities": {},
            "clientInfo": {"name": "deepseek-phone-agent", "version": "0.1"},
        })
        self.protocol_version = res.get("protocolVersion") or None
        # 초기화 완료 알림 (id 없는 notification, 응답은 202)
        try:
            self._send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        except MCPError:
            pass
        return res

    def list_tools(self):
        tools, cursor = [], None
        while True:
            res = self._rpc("tools/list", {"cursor": cursor} if cursor else {})
            tools += res.get("tools", [])
            cursor = res.get("nextCursor")
            if not cursor:
                break
        for t in tools:  # 디바이스 slug가 붙은 접두사 감지 (android_<slug>_)
            if t["name"].endswith("get_screen_state"):
                self.prefix = t["name"][: -len("get_screen_state")]
        return tools

    def call(self, name, arguments=None):
        """반환: (text, is_error, n_images)"""
        res = self._rpc("tools/call", {"name": name, "arguments": arguments or {}})
        texts, n_img = [], 0
        for c in res.get("content", []):
            if c.get("type") == "text":
                texts.append(c.get("text", ""))
            elif c.get("type") == "image":
                n_img += 1
        return "\n".join(texts), bool(res.get("isError")), n_img

    def screen(self, offscreen=False):
        name = self.prefix + "get_screen_state"
        if getattr(self, "_screen_args_ok", True):
            text, err, _ = self.call(name, {"include_hierarchy": False, "include_offscreen": offscreen})
            if not (err and ("param" in text.lower() or "argument" in text.lower())):
                return text, err
            self._screen_args_ok = False  # 구버전 앱: 옵션 인자 미지원 → 인자 없이 호출
        text, err, _ = self.call(name, {})
        return text, err
