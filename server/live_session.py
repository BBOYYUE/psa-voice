"""Experimental live conversation endpoint; no audio is written to disk."""
import asyncio
import contextlib
import hmac
import json
import logging
import os
import re
import time
import uuid

from fastapi import WebSocket, WebSocketDisconnect

import config
import live_agent
import live_asr
import music

_slots = asyncio.Semaphore(1)  # CPU admission; one live car until measured
logger = logging.getLogger('vg.live')
EXIT = {'结束对话', '退出助手', '不聊了'}
PACKET_TIMEOUT_S = 10
IDLE_TIMEOUT_S = 60
EXECUTION_TIMEOUT_S = 30
RESUME_TIMEOUT_S = 120
SESSION_TIMEOUT_S = 900
AGENT_TIMEOUT_S = 35
TICK_S = 0.25


async def serve(ws: WebSocket):
    if os.getenv('VG_LIVE_ENABLED', '').lower() != 'true':
        await ws.close(code=1008)
        return
    supplied = ws.headers.get('authorization', '')
    expected = 'Bearer ' + config.GATEWAY_TOKEN
    if not config.GATEWAY_TOKEN or not hmac.compare_digest(supplied.encode(), expected.encode()):
        await ws.close(code=1008)
        return
    if _slots.locked():
        await ws.close(code=1013)
        return
    await _slots.acquire()
    task = None
    heartbeat = None
    try:
        await ws.accept()
    except BaseException:
        _slots.release()
        raise
    lock = asyncio.Lock()
    sid = uuid.uuid4().hex
    history = []
    turn = 0
    mode = 'starting'
    pending = None
    last_text = ''
    started = time.monotonic()
    last_activity = started
    last_packet = started
    sequence = 0
    phase_started = started

    async def send(kind, **data):
        nonlocal sequence
        async with lock:
            sequence += 1
            if kind == 'error':
                data.setdefault('recoverable', False)
            await asyncio.wait_for(ws.send_json({
                'event': kind, 'session_id': sid, 'seq': sequence, 'state': mode,
                'elapsed_ms': round((time.monotonic() - started) * 1000), **data}), 5)

    async def pulse():
        while True:
            await asyncio.sleep(3)
            await send('heartbeat', state=mode, turn_id=turn)

    async def process(text, tid):
        nonlocal mode, pending, phase_started
        first_speech_ms = None
        speech_count = 0
        t0 = time.monotonic()
        async def progress(stage):
            await send('stage', stage=stage, turn_id=tid)
        async def speech(text_part):
            nonlocal first_speech_ms, speech_count
            if first_speech_ms is None:
                first_speech_ms = round((time.monotonic() - t0) * 1000)
            speech_count += 1
            await send('speech', turn_id=tid, text=text_part, index=speech_count)
        try:
            await progress('understanding')
            key = ' '.join(text.strip().split()).casefold().rstrip('。！!')
            control = music._FAST.get(key)
            fast = ({'decision': 'execute', 'action': {'app': 'qqmusic', **control}, 'answer': ''}
                    if control else None)
            if fast and history and key == '播放':
                fast = None  # '播放' can confirm an earlier suggestion
            async with asyncio.timeout(AGENT_TIMEOUT_S):
                result = fast or await live_agent.run(text, history[-16:], progress, speech)
            if result['decision'] == 'exit':
                return 'voice_exit'
            action = result.get('action')
            if action and not music.client_supports(action['type'], client_version):
                result = {'decision': 'unsupported', 'action': None, 'answer': '请升级客户端后再试。'}
                action = None
            if action:
                pending = tid
            history.extend([{'role': 'user', 'content': text},
                            {'role': 'assistant', 'content': json.dumps(result, ensure_ascii=False)}])
            del history[:-16]
            mode = 'awaiting_execution' if pending else 'awaiting_resume'
            phase_started = time.monotonic()
            await send('result', turn_id=tid, **result,
                       agent_ms=round((time.monotonic() - t0) * 1000),
                       first_speech_ms=first_speech_ms, speech_count=speech_count)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            mode = 'awaiting_resume'
            phase_started = time.monotonic()
            code = 'agent_timeout' if isinstance(exc, (TimeoutError, live_agent.httpx.TimeoutException)) else 'agent_failed'
            if isinstance(exc, ValueError):
                code = 'agent_invalid_output'
            logger.warning('Agent turn failed turn_id=%d error_type=%s', tid, type(exc).__name__)
            if isinstance(exc, json.JSONDecodeError):
                logger.warning('Agent JSON failure pos=%d length=%d json_start=%s fence_start=%s',
                               exc.pos, len(exc.doc), exc.doc.lstrip().startswith(('{', '[')),
                               exc.doc.lstrip().startswith('```'))
            await send('error', turn_id=tid, code=code, recoverable=True, message='处理失败，请重试。')

    try:
        hello = await asyncio.wait_for(ws.receive_json(), 10)
        if (not isinstance(hello, dict) or hello.get('event') != 'start'
                or hello.get('sample_rate') != 16000 or hello.get('format') != 'pcm_s16le'
                or hello.get('channels') != 1):
            await send('error', code='invalid_start')
            return
        client_version = hello.get('client_version')
        decoder = await asyncio.to_thread(live_asr.Decoder)
        mode = 'listening'
        await send('ready', audio_saved=False, turn_id=0)
        heartbeat = asyncio.create_task(pulse())
        last_packet = time.monotonic()
        while True:
            now = time.monotonic()
            reason = None
            if now - started > SESSION_TIMEOUT_S:
                reason = 'session_timeout'
            elif now - last_packet > PACKET_TIMEOUT_S:
                reason = 'connection_timeout'
            elif mode == 'listening' and now - last_activity > IDLE_TIMEOUT_S:
                reason = 'idle_timeout'
            elif mode == 'awaiting_execution' and now - phase_started > EXECUTION_TIMEOUT_S:
                reason = 'execution_timeout'
            elif mode == 'awaiting_resume' and now - phase_started > RESUME_TIMEOUT_S:
                reason = 'resume_timeout'
            if not reason and task and task.done() and not task.cancelled():
                reason = task.result()
            if reason:
                mode = 'ended'
                await send('ended', reason=reason)
                break
            if heartbeat.done():
                heartbeat.result()
            try:
                message = await asyncio.wait_for(ws.receive(), TICK_S)
            except asyncio.TimeoutError:
                continue
            last_packet = time.monotonic()
            if message['type'] == 'websocket.disconnect':
                break
            data = message.get('bytes')
            if data is not None:
                if not data or len(data) % 2 or len(data) > 6400:
                    await send('error', code='invalid_audio_frame')
                    break
                if mode != 'listening':
                    continue  # explicit resume prevents TTS/old queued audio becoming commands
                text, endpoint = await asyncio.to_thread(decoder.feed, data)
                if text:
                    last_activity = time.monotonic()
                if text != last_text:
                    last_text = text
                    await send('transcript', text=text, final=False, turn_id=turn + 1)
                if endpoint:
                    # Small conservative guard for obvious unfinished prefixes, not semantic endpointing.
                    incomplete = re.sub(r'[，。！？!?\s]', '', text) in {'我想听', '我想', '播放一首', '帮我找', '帮我找一下'}
                    if incomplete and decoder.samples < 16000 * 8:
                        continue
                    audio_ms = decoder.samples * 1000 // 16000
                    text = await live_asr.refine_repetitions(text, decoder.pcm)
                    decoder.reset()
                    last_text = ''
                    if not text:
                        continue
                    turn += 1
                    await send('transcript', text=text, final=True, turn_id=turn, audio_ms=audio_ms)
                    if text.strip().rstrip('。！!？?') in EXIT:
                        mode = 'ended'
                        await send('ended', reason='voice_exit')
                        break
                    mode = 'processing'
                    phase_started = time.monotonic()
                    task = asyncio.create_task(process(text, turn))
            else:
                raw = message.get('text', '')
                if len(raw) > 4096:
                    raise ValueError('control too large')
                control = json.loads(raw)
                if not isinstance(control, dict):
                    raise ValueError('invalid control')
                event = control.get('event')
                if event == 'cancel':
                    if task:
                        task.cancel()
                        with contextlib.suppress(asyncio.CancelledError):
                            await task
                    mode = 'ended'
                    await send('ended', reason='cancelled')
                    break
                if event == 'execution_result':
                    if pending is None or type(control.get('turn_id')) is not int or control.get('turn_id') != pending:
                        await send('error', code='unexpected_execution_result', recoverable=True)
                        continue
                    status = control.get('status')
                    if status not in {'confirmed', 'sent', 'failed'}:
                        raise ValueError('invalid execution status')
                    history.append({'role': 'user', 'content': '客户端执行反馈：' + status})
                    pending = None
                    mode = 'awaiting_resume'
                    phase_started = time.monotonic()
                    await send('execution_ack', turn_id=turn)
                elif event == 'resume':
                    if mode != 'awaiting_resume' or pending is not None or type(control.get('turn_id')) is not int or control.get('turn_id') != turn:
                        await send('error', code='not_ready_to_resume', recoverable=True)
                        continue
                    decoder.reset()
                    mode = 'listening'
                    last_activity = time.monotonic()
                    await send('listening', turn_id=turn + 1)
                elif event != 'ping':
                    await send('error', code='unknown_control', recoverable=True)
    except (WebSocketDisconnect, asyncio.TimeoutError):
        pass
    except Exception:
        with contextlib.suppress(Exception):
            await send('error', code='live_unavailable', message='实时会话暂时不可用，请检查服务端配置。')
    finally:
        for background in (task, heartbeat):
            if background:
                background.cancel()
                with contextlib.suppress(asyncio.CancelledError, Exception):
                    await background
        _slots.release()
        with contextlib.suppress(Exception):
            await ws.close()
