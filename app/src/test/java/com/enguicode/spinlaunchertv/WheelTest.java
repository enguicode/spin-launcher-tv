package com.enguicode.spinlaunchertv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class WheelTest {
    @Test
    public void wrapTakesTheShortWayRound() {
        assertEquals(1f, Wheel.wrap(1f, 10), 1e-6);
        assertEquals(-1f, Wheel.wrap(9f, 10), 1e-6);   // last -> first is one step, not nine
        assertEquals(-1f, Wheel.wrap(-1f, 10), 1e-6);
        assertEquals(0.5f, Wheel.wrap(10.5f, 10), 1e-6);
        assertEquals(-5f, Wheel.wrap(5f, 10), 1e-6);
    }

    @Test
    public void focusedSitsOnTopAndNeighboursSlideDownSymmetrically() {
        float[] top = Wheel.place(0, 1000, 126, 24, 0);
        assertEquals(0f, top[0], 1e-4);
        assertEquals(0f, top[1], 1e-4);
        assertEquals(1f, top[3], 1e-6);
        float[] right = Wheel.place(1, 1000, 126, 24, 0), left = Wheel.place(-1, 1000, 126, 24, 0);
        assertTrue(right[0] > 0 && right[1] > 0 && right[2] > 0);
        assertEquals(-right[0], left[0], 1e-4);
        assertEquals(right[1], left[1], 1e-4);
        assertEquals(-right[2], left[2], 1e-4);
        assertTrue(right[3] < 1f);
    }

    @Test
    public void focusedNeighboursGetExtraRoomOnlyOnce() {
        float a1 = Wheel.place(1, 1000, 126, 24, 0)[2], a2 = Wheel.place(2, 1000, 126, 24, 0)[2];
        assertEquals(Math.toDegrees(150.0 / 1000), a1, 1e-3);
        assertEquals(Math.toDegrees(126.0 / 1000), a2 - a1, 1e-3);
    }

    @Test
    public void secondStepGetsItsOwnExtra() {
        float a2 = Wheel.place(2, 1000, 126, 24, 10)[2], a3 = Wheel.place(3, 1000, 126, 24, 10)[2];
        assertEquals(Math.toDegrees((126 * 2 + 24 + 10) / 1000.0), a2, 1e-3);
        assertEquals(Math.toDegrees(126.0 / 1000), a3 - a2, 1e-3);
    }

    @Test
    public void neighboursAreBoostedOnlyAroundOneStep() {
        assertEquals(1.18f, Wheel.neighbourBoost(1, 0.18f), 1e-6);
        assertEquals(1.18f, Wheel.neighbourBoost(-1, 0.18f), 1e-6);
        assertEquals(1f, Wheel.neighbourBoost(0, 0.18f), 1e-6);
        assertEquals(1f, Wheel.neighbourBoost(2, 0.18f), 1e-6);
        assertEquals(1.09f, Wheel.neighbourBoost(1.5f, 0.18f), 1e-6);
    }

    @Test
    public void spinOutLandsOnTheChosenTileGoingTheRightWay() {
        for (int direction : new int[]{1, -1}) {
            for (int index = 0; index < 12; index++) {
                for (float pos : new float[]{0f, 7.4f, 130f, -25f}) {
                    int to = Wheel.spinOut(pos, direction, 50, index, 12);
                    assertEquals(index, Math.floorMod(to, 12));
                    float travelled = (to - pos) * direction;
                    assertTrue(travelled >= 49 && travelled < 63);
                }
            }
        }
    }

    @Test
    public void wrapHandlesARingOfFractionalSize() {
        assertEquals(-1f, Wheel.wrap(12.5f, 13.5f), 1e-5);
        assertEquals(0.5f, Wheel.wrap(14f, 13.5f), 1e-5);
    }

    @Test
    public void hopStartsAndEndsAtRest() {
        assertEquals(0f, Wheel.hop(0f, 0.1f), 1e-6);
        assertEquals(0f, Wheel.hop(1f, 0.1f), 1e-6);
        assertEquals(0f, Wheel.hop(5f, 0.1f), 1e-6);
        assertTrue(Wheel.hop(0.2f, 0.1f) > 0.05f);
    }

    @Test
    public void topKeepsTheMostUsedCandidatesOnly() {
        Map<String, Long> time = new HashMap<>();
        time.put("a", 10L);
        time.put("b", 30L);
        time.put("c", 20L);
        time.put("idle", 0L);
        time.put("tool", 99L);
        assertEquals(Arrays.asList("b", "c"),
                Wheel.top(time, new HashSet<>(Arrays.asList("a", "b", "c", "idle", "never-opened")), 2));
        assertEquals(Arrays.asList("b", "c", "a"),
                Wheel.top(time, new HashSet<>(Arrays.asList("a", "b", "c", "idle")), 5));
    }

    @Test
    public void farTilesFadeOut() {
        assertEquals(0f, Wheel.place(12, 1000, 126, 24, 0)[3], 1e-6);
    }

    @Test
    public void ticketsSkipNeverWinsAndWeightTheTop() {
        java.util.List<String> pkgs = Arrays.asList("netflix", "vlc", "youtube");
        java.util.List<Integer> t = Wheel.tickets(pkgs, new HashSet<>(Arrays.asList("vlc")), Arrays.asList("vlc", "netflix"), 2);
        // Netflix: 1 + 2 extra; VLC never (even in the top); YouTube: 1.
        assertEquals(Arrays.asList(0, 0, 0, 2), t);
        assertTrue(Wheel.tickets(Arrays.asList("vlc"), new HashSet<>(Arrays.asList("vlc")), Arrays.asList(), 1).isEmpty());
    }
}
