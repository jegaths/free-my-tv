package io.freemytv;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;

import java.util.ArrayList;
import java.util.List;

/**
 * What the TV's apps say the person was watching: Android TV's Watch Next list (TvContract), where YouTube, Nuvio and
 * the rest put the title being watched, its picture and the intent that resumes it. Google's home shows it as
 * "Continue watching". Reading other apps' rows needs READ_TV_LISTINGS, which the switch grants through the TV's ADB.
 */
final class WatchNext {
    private WatchNext() {}

    static final class Item {
        String pkg, title, poster, intent;
        String description;     // the app's short summary (the billboard), or null
        long engagedAt, position, duration;
        String meta = "";   // "Apple TV · 21 min left", made by the home screen
    }

    /** The same items in the same state (nothing to redraw). */
    static boolean same(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Item x = a.get(i), y = b.get(i);
            if (!x.intent.equals(y.intent) || x.position != y.position || !x.title.equals(y.title)) return false;
        }
        return true;
    }

    /** The list, newest first; empty without the permission or when no app put anything there. */
    static List<Item> read(Context c) {
        List<Item> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        String[] columns = {TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION};
        try (Cursor cur = cr.query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(7) == 0) continue;   // the person removed it from the row
                Item it = new Item();
                it.pkg = cur.getString(0);
                it.title = cur.getString(1);
                it.poster = cur.getString(2);
                it.intent = cur.getString(3);
                it.engagedAt = cur.getLong(4);
                it.position = cur.getLong(5);
                it.duration = cur.getLong(6);
                it.description = cur.getString(9);
                if (it.title == null || it.intent == null) continue;
                out.add(it);
            }
        } catch (SecurityException e) {
            AppLog.w("watch next: no permission");
        } catch (RuntimeException e) {
            AppLog.w("watch next: " + e.getMessage());
        }
        return out;
    }
}
