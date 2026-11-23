"""Online PCM decoding. Separate models are required; never falls back to batch ASR."""
import array
import os
import sys
import threading
from pathlib import Path

import config

_lock = threading.Lock()
_recognizer = None


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

    def feed(self, data):
        pcm = array.array('h')
        pcm.frombytes(data)
        if sys.byteorder != 'little':
            pcm.byteswap()
        self.samples += len(pcm)
        self.stream.accept_waveform(16000, [x / 32768.0 for x in pcm])
        while self.rec.is_ready(self.stream):
            self.rec.decode_stream(self.stream)
        text = self.rec.get_result(self.stream).strip()
        return text, self.rec.is_endpoint(self.stream) or self.samples >= 16000 * 20

    def reset(self):
        self.stream = self.rec.create_stream()
        self.samples = 0
