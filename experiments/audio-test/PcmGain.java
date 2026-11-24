package com.sollado.psavoice;

/** Playback loudness gain with a soft limiter for isolated input transients. */
final class PcmGain {
    static double gainFor(int peak, double rmsDbfs) {
        if (peak < 64 || rmsDbfs < -65) return 1;
        return Math.pow(10, Math.max(0, Math.min(24, -20 - rmsDbfs)) / 20);
    }

    static short apply(short sample, double gain) {
        if (gain <= 1) return sample;
        double value = sample * gain;
        double ceiling = 29204; // About -1 dBFS; leave room for downstream processing.
        double knee = ceiling * 0.7;
        double magnitude = Math.abs(value);
        if (magnitude > knee) {
            double headroom = ceiling - knee;
            magnitude = knee + headroom * Math.tanh((magnitude - knee) / headroom);
        }
        return (short) Math.round(Math.copySign(magnitude, value));
    }
}
