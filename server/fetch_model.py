#!/usr/bin/env python3
"""从 ModelScope 下载 sherpa-onnx paraformer 中文模型（比 GitHub 快 400 倍）。"""
import sys
import time
from pathlib import Path

import requests

REPO = "pengzhendong/sherpa-onnx-paraformer-zh-small"
BASE = f"https://modelscope.cn/models/{REPO}/resolve/master"
FILES = ["model.int8.onnx", "tokens.txt"]
DEST = Path("/home/sollado/projects/voice-gateway/models/sherpa-onnx-paraformer-zh-small-2024-03-09")


def download(name: str) -> bool:
    url = f"{BASE}/{name}"
    dest = DEST / name
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"→ {name}")
    t0 = time.time()
    with requests.get(url, stream=True, timeout=60) as r:
        if r.status_code != 200:
            print(f"  HTTP {r.status_code}: {r.text[:200]}")
            return False
        total = int(r.headers.get("content-length") or 0)
        got = 0
        with open(tmp, "wb") as fh:
            for chunk in r.iter_content(1 << 16):
                fh.write(chunk)
                got += len(chunk)
        tmp.rename(dest)
    dt = time.time() - t0
    print(f"  {got/1e6:.1f}MB / {dt:.1f}s = {got/dt/1e6:.1f} MB/s → {dest}")
    return total in (0, got)


def main() -> int:
    DEST.mkdir(parents=True, exist_ok=True)
    ok = all([download(n) for n in FILES])
    print("\n目录内容:")
    for p in sorted(DEST.iterdir()):
        print(f"  {p.name:24s} {p.stat().st_size/1e6:7.2f} MB")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
