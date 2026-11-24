package com.sollado.psavoice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main-thread state machine. Audio capture and music execution never block each other or the UI. */
public final class LiveAssistantService extends Service {
    static final String TOGGLE = "com.sollado.psavoice.LIVE_TOGGLE";
    static final String CANCEL = "com.sollado.psavoice.LIVE_CANCEL";
    private static final String CHANNEL = "live-conversation";
    private static final int NOTIFICATION = 72;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService captureWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService actionWorker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> speechQueue = new ArrayDeque<>();
    private LiveGateway gateway;
    private CarTtsClient tts;
    private PcmRecorder recorder;
    private MusicExecutor music;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private long focusEpoch;
    private long generation;
    private long lastServerAt;
    private boolean running;
    private boolean ready;
    private boolean greetingStarted;
    private boolean greetingDone;
    private boolean speaking;
    private boolean listening;
    private String sessionId;
    private LiveTurn turn;

    @Override public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                CHANNEL, "连续语音对话", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running || (intent != null && CANCEL.equals(intent.getAction()))) {
            end("已结束对话。", false);
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ConversationStore.end("需要授予麦克风权限。", true);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        stopService(new Intent(this, AssistantService.class));
        final long run = ++generation;
        ConversationStore.begin();
        try {
            Notification notification = notification("正在准备连续对话");
            if (Build.VERSION.SDK_INT >= 30)
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION, notification);
            ServerConfig config = ServerConfig.load(this);
            state("正在连接", "正在连接服务器与原车人声服务");
            lastServerAt = SystemClock.elapsedRealtime();
            tts = new CarTtsClient(this, new CarTtsClient.Listener() {
                @Override public void ready(boolean available, String message) {
                    if (!valid(run)) return;
                    if (available && !greetingStarted && !greetingDone) greet(run);
                }
                @Override public void finished(String error) {
                    if (!valid(run)) return;
                    speaking = false;
                    if (!greetingDone) {
                        greetingDone = true;
                        startListening(run);
                    } else if (error != null) {
                        speechQueue.clear();
                        state("播报不可用", "文字已显示，正在恢复监听");
                        complete(run);
                    } else pump(run);
                }
            });
            tts.connect();
            gateway = new LiveGateway(config, new LiveGateway.Listener() {
                @Override public void event(JSONObject body) { main.post(() -> {
                    if (valid(run)) LiveAssistantService.this.event(run, body);
                }); }
                @Override public void failed(String message) { main.post(() -> {
                    if (valid(run)) end(message, true);
                }); }
            });
            main.postDelayed(() -> {
                if (!valid(run) || greetingDone || greetingStarted) return;
                greetingDone = true;
                state("播报不可用", "原车人声未就绪，使用文字反馈");
                startListening(run);
            }, 6000);
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!valid(run)) return;
                    long silence = SystemClock.elapsedRealtime() - lastServerAt;
                    if (silence > (ready ? 12000 : 25000)) { end("服务器长时间没有响应，请重新唤醒。", true); return; }
                    send("ping", 0, null);
                    main.postDelayed(this, 4000);
                }
            }, 4000);
        } catch (Exception e) { end("无法启动会话，请检查服务器配置和麦克风权限。", true); }
        return START_NOT_STICKY;
    }
    private void greet(long run) {
        if (!requestDuckingFocus()) { end("请结束通话后再试。", true); return; }
        greetingStarted = true;
        speaking = true;
        state("我在", "正在播报唤醒提示");
        tts.speak("我在。");
    }
    private void event(long run, JSONObject body) {
        try {
            lastServerAt = SystemClock.elapsedRealtime();
            String event = body.getString("event");
            if (event.equals("heartbeat")) return;
            if (event.equals("ready")) {
                ready = true;
                sessionId = body.getString("session_id");
                turn = new LiveTurn(1);
                if (!greetingDone && !speaking) state("已连接", "正在准备语音提示");
                startListening(run);
                return;
            }
            if (event.equals("ended")) {
                String reason = body.optString("reason");
                boolean expected = reason.equals("voice_exit") || reason.equals("cancelled") || reason.equals("idle_timeout");
                end(expected ? "对话已结束。" : "会话已超时或中断，请重新唤醒。", !expected);
                return;
            }
            if (event.equals("error")) {
                stopCapture();
                String code = body.optString("code");
                if (body.optBoolean("recoverable") && turn != null && !turn.actionStarted
                        && code.startsWith("agent_") && LiveProtocol.integer(body, "turn_id") == turn.id) {
                    tts.cancelSpeech(); speaking = false; speechQueue.clear();
                    turn.resultReceived = true;
                    String text = "处理失败，请再说一次。";
                    ConversationStore.turn(turn.id).assistant += "\n" + text;
                    ConversationStore.changed();
                    enqueue(run, text);
                } else end("会话发生错误，请重新唤醒。", true);
                return;
            }
            if (event.equals("listening")) {
                long next = LiveProtocol.integer(body, "turn_id");
                if (turn == null || !turn.resumeSent || next != turn.id + 1) throw new IOException("恢复轮次无效");
                turn = new LiveTurn(next);
                startListening(run);
                return;
            }
            if (turn == null || LiveProtocol.integer(body, "turn_id") != turn.id) throw new IOException("轮次无效");
            if (event.equals("transcript")) {
                String text = body.getString("text");
                if (text.length() > 2000) throw new IOException("转写过长");
                boolean complete = body.getBoolean("final");
                turn.transcript(complete);
                ConversationStore.Turn row = ConversationStore.turn(turn.id);
                row.user = text; row.finalText = complete;
                ConversationStore.changed();
                if (complete) {
                    stopCapture();
                    if (LocalMusicCommand.exits(text)) { end("对话已结束。", false); return; }
                    state("正在处理", "已经听到，正在理解你的话");
                    turn.localAction = LocalMusicCommand.parse(text);
                    if (turn.localAction != null) execute(run, turn.localAction);
                }
            } else if (event.equals("stage")) {
                if (!turn.finalText || turn.resultReceived) throw new IOException("阶段次序无效");
                if (!speaking && !turn.actionStarted) state("正在处理", "generating".equals(body.optString("stage")) ? "助手正在生成回答" : "正在理解你的话");
            } else if (event.equals("speech")) {
                String text = body.getString("text");
                long index = LiveProtocol.integer(body, "index");
                if (index > Integer.MAX_VALUE) throw new IOException("片段编号无效");
                turn.speech((int) index, text);
                ConversationStore.turn(turn.id).assistant = turn.speech.toString();
                ConversationStore.changed();
                enqueue(run, text);
            } else if (event.equals("result")) {
                GatewayClient.Result result = LiveProtocol.result(body, sessionId, turn.id);
                long count = LiveProtocol.integer(body, "speech_count");
                if (count < 0 || count > Integer.MAX_VALUE) throw new IOException("片段数量无效");
                turn.result(result, (int) count);
                if ("execute".equals(result.decision)) {
                    ConversationStore.turn(turn.id).action = describe(result);
                    ConversationStore.changed();
                    if (!turn.actionStarted) execute(run, result);
                    else actionFeedback(run);
                } else {
                    ConversationStore.turn(turn.id).assistant = result.answer;
                    ConversationStore.changed();
                    if (count == 0) enqueue(run, result.answer);
                    else complete(run);
                }
            } else if (event.equals("execution_ack")) {
                if (!turn.ackSent || turn.executionAck) throw new IOException("重复执行确认");
                turn.executionAck = true;
                complete(run);
            } else throw new IOException("未知事件");
        } catch (Exception e) { end("会话数据异常，已停止本次操作，请重新唤醒。", true); }
    }
    private void startListening(long run) {
        if (!valid(run) || !ready || !greetingDone || speaking || listening || turn == null || turn.finalText) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { end("麦克风权限已失效。", true); return; }
        if (!requestDuckingFocus()) { end("麦克风交互被其他音频占用。", true); return; }
        final PcmRecorder capture = new PcmRecorder();
        final LiveGateway client = gateway;
        final LiveTurn current = turn;
        recorder = capture;
        listening = true;
        state("正在听", "说完后会自动处理");
        captureWorker.execute(() -> {
            try { capture.record(this, client::audio); }
            catch (Exception e) { main.post(() -> {
                if (valid(run) && turn == current && recorder == capture) end("录音或音频上传失败，请重新唤醒。", true);
            }); }
        });
    }
    private void stopCapture() {
        listening = false;
        PcmRecorder current = recorder;
        recorder = null;
        if (current != null) current.cancel();
    }
    private void enqueue(long run, String text) {
        if (text == null || text.isEmpty()) { complete(run); return; }
        for (int i = 0; i < text.length(); i += 160) speechQueue.add(text.substring(i, Math.min(i + 160, text.length())));
        pump(run);
    }
    private void pump(long run) {
        if (!valid(run) || speaking || !greetingDone) return;
        if (speechQueue.isEmpty()) { complete(run); return; }
        if (!tts.isReady()) { speechQueue.clear(); complete(run); return; }
        stopCapture();
        if (!requestDuckingFocus()) { end("语音交互被其他音频占用。", true); return; }
        speaking = true;
        state("正在播报", "助手说完后会继续听");
        tts.speak(speechQueue.remove());
    }
    private void complete(long run) {
        if (!valid(run) || turn == null || !greetingDone) return;
        if (speaking || !speechQueue.isEmpty()) { pump(run); return; }
        if (turn.canResume(false, false)) {
            turn.resumeSent = true;
            state("准备继续听", "正在恢复下一轮对话");
            if (!send("resume", turn.id, null)) end("连接已断开，请重新唤醒。", true);
        } else if (!turn.resultReceived && turn.finalText && !turn.actionStarted) {
            state("正在处理", "连接正常，正在等待后续回答");
        }
    }
    private void execute(long run, GatewayClient.Result action) {
        if (turn.actionStarted) return;
        turn.actionStarted = true;
        final LiveTurn current = turn;
        stopCapture();
        releaseFocus();
        state("正在执行", describe(action));
        MusicExecutor executor = new MusicExecutor(this);
        music = executor;
        actionWorker.execute(() -> {
            MusicExecutor.Outcome outcome;
            try { Thread.sleep(180); outcome = executor.execute(action); }
            catch (Exception e) { outcome = new MusicExecutor.Outcome("failed", "音乐操作未完成，请确认QQ音乐状态。"); }
            final MusicExecutor.Outcome result = outcome;
            main.post(() -> {
                if (!valid(run) || turn != current) return;
                current.outcome = result;
                actionFeedback(run);
            });
        });
    }
    private void actionFeedback(long run) {
        if (!turn.resultReceived || turn.outcome == null || turn.ackSent) return;
        turn.ackSent = true;
        if (!send("execution_result", turn.id, turn.outcome.status)) { end("连接已断开，请重新唤醒。", true); return; }
        ConversationStore.turn(turn.id).action = turn.outcome.message;
        ConversationStore.changed();
        // QQ may have brought its UI forward; SHOW never toggles/cancels this session.
        try { startActivity(new Intent(this, ConversationActivity.class).setAction(ConversationActivity.SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
        catch (RuntimeException ignored) { /* Notification remains a user-controlled return path. */ }
        enqueue(run, turn.outcome.message);
    }
    private boolean send(String event, long turnId, String status) {
        try {
            if (gateway == null || !gateway.isOpen()) return false;
            JSONObject json = new JSONObject().put("event", event);
            if (turnId > 0) json.put("turn_id", turnId);
            if (status != null) json.put("status", status);
            return gateway.send(json);
        } catch (Exception e) { return false; }
    }
    private static String describe(GatewayClient.Result action) {
        if (action.query != null) return "搜索：" + action.query;
        if (action.page != null) {
            switch (action.page) {
                case "recent": return "打开最近播放";
                case "local": return "打开本地音乐";
                case "favorite": return "打开收藏";
                case "car_playlist": return "打开车载歌单";
                case "radio": return "打开音乐电台";
                case "rank": return "打开排行榜";
                case "my_playlist": return "打开自建歌单";
                default: return "打开音乐页面";
            }
        }
        switch (action.type) {
            case "music.pause": return "暂停音乐";
            case "music.resume": return "继续播放音乐";
            case "music.next": return "切换下一首";
            case "music.previous": return "切换上一首";
            case "music.open": return "打开QQ音乐";
            case "music.close": return "关闭QQ音乐";
            default: return "正在发送音乐操作";
        }
    }
    private boolean requestDuckingFocus() {
        if (focus == null) {
            final long epoch = focusEpoch;
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(change -> focusChanged(epoch, change), main).build();
        }
        // Use the same focus owner across speaking and listening.
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    private void focusChanged(long epoch, int change) {
        if (epoch != focusEpoch || !running) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || (change == AudioManager.AUDIOFOCUS_LOSS && audio.getMode() != AudioManager.MODE_NORMAL)) {
            end("语音交互被其他音频操作中断。", true);
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            if (!listening) { end("语音交互被其他音频操作中断。", true); return; }
            // QQ may request focus before updating MediaSession on a track change.
            main.postDelayed(() -> {
                if (epoch != focusEpoch || !listening) return;
                if (audio.getMode() == AudioManager.MODE_NORMAL && new MusicExecutor(this).isPlaybackActive())
                    releaseFocus();
                else end("语音交互被其他音频操作中断。", true);
            }, 250);
        }
    }
    private void releaseFocus() {
        focusEpoch++;
        if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
    }
    private void state(String label, String detail) {
        ConversationStore.state(label, detail);
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(label));
    }
    private Notification notification(String message) {
        Intent panel = new Intent(this, ConversationActivity.class).setAction(ConversationActivity.SHOW);
        PendingIntent show = PendingIntent.getActivity(this, 72, panel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setContentTitle("语音助手 · 连续对话")
                .setContentText(message).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(show).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
    }
    private boolean valid(long run) { return running && generation == run; }
    private void end(String reason, boolean error) {
        running = false; generation++;
        main.removeCallbacksAndMessages(null);
        stopCapture();
        speechQueue.clear(); speaking = false;
        if (music != null) music.cancel();
        if (tts != null) { tts.close(); tts = null; }
        if (gateway != null) { gateway.cancel(); gateway = null; }
        releaseFocus();
        ConversationStore.end(reason, error);
        stopForeground(true);
        stopSelf();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        if (running) end("对话已结束。", false);
        captureWorker.shutdownNow();
        actionWorker.shutdownNow();
        super.onDestroy();
    }
}
