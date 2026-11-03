package com.enguicode.spinlaunchertv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConfettiViewTest {
    @Test
    public void startsInPlaceAndAirBrakesIt() {
        assertEquals(0f, ConfettiView.travel(100, 0, 2.4f, 0), 1e-4);
        assertEquals(100 / 2.4f, ConfettiView.travel(100, 0, 2.4f, 50), 1e-2);
    }

    @Test
    public void thrownUpwardsItRisesThenFalls() {
        float v = -1000, g = 1000, k = 2.4f;
        assertTrue(ConfettiView.travel(v, g, k, 0.3f) < 0);
        assertTrue(ConfettiView.travel(v, g, k, 2.4f) > ConfettiView.travel(v, g, k, 0.3f));
    }
}
