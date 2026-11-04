package com.enguicode.spinlaunchertv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional settings.json, written through ConfigProvider, so behaviour changes without rebuilding. Every key is
 * optional; a missing or out-of-range value falls back to the default or the nearest limit.
 * {"neverWins":["org.videolan.vlc"],"neverTop":[],"favourites":5,"favouritesDays":30,"favouriteTickets":2,
 *  "holdSeconds":3,"cardSeconds":20,"refreshMinutes":10}
 */
final class LauncherSettings {
    /** The top of most used apps never holds more than this. */
    static final int MAX_FAVOURITES = 5;
    static final LauncherSettings DEFAULT = new LauncherSettings(new JSONObject());

    /** Apps the roulette never stops on; they still turn with the wheel and open normally. */
    final Set<String> neverWins;
    /** Apps that never enter the top of most used, so they never lead the wheel. */
    final Set<String> neverTop;
    final int favourites, favouriteTickets;
    final long favouritesWindowMs, holdMs, cardMs, refreshMs;

    LauncherSettings(JSONObject o) {
        neverWins = set(o.optJSONArray("neverWins"));
        neverTop = set(o.optJSONArray("neverTop"));
        favourites = (int) clamp(o.optInt("favourites", MAX_FAVOURITES), 0, MAX_FAVOURITES);
        favouriteTickets = (int) clamp(o.optInt("favouriteTickets", 2), 1, 10);
        favouritesWindowMs = (long) clamp(o.optInt("favouritesDays", 30), 1, 365) * 24L * 60 * 60 * 1000;
        holdMs = Math.round(clamp(o.optDouble("holdSeconds", 3), 1, 10) * 1000);
        cardMs = Math.round(clamp(o.optDouble("cardSeconds", 20), 5, 300) * 1000);
        refreshMs = Math.round(clamp(o.optDouble("refreshMinutes", 10), 1, 120) * 60 * 1000);
    }

    static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private static Set<String> set(JSONArray a) {
        if (a == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            String p = a.optString(i, "").trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }
}
