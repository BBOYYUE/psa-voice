"""语音网关配置。所有可调参数集中在这里，改配置不用碰业务代码。"""
from __future__ import annotations

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent


def _load_dotenv(path: Path) -> None:
    """极简 .env 读取，避免额外依赖。只在变量未设置时写入。"""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.strip()
        v = v.strip().strip('"').strip("'")
        os.environ.setdefault(k, v)


_load_dotenv(BASE_DIR / ".env")


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, "") or default)
    except ValueError:
        return default


# ---------- 服务监听 ----------
HOST = os.getenv("VG_HOST", "127.0.0.1")
PORT = _int("VG_PORT", 8650)

# ---------- 鉴权 ----------
# 客户端用 Authorization: Bearer <token> 调用
GATEWAY_TOKEN = os.getenv("VOICE_GATEWAY_TOKEN", "")

# ---------- ASR ----------
ASR_MODEL_DIR = Path(
    os.getenv(
        "VG_ASR_MODEL_DIR",
        str(BASE_DIR / "models" / "sherpa-onnx-paraformer-zh-small-2024-03-09"),
    )
)
ASR_NUM_THREADS = _int("VG_ASR_THREADS", 2)
ASR_MAX_CONCURRENCY = _int("VG_ASR_MAX_CONCURRENCY", 1)  # 2 核机器，串行最稳
ASR_TIMEOUT_S = _int("VG_ASR_TIMEOUT_S", 60)

# ---------- 音频约束 ----------
MAX_UPLOAD_BYTES = _int("VG_MAX_UPLOAD_BYTES", 25 * 1024 * 1024)  # 25MB
MAX_AUDIO_SECONDS = _int("VG_MAX_AUDIO_SECONDS", 120)
FFMPEG_BIN = os.getenv("VG_FFMPEG_BIN", "ffmpeg")
TARGET_SAMPLE_RATE = 16000
AUDIO_TEMP_DIR = Path(os.getenv("VG_AUDIO_TEMP_DIR", str(BASE_DIR / ".audio-tmp")))
# Normal requests delete their audio immediately; this is only crash recovery.
AUDIO_TEMP_TTL_S = max(300, _int("VG_AUDIO_TEMP_TTL_S", 1800))
AUDIO_CLEANUP_INTERVAL_S = max(10, _int("VG_AUDIO_CLEANUP_INTERVAL_S", 60))

# ---------- Hermes ----------
HERMES_BASE_URL = os.getenv("VG_HERMES_BASE_URL", "http://127.0.0.1:8643")
HERMES_API_KEY = (
    os.getenv("VG_HERMES_API_KEY", "")
    or os.getenv("HERMES_API_KEY", "")
)
HERMES_MODEL = os.getenv("VG_HERMES_MODEL", "hermes-agent")
HERMES_TIMEOUT_S = _int("VG_HERMES_TIMEOUT_S", 180)
MUSIC_HERMES_TIMEOUT_S = max(5, _int("VG_MUSIC_HERMES_TIMEOUT_S", 30))

# 车载场景的系统提示：只放「怎么念」的约束。
# 能力边界（没有工具、查不了实时数据）由 voice profile 的 SOUL.md 负责，不要在这里重复。
HERMES_SYSTEM_PROMPT = os.getenv(
    "VG_HERMES_SYSTEM_PROMPT",
    (
        "司机在用语音和你说话，你的回答会被直接念出来。"
        "一般不超过 80 个字，口语化，不要列表、标题、markdown 或表情符号。"
        "除非司机要求展开。"
    ),
)

# 会话连续性：同一个 device_id 保持同一段对话，方便"刚才说的那个"之类的追问
SESSION_ENABLED = os.getenv("VG_SESSION_ENABLED", "true").lower() in {"1", "true", "yes"}

# ---------- 日志 ----------
LOG_LEVEL = os.getenv("VG_LOG_LEVEL", "INFO")
