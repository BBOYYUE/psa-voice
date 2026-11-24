package com.sollado.psavoice;

import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** One authenticated WSS connection; never reconnects or replays audio/actions. */
final class LiveGateway extends WebSocketListener {
    interface Listener {
        void event(JSONObject body);
        void failed(String message);
    }
    private final Listener listener;
    private final LiveProtocol protocol = new LiveProtocol();
    private final OkHttpClient client;
    private volatile WebSocket socket;
    private volatile boolean closed;
    private volatile boolean opened;
    LiveGateway(ServerConfig config, Listener listener) {
        this.listener = listener;
        client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build();
        socket = client.newWebSocket(new Request.Builder()
                .url(config.base.replaceFirst("^https:", "wss:") + "/v1/live")
                .header("Authorization", "Bearer " + config.token).build(), this);
    }
    @Override public void onOpen(WebSocket webSocket, Response response) {
        socket = webSocket;
        if (closed) { webSocket.cancel(); return; }
        opened = true;
        try {
            send(new JSONObject().put("event", "start").put("format", "pcm_s16le")
                    .put("sample_rate", 16000).put("channels", 1).put("client_version", BuildConfig.VERSION_NAME));
        } catch (Exception e) { fail("无法建立实时会话。"); }
    }
    @Override public void onMessage(WebSocket webSocket, String text) {
        if (closed) return;
        try {
            if (text.length() > 32768) throw new IOException("事件过大");
            JSONObject body = new JSONObject(text);
            protocol.accept(body);
            listener.event(body);
        } catch (Exception e) { fail("服务器会话协议异常，请重新唤醒。"); }
    }
    @Override public void onMessage(WebSocket webSocket, ByteString bytes) { fail("服务器会话协议异常。"); }
    @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
        if (!closed) fail(response != null && (response.code() == 401 || response.code() == 403)
                ? "访问被拒绝，请检查凭证及实时服务是否启用。" : "实时连接已断开，请重新唤醒。");
        retire();
    }
    @Override public void onClosing(WebSocket webSocket, int code, String reason) { webSocket.close(code, null); }
    @Override public void onClosed(WebSocket webSocket, int code, String reason) {
        if (!closed) fail("实时会话已关闭，请重新唤醒。");
        retire();
    }
    boolean audio(byte[] bytes, int count) {
        WebSocket current = socket;
        if (closed || current == null) return false;
        if (current.queueSize() > 64000) { fail("网络上传太慢，请重新唤醒。"); return false; }
        return current.send(ByteString.of(bytes, 0, count));
    }
    boolean send(JSONObject body) {
        WebSocket current = socket;
        return !closed && current != null && current.send(body.toString());
    }
    boolean isOpen() { return opened && !closed; }
    private synchronized void fail(String message) {
        if (closed) return;
        closed = true;
        WebSocket current = socket;
        if (current != null) current.cancel();
        listener.failed(message);
        retire();
    }
    void cancel() {
        if (closed) return;
        closed = true;
        WebSocket current = socket;
        // Immediate close interrupts generation server-side; no queued audio is replayed.
        if (current != null) current.cancel();
        retire();
    }
    private void retire() {
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }
}
