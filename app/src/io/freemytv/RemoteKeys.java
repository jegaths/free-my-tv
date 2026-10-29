package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * The remote's app buttons on TCL TVs. The stock home screen opens the apps for Netflix, YouTube and Prime (and TCL
 * Channel); with it off, the keys still arrive, so we open the app the person picked (Settings > Remote Buttons).
 * Disney and Globoplay are opened by the system itself, so they are not listed. The YouTube button (BUTTON_3) never
 * reaches an app's window; only the accessibility service's key filter (RemoteKeyService) sees it.
 * On Vestel TVs and Formovie (Xiaomi) projectors only the Netflix button is ours: the system opens Netflix itself (a
 * global key, after the filter).
 * On KTC TVs (NextStar) only the Tabii button is ours, a global key too: the TV's hot-key receiver opens Tabii with it.
 * On Sony TVs, the app buttons (and the Google Play button of older remotes) are global keys for Sony's own receiver;
 * the filter sees them first, the same way.
 * Any other button is learned in Settings > Remote Buttons > Add a button: the service records the next key pressed,
 * and from then on opens the app picked for it (Prefs' learned keys; on any brand, the service goes on for them).
 * The remote's settings (dashboard) button (KEYCODE_NOTIFICATION or KEYCODE_SETTINGS; FOCUS on KTC) opens Google TV's
 * panel, which lives in Google's home app and goes off with it: here it opens the TV's Settings instead
 * (settings_button=true turns the service on for it on other brands). With keep_google_dashboard, Google's home app
 * stays on for that panel, and the Home button is ours instead: Google's home would win it otherwise.
 * The input (source) button (TV_INPUT): Google TV's home app answers it by opening the input player's picker, which
 * Android blocks while another home is on screen ("background activity launch"). Where that picker exists, we open it
 * ourselves: on the home screen on any brand, and everywhere where the key service runs.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    /** Some TCL remotes (BT_RC833) send TCL's own YouTube key instead of BUTTON_3: the same button. */
    private static final int KEY_YOUTUBE_TCL = 4065;
    /** Google TV's input picker (the input player's SelectInputActivity), which the input button opens. */
    private static final String VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS", INPUT_PLAYER = "com.google.android.tv.inputplayer";
    private static Boolean googleInputs;
    /** KTC remotes (Lumio) send FOCUS (scan code 240) for the settings button; the TV turns it into the panel toggle. */
    private static final int KEY_KTC_DASHBOARD = KeyEvent.KEYCODE_FOCUS;

    /**
     * The key codes are the brand's own: on other brands they can be other buttons, so nothing here runs there.
     * Vestel builds TVs sold under other brands (F&U...), so BRAND varies: check the maker too.
     */
    static final boolean TCL = is("TCL"), VESTEL = is("Vestel"), KTC = is("KTC"), SONY = is("Sony"), FORMOVIE = is("Formovie");
    /** Brands whose Netflix button is STEM_1, a global key (MediaTek): only that button is ours. */
    static final boolean STEM_NETFLIX = VESTEL || FORMOVIE;

    static boolean supported() {
        return TCL || STEM_NETFLIX || KTC || SONY || Config.SETTINGS_BUTTON || Config.KEEP_GOOGLE_DASHBOARD
                || Prefs.hasLearned() || Prefs.learning();
    }

    private static int learningKey = -1;   // the button being learned: its release is swallowed too

    /** Opens Free My TV's home screen, the way the Home button would. */
    static void goHome(Context c) {
        c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(c.getPackageName(), c.getPackageName() + ".HomeActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private static boolean is(String maker) {
        return maker.equalsIgnoreCase(Build.MANUFACTURER) || maker.equalsIgnoreCase(Build.BRAND);
    }

    /** Opens the app for this key on its first press. True if the key is ours and in use (it is then consumed). */
    static boolean onKey(Context c, KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_TV_INPUT && hasGoogleInputs(c)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                AppLog.w("input button -> input picker");
                try {
                    c.startActivity(new Intent(VIEW_INPUTS).setPackage(INPUT_PLAYER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException ex) {
                    AppLog.w("input picker: " + ex.getMessage());
                }
            }
            return true;
        }
        if (!supported()) return false;
        // Settings > Remote Buttons > Add a button: the next button pressed is recorded, not acted on.
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && Prefs.learnKey(e.getKeyCode(), e.getScanCode())) {
            learningKey = e.getKeyCode();
            return true;
        }
        if (e.getKeyCode() == learningKey) {
            if (e.getAction() == KeyEvent.ACTION_UP) learningKey = -1;
            return true;
        }
        if (Config.KEEP_GOOGLE_DASHBOARD && e.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            // Google's home is on for its panel and would win the Home button: it never gets it.
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) goHome(c);
            return true;
        }
        if (!Config.KEEP_GOOGLE_DASHBOARD && (e.getKeyCode() == KeyEvent.KEYCODE_NOTIFICATION
                || e.getKeyCode() == KeyEvent.KEYCODE_SETTINGS || (KTC && e.getKeyCode() == KEY_KTC_DASHBOARD))) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                AppLog.w("settings button (key " + e.getKeyCode() + ") -> TV settings");
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        // BUTTON_3 is also a game controller's button: keys from a gamepad or a joystick are left alone.
        int source = e.getSource();
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return false;
        int keyCode = e.getKeyCode() == KEY_YOUTUBE_TCL ? Prefs.KEY_YOUTUBE : e.getKeyCode();
        // Sony sends BUTTON_9 for five app buttons (BUTTON_4 for Netflix, BUTTON_8 for Google Play): only the scan code
        // tells them apart.
        if (SONY && (keyCode == KeyEvent.KEYCODE_BUTTON_4 || keyCode == KeyEvent.KEYCODE_BUTTON_8
                || keyCode == KeyEvent.KEYCODE_BUTTON_9)) {
            keyCode = Prefs.SONY_KEY + e.getScanCode();
        }
        // A button learned in Settings: by its scan code (several buttons can share a key code), else its key code.
        if (e.getScanCode() != 0 && Prefs.isLearned(Prefs.SCAN_KEY + e.getScanCode())) keyCode = Prefs.SCAN_KEY + e.getScanCode();
        boolean ours = false;
        for (int key : Prefs.keys()) ours |= key == keyCode;
        if (!ours) return false;
        String pkg = Prefs.keyApp(keyCode);
        if (pkg.isEmpty()) return false;   // "Nothing": the key goes on as if we weren't here
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Intent intent = launchIntent(c.getPackageManager(), pkg);
            AppLog.w("remote key " + e.getKeyCode() + " -> " + pkg + (intent == null ? " (not installed)" : ""));
            if (intent != null) c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    private static boolean hasGoogleInputs(Context c) {
        if (googleInputs == null) {
            googleInputs = c.getPackageManager().resolveActivity(new Intent(VIEW_INPUTS).setPackage(INPUT_PLAYER), 0) != null;
        }
        return googleInputs;
    }

    /** The app's TV launcher, or its plain one (kept here, not in HomeApps, so the key process stays small). */
    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }
}
