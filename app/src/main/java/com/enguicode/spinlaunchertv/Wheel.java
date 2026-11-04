package com.enguicode.spinlaunchertv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometry of the wheel dock: the apps sit on a large ring whose centre is below the screen, so only its top
 * arc shows. The focused app is at the top; the others slide down and tilt outwards on either side.
 */
final class Wheel {
    private Wheel() { }

    /** Beyond this angle a tile is fully faded (it is off screen by then anyway). */
    static final double MAX_ANGLE = Math.toRadians(70);

    /** Signed distance in steps on a ring of n items (fractional while it resizes), folded into [-n/2, n/2) so the wheel always turns the short way. */
    static float wrap(float d, float n) {
        if (n <= 0) return d;
        float m = ((d % n) + n) % n;
        return m >= n / 2f ? m - n : m;
    }

    /** Where a wheel coasting in {@code direction} (+1/-1) stops: on tile {@code index}, at least {@code minSteps} away. */
    static int spinOut(float pos, int direction, int minSteps, int index, int n) {
        int start = Math.round(pos) + direction * minSteps;
        return start + direction * Math.floorMod(direction * (index - start), n);
    }

    /** The {@code n} candidates with the most time, most used first; ones with no time never make the list. */
    static List<String> top(Map<String, Long> time, Set<String> candidates, int n) {
        List<String> found = new ArrayList<>();
        for (String c : candidates) {
            Long t = time.get(c);
            if (t != null && t > 0) found.add(c);
        }
        found.sort((a, b) -> Long.compare(time.get(b), time.get(a)));
        return found.subList(0, Math.min(n, found.size()));
    }

    /** A damped hop: 0 at t=0, about {@code height} at its peak, one smaller rebound, 0 from t=1 on. */
    static float hop(float t, float height) {
        if (t >= 1) return 0;
        return height * (1 - t) * (float) Math.sin(2.5 * Math.PI * t);
    }

    /**
     * Where a tile {@code d} steps from the focused one goes: {dx, dy, rotation in degrees, opacity factor}, with dx/dy
     * measured from the top of the ring, dy growing downwards. Tiles sit {@code spacing} apart along the arc; the two
     * beside the focused one get {@code extra} more (the focused tile is drawn larger) and the next ones {@code extra2}
     * more again (the neighbours are drawn a little larger too).
     */
    static float[] place(float d, float radius, float spacing, float extra, float extra2) {
        float ad = Math.abs(d);
        double a = Math.signum(d) * (spacing * ad + extra * Math.min(ad, 1f)
                + extra2 * Math.max(0f, Math.min(ad - 1f, 1f))) / radius;
        float dx = (float) (radius * Math.sin(a));
        float dy = (float) (radius * (1 - Math.cos(a)));
        double t = Math.min(1, Math.abs(a) / MAX_ANGLE);
        return new float[]{dx, dy, (float) Math.toDegrees(a), (float) (1 - t * t)};
    }

    /** Size boost for a tile {@code d} steps from the focused one: {@code boost} at one step, fading to none at 0 and 2. */
    static float neighbourBoost(float d, float boost) {
        return 1f + boost * Math.max(0f, 1f - Math.abs(Math.abs(d) - 1f));
    }

    /**
     * The draw's tickets, as card indexes: one per app that may win, plus {@code extra} more for each of those in
     * the top. Empty when no app may win, so the caller falls back to any card.
     */
    static List<Integer> tickets(List<String> pkgs, Set<String> neverWins, List<String> top, int extra) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < pkgs.size(); i++) {
            String p = pkgs.get(i);
            if (neverWins.contains(p)) continue;
            int n = 1 + (top.contains(p) ? extra : 0);
            for (int k = 0; k < n; k++) out.add(i);
        }
        return out;
    }
}
