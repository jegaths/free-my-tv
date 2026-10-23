package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings changed on the TV itself (Free My TV > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen, screensaver, settings).
 */
final class Prefs {
    private Prefs() {}

    /** Remote keys we handle, with the app each opens until the user picks another. Codes read from the TCL's log. */
    static final int KEY_NETFLIX = 4062, KEY_YOUTUBE = 190, KEY_PRIME = 4099, KEY_TCL_CHANNEL = 4020;
    /** The Netflix button on Vestel TVs and Formovie projectors: STEM_1, which the TV hands to its Netflix key receiver (dumpsys window policy). */
    static final int KEY_STEM_NETFLIX = KeyEvent.KEYCODE_STEM_1;
    /** KTC's (NextStar) Tabii button: BUTTON_8, which the TV hands to its hot-key receiver (its log: "keycode = 195"). */
    static final int KEY_KTC_TABII = KeyEvent.KEYCODE_BUTTON_8;
    /** Sony's app buttons, as SONY_KEY + scan code (read from a BRAVIA's log; the key codes are shared). */
    static final int SONY_KEY = 100_000;
    static final int KEY_SONY_CORE = SONY_KEY + 496, KEY_SONY_NETFLIX = SONY_KEY + 583, KEY_SONY_DISNEY = SONY_KEY + 491,
            KEY_SONY_PRIME = SONY_KEY + 490, KEY_SONY_YOUTUBE = SONY_KEY + 485, KEY_SONY_CRUNCHYROLL = SONY_KEY + 507,
            KEY_SONY_PLAY = SONY_KEY + 587;   // the Google Play button of older remotes: BUTTON_8 (BRAVIA 4K UR3, Android 10)
    static final int[] KEYS = RemoteKeys.SONY
            ? new int[] {KEY_SONY_CORE, KEY_SONY_NETFLIX, KEY_SONY_DISNEY, KEY_SONY_PRIME, KEY_SONY_YOUTUBE, KEY_SONY_CRUNCHYROLL,
                    KEY_SONY_PLAY}
            : RemoteKeys.STEM_NETFLIX ? new int[] {KEY_STEM_NETFLIX}
            : RemoteKeys.KTC ? new int[] {KEY_KTC_TABII}
            : new int[] {KEY_NETFLIX, KEY_YOUTUBE, KEY_PRIME, KEY_TCL_CHANNEL};

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;

    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
        migrateKeys();   // before anything reads the button choices
        Ui.applyLanguage();   // the language picked on the TV, now that it can be read
    }

    /** The app's context in this process (null before App.onCreate). */
    static Context context() {
        return app;
    }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language and units

    /** "auto" or a language code as picked ("pt", "fr", "zh-CN"…); Ui.applyLanguage finds its catalogue. */
    static String language() {
        String v = app == null ? Config.LANGUAGE : sp().getString("language", Config.LANGUAGE);
        v = v.trim();
        return v.isEmpty() || v.equalsIgnoreCase("auto") ? "auto" : v;
    }

    static void setLanguage(String v) { put("language", v); Ui.applyLanguage(); }

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        String v = sp().getString("theme", Config.THEME).trim().toLowerCase(Locale.ROOT);
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    // MARK: - City

    static String city() { return sp().getString("city", Config.CITY).trim(); }
    static String latitude() { return sp().getString("latitude", Config.LATITUDE); }
    static String longitude() { return sp().getString("longitude", Config.LONGITUDE); }

    static void setCity(String name, String latitude, String longitude) {
        sp().edit().putString("city", name).putString("latitude", latitude).putString("longitude", longitude).apply();
        version++;
    }

    /** The home screen's soft click when moving and opening. */
    static boolean sounds() { return sp().getBoolean("sounds", true); }
    static void setSounds(boolean on) { put("sounds", on); }

    // MARK: - Widgets and coins

    static boolean weather() { return sp().getBoolean("weather", true); }
    static void setWeather(boolean on) { put("weather", on); }

    static boolean quotes() { return sp().getBoolean("quotes", Config.SHOW_CRYPTO); }
    static void setQuotes(boolean on) { put("quotes", on); }

    static boolean coin(int i) { return sp().getBoolean("coin" + i, true); }
    static void setCoin(int i, boolean on) { put("coin" + i, on); }

    // MARK: - Picks

    /** Whether a streaming service (Recs' key: "netflix", "appletv"…) gives picks; hide_services gives the first value. */
    static boolean pickSource(String key) { return sp().getBoolean("picks." + key, !Recs.hiddenByConfig(key)); }
    static void setPickSource(String key, boolean on) { put("picks." + key, on); }

    /** Titles on none of the services above come too and open in Nuvio (only when Nuvio is on the TV). */
    static boolean nuvio() { return sp().getBoolean("nuvio", true); }
    static void setNuvio(boolean on) { put("nuvio", on); }

    // MARK: - Shelf

    /** Packages on the home shelf, in order; null until the user edits it (then the built-in list applies). */
    static List<String> shelf() {
        String v = sp().getString("shelf", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setShelf(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("shelf", sb.toString());
    }

    // MARK: - Top buttons

    /**
     * The round buttons at the top right, in order: "live", "inputs" and/or "settings". null until the user edits
     * them (then HomeApps.TOP_DEFAULT applies); empty when they took all three off.
     */
    static List<String> topButtons() {
        String v = sp().getString("topButtons", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setTopButtons(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String p : keys) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("topButtons", sb.toString());
    }

    // MARK: - Remote buttons

    private static String defaultApp(int keyCode) {
        switch (keyCode) {
            case KEY_NETFLIX: case KEY_STEM_NETFLIX: return "com.netflix.ninja";
            case KEY_YOUTUBE: return "com.google.android.youtube.tv";
            case KEY_PRIME: return "com.amazon.amazonvideo.livingroom";
            case KEY_TCL_CHANNEL: return "";
            default: return "";
        }
    }

    /**
     * Buttons learned in Settings > Remote Buttons > Add a button (any brand): keyed by scan code (SCAN_KEY + scan; the
     * key code when there is none), since several buttons can share a key code (Sony). In the file as "+id:keyCode=pkg".
     */
    static final int SCAN_KEY = SONY_KEY;
    private static final List<int[]> learned = new ArrayList<>();          // {id, keyCode}, in the order learned
    private static final Map<Integer, String> learnedApps = new HashMap<>();

    // The key service runs in its own small process (:keys), so that a busy home screen can never delay the TV's keys.
    // SharedPreferences are not shared between processes, so these few values live in a tiny file both processes read.
    // The service asks on every key: the values stay in memory and the file is read again only when it changes.
    private static File keysFile() {
        return new File(app.getFilesDir(), "remote-keys.txt");
    }

    private static final String[] keyApps = new String[KEYS.length];   // null = the default
    private static long keysStamp = Long.MIN_VALUE;

    /** A temporary file per process (two can start together after an update), synced before it replaces the old one. */
    private static boolean writeKeys(String text) {
        File tmp = new File(app.getFilesDir(), "remote-keys." + android.os.Process.myPid() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(keysFile());
    }

    /** Choices saved before they moved to the file (in SharedPreferences) are copied there once. */
    private static void migrateKeys() {
        if (keysFile().exists()) return;
        SharedPreferences old = sp();
        boolean any = false;
        for (int key : KEYS) any |= old.contains("key" + key);
        if (!any) return;
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(old.getString("key" + key, defaultApp(key))).append('\n');
        writeKeys(sb.toString());
    }

    /** Reads the file again only when it changed. Call with keyApps held. */
    private static void load() {
        File f = keysFile();
        long stamp = f.lastModified() * 31 + f.length();   // 0 without the file: every key opens its default
        if (stamp == keysStamp) return;
        keysStamp = stamp;
        Arrays.fill(keyApps, null);
        learned.clear();
        learnedApps.clear();
        if (stamp == 0) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq), pkg = line.substring(eq + 1);
                if (key.startsWith("+")) {
                    int colon = key.indexOf(':');
                    try {
                        int id = Integer.parseInt(key.substring(1, colon < 0 ? key.length() : colon));
                        int code = colon < 0 ? 0 : Integer.parseInt(key.substring(colon + 1));
                        if (!learnedApps.containsKey(id)) learned.add(new int[] {id, code});
                        learnedApps.put(id, pkg);
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    for (int i = 0; i < KEYS.length; i++) if (key.equals(String.valueOf(KEYS[i]))) keyApps[i] = pkg;
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** The brand's buttons, then the learned ones. */
    static int[] keys() {
        synchronized (keyApps) {
            load();
            int[] out = Arrays.copyOf(KEYS, KEYS.length + learned.size());
            for (int i = 0; i < learned.size(); i++) out[KEYS.length + i] = learned.get(i)[0];
            return out;
        }
    }

    static boolean isLearned(int id) {
        synchronized (keyApps) {
            load();
            return learnedApps.containsKey(id);
        }
    }

    static boolean hasLearned() {
        synchronized (keyApps) {
            load();
            return !learned.isEmpty();
        }
    }

    /** The key code a learned button sends (0 if unknown). */
    private static int learnedKeyCode(int id) {
        synchronized (keyApps) {
            load();
            for (int[] l : learned) if (l[0] == id) return l[1];
            return 0;
        }
    }

    /** A learned button's name: Android's name for its key ("Button 4", "Focus"), or its number. */
    static String keyName(int id) {
        int code = learnedKeyCode(id);
        String s = code == 0 ? "" : KeyEvent.keyCodeToString(code);
        if (s.startsWith("KEYCODE_")) {
            s = s.substring(8).replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        return Ui.trf("Key {n}", "n", code != 0 ? code : id >= SCAN_KEY ? id - SCAN_KEY : id);
    }

    /** Package the key opens ("" = nothing). */
    static String keyApp(int keyCode) {
        synchronized (keyApps) {
            load();
            String own = learnedApps.get(keyCode);
            if (own != null) return own;
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode && keyApps[i] != null) return keyApps[i];
            return defaultApp(keyCode);
        }
    }

    static void setKeyApp(int keyCode, String pkg) {
        setKeyApp(keyCode, learnedKeyCode(keyCode), pkg);
    }

    /** Sets a button's app; a learned button not in the list yet (keyCode: what it sends) is added. */
    static void setKeyApp(int id, int keyCode, String pkg) {
        synchronized (keyApps) {
            load();
            boolean brand = false;
            for (int key : KEYS) brand |= key == id;
            if (!brand) {
                if (!learnedApps.containsKey(id)) learned.add(new int[] {id, keyCode});
                learnedApps.put(id, pkg);
            }
            if (writeAll(id, pkg)) version++;
        }
    }

    static void removeKey(int id) {
        synchronized (keyApps) {
            load();
            for (Iterator<int[]> it = learned.iterator(); it.hasNext(); ) if (it.next()[0] == id) it.remove();
            learnedApps.remove(id);
            if (writeAll(-1, null)) version++;
        }
    }

    private static boolean writeAll(int id, String pkg) {
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(key == id ? pkg : keyApp(key)).append('\n');
        for (int[] l : learned) sb.append('+').append(l[0]).append(':').append(l[1]).append('=').append(learnedApps.get(l[0])).append('\n');
        return writeKeys(sb.toString());
    }

    // Learning a button: Settings leaves a mark (learnFile); the key service, which sees every key, writes the next
    // button pressed (learnedFile) and takes the mark away; Settings picks the result up.
    private static File learnFile() { return new File(app.getFilesDir(), "remote-learn"); }
    private static File learnedFile() { return new File(app.getFilesDir(), "remote-learned.txt"); }

    static void startLearning() {
        //noinspection ResultOfMethodCallIgnored
        learnedFile().delete();
        try {
            new FileOutputStream(learnFile()).close();
        } catch (IOException ignored) {
        }
    }

    static void cancelLearning() {
        //noinspection ResultOfMethodCallIgnored
        learnFile().delete();
        //noinspection ResultOfMethodCallIgnored
        learnedFile().delete();
    }

    static boolean learning() {
        return learnFile().exists();
    }

    /** The keys the remote needs for everything else: never learned. */
    private static boolean navigation(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE: case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_TV_POWER:
                return true;
            default:
                return false;
        }
    }

    /** The key service, while learning: records the button and ends the learning. True if it took the key. */
    static boolean learnKey(int keyCode, int scanCode) {
        if (navigation(keyCode) || !learning()) return false;
        int id = scanCode != 0 ? SCAN_KEY + scanCode : keyCode;
        File tmp = new File(app.getFilesDir(), "remote-learned.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write((id + " " + keyCode).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
        if (!tmp.renameTo(learnedFile())) return false;
        //noinspection ResultOfMethodCallIgnored
        learnFile().delete();
        return true;
    }

    /** Settings: the button just learned, as {id, keyCode}, once; null until then. */
    static int[] takeLearned() {
        File f = learnedFile();
        if (!f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String[] parts = String.valueOf(r.readLine()).trim().split(" ");
            return new int[] {Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0};
        } catch (Exception e) {
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
