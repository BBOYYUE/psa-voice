package com.sollado.psavoice;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class GatewayClient {
    interface StageListener {
        void analyzing();
    }
    volatile StageListener stageListener;
    static final class Result {
        String decision;
        String answer;
        String type;
        String query;
        String page;
        boolean autoplay;
    }
    private static final Set<String> ACTIONS = new HashSet<>(Arrays.asList(
            "music.search", "music.search_play", "music.pause", "music.resume", "music.next", "music.previous",
            "music.open", "music.close", "music.open_page"));
    private static final Set<String> PAGES = new HashSet<>(Arrays.asList(
            "recent", "local", "favorite", "car_playlist", "radio", "rank", "my_playlist"));
    private final String token;
    private final String base;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    GatewayClient(Context context) throws Exception {
        ServerConfig config = ServerConfig.load(context);
        base = config.base;
        token = config.token;
    }

    Result voice(File file, String requestId, String deviceId, String scene) throws Exception {
        String boundary = "psa-" + requestId;
        StringBuilder head = new StringBuilder();
        field(head, boundary, "scene", scene);
        if ("music".equals(scene)) field(head, boundary, "preferred_app", "qqmusic");
        field(head, boundary, "trigger", "music_key_long_press");
        field(head, boundary, "device_id", deviceId);
        field(head, boundary, "request_id", requestId);
        field(head, boundary, "client_version", BuildConfig.VERSION_NAME);
        head.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"record.wav\"\r\nContent-Type: audio/wav\r\n\r\n");
        byte[] prefix = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection current = open("/v1/voice", "multipart/form-data; boundary=" + boundary);
        try {
            current.setFixedLengthStreamingMode(prefix.length + file.length() + suffix.length);
            try (OutputStream output = current.getOutputStream(); FileInputStream input = new FileInputStream(file)) {
                output.write(prefix);
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (cancelled) throw new IOException("请求已取消");
                    output.write(buffer, 0, n);
                }
                output.write(suffix);
            }
            stage(current);
            return result(current, requestId, scene);
        } finally { current.disconnect(); connection = null; }
    }
    Result text(String text, String requestId, String deviceId, String scene) throws Exception {
        JSONObject json = new JSONObject().put("scene", scene)
                .put("device_id", deviceId).put("request_id", requestId)
                .put("client_version", BuildConfig.VERSION_NAME).put("text", text);
        if ("music".equals(scene)) json.put("preferred_app", "qqmusic");
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection current = open("/v1/text", "application/json; charset=utf-8");
        try {
            current.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = current.getOutputStream()) { output.write(body); }
            stage(current);
            return result(current, requestId, scene);
        } finally { current.disconnect(); connection = null; }
    }
    // 录音期间预先建立 TCP+TLS，正式上传时几乎零握手等待。
    void warmup() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/health").openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("Authorization", "Bearer " + token);
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[512];
                while (in.read(buffer) != -1) { }
            }
        } catch (Exception ignored) { }
    }
    // 用户没有回答追问或取消交互时，尽力通知服务端清除挂起的追问上下文。
    void clearContext(String deviceId) {
        HttpURLConnection c = null;
        try {
            byte[] body = new JSONObject().put("device_id", deviceId)
                    .toString().getBytes(StandardCharsets.UTF_8);
            c = (HttpURLConnection) new URL(base + "/v1/music/context/clear").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = c.getOutputStream()) { output.write(body); }
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[256];
                while (in.read(buffer) != -1) { }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
    }
    // 服务端在 LLM 路径会提前刷出 X-Stage: analyzing 响应头。
    private void stage(HttpURLConnection current) throws IOException {
        if (!"analyzing".equals(current.getHeaderField("X-Stage"))) return;
        StageListener listener = stageListener;
        if (listener != null) listener.analyzing();
    }
    private HttpURLConnection open(String path, String contentType) throws IOException {
        if (cancelled) throw new IOException("请求已取消");
        HttpURLConnection current = (HttpURLConnection) new URL(base + path).openConnection();
        connection = current;
        current.setRequestMethod("POST");
        current.setInstanceFollowRedirects(false);
        current.setConnectTimeout(10000);
        current.setReadTimeout(45000);
        current.setDoOutput(true);
        current.setRequestProperty("Authorization", "Bearer " + token);
        current.setRequestProperty("Content-Type", contentType);
        return current;
    }
    private Result result(HttpURLConnection current, String requestId, String scene) throws Exception {
        int status = current.getResponseCode();
        try (InputStream input = status >= 400 ? current.getErrorStream() : current.getInputStream()) {
            JSONObject body = new JSONObject(read(input, 32768));
            if (cancelled) throw new IOException("请求已取消");
            if (status != 200 || !body.optBoolean("ok")) {
                JSONObject error = body.optJSONObject("error");
                String code = error != null ? error.optString("code") : "";
                if ("empty_transcript".equals(code) || "asr_failed".equals(code)) throw new IOException("没听清，请再说一次。");
                if ("hermes_failed".equals(code)) throw new IOException("语义分析服务暂时无响应，请稍后再试。");
                if ("invalid_intent".equals(code)) throw new IOException("没有理解这条音乐指令，请换个说法。");
                if (status == 401 || status == 403) throw new IOException("服务器访问凭证需要更新。");
                throw new IOException("服务器暂时不可用，请稍后再试。");
            }
            return "general".equals(scene) ? parseChat(body, requestId) : parse(body, requestId);
        }
    }
    // 通用对话响应（v0）：只需要 request_id 匹配与一段非空回答。
    private static Result parseChat(JSONObject body, String requestId) throws Exception {
        if (!requestId.equals(body.optString("request_id"))) throw new IOException("服务器返回协议不匹配。");
        Object answer = body.opt("answer");
        if (!(answer instanceof String) || ((String) answer).trim().isEmpty()
                || ((String) answer).length() > 4000) throw new IOException("服务器返回提示无效。");
        Result result = new Result();
        result.decision = "answer";
        result.answer = ((String) answer).trim();
        return result;
    }
    static Result parse(JSONObject body, String requestId) throws Exception {
        if (!"v1".equals(body.optString("schema")) || !"music".equals(body.optString("scene"))
                || !requestId.equals(body.optString("request_id"))) throw new IOException("服务器返回协议不匹配。");
        Result result = new Result();
        result.decision = body.optString("decision");
        if ("clarify".equals(result.decision) || "unsupported".equals(result.decision)) {
            if (!body.isNull("action")) throw new IOException("服务器返回动作无效。");
            Object answer = body.opt("answer");
            if (!(answer instanceof String) || ((String) answer).isEmpty() || ((String) answer).length() > 160) {
                throw new IOException("服务器返回提示无效。");
            }
            result.answer = (String) answer;
            return result;
        }
        if (!"execute".equals(result.decision)) throw new IOException("服务器返回未知决策。");
        JSONObject action = body.getJSONObject("action");
        result.type = action.optString("type");
        if (!ACTIONS.contains(result.type) || !"qqmusic".equals(action.optString("app"))) throw new IOException("不支持该音乐操作。");
        boolean search = result.type.equals("music.search") || result.type.equals("music.search_play");
        boolean page = result.type.equals("music.open_page");
        for (java.util.Iterator<String> keys = action.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!key.equals("type") && !key.equals("app")
                    && !(search && key.equals("query"))
                    && !(page && (key.equals("page") || key.equals("autoplay")))) throw new IOException("音乐操作参数无效。");
        }
        if (search) {
            Object query = action.opt("query");
            if (!(query instanceof String) || ((String) query).trim().isEmpty() || ((String) query).length() > 200) throw new IOException("搜索词无效。");
            result.query = ((String) query).trim();
            for (int i = 0; i < result.query.length(); i++) {
                if (Character.isISOControl(result.query.charAt(i))) throw new IOException("搜索词无效。");
            }
        }
        if (page) {
            Object name = action.opt("page");
            if (!(name instanceof String) || !PAGES.contains(name)) throw new IOException("音乐页面参数无效。");
            result.page = (String) name;
            Object autoplay = action.opt("autoplay");
            if (autoplay != null && !(autoplay instanceof Boolean)) throw new IOException("自动播放参数无效。");
            result.autoplay = Boolean.TRUE.equals(autoplay);
        }
        return result;
    }
    void cancel() {
        cancelled = true;
        HttpURLConnection current = connection;
        if (current != null) current.disconnect();
    }
    private static String read(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("服务器没有返回内容。");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (output.size() + n > limit) throw new IOException("服务器返回过大。");
            output.write(buffer, 0, n);
        }
        return output.toString("UTF-8");
    }
    private static void field(StringBuilder out, String boundary, String key, String value) {
        out.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
                .append(key).append("\"\r\n\r\n").append(value).append("\r\n");
    }
}
