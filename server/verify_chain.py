#!/usr/bin/env python3
"""一次跑完车载语音链路的端到端验证，不碰 Hermes 主 gateway。

用法：venv/bin/python verify_chain.py
"""
import json
import time

import requests

import config

OK = "\033[32m✓\033[0m"
BAD = "\033[31m✗\033[0m"
AUDIO = "/home/sollado/hermes/workspace/voice-asr/t2.mp3"


def line(name, ok, detail=""):
    print(f"  {OK if ok else BAD} {name}" + (f"  {detail}" if detail else ""))
    return ok


def main() -> int:
    fails = 0

    print("[1] 本机 voice-gateway")
    try:
        t0 = time.time()
        r = requests.get("http://127.0.0.1:8650/health", timeout=10)
        d = r.json()
        fails += not line("health 200", r.status_code == 200, f"{time.time()-t0:.2f}s")
        fails += not line("ASR ready", d.get("asr", {}).get("ready") is True)
        fails += not line("Hermes reachable", d.get("hermes", {}).get("reachable") is True,
                          d.get("hermes", {}).get("base_url", ""))
    except Exception as exc:
        print(f"  {BAD} 连不上: {exc}")
        return 1

    print("[2] voice profile 直连（8643）")
    try:
        t0 = time.time()
        r = requests.post(
            f"{config.HERMES_BASE_URL}/v1/chat/completions",
            headers={"Authorization": f"Bearer {config.HERMES_API_KEY}"},
            json={"model": config.HERMES_MODEL,
                  "messages": [{"role": "user", "content": "一加一等于几"}],
                  "stream": False},
            timeout=120,
        )
        dt = time.time() - t0
        d = r.json()
        pt = d.get("usage", {}).get("prompt_tokens")
        fails += not line("200", r.status_code == 200)
        fails += not line(f"prompt 精简 ({pt} token)", (pt or 99999) < 2000)
        fails += not line(f"延迟 {dt:.2f}s", dt < 10)
    except Exception as exc:
        print(f"  {BAD} 失败: {exc}")
        fails += 1

    print("[3] 公网 https://ai.sollado.com")
    try:
        t0 = time.time()
        r = requests.get("https://ai.sollado.com/health", timeout=15)
        fails += not line("health 200", r.status_code == 200, f"{time.time()-t0:.2f}s")
    except Exception as exc:
        print(f"  {BAD} 失败: {exc}")
        fails += 1

    print("[4] 端到端音频（公网）")
    try:
        t0 = time.time()
        with open(AUDIO, "rb") as fh:
            r = requests.post(
                "https://ai.sollado.com/v1/voice",
                headers={"Authorization": f"Bearer {config.GATEWAY_TOKEN}"},
                files={"file": ("t2.mp3", fh, "audio/mpeg")},
                data={"device_id": "verify-chain"},
                timeout=240,
            )
        dt = time.time() - t0
        d = r.json()
        meta = d.get("meta", {})
        fails += not line("HTTP 200", r.status_code == 200)
        fails += not line(f"总耗时 {dt:.2f}s", dt < 15)
        fails += not line(f"ASR {meta.get('asr_ms')}ms", bool(meta.get("asr_ms")))
        fails += not line(f"Hermes {meta.get('hermes_ms')}ms", bool(meta.get("hermes_ms")))
        print(f"    转写: {d.get('transcript')}")
        print(f"    回答: {d.get('answer')}")
    except Exception as exc:
        print(f"  {BAD} 失败: {exc}")
        fails += 1

    print()
    print(f"{OK} 全部通过" if not fails else f"{BAD} {fails} 项失败")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
