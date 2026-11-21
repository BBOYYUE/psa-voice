package com.genee.psavoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

final class VoiceRecorder {
    static final int RATE = 16000;
    private volatile AudioRecord input;
    private volatile boolean cancelled;

    boolean record(Context context, File file) throws IOException {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("需要麦克风权限");
        }
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("麦克风格式不可用");
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 6400));
        input = recorder;
        SpeechGate gate = new SpeechGate();
        long samples = 0;
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength(0);
            out.write(new byte[44]);
            if (cancelled) return false;
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("麦克风初始化失败");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("麦克风被占用");
            long start = SystemClock.elapsedRealtime();
            short[] frame = new short[320];
            byte[] bytes = new byte[640];
            while (!cancelled && !Thread.currentThread().isInterrupted()) {
                int count = recorder.read(frame, 0, frame.length);
                if (count < 0) {
                    if (cancelled) break;
                    throw new IOException("麦克风读取失败：" + count);
                }
                if (count == 0) continue;
                double squares = 0;
                for (int i = 0; i < count; i++) {
                    squares += (double) frame[i] * frame[i];
                    bytes[i * 2] = (byte) frame[i];
                    bytes[i * 2 + 1] = (byte) (frame[i] >> 8);
                }
                out.write(bytes, 0, count * 2);
                samples += count;
                if (gate.accept(Math.sqrt(squares / count), SystemClock.elapsedRealtime() - start)) break;
            }
            header(out, samples * 2);
            return !cancelled && gate.heardSpeech();
        } finally {
            input = null;
            try { recorder.stop(); } catch (IllegalStateException ignored) { }
            recorder.release();
        }
    }
    void cancel() {
        cancelled = true;
        AudioRecord recorder = input;
        if (recorder != null) {
            try { recorder.stop(); } catch (IllegalStateException ignored) { }
        }
    }
    private static void header(RandomAccessFile out, long bytes) throws IOException {
        out.seek(0);
        out.writeBytes("RIFF"); le32(out, bytes + 36); out.writeBytes("WAVEfmt "); le32(out, 16);
        out.writeShort(Short.reverseBytes((short) 1)); out.writeShort(Short.reverseBytes((short) 1));
        le32(out, RATE); le32(out, RATE * 2);
        out.writeShort(Short.reverseBytes((short) 2)); out.writeShort(Short.reverseBytes((short) 16));
        out.writeBytes("data"); le32(out, bytes);
    }
    private static void le32(RandomAccessFile out, long value) throws IOException {
        out.writeInt(Integer.reverseBytes((int) value));
    }
}
