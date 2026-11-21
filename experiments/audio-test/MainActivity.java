package com.genee.psavoice;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone foreground audio probe; no network requests or vehicle changes. */
public class MainActivity extends Activity {
    private static final String TAG = "PSAVoiceTest";
    private static final int RATE = 16000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean recording;
    private boolean busy;
    private boolean capturing;
    private boolean visible;
    private boolean destroyed;
    private boolean ttsReady;
    private TextView status;
    private TextView meter;
    private TextView ttsStatus;
    private Button micButton;
    private Button voiceButton;
    private Button stopButton;
    private Button playButton;
    private Button toneButton;
    private Button speakButton;
    private File lastRecording;
    private File amplifiedRecording;
    private CheckBox amplifyPlayback;
    private MediaPlayer player;
    private ToneGenerator tone;
    private TextToSpeech tts;
    private CarTtsClient carTts;
    private boolean carUtterance;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int pendingSource = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        recording = false;
                        stopOutput();
                        setStatus("音频焦点已被其他应用接管，测试已停止。");
                    }
                }, main).build();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(16), dp(28), dp(16));
        root.setBackgroundColor(Color.rgb(17, 24, 39));
        TextView heading = text("PSA 语音助手 · 音频测试", 28);
        root.addView(heading);
        root.addView(text("仅在点击后录音，最长 10 秒；文件只保存在车机应用内，不上传。", 17));
        status = text("准备就绪。先选择一种音源录音，说一句话，再点击回放。", 20);
        root.addView(status);
        meter = text("音量：等待录音", 20);
        root.addView(meter);
        amplifyPlayback = new CheckBox(this);
        amplifyPlayback.setText("人声增益：自动提升响度并限制瞬间峰值（最多 +24 dB，原始录音不变）");
        amplifyPlayback.setTextColor(Color.WHITE);
        amplifyPlayback.setTextSize(17);
        amplifyPlayback.setChecked(true);
        root.addView(amplifyPlayback);
        LinearLayout row = row(root);
        micButton = button(row, "① 麦克风录音", () -> requestRecording(MediaRecorder.AudioSource.MIC));
        voiceButton = button(row, "① 语音音源录音", () -> requestRecording(MediaRecorder.AudioSource.VOICE_RECOGNITION));
        stopButton = button(row, "停止录音", () -> recording = false);
        LinearLayout row2 = row(root);
        playButton = button(row2, "② 回放刚才录音", this::playRecording);
        toneButton = button(row2, "测试人声提示", this::playPrompt);
        speakButton = button(row2, "测试中文播报", this::speak);
        ttsStatus = text("正在检查系统语音合成引擎…", 17);
        root.addView(ttsStatus);
        root.addView(text("Hermes：本版尚未连接服务器。电话键：本版尚未改动。\nMIC 没声音时，请再试“语音音源”；两种都无声则需要继续检查车机音频路由。", 16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        updateButtons();
        restoreRecording();
        carTts = new CarTtsClient(this, new CarTtsClient.Listener() {
            @Override public void ready(boolean available, String message) {
                if (destroyed) return;
                refreshTtsStatus();
                Log.i(TAG, "car_tts_ready=" + available + " " + message);
                updateButtons();
            }
            @Override public void finished(String error) {
                if (destroyed || !carUtterance) return;
                carUtterance = false;
                busy = false;
                setStatus(error == null ? "原车人声播报完成。" : error);
                updateButtons();
            }
        });
        carTts.connect();
        tts = new TextToSpeech(this, result -> main.post(() -> {
            if (destroyed) return;
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                ttsReady = language >= TextToSpeech.LANG_AVAILABLE;
                ttsStatus.setText(ttsReady ? "系统中文语音合成可用。" : "系统没有可用中文语音；后续可接服务器语音合成。");
            } else {
                ttsStatus.setText("未发现可用系统 TTS 引擎；提示音与录音回放仍可测试。");
            }
            Log.i(TAG, "tts_ready=" + ttsReady);
            refreshTtsStatus();
            updateButtons();
        }));
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(() -> {
                if (destroyed) return;
                audio.abandonAudioFocusRequest(focus);
                busy = false;
                setStatus("中文播报结束。");
                updateButtons();
            }); }
            @Override public void onError(String id) { main.post(() -> {
                if (destroyed) return;
                stopOutput();
                setStatus("中文播报失败。");
            }); }
        });
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row);
        return row;
    }
    private Button button(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(19);
        button.setMinHeight(dp(65));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), dp(5), dp(4), dp(5));
        row.addView(button, params);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private void setStatus(String message) {
        status.setText(message);
        Log.i(TAG, message);
    }
    private void refreshTtsStatus() {
        if (carTts != null && carTts.isReady()) {
            ttsStatus.setText("原车人声服务可用；提示语：我在，请说。使用原车当前音色。");
        } else {
            ttsStatus.setText(ttsReady ? "系统中文语音合成可用。" : "原车人声尚未就绪；可测试提示音及录音回放。");
        }
    }
    private void updateButtons() {
        if (destroyed) return;
        micButton.setEnabled(!busy);
        voiceButton.setEnabled(!busy);
        stopButton.setEnabled(recording);
        playButton.setEnabled(!busy && lastRecording != null);
        toneButton.setEnabled(!busy);
        speakButton.setEnabled(!busy && (ttsReady || (carTts != null && carTts.isReady())));
    }
    private boolean takeFocus() {
        if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true;
        setStatus("未取得音频焦点，请结束通话或其他语音操作后重试。");
        return false;
    }
    private void requestRecording(int source) {
        if (busy) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingSource = source;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        startRecording(source);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 1) return;
        int source = pendingSource;
        pendingSource = -1;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && visible && source >= 0) {
            startRecording(source);
        } else setStatus("需要麦克风权限才能进行录音测试，请再次点击录音并允许权限。");
    }
    private void startRecording(int source) {
        if (busy || !visible || !takeFocus()) return;
        recording = true;
        busy = true;
        capturing = true;
        lastRecording = null;
        amplifiedRecording = null;
        updateButtons();
        setStatus("正在录音，请说话；10 秒后自动停止，也可点击停止。");
        worker.execute(() -> capture(source));
    }
    private void capture(int source) {
        AudioRecord input = null;
        File file = new File(getFilesDir(), "last-recording.wav");
        long samples = 0;
        long sumSquares = 0;
        int peak = 0;
        String failure = null;
        double gain = 1;
        File boosted = new File(getFilesDir(), "playback-recording.wav");
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("麦克风权限未授予或已撤销");
            }
            int min = AudioRecord.getMinBufferSize(RATE, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("16 kHz 单声道音源不可用：" + min);
            input = new AudioRecord(source, RATE, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 6400));
            if (input.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("麦克风初始化失败");
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("无法开始录音，麦克风可能被占用");
            long start = SystemClock.elapsedRealtime();
            long lastUpdate = start;
            short[] pcm = new short[1600];
            byte[] bytes = new byte[3200];
            try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
                out.setLength(0);
                out.write(new byte[44]);
                while (recording && SystemClock.elapsedRealtime() - start < 10000) {
                    int count = input.read(pcm, 0, pcm.length);
                    if (count < 0) throw new IllegalStateException("读取音频失败：" + count);
                    if (count == 0) continue;
                    long frameSquares = 0;
                    for (int i = 0; i < count; i++) {
                        int sample = pcm[i];
                        peak = Math.max(peak, Math.abs(sample));
                        frameSquares += (long) sample * sample;
                        bytes[i * 2] = (byte) sample;
                        bytes[i * 2 + 1] = (byte) (sample >> 8);
                    }
                    out.write(bytes, 0, count * 2);
                    samples += count;
                    sumSquares += frameSquares;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUpdate >= 200) {
                        String level = String.format(Locale.ROOT, "已录 %.1f 秒 · 当前音量 %.1f dBFS · 峰值 %d",
                                samples / (double) RATE, dbfs(frameSquares, count), peak);
                        main.post(() -> { if (!destroyed) meter.setText(level); });
                        lastUpdate = now;
                    }
                }
                writeWavHeader(out, samples * 2);
            }
            if (samples == 0) throw new IllegalStateException("没有采集到音频数据");
            gain = PcmGain.gainFor(peak, dbfs(sumSquares, samples));
            writePlaybackCopy(file, boosted, gain);
        } catch (Exception e) {
            failure = e.getMessage();
            Log.e(TAG, "capture source=" + source + " failed", e);
        } finally {
            if (input != null) {
                try { input.stop(); } catch (IllegalStateException ignored) { }
                input.release();
            }
            recording = false;
        }
        String result;
        boolean success = failure == null;
        if (success) {
            result = String.format(Locale.ROOT, "录音完成：%.1f 秒，平均 %.1f dBFS，峰值 %d；回放增益 +%.1f dB。%s",
                    samples / (double) RATE, dbfs(sumSquares, samples), peak, 20 * Math.log10(gain),
                    peak == 0 ? "采集结果全静音，请尝试另一音源。" : "请回放确认是否是你的声音。");
        } else result = "录音失败：" + failure;
        Log.i(TAG, "source=" + source + " " + result);
        main.post(() -> {
            audio.abandonAudioFocusRequest(focus);
            capturing = false;
            if (destroyed) return;
            lastRecording = success ? file : null;
            amplifiedRecording = success ? boosted : null;
            busy = false;
            setStatus(result);
            updateButtons();
        });
    }
    private static double dbfs(long squares, long count) {
        if (count <= 0 || squares == 0) return -96;
        return Math.max(-96, 20 * Math.log10(Math.sqrt(squares / (double) count) / 32768.0));
    }
    private static void writeWavHeader(RandomAccessFile out, long bytes) throws java.io.IOException {
        out.seek(0);
        out.writeBytes("RIFF"); le32(out, bytes + 36);
        out.writeBytes("WAVEfmt "); le32(out, 16);
        out.writeShort(Short.reverseBytes((short) 1));
        out.writeShort(Short.reverseBytes((short) 1));
        le32(out, RATE); le32(out, RATE * 2);
        out.writeShort(Short.reverseBytes((short) 2));
        out.writeShort(Short.reverseBytes((short) 16));
        out.writeBytes("data"); le32(out, bytes);
    }
    private static void le32(RandomAccessFile out, long value) throws java.io.IOException {
        out.writeInt(Integer.reverseBytes((int) value));
    }
    private static void writePlaybackCopy(File original, File copy, double gain) throws java.io.IOException {
        try (RandomAccessFile in = new RandomAccessFile(original, "r");
             RandomAccessFile out = new RandomAccessFile(copy, "rw")) {
            out.setLength(0);
            byte[] header = new byte[44];
            in.readFully(header);
            out.write(header);
            while (in.getFilePointer() < in.length()) {
                short sample = Short.reverseBytes(in.readShort());
                out.writeShort(Short.reverseBytes(PcmGain.apply(sample, gain)));
            }
        }
    }
    private void restoreRecording() {
        File original = new File(getFilesDir(), "last-recording.wav");
        if (!original.exists() || original.length() <= 44) return;
        busy = true;
        capturing = true;
        updateButtons();
        setStatus("正在为上次录音准备新版人声增益…");
        worker.execute(() -> {
            File copy = new File(getFilesDir(), "playback-recording.wav");
            String result;
            boolean success = false;
            try (RandomAccessFile in = new RandomAccessFile(original, "r")) {
                in.seek(44);
                long count = 0, squares = 0;
                int peak = 0;
                while (in.getFilePointer() + 1 < in.length()) {
                    int value = Short.reverseBytes(in.readShort());
                    squares += (long) value * value;
                    peak = Math.max(peak, Math.abs(value));
                    count++;
                }
                double gain = PcmGain.gainFor(peak, dbfs(squares, count));
                writePlaybackCopy(original, copy, gain);
                result = String.format(Locale.ROOT, "上次录音已准备好，回放增益 +%.1f dB。可直接回放，无需重新录音。", 20 * Math.log10(gain));
                success = true;
            } catch (Exception e) {
                result = "上次录音读取失败，请重新录音。";
                Log.w(TAG, result, e);
            }
            final boolean ready = success;
            final String message = result;
            main.post(() -> {
                capturing = false;
                busy = false;
                if (destroyed) return;
                lastRecording = ready ? original : null;
                amplifiedRecording = ready ? copy : null;
                setStatus(message);
                updateButtons();
            });
        });
    }
    private void playRecording() {
        if (busy || lastRecording == null || !takeFocus()) return;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File playback = amplifyPlayback.isChecked() && amplifiedRecording != null
                    ? amplifiedRecording : lastRecording;
            player.setDataSource(playback.getAbsolutePath());
            player.setVolume(1.0f, 1.0f);
            player.setOnCompletionListener(p -> {
                stopOutput();
                setStatus("回放完成。如果能听到自己的声音，录音和回放链路已通。");
            });
            player.setOnErrorListener((p, what, extra) -> {
                stopOutput(); setStatus("回放失败：" + what + "/" + extra); return true;
            });
            player.prepare();
            player.start();
            busy = true;
            setStatus("正在回放" + (playback == amplifiedRecording ? "（已启用增益）" : "（原始录音）")
                    + "。媒体音量 " + audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) + "，可用音量键调整。");
            updateButtons();
        } catch (Exception e) {
            stopOutput();
            setStatus("回放失败：" + e.getMessage());
        }
    }
    private void playTone() {
        if (busy || !takeFocus()) return;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 350);
            busy = true;
            setStatus("正在播放提示音。");
            updateButtons();
            main.postDelayed(() -> {
                if (destroyed || tone == null) return;
                stopOutput();
                setStatus("提示音结束。");
            }, 500);
        } catch (RuntimeException e) {
            stopOutput(); setStatus("提示音失败：" + e.getMessage());
        }
    }
    private void playPrompt() {
        if (carTts != null && carTts.isReady()) speakCar("我在，请说。");
        else if (ttsReady) speak();
        else playTone();
    }
    private void speakCar(String text) {
        if (busy) return;
        busy = true;
        carUtterance = true;
        setStatus("正在使用原车音色播报：" + text);
        updateButtons();
        carTts.speak(text);
    }
    private void speak() {
        if (carTts != null && carTts.isReady()) {
            speakCar("你好，车机语音播报测试。我在，请说。");
            return;
        }
        if (busy || !ttsReady || !takeFocus()) return;
        busy = true;
        setStatus("正在测试中文语音播报。");
        updateButtons();
        if (tts.speak("你好，车机语音播报测试。", TextToSpeech.QUEUE_FLUSH, null, "probe") == TextToSpeech.ERROR) {
            stopOutput(); setStatus("语音引擎未接受播报请求。");
        }
    }
    private void stopOutput() {
        if (carTts != null && carUtterance) { carTts.stop(); carUtterance = false; }
        if (player != null) { player.release(); player = null; }
        if (tone != null) { tone.release(); tone = null; }
        if (tts != null) tts.stop();
        if (!capturing) {
            // Capture completion owns busy/focus until its file is finalized.
            busy = false;
            audio.abandonAudioFocusRequest(focus);
        }
        updateButtons();
    }
    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onStop() {
        visible = false;
        recording = false;
        stopOutput();
        super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        recording = false;
        stopOutput();
        if (tts != null) tts.shutdown();
        if (carTts != null) carTts.close();
        worker.shutdown();
        super.onDestroy();
    }
}
