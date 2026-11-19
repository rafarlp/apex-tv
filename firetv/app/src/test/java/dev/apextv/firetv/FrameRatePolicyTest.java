package dev.apextv.firetv;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameRatePolicyTest {
    @Test public void fiftyFpsNeedsEvenCadence() {
        assertTrue(FrameRatePolicy.matches(50, 50));
        assertTrue(FrameRatePolicy.matches(100, 50));
        assertFalse(FrameRatePolicy.matches(60, 50));
        assertFalse(FrameRatePolicy.matches(59.94f, 50));
    }
    @Test public void acceptsCinemaAndBroadcastMultiples() {
        assertTrue(FrameRatePolicy.matches(50, 25));
        assertTrue(FrameRatePolicy.matches(59.94f, 29.97f));
        assertTrue(FrameRatePolicy.matches(23.976f, 23.976f));
        assertFalse(FrameRatePolicy.matches(50, -1));
        assertFalse(FrameRatePolicy.matches(Float.NaN, 50));
    }
    @Test public void doesNotTradeAwayUhdForFrameRate() {
        int[] widths = {1920, 3840, 3840}, heights = {1080, 2160, 2160};
        assertEquals(2, FrameRatePolicy.choose(widths, heights, new float[]{50, 60, 50}, 3840, 2160, 50));
        assertEquals(-1, FrameRatePolicy.choose(new int[]{1920, 3840}, new int[]{1080, 2160}, new float[]{50, 60}, 3840, 2160, 50));
    }
    @Test public void choosesContentRateInsteadOfHardcodingFiftyHz() {
        int[] widths = {3840, 3840, 3840}, heights = {2160, 2160, 2160};
        assertEquals(2, FrameRatePolicy.choose(widths, heights, new float[]{50, 60, 24}, 3840, 2160, 24));
    }
}
