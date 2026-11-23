package com.genee.psavoice;

import org.json.JSONObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Strict connection identity/order guard. An instance is never reused after reconnect. */
final class LiveProtocol {
    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList(
            "ready", "listening", "transcript", "stage", "heartbeat", "speech", "result",
            "execution_ack", "error", "ended"));
    private static final Set<String> STATES = new HashSet<>(Arrays.asList(
            "starting", "listening", "processing", "awaiting_execution", "awaiting_resume", "ended"));
    private String session;
    private long sequence;
    private boolean ended;

    synchronized void accept(JSONObject body) throws Exception {
        if (ended) throw new IOException("会话已结束");
        String sid = body.getString("session_id");
        long seq = integer(body, "seq");
        String event = body.getString("event");
        if (!sid.matches("[a-f0-9]{32}") || seq <= sequence || !EVENTS.contains(event)
                || !STATES.contains(body.getString("state"))) throw new IOException("会话事件无效");
        if (session == null) {
            if (!"ready".equals(event) && !"error".equals(event)) throw new IOException("会话尚未就绪");
            session = sid;
        } else if (!session.equals(sid) || "ready".equals(event)) throw new IOException("会话标识不匹配");
        sequence = seq;
        ended = "ended".equals(event);
    }
    static long integer(JSONObject json, String key) throws Exception {
        Object value = json.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IOException("事件编号无效");
        return ((Number) value).longValue();
    }
    static GatewayClient.Result result(JSONObject body, String sid, long turn) throws Exception {
        if (!sid.equals(body.getString("session_id")) || integer(body, "turn_id") != turn || turn < 1)
            throw new IOException("返回轮次不匹配");
        if ("answer".equals(body.optString("decision"))) {
            Object text = body.get("answer");
            if (!body.isNull("action") || !(text instanceof String) || ((String) text).trim().isEmpty()
                    || ((String) text).length() > 1000) throw new IOException("回答无效");
            GatewayClient.Result result = new GatewayClient.Result();
            result.decision = "answer";
            result.answer = (String) text;
            return result;
        }
        if ("execute".equals(body.optString("decision")) && !"".equals(body.opt("answer")))
            throw new IOException("动作返回内容无效");
        String id = sid + ":" + turn;
        JSONObject wrapped = new JSONObject(body.toString()).put("schema", "v1").put("scene", "music").put("request_id", id);
        return GatewayClient.parse(wrapped, id);
    }
}
