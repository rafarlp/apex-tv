package dev.apextv.firetv;

/** A 50 fps broadcast needs 50/100 Hz for an even cadence, rather than 60 Hz. */
final class FrameRatePolicy {
    static boolean matches(float refreshHz, float videoFps) {
        if (!Float.isFinite(refreshHz) || !Float.isFinite(videoFps) || videoFps <= 0 || refreshHz <= 0) return false;
        int multiple = Math.round(refreshHz / videoFps);
        return multiple >= 1 && Math.abs(refreshHz - videoFps * multiple) <= 0.15f;
    }
    /** Prefer an even cadence without reducing the current/source output resolution. */
    static int choose(int[] widths, int[] heights, float[] rates, int minimumWidth, int minimumHeight, float videoFps) {
        int best = -1;
        for (int i = 0; i < rates.length; i++) {
            if (widths[i] < minimumWidth || heights[i] < minimumHeight || !matches(rates[i], videoFps)) continue;
            if (best == -1 || rates[i] < rates[best]) best = i;
        }
        return best;
    }
    private FrameRatePolicy() {}
}
