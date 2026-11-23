package com.genee.psavoice;

import java.io.IOException;

/** Per-turn gates, also exercised on the JVM without audio or a car. */
final class LiveTurn {
    final long id;
    boolean finalText;
    boolean resultReceived;
    boolean actionStarted;
    boolean executionAck;
    boolean ackSent;
    boolean resumeSent;
    int speechCount;
    final StringBuilder speech = new StringBuilder();
    GatewayClient.Result localAction;
    MusicExecutor.Outcome outcome;
    LiveTurn(long id) { this.id = id; }
    void transcript(boolean complete) throws IOException {
        if (finalText || resultReceived) throw new IOException("重复的转写结果");
        finalText = complete;
    }
    void speech(int index, String text) throws IOException {
        if (!finalText || resultReceived || index != speechCount + 1 || text.isEmpty()
                || text.length() > 160 || speech.length() + text.length() > 1000)
            throw new IOException("回答片段无效");
        speechCount = index;
        speech.append(text);
    }
    void result(GatewayClient.Result result, int count) throws IOException {
        if (!finalText || resultReceived || count != speechCount) throw new IOException("最终回答次序无效");
        if ("execute".equals(result.decision)) {
            if (count != 0 || (localAction != null && !localAction.type.equals(result.type)))
                throw new IOException("动作结果不匹配");
        } else if (localAction != null || (count > 0 && !speech.toString().trim().equals(result.answer.trim()))) {
            throw new IOException("回答内容不匹配");
        }
        resultReceived = true;
    }
    boolean canResume(boolean speaking, boolean queued) {
        return resultReceived && !resumeSent && !speaking && !queued
                && (!actionStarted || (outcome != null && executionAck));
    }
}
