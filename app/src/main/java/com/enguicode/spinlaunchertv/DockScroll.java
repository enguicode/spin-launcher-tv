package com.enguicode.spinlaunchertv;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.HorizontalScrollView;

/** The dock's scroller. Locked for the wheel, where tiles are placed by hand and focus must not scroll the row. */
public class DockScroll extends HorizontalScrollView {
    private boolean locked;

    public DockScroll(Context c, AttributeSet a) { super(c, a); }

    void setLocked(boolean locked) {
        this.locked = locked;
        if (locked) scrollTo(0, 0);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
        return locked ? 0 : super.computeScrollDeltaToGetChildRectOnScreen(r);
    }

    /** A smooth scroll started just before locking would otherwise keep running and shift the whole wheel. */
    @Override
    public void computeScroll() {
        if (!locked) super.computeScroll();
    }

    @Override
    public boolean executeKeyEvent(KeyEvent e) {
        return !locked && super.executeKeyEvent(e);
    }
}
