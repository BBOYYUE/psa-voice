#!/usr/bin/env python3
"""单独测 ASR：给音频文件，出文字 + 耗时。"""
import sys
import time
from pathlib import Path

sys.path.insert(0, "/home/sollado/projects/voice-gateway")

import asyncio

import asr

async def main(paths):
    if not paths:
        paths = ["/home/sollado/hermes/workspace/voice-asr/t2.mp3"]
    for p in paths:
        path = Path(p)
        if not path.exists():
            print(f"跳过（不存在）: {p}")
            continue
        print(f"\n=== {path.name} ({path.stat().st_size/1024:.0f} KB) ===")
        t0 = time.time()
        try:
            text, ms, dur = await asr.transcribe_file(path)
        except Exception as exc:
            print("失败:", type(exc).__name__, exc)
            continue
        print(f"音频时长 {dur:.2f}s | 识别耗时 {ms}ms | 总耗时 {(time.time()-t0)*1000:.0f}ms")
        print(f"结果: {text!r}")

if __name__ == "__main__":
    asyncio.run(main(sys.argv[1:]))
