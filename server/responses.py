"""★ 返回结构唯一改动点 ★

客户端（车机）之后会指定固定的返回结构，但目前还没定。
所以所有对外 JSON 都在这里构造 —— 到时候只改这个文件，其它代码不用动。

当前结构（v0，临时）：
{
  "ok": true,
  "request_id": "uuid",
  "transcript": "用户说的话（ASR 结果）",
  "answer": "Hermes 的分析/回答",
  "meta": {
    "asr_ms": 320,
    "hermes_ms": 4200,
    "session_id": "car-001",
    "model": "hermes-agent"
  }
}

失败时：
{
  "ok": false,
  "request_id": "uuid",
  "error": {"code": "asr_failed", "message": "..."}
}
"""
from __future__ import annotations

from typing import Any, Dict, Optional

# 结构版本号，客户端可以据此判断兼容性
SCHEMA_VERSION = "v0"


def ok_voice(
    *,
    request_id: str,
    transcript: str,
    answer: str,
    asr_ms: int,
    hermes_ms: int,
    session_id: Optional[str] = None,
    model: Optional[str] = None,
    extra_meta: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """语音 → 转写 + 回答 的完整结果。"""
    meta: Dict[str, Any] = {"asr_ms": asr_ms, "hermes_ms": hermes_ms}
    if session_id:
        meta["session_id"] = session_id
    if model:
        meta["model"] = model
    if extra_meta:
        meta.update(extra_meta)
    return {
        "ok": True,
        "schema": SCHEMA_VERSION,
        "request_id": request_id,
        "transcript": transcript,
        "answer": answer,
        "meta": meta,
    }


def ok_transcript(
    *,
    request_id: str,
    transcript: str,
    asr_ms: int,
    duration_s: Optional[float] = None,
) -> Dict[str, Any]:
    """只转写、不分析。调试和"客户端自己调模型"两种场景都有用。"""
    meta: Dict[str, Any] = {"asr_ms": asr_ms}
    if duration_s is not None:
        meta["duration_s"] = round(duration_s, 2)
    return {
        "ok": True,
        "schema": SCHEMA_VERSION,
        "request_id": request_id,
        "transcript": transcript,
        "meta": meta,
    }


def ok_text(
    *,
    request_id: str,
    answer: str,
    hermes_ms: int,
    session_id: Optional[str] = None,
    model: Optional[str] = None,
) -> Dict[str, Any]:
    """纯文本请求的返回。"""
    meta: Dict[str, Any] = {"hermes_ms": hermes_ms}
    if session_id:
        meta["session_id"] = session_id
    if model:
        meta["model"] = model
    return {
        "ok": True,
        "schema": SCHEMA_VERSION,
        "request_id": request_id,
        "answer": answer,
        "meta": meta,
    }


def fail(*, request_id: str, code: str, message: str, status: int = 400) -> Dict[str, Any]:
    return {
        "ok": False,
        "schema": SCHEMA_VERSION,
        "request_id": request_id,
        "error": {"code": code, "message": message},
        "_status": status,
    }


def with_status(payload: Dict[str, Any]) -> tuple[Dict[str, Any], int]:
    """把内部 _status 摘出来，返回 (body, http_status)。"""
    status = int(payload.pop("_status", 200))
    return payload, status
