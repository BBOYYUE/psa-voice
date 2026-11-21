"""ASR：本地 sherpa-onnx + Paraformer 中文模型。

设计要点：
- 模型只加载一次，常驻内存（2 核机器，避免反复加载）
- 推理是 CPU 密集，用信号量串行化，避免多请求把机器打死
- 任何输入格式先过 ffmpeg 转成 16k 单声道 wav
"""
from __future__ import annotations

import asyncio
import logging
import subprocess
import tempfile
import time
import wave
from pathlib import Path
from typing import Optional, Tuple

import config
import temporary_audio

logger = logging.getLogger("vg.asr")

_recognizer = None
_load_lock = asyncio.Lock()
_sem = asyncio.Semaphore(config.ASR_MAX_CONCURRENCY)


class ASRError(RuntimeError):
    """ASR 环节的可预期失败（音频坏了、格式不支持等）。"""


def _pick_model_file(model_dir: Path) -> Path:
    for name in ("model.int8.onnx", "model.onnx", "model.fp32.onnx"):
        p = model_dir / name
        if p.exists():
            return p
    candidates = sorted(model_dir.glob("*.onnx"))
    if not candidates:
        raise ASRError(f"模型目录里没有 .onnx 文件: {model_dir}")
    return candidates[0]


def _build_recognizer():
    import sherpa_onnx

    model_dir = config.ASR_MODEL_DIR
    model_file = _pick_model_file(model_dir)
    tokens = model_dir / "tokens.txt"
    if not tokens.exists():
        raise ASRError(f"缺少 tokens.txt: {model_dir}")

    logger.info("加载 ASR 模型 %s (threads=%d)", model_file.name, config.ASR_NUM_THREADS)
    t0 = time.time()
    rec = sherpa_onnx.OfflineRecognizer.from_paraformer(
        paraformer=str(model_file),
        tokens=str(tokens),
        num_threads=config.ASR_NUM_THREADS,
        sample_rate=config.TARGET_SAMPLE_RATE,
        feature_dim=80,
        decoding_method="greedy_search",
        provider="cpu",
    )
    logger.info("ASR 模型加载完成，用时 %.2fs", time.time() - t0)
    return rec


async def get_recognizer():
    global _recognizer
    if _recognizer is not None:
        return _recognizer
    async with _load_lock:
        if _recognizer is None:
            _recognizer = await asyncio.to_thread(_build_recognizer)
    return _recognizer


def warmup() -> None:
    """同步预热，供启动时调用（可选）。"""
    global _recognizer
    if _recognizer is None:
        _recognizer = _build_recognizer()


# ---------------------------------------------------------------- 音频转码

def _ffmpeg_to_wav(src: Path, dst: Path) -> None:
    cmd = [
        config.FFMPEG_BIN, "-y", "-loglevel", "error",
        "-i", str(src),
        "-vn",
        "-ac", "1",
        "-ar", str(config.TARGET_SAMPLE_RATE),
        "-f", "wav",
        "-t", str(config.MAX_AUDIO_SECONDS),
        str(dst),
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=config.ASR_TIMEOUT_S)
    except subprocess.TimeoutExpired as exc:
        raise ASRError("音频转码超时") from exc
    except FileNotFoundError as exc:
        raise ASRError(f"找不到 ffmpeg: {config.FFMPEG_BIN}") from exc

    if proc.returncode != 0:
        msg = (proc.stderr or b"").decode("utf-8", "ignore").strip()[:300]
        raise ASRError(f"音频无法解码（格式不支持或文件损坏）: {msg}")


def _read_wav(path: Path) -> Tuple[list, int]:
    import array

    with wave.open(str(path), "rb") as wf:
        if wf.getnchannels() != 1 or wf.getsampwidth() != 2:
            raise ASRError("转码后音频格式异常")
        sr = wf.getframerate()
        frames = wf.readframes(wf.getnframes())
    samples = array.array("h")
    samples.frombytes(frames)
    return [s / 32768.0 for s in samples], sr


# ---------------------------------------------------------------- 主入口

async def transcribe_file(src: Path) -> Tuple[str, int, float]:
    """把任意音频文件转成文字。

    返回 (text, elapsed_ms, duration_s)
    """
    async with _sem:
        rec = await get_recognizer()

        with temporary_audio.workspace("decode-") as tmp:
            wav_path = tmp / "audio.wav"
            await asyncio.to_thread(_ffmpeg_to_wav, src, wav_path)
            samples, sr = await asyncio.to_thread(_read_wav, wav_path)

        if not samples:
            raise ASRError("音频为空")

        duration_s = len(samples) / float(sr or config.TARGET_SAMPLE_RATE)
        if duration_s < 0.25:
            return "", 0, duration_s

        # Silence can otherwise produce plausible-looking ASR hallucinations.
        # -65 dBFS is far below the observed in-car speech (~-43 dBFS).
        if max(abs(s) for s in samples) < 64 / 32768 or sum(s * s for s in samples) / len(samples) < 10 ** (-65 / 10):
            return "", 0, duration_s

        def _run():
            stream = rec.create_stream()
            stream.accept_waveform(sample_rate=sr, waveform=samples)
            rec.decode_stream(stream)
            return (stream.result.text or "").strip()

        t0 = time.time()
        text = await asyncio.to_thread(_run)
        elapsed_ms = int((time.time() - t0) * 1000)
        logger.info("ASR 完成 %.1fs 音频 → %dms, %d 字", duration_s, elapsed_ms, len(text))
        return text, elapsed_ms, duration_s


async def transcribe_bytes(data: bytes, suffix: str = ".bin") -> Tuple[str, int, float]:
    with temporary_audio.workspace() as tmp:
        tmp_path = tmp / ("input" + suffix)
        tmp_path.write_bytes(data)
        return await transcribe_file(tmp_path)
