package com.genee.psavoice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One foreground interaction at a time, with no app window or persistent audio. */
public final class AssistantService extends Service {
    private static final String TAG = "PSAMusicAssistant";
    private static final String CHANNEL = "music-assistant";
    private static final int NOTIFICATION = 71;
    private enum State { IDLE, CONNECTING, PROMPTING, RECORDING, WAITING, EXECUTING, SPEAKING }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private State state = State.IDLE;
    private long stateStartedAt = SystemClock.elapsedRealtime();
    private long generation;
    private long lastTrigger;
    private boolean destroyed;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int focusGain;
    private boolean focused;
    private String scene = "music";
    private CarTtsClient tts;
    private VoiceRecorder recorder;
    private GatewayClient gateway;
    private MusicExecutor music;
    private ToneGenerator tone;
    private String requestId;
    private String deviceId;
    private String diagnosticText;
    private boolean retried;
    private volatile boolean fillerSpoken;
    // 追问已播报、正等待用户回答；交互在此期间结束要通知服务端清除追问上下文。
    private volatile boolean clarifyAwaiting;
    private File requestDirectory;
    private SharedPreferences diagnostic;

    @Override public void onCreate() {
        super.onCreate();
        diagnostic = getSharedPreferences("assistant_status", MODE_PRIVATE);
        requestDirectory = new File(getCacheDir(), "voice-requests");
        requestDirectory.mkdirs();
        File[] stale = requestDirectory.listFiles();
        if (stale != null) for (File file : stale) {
            if (file.getName().endsWith(".wav") && System.currentTimeMillis() - file.lastModified() > 600000) file.delete();
        }
        // Retire the two explicitly app-owned audio-test files when upgrading.
        new File(getFilesDir(), "last-recording.wav").delete();
        new File(getFilesDir(), "playback-recording.wav").delete();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "音乐语音助手", NotificationManager.IMPORTANCE_LOW));
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = notification("正在准备音乐语音助手");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else startForeground(NOTIFICATION, notification);
        long now = SystemClock.elapsedRealtime();
        if (state != State.IDLE) {
            if (now - lastTrigger > 1500) cancelInteraction("已取消。");
            return START_NOT_STICKY;
        }
        lastTrigger = now;
        scene = "music";
        requestId = UUID.randomUUID().toString();
        diagnostic.edit().remove("last_action").remove("last_confirmed").remove("last_result").apply();
        long run = ++generation;
        diagnosticText = null;
        retried = false;
        fillerSpoken = false;
        clarifyAwaiting = false;
        // 入口：TriggerActivity=音乐助手（默认），ChatTriggerActivity=语音聊天。
        String requested = intent != null ? intent.getStringExtra("scene") : null;
        scene = "general".equals(requested) ? "general" : "music";
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent != null) {
            diagnosticText = intent.getStringExtra("diagnostic_text");
        }
        deviceId = diagnostic.getString("device_id", null);
        if (deviceId == null) {
            deviceId = "car-" + UUID.randomUUID().toString().substring(0, 12);
            diagnostic.edit().putString("device_id", deviceId).apply();
        }
        try { gateway = new GatewayClient(this); }
        catch (Exception e) {
            state(State.SPEAKING, "服务器配置缺失或无效。");
            beep(run, () -> finish(run));
            return START_NOT_STICKY;
        }
        // 录音期间预热到网关的 TCP+TLS 连接，正式上传几乎零握手等待。
        io.execute(gateway::warmup);
        state(State.CONNECTING, "正在连接原车人声服务");
        tts = new CarTtsClient(this, new CarTtsClient.Listener() {
            @Override public void ready(boolean available, String message) {
                if (valid(run) && state == State.CONNECTING && available) begin(run);
            }
            @Override public void finished(String error) {
                if (!valid(run)) return;
                if (state == State.PROMPTING) {
                    if (error != null) beep(run, () -> record(run));
                    else record(run);
                } else if (state == State.SPEAKING) finish(run);
            }
        });
        tts.connect();
        main.postDelayed(() -> { if (valid(run) && state == State.CONNECTING) begin(run); }, 6000);
        return START_NOT_STICKY;
    }

    private void begin(long run) {
        if (!valid(run) || state != State.CONNECTING) return;
        if (diagnosticText != null) {
            state(State.WAITING, "正在测试音乐请求");
            upload(run, null, diagnosticText);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            feedback(run, "需要先授予麦克风权限。");
            return;
        }
        if (!acquireFocus()) {
            feedback(run, "暂时无法录音，请结束通话后再试。");
            return;
        }
        state(State.PROMPTING, "请准备说出音乐指令");
        if (tts.isReady()) tts.speak("我在，请说。");
        else beep(run, () -> record(run));
    }

    private void record(long run) {
        if (!valid(run) || state != State.PROMPTING) return;
        if (!focused && !acquireFocus()) {
            feedback(run, "暂时无法录音，请结束通话后再试。");
            return;
        }
        state(State.RECORDING, "general".equals(scene) ? "我在听，请说" : "正在听，请说出音乐指令");
        File file = new File(requestDirectory, requestId + ".wav");
        VoiceRecorder capture = new VoiceRecorder();
        recorder = capture;
        io.execute(() -> {
            try {
                boolean heard = capture.record(this, file);
                main.post(() -> {
                    if (!valid(run)) { file.delete(); return; }
                    recorder = null;
                    if (!heard) {
                        file.delete();
                        // 没听到人声时自动再录一次，第二次仍失败才结束。
                        if (!retried) retry(run, "没听清，请再说一次。");
                        else feedback(run, "没听清，请再说一次。");
                    } else {
                        state(State.WAITING, "general".equals(scene) ? "正在思考" : "正在理解音乐指令");
                        clarifyAwaiting = false; // 回答已发出，上下文交由服务端按响应更新
                        upload(run, file, null);
                    }
                });
            } catch (Exception e) {
                file.delete();
                main.post(() -> { if (valid(run)) feedback(run, "录音失败，请稍后再试。"); });
            }
        });
    }

    private void upload(long run, File file, String text) {
        GatewayClient client = gateway;
        String id = requestId, device = deviceId;
        final String currentScene = scene;
        // 插话只在真的慢时出现：服务端 X-Stage 头（ASR 完成、LLM 开始）后再等 2.5 秒；
        // 旧服务端没有该响应头时，从上传起盲等 3.5 秒兜底。普通速度的请求不插话。
        if (file != null) {
            client.stageListener = () -> main.postDelayed(() -> maybeFiller(run), 2500);
            main.postDelayed(() -> maybeFiller(run), 3500);
        }
        io.execute(() -> {
            try {
                GatewayClient.Result result = text == null
                        ? client.voice(file, id, device, currentScene)
                        : client.text(text, id, device, currentScene);
                main.post(() -> {
                    if (!valid(run)) return;
                    // 语音聊天：直接播报回答；跨按键的上下文由服务端会话保持。
                    if ("answer".equals(result.decision)) { feedback(run, result.answer); return; }
                    // 表述不清晰时不执行任何操作：播报追问并直接再录一次。
                    // 追问不限轮次（服务端会记住最近一轮追问的上下文）；
                    // ADB 文字探针不重试，直接播报问题结束。
                    if ("clarify".equals(result.decision) && file != null) { clarifyAwaiting = true; retry(run, result.answer); return; }
                    if (!"execute".equals(result.decision)) { feedback(run, result.answer); return; }
                    if (id.equals(diagnostic.getString("last_executed_id", ""))) { finish(run); return; }
                    diagnostic.edit().putString("last_executed_id", id).putString("last_action", result.type).apply();
                    execute(run, result);
                });
            } catch (Exception e) {
                Log.w(TAG, "request=" + id + " gateway failure: " + e.getClass().getSimpleName());
                main.post(() -> {
                    if (!valid(run)) return;
                    String message = userError(e);
                    if ("没听清，请再说一次。".equals(message) && !retried && file != null) retry(run, message);
                    else feedback(run, message);
                });
            } finally {
                if (file != null) file.delete();
            }
        });
    }

    private void execute(long run, GatewayClient.Result action) {
        releaseFocus();
        state(State.EXECUTING, "正在控制QQ音乐");
        MusicExecutor executor = new MusicExecutor(this);
        music = executor;
        io.execute(() -> {
            try {
                Thread.sleep(180); // Let QQ finish its audio-focus restoration first.
                MusicExecutor.Outcome result = executor.execute(action);
                main.post(() -> {
                    if (!valid(run)) return;
                    diagnostic.edit().putBoolean("last_confirmed", result.confirmed).apply();
                    feedback(run, result.message);
                });
            } catch (Exception e) {
                main.post(() -> { if (valid(run)) feedback(run, "音乐操作未完成，请确认QQ音乐已安装。"); });
            }
        });
    }

    private void maybeFiller(long run) {
        if (!valid(run) || state != State.WAITING || fillerSpoken) return;
        if (tts == null || !tts.isReady()) return;
        fillerSpoken = true;
        tts.speak(FILLER);
    }

    private void retry(long run, String message) {
        if (!valid(run)) return;
        retried = true;
        // 保持音频焦点不释放：追问与再次录音之间音乐不能恢复。
        state(State.PROMPTING, message);
        if (tts != null && tts.isReady()) tts.speak(message);
        else beep(run, () -> record(run));
    }

    private static final String FILLER = "我想一下。";

    private void feedback(long run, String message) {
        if (!valid(run)) return;
        releaseFocus();
        state(State.SPEAKING, message);
        diagnostic.edit().putString("last_result", message).apply();
        if (tts != null && tts.isReady()) tts.speak(message);
        else beep(run, () -> finish(run));
    }

    private void cancelInteraction(String message) {
        long run = ++generation;
        if (recorder != null) recorder.cancel();
        if (gateway != null) gateway.cancel();
        if (music != null) music.cancel();
        if (tts != null) tts.stop();
        releaseFocus();
        state(State.SPEAKING, message);
        diagnostic.edit().putString("last_result", message).apply();
        // Do not reuse a callback tied to the cancelled generation.
        beep(run, () -> finish(run));
    }

    private static String userError(Exception e) {
        String message = e.getMessage();
        if (message != null && (message.equals("没听清，请再说一次。") || message.equals("服务器访问凭证需要更新。") || message.equals("语义分析服务暂时无响应，请稍后再试。") || message.equals("没有理解这条音乐指令，请换个说法。") || message.equals("服务器暂时不可用，请稍后再试。"))) return message;
        return "连接服务器失败，请稍后再试。";
    }
    private void beep(long run, Runnable next) {
        try {
            if (tone != null) tone.release();
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
        } catch (RuntimeException ignored) { }
        main.postDelayed(() -> {
            if (!valid(run)) return;
            if (tone != null) { tone.release(); tone = null; }
            next.run();
        }, 350);
    }
    private void finish(long run) {
        if (!valid(run)) return;
        // 交互结束时若仍挂着未回答的追问，尽力通知服务端清除上下文。
        clearPendingClarify();
        releaseFocus();
        state(State.IDLE, "交互结束");
        stopForeground(true);
        stopSelf();
    }
    private void clearPendingClarify() {
        if (!clarifyAwaiting) return;
        clarifyAwaiting = false;
        GatewayClient client = gateway;
        String device = deviceId;
        if (client != null && device != null) io.execute(() -> client.clearContext(device));
    }
    private void state(State value, String message) {
        long now = SystemClock.elapsedRealtime();
        diagnostic.edit().putLong("elapsed_" + state.name().toLowerCase(java.util.Locale.ROOT) + "_ms", now - stateStartedAt).apply();
        Log.i(TAG, "request=" + requestId + " stage=" + state + " elapsed_ms=" + (now - stateStartedAt));
        stateStartedAt = now;
        state = value;
        Log.i(TAG, "request=" + requestId + " state=" + value + " " + message);
        diagnostic.edit().putString("request_id", requestId).putString("state", value.name())
                .putString("message", message).putLong("updated_at", System.currentTimeMillis()).apply();
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(message));
        }
    }
    private Notification notification(String message) {
        return new Notification.Builder(this, CHANNEL).setContentTitle("PSA音乐助手")
                .setContentText(message).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
    }
    private boolean valid(long run) { return !destroyed && run == generation; }
    private boolean acquireFocus() {
        // 音乐助手暂停音乐（QQ 收到焦点丢失会暂停）；语音聊天只压低音量（DUCK）。
        int gain = "general".equals(scene) ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
        if (focus == null || focusGain != gain) {
            focusGain = gain;
            focus = new AudioFocusRequest.Builder(gain)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(change -> {
                        if ((change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                                && state == State.RECORDING) cancelInteraction("录音被其他音频操作中断。");
                    }, main).build();
        }
        focused = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focused;
    }
    private void releaseFocus() {
        if (focused) { focused = false; audio.abandonAudioFocusRequest(focus); }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true;
        generation++;
        clearPendingClarify();
        if (recorder != null) recorder.cancel();
        if (gateway != null) gateway.cancel();
        if (music != null) music.cancel();
        if (tts != null) tts.close();
        if (tone != null) tone.release();
        releaseFocus();
        io.shutdown();
        super.onDestroy();
    }
}
