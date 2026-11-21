#!/usr/bin/env python3
"""端到端：音频文件 → /v1/voice → 转写 + Hermes 回答。"""
import json
import sys
import time
from pathlib import Path

import requests

BASE = "http://127.0.0.1:8650"
ENV_PATH = "/home/sollado/projects/voice-gateway/.env"


def load_env(path):
    out = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            out[k] = v
    return out


def main():
    env = load_env(ENV_PATH)
    headers = {"Authorization": "Bearer " + env["VOICE_GATEWAY_TOKEN"]}
    paths = sys.argv[1:] or ["/home/sollado/hermes/workspace/voice-asr/t2.mp3"]

    for p in paths:
        path = Path(p)
        if not path.exists():
            print(f"不存在: {p}")
            continue
        print(f"\n=== {path.name} ({path.stat().st_size/1024:.0f} KB) ===")
        t0 = time.time()
        with open(path, "rb") as fh:
            r = requests.post(
                f"{BASE}/v1/voice",
                headers=headers,
                files={"file": (path.name, fh, "application/octet-stream")},
                data={"device_id": "test-car-01"},
                timeout=240,
            )
        dt = time.time() - t0
        print(f"HTTP {r.status_code}  总耗时 {dt:.1f}s")
        try:
            print(json.dumps(r.json(), ensure_ascii=False, indent=2))
        except ValueError:
            print(r.text[:500])
    return 0


if __name__ == "__main__":
    sys.exit(main())
