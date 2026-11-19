package dev.apextv.firetv;

import java.util.ArrayDeque;
import java.util.Locale;

/** Recent renderer counters; startup drops cannot dominate the display indefinitely. */
final class PlaybackWindow {
    private static final long WINDOW_MS = 10_000;
    private record Sample(long ms, int rendered, int dropped) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    void clear() { samples.clear(); }
    void record(long nowMs, int rendered, int dropped, boolean playing) {
        if (!playing) { clear(); return; }
        Sample last = samples.peekLast();
        if (last != null && (nowMs <= last.ms || rendered < last.rendered || dropped < last.dropped)) clear();
        samples.addLast(new Sample(nowMs, rendered, dropped));
        while (samples.size() > 1 && samples.peekFirst().ms < nowMs - WINDOW_MS) samples.removeFirst();
    }
    double fps() {
        if (samples.size() < 2) return Double.NaN;
        Sample first = samples.peekFirst(), last = samples.peekLast();
        return (last.rendered - first.rendered) * 1000.0 / (last.ms - first.ms);
    }
    double dropPercent() {
        if (samples.size() < 2) return Double.NaN;
        Sample first = samples.peekFirst(), last = samples.peekLast();
        int dropped = last.dropped - first.dropped, total = last.rendered - first.rendered + dropped;
        return total == 0 ? 0 : dropped * 100.0 / total;
    }
    String summary() {
        return samples.size() < 2 ? "Recent renderer: warming up" :
            String.format(Locale.US, "Recent renderer: %.1f fps · %.1f%% dropped", fps(), dropPercent());
    }
}
