#!/usr/bin/env python3
"""Send WAV files as paced PCM to /v1/live; print events, never execute music."""
import argparse
import asyncio
import json
import os
import time
import wave

import websockets

async def main(args):
    token = os.environ['VOICE_GATEWAY_TOKEN']
    async with websockets.connect(args.url, additional_headers={'Authorization': 'Bearer '+token},
                                  max_size=65536, max_queue=16) as ws:
        await ws.send(json.dumps({'event':'start','format':'pcm_s16le','sample_rate':16000,
                                  'channels':1,'client_version':'0.5.0'}))
        origin=time.monotonic()
        async def receive_until(kinds):
            while True:
                event=json.loads(await asyncio.wait_for(ws.recv(),45))
                print(f'{time.monotonic()-origin:.3f}s',json.dumps(event,ensure_ascii=False),flush=True)
                if event['event']=='error': raise RuntimeError(event['code'])
                if event['event'] in kinds: return event
        if (await receive_until({'ready','ended'}))['event']=='ended': return
        for filename in args.wav:
            async def upload():
                with wave.open(filename,'rb') as audio:
                    if (audio.getnchannels(),audio.getsampwidth(),audio.getframerate())!=(1,2,16000):
                        raise ValueError('Expected mono 16-bit 16 kHz WAV')
                    while data:=audio.readframes(1600):
                        await ws.send(data)
                        await asyncio.sleep(.1)
                for _ in range(30):
                    await ws.send(bytes(3200))
                    await asyncio.sleep(.1)
                while True:
                    await ws.send('{"event":"ping"}')
                    await asyncio.sleep(5)
            sender=asyncio.create_task(upload())
            try:
                result=await receive_until({'result','ended'})
            finally:
                sender.cancel()
                try: await sender
                except asyncio.CancelledError: pass
            if result['event']=='ended': return
            if result.get('action'):
                # Probe cannot execute. Report failure honestly rather than claiming playback.
                await ws.send(json.dumps({'event':'execution_result','turn_id':result['turn_id'],'status':'failed'}))
                await receive_until({'execution_ack'})
            await ws.send(json.dumps({'event':'resume','turn_id':result['turn_id']}))
            await receive_until({'listening'})
        await ws.send('{"event":"cancel"}')
        await receive_until({'ended'})

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default='ws://127.0.0.1:8650/v1/live')
    parser.add_argument('wav',nargs='+')
    asyncio.run(main(parser.parse_args()))
