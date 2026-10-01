package sk.zbierkapohladnic.zberatel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;

public final class NotificationHelper {
    private NotificationHelper() {}

    private static String channelId(Context c) {
        String s = AppPrefs.sound(c).replaceAll("[^A-Za-z]", "").toLowerCase();
        return "zberatel_" + s + "_" + (AppPrefs.vibration(c) ? "v" : "nv");
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = channelId(c);
        NotificationChannel ch = new NotificationChannel(id, "ZBERATEĽ – nové nálezy", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Upozornenia na nové relevantné pohľadnice a historické materiály");
        ch.enableVibration(AppPrefs.vibration(c));
        String sound = AppPrefs.sound(c);
        Uri uri = null;
        if ("Jemné".equals(sound)) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if ("Výrazné".equals(sound)) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ch.setSound(uri, null);
        nm.createNotificationChannel(ch);
    }

    public static void notifyResults(Context c, List<SearchResult> fresh) {
        if (!AppPrefs.notifications(c) || fresh.isEmpty()) return;
        ensureChannel(c);
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("open_results", true);
        PendingIntent pi = PendingIntent.getActivity(c, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if ("Každý nález".equals(AppPrefs.notificationMode(c))) {
            int i=0;
            for (SearchResult r : fresh) {
                if (i++ >= 4) break;
                Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channelId(c)) : new Notification.Builder(c);
                b.setSmallIcon(android.R.drawable.ic_menu_search)
                        .setContentTitle("Nový nález • " + r.score + "% zhoda")
                        .setContentText(r.title)
                        .setStyle(new Notification.BigTextStyle().bigText(r.title + "\n" + r.source + (r.year.isEmpty()?"":" • "+r.year)))
                        .setAutoCancel(true).setContentIntent(pi);
                if (Build.VERSION.SDK_INT < 26) {
                    b.setPriority(Notification.PRIORITY_HIGH);
                    if (AppPrefs.vibration(c)) b.setVibrate(new long[]{0,180,120,180});
                }
                nm.notify(2000 + i, b.build());
            }
        } else {
            SearchResult best = fresh.get(0);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channelId(c)) : new Notification.Builder(c);
            String text = fresh.size() == 1 ? best.title : fresh.size() + " nových relevantných nálezov. Najlepší: " + best.title;
            b.setSmallIcon(android.R.drawable.ic_menu_search)
                    .setContentTitle("ZBERATEĽ našiel niečo nové")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true).setContentIntent(pi);
            if (Build.VERSION.SDK_INT < 26) {
                b.setPriority(Notification.PRIORITY_HIGH);
                if (AppPrefs.vibration(c)) b.setVibrate(new long[]{0,180,120,180});
            }
            nm.notify(2001, b.build());
        }
    }

    public static void test(Context c) {
        SearchResult r = new SearchResult("Chata pod Chlebom – skúšobné upozornenie", "https://example.com", "", "test", "Pohľadnica", "1930", 92);
        notifyResults(c, java.util.Collections.singletonList(r));
    }
}
