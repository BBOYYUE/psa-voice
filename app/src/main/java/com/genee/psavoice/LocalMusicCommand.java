package com.genee.psavoice;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Whole-utterance fast path only; negatives/additional conditions must not match. */
final class LocalMusicCommand {
    private static final Map<String, String> CONTROL = new HashMap<>();
    static {
        add("music.pause", "暂停", "暂停一下", "暂停播放", "停止播放");
        add("music.resume", "继续", "继续播放", "恢复播放");
        add("music.next", "下一首", "切下一首", "换首歌");
        add("music.previous", "上一首", "切上一首");
    }
    private static void add(String type, String... texts) { for (String text : texts) CONTROL.put(text, type); }
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim().replaceFirst("[。！!？?]+$", "");
    }
    static boolean exits(String text) {
        String value = normalize(text);
        return value.equals("结束对话") || value.equals("退出助手") || value.equals("不聊了");
    }
    static GatewayClient.Result parse(String text) {
        String type = CONTROL.get(normalize(text));
        if (type == null) return null;
        GatewayClient.Result result = new GatewayClient.Result();
        result.decision = "execute";
        result.answer = "";
        result.type = type;
        return result;
    }
}
