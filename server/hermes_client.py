"""调用本机 Hermes API Server（OpenAI 兼容 /v1/chat/completions）。"""
from __future__ import annotations

import logging
import time
from typing import Optional, Tuple

import requests

import config

logger = logging.getLogger("vg.hermes")


class HermesError(RuntimeError):
    pass


def _headers(session_id: Optional[str]) -> dict:
    h = {"Content-Type": "application/json"}
    if config.HERMES_API_KEY:
        h["Authorization"] = f"Bearer {config.HERMES_API_KEY}"
    # Hermes 用它把多轮对话串成同一段会话（/new 语义由客户端换 id 控制）
    if session_id and config.SESSION_ENABLED:
        h["X-Hermes-Session-Id"] = session_id
    return h


def ask(
    text: str,
    *,
    session_id: Optional[str] = None,
    system_prompt: Optional[str] = None,
    extra_instruction: Optional[str] = None,
    timeout: Optional[int] = None,
) -> Tuple[str, int]:
    """把一句话交给 Hermes，返回 (回答, 耗时ms)。"""
    if not text.strip():
        raise HermesError("空文本")

    messages = [{"role": "system", "content": system_prompt or config.HERMES_SYSTEM_PROMPT}]
    if extra_instruction:
        messages.append({"role": "system", "content": extra_instruction})
    messages.append({"role": "user", "content": text})

    body = {
        "model": config.HERMES_MODEL,
        "messages": messages,
        "stream": False,
    }

    url = f"{config.HERMES_BASE_URL.rstrip('/')}/v1/chat/completions"
    t0 = time.time()
    try:
        r = requests.post(
            url,
            headers=_headers(session_id),
            json=body,
            timeout=timeout or config.HERMES_TIMEOUT_S,
        )
    except requests.Timeout as exc:
        raise HermesError("Hermes 响应超时") from exc
    except requests.RequestException as exc:
        raise HermesError(f"连不上 Hermes（{url}）：{exc}") from exc

    elapsed_ms = int((time.time() - t0) * 1000)

    if r.status_code != 200:
        snippet = r.text[:300].replace("\n", " ")
        raise HermesError(f"Hermes 返回 {r.status_code}: {snippet}")

    try:
        data = r.json()
        answer = data["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise HermesError(f"Hermes 返回格式异常: {r.text[:200]}") from exc

    if isinstance(answer, list):  # 多段 content 的兼容处理
        answer = "".join(
            part.get("text", "") for part in answer if isinstance(part, dict)
        )
    if not isinstance(answer, str):
        raise HermesError("Hermes 回答类型异常")

    logger.info("Hermes 完成 %dms, %d 字", elapsed_ms, len(answer or ""))
    return (answer or "").strip(), elapsed_ms


def health() -> bool:
    try:
        r = requests.get(
            f"{config.HERMES_BASE_URL.rstrip('/')}/health",
            timeout=5,
        )
        return r.status_code == 200
    except requests.RequestException:
        return False
