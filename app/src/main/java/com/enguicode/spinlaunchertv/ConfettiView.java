package com.enguicode.spinlaunchertv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Paper confetti thrown from points on screen: it shoots out, air brakes it, and it flutters down fading. */
public class ConfettiView extends View {
    private static final int[] COLORS = {0xFFFFD166, 0xFFEF476F, 0xFF06D6A0, 0xFF4CC9F0, 0xFFFFFFFF, 0xFFB388FF};
    private static final float DRAG = 2.4f, GRAVITY_DP = 520, LIFE_S = 2.4f;
    /** Each piece: x, y, vx, vy, turn, turn speed, flutter phase, delay, width, height. */
    private static final int FIELDS = 10;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float dp;
    private float[] pieces = new float[0];
    private long start;

    public ConfettiView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
    }

    /** Distance covered after {@code t} seconds from speed {@code v}, with air drag {@code k} and constant {@code g}. */
    static float travel(float v, float g, float k, float t) {
        float brake = (1 - (float) Math.exp(-k * t)) / k;
        return v * brake + g / k * (t - brake);
    }

    /** Starts a new show of {@code bursts}, each {x, y, delay in seconds, pieces, force in dp/s} in view coordinates. */
    void fire(float[][] bursts) {
        int total = 0;
        for (float[] b : bursts) total += (int) b[3];
        pieces = new float[total * FIELDS];
        int at = 0;
        for (float[] b : bursts) {
            for (int i = 0; i < (int) b[3]; i++, at += FIELDS) {
                // Mostly upwards, in a wide fan; squaring the random speed leaves a few pieces flying far.
                double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI * 1.3;
                float force = b[4] * dp * (0.25f + 0.75f * (float) Math.sqrt(random.nextDouble()));
                pieces[at] = b[0];
                pieces[at + 1] = b[1];
                pieces[at + 2] = force * (float) Math.cos(angle);
                pieces[at + 3] = force * (float) Math.sin(angle);
                pieces[at + 4] = random.nextFloat() * 360;
                pieces[at + 5] = (random.nextFloat() - 0.5f) * 900;
                pieces[at + 6] = random.nextFloat() * 6.28f;
                pieces[at + 7] = b[2];
                pieces[at + 8] = (3 + random.nextFloat() * 3) * dp;
                pieces[at + 9] = (5 + random.nextFloat() * 5) * dp;
            }
        }
        start = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        pieces = new float[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pieces.length == 0) return;
        float now = (SystemClock.uptimeMillis() - start) / 1000f;
        boolean alive = false;
        for (int at = 0; at < pieces.length; at += FIELDS) {
            float t = now - pieces[at + 7];
            if (t >= LIFE_S) continue;
            alive = true;
            if (t < 0) continue;
            float flutter = (float) Math.sin(t * 7 + pieces[at + 6]);
            float x = pieces[at] + travel(pieces[at + 2], 0, DRAG, t) + flutter * 7 * dp * Math.min(1f, t);
            float y = pieces[at + 1] + travel(pieces[at + 3], GRAVITY_DP * dp, DRAG, t);
            float w = pieces[at + 8], h = pieces[at + 9];
            paint.setColor(COLORS[at / FIELDS % COLORS.length]);
            paint.setAlpha(Math.round(255 * Math.min(1f, (LIFE_S - t) / 0.7f)));
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(pieces[at + 4] + pieces[at + 5] * t);
            // Flattening one axis reads as paper turning over in the air.
            canvas.scale(1, 0.35f + 0.65f * Math.abs(flutter));
            canvas.drawRect(-w / 2, -h / 2, w / 2, h / 2, paint);
            canvas.restore();
        }
        if (alive) postInvalidateOnAnimation(); else pieces = new float[0];
    }
}
