package com.sollado.psavoice;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LiveConversationTest {
    private static final String SID = "0123456789abcdef0123456789abcdef";
    private static JSONObject event(String event, int seq) throws Exception {
        return new JSONObject().put("event", event).put("seq", seq).put("session_id", SID).put("state", "listening");
    }
    @Test public void connectionOrderIdentityAndTerminalAreStrict() throws Exception {
        LiveProtocol protocol = new LiveProtocol();
        protocol.accept(event("ready", 1));
        assertThrows(Exception.class, () -> protocol.accept(event("ready", 2)));
        assertThrows(Exception.class, () -> protocol.accept(event("heartbeat", 1)));
        assertThrows(Exception.class, () -> protocol.accept(event("heartbeat", 2).put("session_id", "ffffffffffffffffffffffffffffffff")));
        assertThrows(Exception.class, () -> protocol.accept(event("heartbeat", 2).put("seq", "2")));
        protocol.accept(event("heartbeat", 2));
        protocol.accept(event("ended", 3).put("state", "ended"));
        assertThrows(Exception.class, () -> protocol.accept(event("result", 4)));
    }
    @Test public void unreadyOrUnknownEventsFailClosed() throws Exception {
        assertThrows(Exception.class, () -> new LiveProtocol().accept(event("result", 1)));
        assertThrows(Exception.class, () -> new LiveProtocol().accept(event("ready", 1).put("state", "unknown")));
    }
    @Test public void actionContractAndTurnMustMatch() throws Exception {
        JSONObject body = event("result", 2).put("turn_id", 1).put("decision", "execute").put("answer", "")
                .put("action", new JSONObject().put("app", "qqmusic").put("type", "music.search_play").put("query", "T-ARA"));
        assertEquals("T-ARA", LiveProtocol.result(body, SID, 1).query);
        assertThrows(Exception.class, () -> LiveProtocol.result(body, SID, 2));
        body.getJSONObject("action").put("type", "shell.exec");
        assertThrows(Exception.class, () -> LiveProtocol.result(body, SID, 1));
        body.getJSONObject("action").put("type", "music.search_play").put("query", "bad\nquery");
        assertThrows(Exception.class, () -> LiveProtocol.result(body, SID, 1));
    }
    @Test public void finalAnswerCannotCarryAnAction() throws Exception {
        JSONObject body = event("result", 2).put("turn_id", 1).put("decision", "answer").put("answer", "你好。")
                .put("action", new JSONObject().put("type", "music.next"));
        assertThrows(Exception.class, () -> LiveProtocol.result(body, SID, 1));
        body.put("action", JSONObject.NULL);
        assertEquals("你好。", LiveProtocol.result(body, SID, 1).answer);
    }
    @Test public void speechMustFinishBeforeResumeAndIsNeverRepeated() throws Exception {
        LiveTurn turn = new LiveTurn(1);
        turn.transcript(true);
        turn.speech(1, "你好。");
        assertThrows(Exception.class, () -> turn.speech(1, "你好。"));
        assertFalse(turn.canResume(false, false));
        GatewayClient.Result answer = new GatewayClient.Result();
        answer.decision = "answer"; answer.answer = "你好。";
        turn.result(answer, 1);
        assertFalse(turn.canResume(true, false));
        assertFalse(turn.canResume(false, true));
        assertTrue(turn.canResume(false, false));
        turn.resumeSent = true;
        assertFalse(turn.canResume(false, false));
        assertThrows(Exception.class, () -> turn.result(answer, 1));
    }
    @Test public void executionRequiresOutcomeAndServerAck() throws Exception {
        LiveTurn turn = new LiveTurn(1);
        turn.transcript(true);
        turn.localAction = LocalMusicCommand.parse("下一首");
        turn.actionStarted = true;
        turn.result(LocalMusicCommand.parse("下一首"), 0);
        assertFalse(turn.canResume(false, false));
        turn.outcome = new MusicExecutor.Outcome(false, "指令已发送。");
        assertEquals("sent", turn.outcome.status);
        assertFalse(turn.canResume(false, false));
        turn.executionAck = true;
        assertTrue(turn.canResume(false, false));
    }
    @Test public void partialOrMismatchedResultsCannotExecute() throws Exception {
        LiveTurn turn = new LiveTurn(1);
        assertThrows(Exception.class, () -> turn.result(LocalMusicCommand.parse("下一首"), 0));
        turn.transcript(true);
        turn.localAction = LocalMusicCommand.parse("下一首");
        assertThrows(Exception.class, () -> turn.result(LocalMusicCommand.parse("上一首"), 0));
    }
    @Test public void exactCommandsRejectNegationAndConditions() {
        assertNotNull(LocalMusicCommand.parse("暂停一下。"));
        assertNotNull(LocalMusicCommand.parse("继续播放！"));
        assertNull(LocalMusicCommand.parse("不要暂停"));
        assertNull(LocalMusicCommand.parse("下一首之后暂停"));
        assertNull(LocalMusicCommand.parse("播放"));
        assertTrue(LocalMusicCommand.exits("结束对话。"));
        assertFalse(LocalMusicCommand.exits("不要结束对话"));
    }
    @Test public void credentialsRequireExactTlsHostAndRejectHeaderInjection() throws Exception {
        new ServerConfig("https://ai.sollado.com", "test-only");
        assertThrows(Exception.class, () -> new ServerConfig("http://ai.sollado.com", "test-only"));
        assertThrows(Exception.class, () -> new ServerConfig("https://ai.sollado.com.evil.test", "test-only"));
        assertThrows(Exception.class, () -> new ServerConfig("https://ai.sollado.com", "x\r\nHeader: value"));
    }
}
