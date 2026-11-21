package com.genee.psavoice;

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
        File file = new File(context.getFilesDir(), "server.json");
        try (FileInputStream input = new FileInputStream(file)) {
            JSONObject config = new JSONObject(read(input, 8192));
            base = config.getString("base_url");
            token = config.getString("token");
        }
        URL endpoint = new URL(base);
        if (!"https".equals(endpoint.getProtocol()) || !"ai.sollado.com".equals(endpoint.getHost())
                || endpoint.getUserInfo() != null || (endpoint.getPort() != -1 && endpoint.getPort() != 443)
                || !endpoint.getPath().isEmpty() || endpoint.getQuery() != null || endpoint.getRef() != null
                || token.isEmpty() || token.contains("\n") || token.contains("\r")) {
            throw new IOException("服务配置无效");
        }
    }
    Result voice(File file, String requestId, String deviceId) throws Exception {
        String boundary = "psa-" + requestId;
        StringBuilder head = new StringBuilder();
        field(head, boundary, "scene", "music");
        field(head, boundary, "preferred_app", "qqmusic");
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
            return result(current, requestId);
        } finally { current.disconnect(); connection = null; }
    }
    Result text(String text, String requestId, String deviceId) throws Exception {
        byte[] body = new JSONObject().put("scene", "music").put("preferred_app", "qqmusic")
                .put("device_id", deviceId).put("request_id", requestId)
                .put("client_version", BuildConfig.VERSION_NAME).put("text", text)
                .toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection current = open("/v1/text", "application/json; charset=utf-8");
        try {
            current.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = current.getOutputStream()) { output.write(body); }
            return result(current, requestId);
        } finally { current.disconnect(); connection = null; }
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
    private Result result(HttpURLConnection current, String requestId) throws Exception {
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
            return parse(body, requestId);
        }
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
