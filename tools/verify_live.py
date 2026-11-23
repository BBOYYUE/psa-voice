#!/usr/bin/env python3
"""Verify authenticated WSS audio -> real ASR -> agent; never execute music actions."""
import argparse
import asyncio
import contextlib
import json
import os
import time
import wave

import websockets


async def verify(args):
    try:
        async with websockets.connect(args.url):
            raise AssertionError('Unauthenticated connection accepted')
    except websockets.exceptions.InvalidStatus as exc:
        assert exc.response.status_code == 403, exc.response.status_code
    print('Unauthenticated WSS rejected', flush=True)
    connected = time.monotonic()
    async with websockets.connect(args.url, additional_headers={
            'Authorization': 'Bearer ' + os.environ['VOICE_GATEWAY_TOKEN']}) as ws:
        sequence = 0
        async def receive():
            nonlocal sequence
            event = json.loads(await asyncio.wait_for(ws.recv(), 45))
            assert event['seq'] > sequence, event
            sequence = event['seq']
            if event['event'] == 'error':
                raise RuntimeError(event['code'])
            return event
        async def until(kind):
            while True:
                event = await receive()
                if event['event'] == kind:
                    return event
                if event['event'] == 'ended':
                    raise RuntimeError(event['reason'])
        await ws.send(json.dumps({'event': 'start', 'format': 'pcm_s16le', 'sample_rate': 16000,
                                  'channels': 1, 'client_version': '0.5.0'}))
        await until('ready')
        print(json.dumps({'ready_ms': round((time.monotonic() - connected) * 1000)}), flush=True)
        for _ in range(args.turns):
            times, counts = {}, {}
            async def upload():
                with wave.open(args.wav, 'rb') as audio:
                    if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) != (1, 2, 16000):
                        raise ValueError('Expected mono 16-bit 16 kHz WAV')
                    while data := audio.readframes(1600):
                        await ws.send(data)
                        await asyncio.sleep(.1)
                times['audio_end'] = time.monotonic()
                for _ in range(30):
                    await ws.send(bytes(3200))
                    await asyncio.sleep(.1)
                while True:
                    await ws.send('{"event":"ping"}')
                    await asyncio.sleep(5)
            sender = asyncio.create_task(upload())
            try:
                while True:
                    event = await receive()
                    kind = event['event']
                    counts[kind] = counts.get(kind, 0) + 1
                    if kind == 'ended':
                        raise RuntimeError(event['reason'])
                    if kind == 'transcript' and event['final']:
                        times['final'] = time.monotonic()
                    if kind == 'speech' and 'speech' not in times:
                        times['speech'] = time.monotonic()
                    if kind == 'result':
                        metrics = {'turn_id': event['turn_id'], 'events': counts,
                                   'agent_ms': event['agent_ms'], 'speech_count': event['speech_count'],
                                   'decision': event['decision'], 'action': event.get('action')}
                        if 'audio_end' in times:
                            metrics['final_after_upload_end_ms'] = round((times['final'] - times['audio_end']) * 1000)
                        if 'speech' in times:
                            metrics['first_speech_after_final_ms'] = round((times['speech'] - times['final']) * 1000)
                        print(json.dumps(metrics, ensure_ascii=False), flush=True)
                        break
            finally:
                sender.cancel()
                with contextlib.suppress(asyncio.CancelledError):
                    await sender
            if event.get('action'):
                await ws.send(json.dumps({'event': 'execution_result', 'turn_id': event['turn_id'], 'status': 'failed'}))
                await until('execution_ack')
            await ws.send(json.dumps({'event': 'resume', 'turn_id': event['turn_id']}))
            await until('listening')
        await ws.send('{"event":"cancel"}')
        ended = await until('ended')
        assert ended['reason'] == 'cancelled'
        print('Public WSS multi-turn/resume/cancel passed', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('wav')
    parser.add_argument('--url', default='wss://ai.sollado.com/v1/live')
    parser.add_argument('--turns', type=int, default=2)
    args = parser.parse_args()
    if not 1 <= args.turns <= 10:
        parser.error('turns must be 1..10')
    asyncio.run(verify(args))
