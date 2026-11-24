"""Online PCM decoding. Separate models are required; never falls back to batch ASR."""
import array
import logging
import os
import re
import sys
import threading
from difflib import SequenceMatcher
from pathlib import Path

import asr
import config

_lock = threading.Lock()
_recognizer = None
_repeated_han = re.compile(r'([\u3400-\u9fff])\1+')
_question = re.compile(r'^(?:请你?|帮我|给我|你能)?(朗读|背诵|念|读|介绍|解释|讲讲|说说|问)')
_action_or_exit = re.compile(r'播放|暂停|切歌|[上下]一首|打开|关闭|退出|结束|不聊|别|不要|不用|停止|导航')
logger = logging.getLogger(__name__)


def _same_read_only_request(online: str, offline: str) -> bool:
    first = _question.match(online)
    second = _question.match(offline)
    return bool(
        first and second and first.group(1) == second.group(1)
        and not _action_or_exit.search(online)
        and not _action_or_exit.search(offline)
        and SequenceMatcher(None, online, offline, autojunk=False).ratio() >= .6
    )


async def refine_repetitions(text: str, pcm: bytearray) -> str:
    if not _repeated_han.search(text):
        return text
    try:
        candidate = await asr.recheck_live_pcm(pcm)
    except Exception as exc:
        logger.warning('Live ASR repeat check failed: %s', type(exc).__name__)
        return text
    if not candidate or len(candidate) >= len(text):
        return text
    original_words = _repeated_han.sub(r'\1', text)
    candidate_words = _repeated_han.sub(r'\1', candidate)
    if candidate_words != original_words:
        # Recover homophones in questions. Commands and exits only allow repeat removal.
        if not _repeated_han.search(candidate) and _same_read_only_request(original_words, candidate_words):
            return candidate
        return text
    # Allow removals only. Preserve deliberate repeats the two recognizers agree on.
    original = iter(text)
    return candidate if all(char in original for char in candidate) else text


def enabled():
    return os.getenv('VG_LIVE_ENABLED', '').lower() == 'true'


def status():
    paths = [os.getenv('VG_LIVE_ASR_' + key, '')
             for key in ('ENCODER', 'DECODER', 'JOINER', 'TOKENS')]
    return {'enabled': enabled(), 'ready': _recognizer is not None,
            'configured': all(path and Path(path).is_file() for path in paths)}


def recognizer():
    global _recognizer
    with _lock:
        if _recognizer is None:
            import sherpa_onnx
            paths = {key: os.getenv('VG_LIVE_ASR_' + key.upper(), '')
                     for key in ('encoder', 'decoder', 'joiner', 'tokens')}
            if not all(value and Path(value).is_file() for value in paths.values()):
                raise RuntimeError('Configure VG_LIVE_ASR_ENCODER/DECODER/JOINER/TOKENS with online transducer files')
            _recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
                **paths, num_threads=config.ASR_NUM_THREADS, sample_rate=16000,
                feature_dim=80, decoding_method='greedy_search', provider='cpu',
                enable_endpoint_detection=True,
                rule1_min_trailing_silence=2.4,
                rule2_min_trailing_silence=max(0.3, min(2.0, float(os.getenv('VG_LIVE_ASR_SILENCE_S', '0.8')))),
                rule3_min_utterance_length=20,
            )
        return _recognizer


class Decoder:
    def __init__(self):
        self.rec = recognizer()
        self.stream = self.rec.create_stream()
        self.samples = 0
        self.pcm = bytearray()

    def feed(self, data):
        pcm = array.array('h')
        pcm.frombytes(data)
        if sys.byteorder != 'little':
            pcm.byteswap()
        self.samples += len(pcm)
        self.pcm.extend(data)
        self.stream.accept_waveform(16000, [x / 32768.0 for x in pcm])
        while self.rec.is_ready(self.stream):
            self.rec.decode_stream(self.stream)
        text = self.rec.get_result(self.stream).strip()
        return text, self.rec.is_endpoint(self.stream) or self.samples >= 16000 * 20

    def reset(self):
        self.stream = self.rec.create_stream()
        self.samples = 0
        self.pcm.clear()
