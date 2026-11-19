package dev.apextv.firetv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackWindowTest {
    @Test public void calculatesRecentThroughputAndDroppedFraction() {
        PlaybackWindow window = new PlaybackWindow();
        window.record(0, 1000, 30, true);
        window.record(1000, 1048, 32, true);
        assertEquals(48, window.fps(), 0.001);
        assertEquals(4, window.dropPercent(), 0.001);
    }
    @Test public void oldStartupDropsLeaveTheWindow() {
        PlaybackWindow window = new PlaybackWindow();
        window.record(0, 0, 0, true);
        window.record(1000, 20, 30, true);
        for (int i = 2; i <= 12; i++) window.record(i * 1000L, 20 + (i - 1) * 50, 30, true);
        assertEquals(50, window.fps(), 0.001);
        assertEquals(0, window.dropPercent(), 0.001);
    }
    @Test public void pauseAndDecoderResetDoNotProduceBogusRates() {
        PlaybackWindow window = new PlaybackWindow();
        window.record(0, 100, 2, true);
        window.record(1000, 150, 2, true);
        window.record(2000, 150, 2, false);
        assertTrue(Double.isNaN(window.fps()));
        window.record(3000, 150, 2, true);
        window.record(4000, 0, 0, true);
        assertTrue(Double.isNaN(window.fps()));
        window.record(5000, 50, 0, true);
        assertEquals(50, window.fps(), 0.001);
    }
}
