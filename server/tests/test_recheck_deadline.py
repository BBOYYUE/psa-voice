import asyncio
import threading
import unittest
from unittest.mock import patch
import asr

class RecheckDeadlineTest(unittest.IsolatedAsyncioTestCase):
    async def test_timeout_keeps_recognizer_reserved_until_worker_finishes(self):
        release = threading.Event()
        entered = threading.Event()
        sem = asyncio.Semaphore(1)
        seen = []
        def decode(rec, samples, rate):
            entered.set()
            release.wait(2)
            seen.extend(samples)
            return 'late result'
        pcm = bytearray(b'\x01\x00' * 8)
        with patch.object(asr, '_recognizer', object()), patch.object(asr, '_sem', sem), patch.object(asr, '_decode_waveform', decode), patch.object(asr, 'LIVE_RECHECK_TIMEOUT_S', .05):
            try:
                self.assertEqual(await asr.recheck_live_pcm(pcm), '')
                self.assertTrue(entered.is_set())
                self.assertTrue(sem.locked())
                pcm.clear()
                self.assertEqual(await asr.recheck_live_pcm(bytearray()), '')
            finally:
                release.set()
                for _ in range(100):
                    if not sem.locked(): break
                    await asyncio.sleep(.01)
            self.assertFalse(sem.locked())
            self.assertEqual(len(seen), 8)

    async def test_cancellation_returns_without_waiting_for_worker(self):
        release = threading.Event()
        entered = threading.Event()
        sem = asyncio.Semaphore(1)
        def decode(*args):
            entered.set()
            release.wait(2)
            return 'late'
        with patch.object(asr, '_recognizer', object()), patch.object(asr, '_sem', sem), patch.object(asr, '_decode_waveform', decode):
            task = asyncio.create_task(asr.recheck_live_pcm(bytearray(b'\x01\x00')))
            try:
                for _ in range(100):
                    if entered.is_set(): break
                    await asyncio.sleep(.01)
                self.assertTrue(entered.is_set())
                task.cancel()
                with self.assertRaises(asyncio.CancelledError): await asyncio.wait_for(task,.2)
                self.assertTrue(sem.locked())
            finally:
                release.set()
                for _ in range(100):
                    if not sem.locked(): break
                    await asyncio.sleep(.01)
            self.assertFalse(sem.locked())
