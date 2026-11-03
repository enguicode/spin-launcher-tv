package com.enguicode.spinlaunchertv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The remote's Apps button sends KEYCODE_ALL_APPS, which only Google TV's launcher answered. This service
 * catches it anywhere and brings the home screen forward.
 */
public class AppsKeyService extends AccessibilityService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
