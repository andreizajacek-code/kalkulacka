package sk.zbierkapohladnic.zberatel;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AppPrefs {
    public static final String PREFS = "zberatel_prefs";
    public static final String DEFAULT_TOPICS = "Tatry, Malá Fatra, horské chaty, horolezectvo";
    public static final String[] DEFAULT_SOURCES = new String[]{
            "aukro.sk", "aukro.cz", "ebay.com", "delcampe.net", "bazos.sk", "antikvariat.sk"
    };

    private AppPrefs() {}

    public static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static String topics(Context c) { return p(c).getString("topics", DEFAULT_TOPICS); }
    public static void topics(Context c, String value) { p(c).edit().putString("topics", value).apply(); }
    public static boolean includePostcards(Context c) { return p(c).getBoolean("inc_postcards", true); }
    public static boolean includePhotos(Context c) { return p(c).getBoolean("inc_photos", true); }
    public static boolean includeDocs(Context c) { return p(c).getBoolean("inc_docs", true); }
    public static boolean excludeArticles(Context c) { return p(c).getBoolean("exc_articles", true); }
    public static boolean excludeAccommodation(Context c) { return p(c).getBoolean("exc_accommodation", true); }
    public static boolean excludeSocial(Context c) { return p(c).getBoolean("exc_social", true); }
    public static boolean notifications(Context c) { return p(c).getBoolean("notifications", true); }
    public static boolean vibration(Context c) { return p(c).getBoolean("vibration", true); }
    public static String sound(Context c) { return p(c).getString("sound", "Jemné"); }
    public static String notificationMode(Context c) { return p(c).getString("notification_mode", "Súhrn"); }
    public static int threshold(Context c) { return p(c).getInt("threshold", 70); }
    public static long intervalMinutes(Context c) { return p(c).getLong("interval_min", 60L); }
    public static Set<String> sources(Context c) {
        Set<String> d = new LinkedHashSet<>(Arrays.asList(DEFAULT_SOURCES));
        return new LinkedHashSet<>(p(c).getStringSet("sources", d));
    }
    public static String customSources(Context c) { return p(c).getString("custom_sources", ""); }
    public static Set<String> knownUrls(Context c) { return new HashSet<>(p(c).getStringSet("known_urls", new HashSet<>())); }
    public static void knownUrls(Context c, Set<String> urls) { p(c).edit().putStringSet("known_urls", new HashSet<>(urls)).apply(); }
    public static Set<String> ignoredUrls(Context c) { return new HashSet<>(p(c).getStringSet("ignored_urls", new HashSet<>())); }
    public static void ignoredUrls(Context c, Set<String> urls) { p(c).edit().putStringSet("ignored_urls", new HashSet<>(urls)).apply(); }
}
