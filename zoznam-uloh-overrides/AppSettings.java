package sk.zoznamuloh.app;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

public final class AppSettings {
    private static final String PREFS = "app_settings";
    private static final String KEY_SOUND = "sound_uri";
    private static final String KEY_VIBRATION = "vibration";

    private AppSettings() {}

    public static boolean vibration(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATION, true);
    }

    public static void setVibration(Context c, boolean enabled) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    public static String soundValue(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOUND, "default_alarm");
    }

    public static void setSoundValue(Context c, String value) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SOUND, value).apply();
    }

    public static Uri soundUri(Context c) {
        String value = soundValue(c);
        if ("silent".equals(value)) return null;
        if ("default_alarm".equals(value)) return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        try {
            return Uri.parse(value);
        } catch (Exception e) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }

    public static String reminderChannelId(Context c) {
        String sound = soundValue(c);
        boolean vib = vibration(c);
        return "task_reminders_" + Integer.toHexString((sound + "_" + vib).hashCode());
    }
}
