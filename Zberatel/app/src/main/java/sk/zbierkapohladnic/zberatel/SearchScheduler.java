package sk.zbierkapohladnic.zberatel;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class SearchScheduler {
    private SearchScheduler() {}

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c);
        am.cancel(pi);
        long interval = Math.max(15, AppPrefs.intervalMinutes(c)) * 60_000L;
        long first = System.currentTimeMillis() + Math.min(interval, 5 * 60_000L);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, interval, pi);
    }

    public static void cancel(Context c) {
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, SearchReceiver.class);
        i.setAction("sk.zbierkapohladnic.zberatel.SEARCH");
        return PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
