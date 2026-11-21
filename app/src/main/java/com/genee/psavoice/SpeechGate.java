package com.genee.psavoice;

/** Energy-based end-of-speech gate for 20 ms PCM frames. */
final class SpeechGate {
    private double noise = 30;
    private int voicedFrames;
    private long lastVoiceMs;
    private boolean heardSpeech;

    boolean accept(double rms, long elapsedMs) {
        double threshold = Math.max(70, noise * 2.4);
        if (rms > threshold) {
            voicedFrames++;
            lastVoiceMs = elapsedMs;
            heardSpeech |= voicedFrames >= 8;
        } else {
            noise = noise * 0.97 + rms * 0.03;
        }
        return elapsedMs >= 10000
                || (!heardSpeech && elapsedMs >= 5000)
                || (heardSpeech && elapsedMs >= 700 && elapsedMs - lastVoiceMs >= 800);
    }
    boolean heardSpeech() { return heardSpeech; }
}
