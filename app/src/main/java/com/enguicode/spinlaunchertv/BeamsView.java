package com.enguicode.spinlaunchertv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Two stage lights from the top corners, aimed at the top of the raised wheel, with a pool of light where they meet. */
public class BeamsView extends View {
    private static final int LIGHT = Color.rgb(255, 244, 214);
    /** Where the raised wheel's main card sits, as measured on screen. */
    private static final float TARGET_Y = 0.56f;
    private static final float SOURCE_X = 0.02f;
    /** Half-widths of the nested cones, as a share of the widest: overlapping them gives the beam a soft edge. */
    private static final float[] SOFT = {1f, 0.82f, 0.64f};

    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG), pool = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] cones = new Path[SOFT.length];
    private final float dp;
    private float length, swing, light = 0.55f;

    public BeamsView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
    }

    /** {@code swing}: how far from the card the beams land, in dp, mirrored on each side. {@code light}: 0 to 1. */
    void aim(float swing, float light) {
        this.swing = swing * dp;
        this.light = light;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float dx = w * (0.5f - SOURCE_X), dy = h * TARGET_Y + 30 * dp;
        float toCard = (float) Math.hypot(dx, dy);
        length = toCard * 1.3f;
        float wide = 120 * dp * length / toCard;
        for (int i = 0; i < SOFT.length; i++) {
            cones[i] = new Path();
            cones[i].moveTo(0, -6 * dp * SOFT[i]);
            cones[i].lineTo(length, -wide * SOFT[i]);
            cones[i].lineTo(length, wide * SOFT[i]);
            cones[i].lineTo(0, 6 * dp * SOFT[i]);
            cones[i].close();
        }
        // Along the beam's own axis, so one shader serves every angle.
        beam.setShader(new LinearGradient(0, 0, length, 0,
                new int[]{withAlpha(70), withAlpha(26), withAlpha(0)}, new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        pool.setShader(new RadialGradient(0, 0, 1, new int[]{withAlpha(120), withAlpha(60), withAlpha(0)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    private static int withAlpha(int alpha) {
        return Color.argb(alpha, Color.red(LIGHT), Color.green(LIGHT), Color.blue(LIGHT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (length == 0) return;
        int w = getWidth();
        float x = w / 2f, y = getHeight() * TARGET_Y, top = -30 * dp;
        beam.setAlpha(Math.round(255 * light));
        pool.setAlpha(Math.round(255 * light));
        for (int side = -1; side <= 1; side += 2) {
            float sx = x + side * w * (0.5f - SOURCE_X);
            // The beams cross while they sweep and meet on the card as the swing dies out.
            double angle = Math.atan2(y - top, x + side * swing - sx);
            canvas.save();
            canvas.translate(sx, top);
            canvas.rotate((float) Math.toDegrees(angle));
            for (Path cone : cones) canvas.drawPath(cone, beam);
            canvas.restore();
        }
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(310 * dp, 190 * dp);
        canvas.drawCircle(0, 0, 1, pool);
        canvas.restore();
    }
}
