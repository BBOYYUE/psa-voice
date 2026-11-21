#!/usr/bin/env python3
"""端到端冒烟测试：/health、鉴权、/v1/text、会话延续。"""
import json
import sys
import time

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

    print("1) /health")
    r = requests.get(f"{BASE}/health", timeout=10)
    print("  ", r.status_code, json.dumps(r.json(), ensure_ascii=False))

    print("2) 无鉴权 → 应 401")
    r = requests.post(f"{BASE}/v1/text", json={"text": "hi"}, timeout=10)
    print("  ", r.status_code)

    print("3) /v1/text 第一轮")
    t0 = time.time()
    r = requests.post(
        f"{BASE}/v1/text",
        headers=headers,
        json={"text": "帮我看看明天下午三点有没有空，另外把空调调到二十四度", "device_id": "test-car-01"},
        timeout=200,
    )
    dt = time.time() - t0
    print(f"   {r.status_code}  {dt:.1f}s")
    body = r.json()
    print("  ", json.dumps(body, ensure_ascii=False)[:500])

    if r.status_code != 200:
        print("!! 第一轮失败，停止")
        return 1

    print("4) /v1/text 第二轮（同 device_id，测会话延续）")
    t0 = time.time()
    r = requests.post(
        f"{BASE}/v1/text",
        headers=headers,
        json={"text": "我刚才说的几点来着？", "device_id": "test-car-01"},
        timeout=200,
    )
    dt = time.time() - t0
    print(f"   {r.status_code}  {dt:.1f}s")
    print("  ", json.dumps(r.json(), ensure_ascii=False)[:500])
    return 0


if __name__ == "__main__":
    sys.exit(main())
