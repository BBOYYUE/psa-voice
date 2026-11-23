package com.genee.psavoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.IOException;

/** In-memory, 100 ms PCM frames. Stop/resume uses a fresh recorder per turn. */
final class PcmRecorder {
    interface Sink { boolean frame(byte[] bytes, int count); }
    private volatile AudioRecord input;
    private volatile boolean cancelled;
    void record(Context context, Sink sink) throws IOException {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("需要麦克风权限");
        int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("麦克风格式不可用");
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 6400));
        input = recorder;
        try {
            if (cancelled) return;
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("麦克风初始化失败");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("麦克风被占用");
            byte[] bytes = new byte[3200];
            while (!cancelled && !Thread.currentThread().isInterrupted()) {
                int count = recorder.read(bytes, 0, bytes.length, AudioRecord.READ_BLOCKING);
                if (cancelled) break;
                if (count <= 0 || count % 2 != 0) throw new IOException("麦克风读取失败");
                if (!sink.frame(bytes, count)) {
                    if (!cancelled) throw new IOException("实时音频发送失败");
                    break;
                }
            }
        } finally {
            input = null;
            try { recorder.stop(); } catch (IllegalStateException ignored) { }
            recorder.release();
        }
    }
    void cancel() {
        cancelled = true;
        AudioRecord current = input;
        if (current != null) try { current.stop(); } catch (IllegalStateException ignored) { }
    }
}
