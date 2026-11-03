package com.enguicode.spinlaunchertv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Dock ordering rules, kept apart from the activity so they can be unit tested. */
final class DockOrder {
    private DockOrder() { }

    /** The user's saved order, stored as comma-separated package names. */
    static List<String> parse(String saved) {
        List<String> out = new ArrayList<>();
        if (saved == null) return out;
        for (String p : Arrays.asList(saved.split(","))) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    /**
     * Sort key: apps the user placed come first in their saved order, then everything else (ties fall back to
     * the label). A newly installed app therefore lands after the saved ones.
     */
    static int rank(String pkg, List<String> saved) {
        int i = saved.indexOf(pkg);
        return i >= 0 ? i : saved.size();
    }

    /** The most used apps lead, most used first; the others keep their usual order behind them. */
    static int rank(String pkg, List<String> top, List<String> saved) {
        int t = top.indexOf(pkg);
        return t >= 0 ? t : top.size() + rank(pkg, saved);
    }

    /** The order to save after arranging {@code rest}: apps now in the top block keep their old place for when they leave it. */
    static List<String> keepingTop(List<String> rest, List<String> top, List<String> saved) {
        List<String> out = new ArrayList<>(rest);
        for (int i = 0; i < saved.size(); i++) {
            String pkg = saved.get(i);
            if (top.contains(pkg) && !out.contains(pkg)) out.add(Math.min(i, out.size()), pkg);
        }
        return out;
    }
}
