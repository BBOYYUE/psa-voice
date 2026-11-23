"""Audio/text gateway with an explicit, validated music-action contract."""
from __future__ import annotations

import asyncio
import contextlib
import hmac
import json
import logging
import re
import uuid
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request, WebSocket
from fastapi.responses import JSONResponse, StreamingResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.formparsers import MultiPartException

import asr
import config
import hermes_client
import music
import live_session
import live_asr
import responses
import temporary_audio

logging.basicConfig(level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("vg")
app = FastAPI(title="Voice Gateway for Hermes", version="0.2.0", docs_url=None, redoc_url=None)
_cleanup_task = None
_live_warmup_task = None


async def _warm_live():
    try:
        await asyncio.to_thread(live_asr.recognizer)
        logger.info('Online ASR warmup ready')
    except Exception as exc:
        logger.error('Online ASR warmup failed: %s', type(exc).__name__)


class UploadLimitMiddleware:
    """Count streamed multipart bytes too, and let Starlette close partial files."""
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope.get("path") not in {"/v1/voice", "/v1/transcribe"}:
            return await self.app(scope, receive, send)
        received = 0

        async def bounded_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > config.MAX_UPLOAD_BYTES + 65536:
                    raise MultiPartException("audio_too_large")
            return message
        await self.app(scope, bounded_receive, send)


app.add_middleware(UploadLimitMiddleware)


async def _cleanup_loop():
    while True:
        try:
            await asyncio.to_thread(temporary_audio.cleanup_stale)
        except Exception:
            logger.exception("Audio workspace cleanup failed")
        await asyncio.sleep(config.AUDIO_CLEANUP_INTERVAL_S)


@app.on_event("startup")
async def _startup():
    global _cleanup_task, _live_warmup_task
    _cleanup_task = asyncio.create_task(_cleanup_loop())
    if (config.ASR_MODEL_DIR / "tokens.txt").exists():
        asyncio.create_task(asyncio.to_thread(asr.warmup))
    if live_asr.enabled():
        _live_warmup_task = asyncio.create_task(_warm_live())


@app.on_event("shutdown")
async def _shutdown():
    if _live_warmup_task:
        with contextlib.suppress(Exception):
            await _live_warmup_task
    if _cleanup_task:
        _cleanup_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await _cleanup_task


async def require_token(authorization: Optional[str] = Header(default=None)):
    if not config.GATEWAY_TOKEN:
        raise HTTPException(500, "服务端未配置访问凭证")
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "缺少 Bearer 访问凭证")
    if not hmac.compare_digest(authorization.split(" ", 1)[1].strip().encode(), config.GATEWAY_TOKEN.encode()):
        raise HTTPException(403, "访问凭证不正确")


def _rid():
    return uuid.uuid4().hex[:16]


def _reply(rid, payload):
    body, status = responses.with_status(payload)
    body["request_id"] = rid
    return JSONResponse(body, status_code=status)


def _fail(rid, code, message, status=400, *, scene=None):
    body = responses.fail(request_id=rid, code=code, message=message, status=status)
    if scene == "music":
        body.update(schema="v1", scene="music", action=None)
    return _reply(rid, body)


@app.get("/health")
async def health():
    return {"ok": True, "service": "voice-gateway", "schema": responses.SCHEMA_VERSION,
            "music_schema": "v1",
            "asr": {"model_dir": str(config.ASR_MODEL_DIR), "ready": (config.ASR_MODEL_DIR / "tokens.txt").exists()},
            "live": live_asr.status(),
            "hermes": {"base_url": config.HERMES_BASE_URL, "reachable": await asyncio.to_thread(hermes_client.health)}}


@app.get("/v1/capabilities", dependencies=[Depends(require_token)])
async def capabilities():
    return {"ok": True, "schema": "v1", "scenes": ["general", "music"],
            "music": {"apps": ["qqmusic"], "actions": sorted(music.ACTIONS), "pages": sorted(music.PAGES),
                      "requires_client": {".".join(map(str, music.MIN_CLIENT_VERSION)): sorted(music.NEW_ACTIONS)}},
            "audio": {"preferred_format": "wav", "sample_rate": 16000, "channels": 1, "sample_bits": 16,
                      "max_bytes": config.MAX_UPLOAD_BYTES, "max_seconds": config.MAX_AUDIO_SECONDS},
            "retention": {"audio_saved": False, "crash_cleanup_after_seconds": config.AUDIO_TEMP_TTL_S}}


AUDIO_CT_SUFFIX = {"audio/wav": ".wav", "audio/x-wav": ".wav", "audio/wave": ".wav",
                   "audio/mpeg": ".mp3", "audio/mp3": ".mp3", "audio/mp4": ".m4a",
                   "audio/x-m4a": ".m4a", "audio/aac": ".aac", "audio/amr": ".amr",
                   "audio/3gpp": ".3gp", "audio/ogg": ".ogg", "audio/webm": ".webm",
                   "audio/flac": ".flac", "application/octet-stream": ".bin"}
FIELDS = ("device_id", "session_id", "prompt", "language", "scene", "preferred_app", "trigger", "request_id", "context", "client_version")


class InputError(ValueError):
    pass


class UploadTooLarge(ValueError):
    pass


async def _extract_audio(request):
    ct = (request.headers.get("content-type") or "").lower()
    fields = dict(request.query_params)
    if ct.startswith("multipart/form-data"):
        # The context manager closes all UploadFiles, including parser-spooled files.
        async with request.form(max_files=1, max_fields=12, max_part_size=16384) as form:
            upload = form.get("file") or form.get("audio") or form.get("data")
            for key in FIELDS:
                value = form.get(key)
                if value is not None:
                    if not isinstance(value, str):
                        raise InputError("上下文字段必须是字符串")
                    fields[key] = value
            if upload is None or isinstance(upload, str):
                return b"", ".bin", fields
            data = await upload.read(config.MAX_UPLOAD_BYTES + 1)
            if len(data) > config.MAX_UPLOAD_BYTES:
                raise UploadTooLarge()
            # Derive suffix from MIME type or a small extension allowlist, never from a path.
            from pathlib import Path
            suffix = Path(upload.filename or "").suffix.lower()
            if suffix not in set(AUDIO_CT_SUFFIX.values()):
                suffix = AUDIO_CT_SUFFIX.get(upload.content_type, ".bin")
            return data, suffix, fields
    chunks, size = [], 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > config.MAX_UPLOAD_BYTES:
            raise UploadTooLarge()
        chunks.append(chunk)
    return b"".join(chunks), AUDIO_CT_SUFFIX.get(ct.split(";")[0].strip(), ".bin"), fields


def _context(fields):
    values = dict(fields)
    nested = values.get("context")
    if isinstance(nested, str):
        try:
            nested = json.loads(nested)
        except ValueError as exc:
            raise InputError("context 必须是 JSON 对象") from exc
    if nested is not None:
        if not isinstance(nested, dict):
            raise InputError("context 必须是对象")
        for key in ("scene", "preferred_app", "trigger"):
            if key in nested and key not in values:
                values[key] = nested[key]
    for key in ("device_id", "session_id", "scene", "preferred_app", "trigger", "request_id", "prompt", "system_prompt", "client_version"):
        if key in values and (not isinstance(values[key], str) or len(values[key]) > 2000):
            raise InputError(f"{key} 类型或长度不正确")
    scene = values.get("scene", "general")
    if scene not in {"music", "general"}:
        raise InputError("scene 仅支持 music 或 general")
    if scene == "music" and values.get("preferred_app", "qqmusic") != "qqmusic":
        raise InputError("音乐场景目前仅支持 qqmusic")
    for key in ("device_id", "session_id"):
        if len(values.get(key, "")) > 128:
            raise InputError(f"{key} 过长")
    rid = values.get("request_id")
    if rid and not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", rid):
        raise InputError("request_id 仅支持 1 到 64 位字母、数字、下划线或短横线")
    values["scene"] = scene
    return values


def _gate_music(rid, result, fields):
    """Old head-unit builds reject unknown actions; downgrade instead of breaking them."""
    if result["decision"] == "execute" and not music.client_supports(
            result["action"]["type"], fields.get("client_version")):
        logger.info("[%s] action %s gated: client_version=%s",
                    rid, result["action"]["type"], fields.get("client_version"))
        return {"decision": "unsupported", "action": None,
                "answer": "车机端 App 需要升级到最新版本后才能执行该操作。"}
    return result


def _music_error(rid, code, message):
    body, _ = responses.with_status(responses.fail(request_id=rid, code=code, message=message, status=502))
    body.update(schema="v1", scene="music", action=None, request_id=rid)
    return json.dumps(body, ensure_ascii=False).encode()


def _music_model_stream(rid, text, fields, session_id, extra, turns, asr_ms, duration_s):
    """LLM 路径流式响应：先刷响应头让客户端尽早插话，正文在模型返回后送达。
    头部一旦发出状态码就是 200，模型错误以 ok:false 正文表达（客户端按 code 处理）。"""
    future = asyncio.get_running_loop().run_in_executor(
        None, music.complete, text, session_id, extra, turns)

    async def body():
        yield b""  # 立即刷出 X-Stage: analyzing 响应头。
        try:
            result, elapsed = await future
        except music.IntentError:
            logger.warning("[%s] Invalid music intent response", rid)
            yield _music_error(rid, "invalid_intent", "没有得到可执行的音乐指令，请换个说法。")
            return
        except hermes_client.HermesError:
            logger.warning("[%s] Hermes unavailable", rid)
            yield _music_error(rid, "hermes_failed", "语义分析暂时不可用，请稍后再试。")
            return
        result = _gate_music(rid, result, fields)
        logger.info("[%s] music asr_ms=%d semantic_ms=%d decision=%s", rid, asr_ms, elapsed, result["decision"])
        payload = music.response(request_id=rid, transcript=text, result=result,
                                 asr_ms=asr_ms, hermes_ms=elapsed, session_id=session_id, duration_s=duration_s)
        body_dict, _ = responses.with_status(payload)
        body_dict["request_id"] = rid
        yield json.dumps(body_dict, ensure_ascii=False).encode()

    return StreamingResponse(body(), media_type="application/json; charset=utf-8",
                             headers={"X-Stage": "analyzing",
                                      "X-Accel-Buffering": "no", "Cache-Control": "no-cache"})


async def _analyze(rid, text, fields, *, asr_ms=0, duration_s=None, audio=False):
    session_id = fields.get("session_id") or fields.get("device_id")
    scene = fields["scene"]
    try:
        if scene == "music":
            result, extra, turns = await asyncio.to_thread(music.prepare, text, session_id)
            if result is None:
                return _music_model_stream(rid, text, fields, session_id, extra, turns, asr_ms, duration_s)
            result = _gate_music(rid, result, fields)
            logger.info("[%s] music asr_ms=%d semantic_ms=0 decision=%s", rid, asr_ms, result["decision"])
            return _reply(rid, music.response(request_id=rid, transcript=text, result=result,
                          asr_ms=asr_ms, hermes_ms=0, session_id=session_id, duration_s=duration_s))
        answer, elapsed = await asyncio.to_thread(
            hermes_client.ask, text, session_id=session_id,
            system_prompt=fields.get("system_prompt"), extra_instruction=fields.get("prompt"))
    except music.IntentError:
        logger.warning("[%s] Invalid music intent response", rid)
        return _fail(rid, "invalid_intent", "没有得到可执行的音乐指令，请换个说法。", 502, scene=scene)
    except hermes_client.HermesError:
        logger.warning("[%s] Hermes unavailable", rid)
        return _fail(rid, "hermes_failed", "语义分析暂时不可用，请稍后再试。", 502, scene=scene)
    if audio:
        return _reply(rid, responses.ok_voice(request_id=rid, transcript=text, answer=answer,
                      asr_ms=asr_ms, hermes_ms=elapsed, session_id=session_id, model=config.HERMES_MODEL,
                      extra_meta={"duration_s": round(duration_s, 2)}))
    return _reply(rid, responses.ok_text(request_id=rid, answer=answer, hermes_ms=elapsed,
                  session_id=session_id, model=config.HERMES_MODEL))


async def _audio_endpoint(request, *, transcribe_only=False):
    rid, fields = _rid(), {}
    try:
        length = request.headers.get("content-length")
        if length and (not length.isdigit() or int(length) > config.MAX_UPLOAD_BYTES + 65536):
            raise UploadTooLarge()
        data, suffix, raw_fields = await _extract_audio(request)
        fields = _context(raw_fields)
        rid = fields.get("request_id") or rid
    except UploadTooLarge:
        return _fail(rid, "audio_too_large", "音频超过上传大小上限", 413)
    except (StarletteHTTPException, MultiPartException) as exc:
        detail = getattr(exc, "detail", str(exc))
        if detail == "audio_too_large":
            return _fail(rid, "audio_too_large", "音频超过上传大小上限", 413)
        return _fail(rid, "bad_request", "音频或上下文格式不正确", 400)
    except (InputError, ValueError):
        return _fail(rid, "bad_request", "音频或上下文格式不正确", 400)
    scene = fields["scene"]
    if not data:
        return _fail(rid, "no_audio", "没有收到音频", 400, scene=scene)
    try:
        # All request and ffmpeg files are removed before the Hermes request starts.
        transcript, asr_ms, duration_s = await asr.transcribe_bytes(data, suffix)
    except asr.ASRError:
        return _fail(rid, "asr_failed", "音频无法识别，请重新录音。", 422, scene=scene)
    except Exception:
        logger.exception("[%s] ASR failed", rid)
        return _fail(rid, "asr_error", "语音识别暂时不可用", 500, scene=scene)
    finally:
        del data
    if transcribe_only:
        return _reply(rid, responses.ok_transcript(request_id=rid, transcript=transcript,
                      asr_ms=asr_ms, duration_s=duration_s))
    if not transcript:
        return _fail(rid, "empty_transcript", "没有听清，请再说一次。", 422, scene=scene)
    return await _analyze(rid, transcript, fields, asr_ms=asr_ms, duration_s=duration_s, audio=True)


@app.post("/v1/music/context/clear", dependencies=[Depends(require_token)])
async def clear_music_context(request: Request):
    rid = _rid()
    try:
        body = await request.json()
        if not isinstance(body, dict):
            raise InputError()
    except (ValueError, TypeError):
        return _fail(rid, "bad_json", "请求体必须是 JSON 对象", 400)
    session = body.get("session_id") or body.get("device_id")
    if not isinstance(session, str) or not session or len(session) > 128:
        return _fail(rid, "bad_request", "device_id 无效", 400)
    music.clear_context(session)
    logger.info("[%s] clarify context cleared by client session=%s", rid, session)
    return {"ok": True, "schema": "v1", "scene": "music"}


@app.post("/v1/voice", dependencies=[Depends(require_token)])
async def voice(request: Request):
    return await _audio_endpoint(request)


@app.post("/v1/transcribe", dependencies=[Depends(require_token)])
async def transcribe(request: Request):
    return await _audio_endpoint(request, transcribe_only=True)


@app.post("/v1/text", dependencies=[Depends(require_token)])
async def text_endpoint(request: Request):
    rid = _rid()
    try:
        body = await request.json()
        if not isinstance(body, dict):
            raise InputError()
        text = body.get("text", body.get("input", ""))
        if not isinstance(text, str) or len(text) > 2000:
            raise InputError()
        fields = _context(body)
        rid = fields.get("request_id") or rid
    except (ValueError, TypeError):
        return _fail(rid, "bad_json", "请求体必须是有效的文本和上下文 JSON 对象", 400)
    if not text.strip():
        return _fail(rid, "empty_text", "text 不能为空", 400, scene=fields["scene"])
    return await _analyze(rid, text.strip(), fields)





# Separate opt-in protocol; existing APK HTTP contracts are unchanged.

@app.websocket('/v1/live')
async def live_conversation(websocket: WebSocket):
    await live_session.serve(websocket)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host=config.HOST, port=config.PORT)
