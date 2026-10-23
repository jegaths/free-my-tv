package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
    }

    /** keep_google_dashboard: Google's home came to the front anyway (boot, an app's "go home"): ours, right away. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!Config.KEEP_GOOGLE_DASHBOARD || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(event.getPackageName()), cls = String.valueOf(event.getClassName());
        if (HomeMode.GOOGLE_HOME.equals(pkg + "/" + cls.replace(pkg, ""))) RemoteKeys.goHome(this);
    }

    @Override public void onInterrupt() {}
}
