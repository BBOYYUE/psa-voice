package com.sollado.psavoice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client for the exported PSA TTS Binder protocol, without a vendor SDK. */
final class CarTtsClient {
    interface Listener {
        void ready(boolean available, String message);
        void finished(String error);
    }
    private static final String SERVICE = "com.cusc.manager.voicemanager.ITextToSpeechService";
    private static final String CALLBACK = "com.cusc.manager.voicemanager.ITextToSpeechCallback";
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService rpc = Executors.newSingleThreadExecutor();
    private volatile IBinder service;
    private volatile Listener listener;
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile String activeId;
    private boolean bound;
    private final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK);
                return true;
            }
            if (code < 1 || code > 4) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            if (code == 1) {
                notifyReady(true, "原车人声播报服务已就绪。");
            } else {
                String id = data.readString();
                if (code == 3) {
                    int status = data.readInt();
                    finish(id, status == 0 ? null : "原车播报结束状态：" + status);
                } else if (code == 4) {
                    finish(id, data.readString());
                }
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            service = binder;
            rpc.execute(() -> {
                try {
                    if (!SERVICE.equals(binder.getInterfaceDescriptor())) throw new RemoteException("接口版本不匹配");
                    transact(5, null, null);
                    boolean available = transact(1, null, null) != 0;
                    notifyReady(available, available ? "原车人声播报服务已连接。" : "原车语音引擎尚未就绪。");
                } catch (Exception e) {
                    notifyReady(false, "原车播报连接失败：" + e.getMessage());
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            notifyReady(false, "原车播报服务已断开。");
            finish(activeId, "原车播报服务已断开。");
        }
    };

    CarTtsClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    void connect() {
        try {
            Intent intent = new Intent("android.intent.action.CUSC_TTS_SERVICE")
                    .setClassName("com.cusc.voiceassistant", "com.cusc.voiceassistant.SpeechService");
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) notifyReady(false, "未找到原车播报服务。");
        } catch (SecurityException e) {
            notifyReady(false, "原车播报接口拒绝访问。");
        }
    }
    boolean isReady() { return ready && !closed; }
    void speak(String text) {
        if (!isReady()) { Listener l = listener; if (l != null) l.finished("原车播报尚未就绪。"); return; }
        final String id = "psa-voice-" + UUID.randomUUID();
        activeId = id;
        rpc.execute(() -> {
            try {
                int result = transact(2, text, id);
                if (result != 0) finish(id, "原车播报拒绝请求，返回码 " + result);
            } catch (Exception e) {
                finish(id, "原车播报失败：" + e.getMessage());
            }
        });
        main.postDelayed(() -> {
            if (id.equals(activeId)) { stop(); finish(id, "原车播报等待超时。"); }
        }, Math.max(20000L, 4000L + text.length() * 300L)); // 超时随文本长度伸缩，长文（如诗词）不会被掐断
    }
    private int transact(int code, String text, String id) throws RemoteException {
        IBinder target = service;
        if (target == null) throw new RemoteException("服务未连接");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE);
            if (code == 2) { data.writeString(text); data.writeString(id); }
            if (code == 5) data.writeStrongBinder(callback);
            if (!target.transact(code, data, reply, 0)) throw new RemoteException("接口不支持该操作");
            reply.readException();
            return code == 5 ? 0 : reply.readInt();
        } finally { data.recycle(); reply.recycle(); }
    }
    private void notifyReady(boolean available, String message) {
        ready = available;
        main.post(() -> { Listener l = listener; if (!closed && l != null) l.ready(available, message); });
    }
    private void finish(String id, String error) {
        main.post(() -> {
            if (id == null || !id.equals(activeId)) return;
            activeId = null;
            Listener l = listener;
            if (!closed && l != null) l.finished(error);
        });
    }
    void stop() {
        if (activeId == null || closed) return;
        rpc.execute(() -> { try { transact(4, null, null); } catch (Exception ignored) { } });
    }
    void cancelSpeech() {
        String id = activeId;
        activeId = null;
        if (id == null || closed) return;
        rpc.execute(() -> { try { transact(4, null, null); } catch (Exception ignored) { } });
    }
    void close() {
        stop();
        closed = true;
        listener = null;
        ready = false;
        if (bound) { context.unbindService(connection); bound = false; }
        rpc.shutdown();
    }
}
