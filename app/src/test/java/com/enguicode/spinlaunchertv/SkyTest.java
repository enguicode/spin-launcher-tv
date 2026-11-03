package com.enguicode.spinlaunchertv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SkyTest {
    @Test
    public void periodBoundaries() {
        assertEquals(Sky.AURORA, Sky.period(0));
        assertEquals(Sky.AURORA, Sky.period(5));
        assertEquals(Sky.MOUNTAINS, Sky.period(6));
        assertEquals(Sky.MOUNTAINS, Sky.period(8));
        assertEquals(Sky.MEDITERRANEAN, Sky.period(9));
        assertEquals(Sky.MEDITERRANEAN, Sky.period(13));
        assertEquals(Sky.FOREST, Sky.period(14));
        assertEquals(Sky.FOREST, Sky.period(17));
        assertEquals(Sky.DUNES, Sky.period(18));
        assertEquals(Sky.DUNES, Sky.period(20));
        assertEquals(Sky.AURORA, Sky.period(21));
        assertEquals(Sky.AURORA, Sky.period(23));
    }

    @Test
    public void weather() {
        assertEquals("Despejado", Sky.label(0));
        assertEquals("Lluvia", Sky.label(63));
        assertEquals("Tormenta", Sky.label(95));
        assertEquals(Sky.ICON_RAIN, Sky.icon(81));
        assertEquals(Sky.ICON_CLOUD, Sky.icon(3));
    }

    @Test
    public void degrees() {
        assertEquals("21,4°", Sky.degrees("21.43"));
        assertNull(Sky.degrees("unavailable"));
        assertNull(Sky.degrees(null));
    }
}
