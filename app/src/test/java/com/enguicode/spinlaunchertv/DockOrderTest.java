package com.enguicode.spinlaunchertv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DockOrderTest {
    @Test
    public void parse() {
        assertEquals(Arrays.asList("a", "b"), DockOrder.parse(" a, ,b,"));
        assertTrue(DockOrder.parse(null).isEmpty());
        assertTrue(DockOrder.parse("").isEmpty());
    }

    @Test
    public void savedOrderWinsThenTheRest() {
        List<String> saved = Arrays.asList("youtube", "netflix");
        assertEquals(0, DockOrder.rank("youtube", saved));
        assertEquals(1, DockOrder.rank("netflix", saved));
        // Not placed by the owner: all share one rank after the saved ones, so the label decides.
        assertEquals(2, DockOrder.rank("prime", saved));
        assertEquals(2, DockOrder.rank("other", saved));
    }

    @Test
    public void noSavedOrderLeavesEveryoneTied() {
        List<String> none = DockOrder.parse(null);
        assertEquals(DockOrder.rank("netflix", none), DockOrder.rank("youtube", none));
    }

    @org.junit.Test
    public void mostUsedLeadThenUsualOrder() {
        java.util.List<String> top = java.util.Arrays.asList("c", "a");
        java.util.List<String> saved = java.util.Arrays.asList("a", "b");
        org.junit.Assert.assertEquals(0, DockOrder.rank("c", top, saved));
        org.junit.Assert.assertEquals(1, DockOrder.rank("a", top, saved));
        org.junit.Assert.assertEquals(3, DockOrder.rank("b", top, saved));
        org.junit.Assert.assertEquals(4, DockOrder.rank("e", top, saved));
    }

    @org.junit.Test
    public void topAppsKeepTheirSavedPlace() {
        java.util.List<String> rest = java.util.Arrays.asList("c", "a");
        java.util.List<String> top = java.util.Arrays.asList("b", "n");
        java.util.List<String> saved = java.util.Arrays.asList("a", "b", "c");
        org.junit.Assert.assertEquals(java.util.Arrays.asList("c", "b", "a"), DockOrder.keepingTop(rest, top, saved));
    }
}
