import asyncio
import json
import os
import tempfile
import time
import unittest
import wave
from pathlib import Path
from unittest.mock import AsyncMock, patch

import httpx

import app
import asr
import config
import hermes_client
import music
import temporary_audio


class IntentContractTest(unittest.TestCase):
    def test_supported_actions_and_non_execution(self):
        for kind in sorted(music.ACTIONS):
            action = {"type": kind, "app": "qqmusic"}
            if kind in {"music.search", "music.search_play"}:
                action["query"] = "周杰伦 晴天"
            result = music.parse_result(json.dumps({"decision": "execute", "action": action, "answer": "已经播放"}))
            self.assertEqual(result["action"], action)
            self.assertEqual(result["answer"], "")
        for decision in ("clarify", "unsupported"):
            self.assertIsNone(music.parse_result(json.dumps({"decision": decision, "action": None, "answer": "请说出歌名"}))["action"])

    def test_invalid_model_outputs_fail_closed(self):
        cases = ["not json", "[]", '{"decision":[]}', '{"decision":"execute","action":null}']
        actions = [{"type": "shell.exec", "app": "qqmusic"},
                   {"type": "music.pause", "app": "other"},
                   {"type": "music.search_play", "app": "qqmusic", "query": ""},
                   {"type": "music.pause", "app": "qqmusic", "query": "injected"},
                   {"type": "music.search", "app": "qqmusic", "query": "晴天", "command": "anything"}]
        cases += [json.dumps({"decision": "execute", "action": action}) for action in actions]
        for raw in cases:
            with self.subTest(raw=raw), self.assertRaises(music.IntentError):
                music.parse_result(raw)


class AudioCleanupTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "audio"
        self.patch = patch.object(config, "AUDIO_TEMP_DIR", self.root)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.tmp.cleanup()

    async def test_audio_removed_on_success_failure_and_cancellation(self):
        async def success(path):
            self.assertEqual(path.read_bytes(), b"audio")
            return "晴天", 1, 1.0
        with patch.object(asr, "transcribe_file", success):
            self.assertEqual((await asr.transcribe_bytes(b"audio", ".wav"))[0], "晴天")
        self.assertEqual(list(self.root.iterdir()), [])
        with patch.object(asr, "transcribe_file", AsyncMock(side_effect=asr.ASRError("bad audio"))):
            with self.assertRaises(asr.ASRError):
                await asr.transcribe_bytes(b"audio", ".wav")
        self.assertEqual(list(self.root.iterdir()), [])
        started = asyncio.Event()
        async def pending(path):
            self.assertTrue(path.exists())
            started.set()
            await asyncio.sleep(60)
        with patch.object(asr, "transcribe_file", pending):
            task = asyncio.create_task(asr.transcribe_bytes(b"audio", ".wav"))
            await started.wait()
            task.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await task
        self.assertEqual(list(self.root.iterdir()), [])

    async def test_crash_cleanup_preserves_active_unknown_and_external_files(self):
        root = temporary_audio.root()
        stale = root / "request-stale"
        stale.mkdir()
        (stale / "input.wav").write_bytes(b"audio")
        old = time.time() - config.AUDIO_TEMP_TTL_S - 10
        os.utime(stale, (old, old))
        recent = root / "decode-recent"
        recent.mkdir()
        unknown = root / "other-directory"
        unknown.mkdir()
        os.utime(unknown, (old, old))
        external = Path(self.tmp.name) / "outside"
        external.mkdir()
        (external / "keep").write_text("keep")
        (root / "request-link").symlink_to(external, target_is_directory=True)
        with temporary_audio.workspace() as active:
            os.utime(active, (old, old))
            self.assertEqual(temporary_audio.cleanup_stale(), 1)
            self.assertTrue(active.exists())
        self.assertFalse(stale.exists())
        self.assertTrue(recent.exists())
        self.assertTrue(unknown.exists())
        self.assertTrue((external / "keep").exists())

    async def test_digital_silence_never_reaches_the_recognizer(self):
        def wav_copy(src, dst):
            with wave.open(str(dst), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(16000)
                output.writeframes(bytes(16000))
        rec = unittest.mock.MagicMock()
        with patch.object(asr, "get_recognizer", AsyncMock(return_value=rec)), patch.object(asr, "_ffmpeg_to_wav", wav_copy):
            text, elapsed, duration = await asr.transcribe_bytes(b"fixture", ".wav")
        self.assertEqual((text, elapsed, duration), ("", 0, 0.5))
        rec.create_stream.assert_not_called()
        self.assertEqual(list(self.root.iterdir()), [])


class HttpContractTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.patchers = [patch.object(config, "GATEWAY_TOKEN", "test-token"),
                         patch.object(config, "AUDIO_TEMP_DIR", Path(self.tmp.name) / "audio")]
        for item in self.patchers:
            item.start()
        self.client = httpx.AsyncClient(transport=httpx.ASGITransport(app=app.app), base_url="http://test")
        self.headers = {"Authorization": "Bearer test-token"}

    async def asyncTearDown(self):
        await self.client.aclose()
        for item in reversed(self.patchers):
            item.stop()
        self.tmp.cleanup()

    async def test_music_context_and_legacy_response(self):
        raw = json.dumps({"decision": "execute", "action": {"type": "music.search_play", "app": "qqmusic", "query": "周杰伦 晴天"}})
        with patch.object(hermes_client, "ask", return_value=(raw, 12)) as ask:
            response = await self.client.post("/v1/text", headers=self.headers,
                json={"text": "周杰伦的晴天", "request_id": "car-001", "device_id": "car", "context": {"scene": "music"}})
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["schema"], "v1")
        self.assertEqual(body["request_id"], "car-001")
        self.assertEqual(body["transcript"], "周杰伦的晴天")
        self.assertEqual(body["action"]["query"], "周杰伦 晴天")
        self.assertIsNone(ask.call_args.kwargs["session_id"])
        with patch.object(hermes_client, "ask", return_value=("你好", 1)):
            response = await self.client.post("/v1/text", headers=self.headers, json={"text": "你好"})
        self.assertEqual(response.json()["schema"], "v0")
        self.assertEqual(response.json()["answer"], "你好")

    async def test_audio_multipart_and_cleanup_before_hermes(self):
        async def transcribe_file(path):
            self.assertTrue(path.exists())
            return "下一首", 2, 1.0
        def answer(*args, **kwargs):
            self.assertEqual(list(config.AUDIO_TEMP_DIR.iterdir()), [])
            return json.dumps({"decision": "execute", "action": {"type": "music.next", "app": "qqmusic"}}), 3
        with patch.object(asr, "transcribe_file", transcribe_file), patch.object(hermes_client, "ask", answer):
            response = await self.client.post("/v1/voice", headers=self.headers,
                files={"file": ("record.wav", b"audio", "audio/wav")},
                data={"scene": "music", "trigger": "music_key_long_press", "request_id": "audio-001"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["action"]["type"], "music.next")
        self.assertEqual(response.json()["meta"]["duration_s"], 1.0)

    async def test_errors_do_not_return_an_action_or_leave_audio(self):
        with patch.object(asr, "transcribe_file", AsyncMock(side_effect=asr.ASRError("broken"))):
            response = await self.client.post("/v1/voice?scene=music", headers=self.headers, content=b"bad",)
        self.assertEqual(response.status_code, 422)
        self.assertIsNone(response.json()["action"])
        self.assertEqual(list(config.AUDIO_TEMP_DIR.iterdir()), [])
        with patch.object(hermes_client, "ask", return_value=("not json", 1)):
            response = await self.client.post("/v1/text", headers=self.headers, json={"text": "晴天", "scene": "music"})
        self.assertEqual(response.status_code, 502)
        self.assertIsNone(response.json()["action"])

    async def test_validation_auth_and_upload_limit(self):
        for body in ([], {"text": 42}, {"text": "下一首", "scene": []}, {"text": "下一首", "scene": "music", "preferred_app": "unknown"}):
            response = await self.client.post("/v1/text", headers=self.headers, json=body)
            self.assertEqual(response.status_code, 400)
        self.assertEqual((await self.client.post("/v1/text", json={"text": "下一首"})).status_code, 401)
        with patch.object(config, "MAX_UPLOAD_BYTES", 8):
            response = await self.client.post("/v1/transcribe", headers=self.headers, content=b"a" * 9)
            self.assertEqual(response.status_code, 413)
            response = await self.client.post("/v1/voice", headers=self.headers, files={"file": ("x.wav", b"a" * 9)})
            self.assertEqual(response.status_code, 413)
            async def chunks():
                yield b'--x\r\nContent-Disposition: form-data; name="file"; filename="x.wav"\r\nContent-Type: audio/wav\r\n\r\n'
                for _ in range(5):
                    yield b"a" * 20000
                yield b'\r\n--x--\r\n'
            response = await self.client.post("/v1/voice", headers={**self.headers, "Content-Type": "multipart/form-data; boundary=x"}, content=chunks())
            self.assertEqual(response.status_code, 413)

class MusicLatencyTest(unittest.TestCase):
    def setUp(self):
        music._cache.clear()

    def test_exact_controls_bypass_model_but_compound_requests_do_not(self):
        with patch.object(hermes_client, "ask") as ask:
            for text, kind in [("暂停一下", "pause"), ("继续播放", "resume"), ("下一首", "next"), ("上一首", "previous")]:
                result, elapsed = music.analyze(text)
                self.assertEqual(result["action"]["type"], "music." + kind)
                self.assertEqual(elapsed, 0)
            ask.assert_not_called()
        raw = json.dumps({"decision": "unsupported", "action": None, "answer": "暂不支持"})
        with patch.object(hermes_client, "ask", return_value=(raw, 7)) as ask:
            music.analyze("不要暂停，播放晴天")
            music.analyze("不要暂停，播放晴天")
            self.assertEqual(ask.call_count, 2)

    def test_success_cache_is_bounded_expires_and_does_not_share_mutations(self):
        raw = json.dumps({"decision": "execute", "action": {"type": "music.search_play", "app": "qqmusic", "query": "晴天"}, "answer": ""})
        with patch.object(hermes_client, "ask", return_value=(raw, 12)) as ask, patch.object(music.time, "monotonic", return_value=100):
            first, _ = music.analyze("缓存测试歌曲")
            first["action"]["query"] = "污染"
            hit, elapsed = music.analyze("缓存测试歌曲")
            self.assertEqual(hit["action"]["query"], "晴天")
            self.assertEqual(elapsed, 0)
            self.assertEqual(ask.call_count, 1)
        with patch.object(hermes_client, "ask", return_value=(raw, 12)) as ask, patch.object(music.time, "monotonic", return_value=701):
            music.analyze("缓存测试歌曲")
            ask.assert_called_once()
            for i in range(music._CACHE_LIMIT + 2):
                music.analyze("缓存边界" + str(i))
            self.assertEqual(len(music._cache), music._CACHE_LIMIT)


if __name__ == "__main__":
    unittest.main()
