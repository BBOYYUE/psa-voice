import asyncio
import os
import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
import app
import config

class Decoder:
    samples = 0
    def feed(self, data):
        self.pcm = bytearray(data)
        return data.decode('utf-16le'), True
    def reset(self):
        pass

class LiveTest(unittest.TestCase):
    def setUp(self):
        self.patches = [patch.dict(os.environ, {'VG_LIVE_ENABLED': 'true'}),
                        patch.object(config, 'GATEWAY_TOKEN', 'test-only'),
                        patch('live_asr.Decoder', Decoder)]
        for p in self.patches: p.start()
        self.client = TestClient(app.app)
    def tearDown(self):
        for p in reversed(self.patches): p.stop()
    def connect(self):
        return self.client.websocket_connect('/v1/live', headers={'Authorization': 'Bearer test-only'})
    def start(self, ws):
        ws.send_json({'event':'start','format':'pcm_s16le','sample_rate':16000,'channels':1,'client_version':'0.5.0'})
        self.assertEqual(ws.receive_json()['event'], 'ready')
    def until(self, ws, kind):
        for _ in range(12):
            event = ws.receive_json()
            if event['event'] == kind: return event
        self.fail('Missing '+kind)
    def test_auth_and_disabled(self):
        with self.assertRaises(WebSocketDisconnect):
            with self.client.websocket_connect('/v1/live'): pass
        with patch.dict(os.environ, {'VG_LIVE_ENABLED':'false'}):
            with self.assertRaises(WebSocketDisconnect):
                with self.connect(): pass
    def test_action_ack_resume_exit(self):
        with self.connect() as ws:
            self.start(ws)
            ws.send_bytes('下一首'.encode('utf-16le'))
            result = self.until(ws, 'result')
            self.assertEqual(result['action']['type'], 'music.next')
            ws.send_json({'event':'resume','turn_id':1})
            self.assertEqual(ws.receive_json()['code'], 'not_ready_to_resume')
            ws.send_json({'event':'execution_result','turn_id':999,'status':'sent'})
            self.assertEqual(ws.receive_json()['code'], 'unexpected_execution_result')
            ws.send_json({'event':'execution_result','turn_id':result['turn_id'],'status':'sent'})
            self.until(ws, 'execution_ack')
            ws.send_json({'event':'resume','turn_id':1})
            self.until(ws, 'listening')
            ws.send_bytes('结束对话'.encode('utf-16le'))
            self.assertEqual(self.until(ws, 'ended')['reason'], 'voice_exit')

    def test_semantic_exit_ends_session_without_speech_or_action(self):
        decision = {'decision': 'exit', 'action': None, 'answer': ''}
        for phrase in ('没事儿', '退下吧'):
            with self.subTest(phrase=phrase), patch('live_agent.run', return_value=decision) as agent:
                with self.connect() as ws:
                    self.start(ws)
                    ws.send_bytes(phrase.encode('utf-16le'))
                    events = []
                    for _ in range(10):
                        events.append(ws.receive_json())
                        if events[-1]['event'] == 'ended':
                            break
                    self.assertEqual(events[-1]['event'], 'ended')
                    self.assertEqual(events[-1]['reason'], 'voice_exit')
                    self.assertEqual(events[-1]['state'], 'ended')
                    self.assertNotIn('result', [event['event'] for event in events])
                    self.assertNotIn('speech', [event['event'] for event in events])
                self.assertEqual(agent.await_args.args[0], phrase)

    def test_final_transcript_checks_repetitions_with_second_recognizer(self):
        cases = [
            ('播播放黑色毛衣', '播放黑色毛衣', '播放黑色毛衣'),
            ('播放黑色毛毛', '播放黑色毛', '播放黑色毛'),
            ('播播放黑色毛毛毛衣', '播放黑色毛衣', '播放黑色毛衣'),
            ('播播放黑色毛毛毛衣', '播放白色毛衣', '播播放黑色毛毛毛衣'),
            ('播播放黑色毛毛毛衣', '播放黑色色毛衣', '播播放黑色毛毛毛衣'),
            ('播播放黑色毛毛毛衣', '', '播播放黑色毛毛毛衣'),
            ('播放甜蜜蜜', '播放甜蜜蜜', '播放甜蜜蜜'),
            ('帮我念念一下沁庆沁沁沁园园春春雪', '帮我念一下沁园春雪', '帮我念一下沁园春雪'),
            ('介绍一下沁沁沁沁沁园原春雪这首词', '介绍一下沁园春雪这首词', '介绍一下沁园春雪这首词'),
            ('帮我念念一下沁庆沁沁沁园园春春雪', '播放沁园春雪', '帮我念念一下沁庆沁沁沁园园春春雪'),
            ('问问问一下怎么暂停播放这首诗', '问一下怎么播放这首诗', '问问问一下怎么暂停播放这首诗'),
            ('哈哈哈', '哈哈哈', '哈哈哈'),
        ]
        for online, offline, expected in cases:
            with self.subTest(online=online, offline=offline):
                reply = {'decision': 'answer', 'action': None, 'answer': '好的。'}
                with patch('asr.recheck_live_pcm', return_value=offline) as recheck, patch('live_agent.run', return_value=reply):
                    with self.connect() as ws:
                        self.start(ws)
                        ws.send_bytes(online.encode('utf-16le'))
                        partial = self.until(ws, 'transcript')
                        final = self.until(ws, 'transcript')
                        self.assertFalse(partial['final'])
                        self.assertEqual(partial['text'], online)
                        self.assertTrue(final['final'])
                        self.assertEqual(final['text'], expected)
                        ws.send_json({'event': 'cancel'})
                        self.until(ws, 'ended')
                    self.assertEqual(recheck.await_count, 1)
    def test_chat_context_and_echo_discard(self):
        seen=[]
        async def agent(text, history, progress, speech=None):
            seen.append((text,list(history)))
            return {'decision':'answer','action':None,'answer':'你好。'}
        with patch('live_agent.run', side_effect=agent):
            with self.connect() as ws:
                self.start(ws)
                ws.send_bytes('你好'.encode('utf-16le'))
                self.until(ws,'result')
                ws.send_bytes('下一首'.encode('utf-16le'))
                ws.send_json({'event':'resume','turn_id':1})
                self.until(ws,'listening')
                ws.send_bytes('刚才说什么'.encode('utf-16le'))
                self.until(ws,'result')
                ws.send_json({'event':'cancel'})
                self.until(ws,'ended')
        self.assertEqual(len(seen),2)
        self.assertEqual(seen[1][1][0]['content'],'你好')
    def test_cancel_agent(self):
        cancelled=[]
        async def slow(*args):
            try: await asyncio.sleep(30)
            finally: cancelled.append(True)
        with patch('live_agent.run',side_effect=slow):
            with self.connect() as ws:
                self.start(ws)
                ws.send_bytes('给我推荐首歌'.encode('utf-16le'))
                self.until(ws,'stage')
                ws.send_json({'event':'cancel'})
                self.assertEqual(self.until(ws,'ended')['reason'],'cancelled')
        self.assertTrue(cancelled)
    def test_bad_frame_and_setup_failure_release_slot(self):
        with patch('live_asr.Decoder',side_effect=RuntimeError('no model')):
            with self.connect() as ws:
                ws.send_json({'event':'start','format':'pcm_s16le','sample_rate':16000,'channels':1})
                self.assertEqual(ws.receive_json()['code'],'live_unavailable')
        with self.connect() as ws:
            self.start(ws)
            ws.send_bytes(b'\x00')
            self.assertEqual(ws.receive_json()['code'],'invalid_audio_frame')

    def test_sequence_state_and_first_speech_metrics(self):
        async def agent(text, history, progress, speech):
            await speech('你好。')
            return {'decision':'answer','action':None,'answer':'你好。'}
        with patch('live_agent.run',side_effect=agent):
            with self.connect() as ws:
                self.start(ws)
                ws.send_bytes('你好'.encode('utf-16le'))
                events=[]
                while True:
                    item=ws.receive_json(); events.append(item)
                    if item['event']=='result': break
                self.assertEqual([e['seq'] for e in events],sorted({e['seq'] for e in events}))
                self.assertEqual(events[-1]['state'],'awaiting_resume')
                self.assertEqual(events[-1]['speech_count'],1)
                self.assertIsNotNone(events[-1]['first_speech_ms'])
                ws.send_json({'event':'resume','turn_id':99})
                self.assertEqual(ws.receive_json()['code'],'not_ready_to_resume')
                ws.send_json({'event':'cancel'})
                self.assertEqual(self.until(ws,'ended')['state'],'ended')

    def test_connection_idle_and_execution_deadlines(self):
        import live_session
        for limit,reason,action in [('PACKET_TIMEOUT_S','connection_timeout',False),
                                    ('IDLE_TIMEOUT_S','idle_timeout',False),
                                    ('EXECUTION_TIMEOUT_S','execution_timeout',True)]:
            # Leave room for TestClient and the server to exchange frames on Windows.
            with self.subTest(limit=limit),patch.object(live_session,limit,.2),patch.object(live_session,'TICK_S',.05):
                with self.connect() as ws:
                    self.start(ws)
                    if action:
                        ws.send_bytes('下一首'.encode('utf-16le'))
                        self.assertEqual(self.until(ws,'result')['state'],'awaiting_execution')
                    self.assertEqual(self.until(ws,'ended')['reason'],reason)

    def test_agent_timeout_can_resume_and_cancel(self):
        import live_session
        async def slow(*args): await asyncio.sleep(30)
        with patch('live_agent.run',side_effect=slow),patch.object(live_session,'AGENT_TIMEOUT_S',.02):
            with self.connect() as ws:
                self.start(ws)
                ws.send_bytes('介绍一下这个组合'.encode('utf-16le'))
                error=self.until(ws,'error')
                self.assertEqual(error['code'],'agent_timeout')
                self.assertTrue(error['recoverable'])
                ws.send_json({'event':'resume','turn_id':1})
                self.until(ws,'listening')
                ws.send_json({'event':'cancel'})
                self.until(ws,'ended')

    def test_second_connection_rejected_and_slot_released(self):
        with self.connect() as first:
            self.start(first)
            with self.assertRaises(WebSocketDisconnect):
                with self.connect(): pass
            first.send_json({'event':'cancel'})
            self.until(first,'ended')
        with self.connect() as next_session:
            self.start(next_session)
            next_session.send_json({'event':'cancel'})
            self.until(next_session,'ended')

class AgentTest(unittest.IsolatedAsyncioTestCase):
    async def invoke(self, payload):
        import json
        from unittest.mock import AsyncMock
        import live_agent
        class Response:
            async def __aenter__(self): return self
            async def __aexit__(self,*args): pass
            def raise_for_status(self): pass
            async def aiter_lines(self):
                yield ': heartbeat'
                for offset in range(0,len(payload),7):
                    yield 'data: '+json.dumps({'choices':[{'delta':{'content':payload[offset:offset+7]}}]})
                yield 'data: [DONE]'
        class Client(Response):
            def __init__(self,**kwargs): pass
            def stream(self,*args,**kwargs): return Response()
        progress, speech = AsyncMock(), AsyncMock()
        with patch('live_agent.httpx.AsyncClient',Client):
            result=await live_agent.run('你好',[],progress,speech)
        return result, speech
    async def test_incremental_sentence_and_final(self):
        result, speech=await self.invoke('{"event":"speech","text":"你好。"}\n{"decision":"answer","action":null,"answer":"你好。"}\n')
        self.assertEqual(result['answer'],'你好。')
        speech.assert_awaited_once_with('你好。')
    async def test_bad_action_and_truncated_output_fail_closed(self):
        for raw in ('{"decision":"execute","action":{"type":"shell.exec","app":"qqmusic"}}',
                    '{"decision":"answer",',
                    '{"decision":"execute","action":{"type":"music.search_play","app":"qqmusic","query":""}}'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                await self.invoke(raw)

    async def test_pretty_and_fenced_json_and_arbitrary_boundaries(self):
        for raw in ('{\n "decision":"answer",\n "action":null,\n "answer":"你好。"\n}',
                    '```json\n{"decision":"answer","action":null,"answer":"你好。"}\n```'):
            with self.subTest(raw=raw):
                result,speech=await self.invoke(raw)
                self.assertEqual(result['answer'],'你好。')
                speech.assert_awaited_once_with('你好。')

    async def test_trailing_garbage_and_incomplete_fence_fail_closed(self):
        for raw in ('{"decision":"answer","action":null,"answer":"你好。"}garbage',
                    '```json\n{"decision":"answer","action":null,"answer":"你好。"}'):
            with self.subTest(raw=raw),self.assertRaises(ValueError):
                await self.invoke(raw)

    async def test_single_json_streams_complete_sentences(self):
        import json
        result,speech=await self.invoke(json.dumps({'decision':'answer','action':None,'answer':'你好。今天想听什么？'}))
        self.assertEqual(result['answer'],'你好。今天想听什么？')
        self.assertEqual([call.args[0] for call in speech.await_args_list],['你好。','今天想听什么？'])

    async def test_action_query_never_becomes_partial_speech(self):
        import json
        result,speech=await self.invoke(json.dumps({'decision':'execute','action':{
            'type':'music.search_play','app':'qqmusic','query':'T-ARA "answer": "你好。"'},'answer':''}))
        self.assertEqual(result['decision'],'execute')
        speech.assert_not_awaited()

    async def test_exit_requires_empty_answer_and_no_action_or_streamed_speech(self):
        import json
        decision = {'decision': 'exit', 'action': None, 'answer': ''}
        result, speech = await self.invoke(json.dumps(decision))
        self.assertEqual(result, decision)
        speech.assert_not_awaited()
        for raw in (
            json.dumps({**decision, 'answer': '再见。'}),
            json.dumps({**decision, 'action': {'type': 'music.close', 'app': 'qqmusic'}}),
            '{"event":"speech","text":"再见。"}\n' + json.dumps(decision),
        ):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                await self.invoke(raw)

    async def test_partial_answer_unicode_and_escape_boundaries(self):
        import json
        import live_agent
        value='你好。\\测试"引用"？'
        raw=json.dumps({'decision':'answer','action':None,'answer':value})
        previous=''
        for end in range(1,len(raw)+1):
            text=live_agent.answer_prefix(raw[:end])
            if text is not None:
                self.assertTrue(text.startswith(previous))
                previous=text
        self.assertEqual(previous,value)

    async def test_malformed_speech_is_not_repaired_into_an_action(self):
        with self.assertRaises(ValueError):
            await self.invoke('{"event":"speech":"坏格式。"}\n{"decision":"answer","action":null,"answer":"坏格式。"}')
