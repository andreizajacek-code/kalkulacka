package sk.zoznamuloh.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;

public final class NotificationHelper {
    public static final String CHANNEL_OVERLAY = "active_overlay";
    private static final int FOREGROUND_ID = 9001;

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String reminderChannelId = AppSettings.reminderChannelId(context);
        if (nm.getNotificationChannel(reminderChannelId) == null) {
            NotificationChannel reminders = new NotificationChannel(
                    reminderChannelId,
                    "Upozornenia na úlohy",
                    NotificationManager.IMPORTANCE_HIGH
            );
            reminders.setDescription("Termíny, upozornenia pred termínom a opakované pripomienky.");
            boolean vibrate = AppSettings.vibration(context);
            reminders.enableVibration(vibrate);
            if (vibrate) reminders.setVibrationPattern(new long[]{0, 250, 140, 350});

            Uri sound = AppSettings.soundUri(context);
            if (sound == null) {
                reminders.setSound(null, null);
            } else {
                reminders.setSound(sound,
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
            }
            nm.createNotificationChannel(reminders);
        }

        if (nm.getNotificationChannel(CHANNEL_OVERLAY) == null) {
            NotificationChannel overlay = new NotificationChannel(
                    CHANNEL_OVERLAY,
                    "Aktívna pripomienka",
                    NotificationManager.IMPORTANCE_LOW
            );
            overlay.setDescription("Udržiava pripomienku na obrazovke, kým ju nedokončíte alebo neodložíte.");
            overlay.setSound(null, null);
            overlay.enableVibration(false);
            nm.createNotificationChannel(overlay);
        }
    }

    private static String remindersChannel(Context context) {
        ensureChannels(context);
        return AppSettings.reminderChannelId(context);
    }

    public static void showPre(Context context, Task t) {
        Intent edit = new Intent(context, TaskEditorActivity.class);
        edit.putExtra(TaskEditorActivity.EXTRA_TASK_ID, t.id);
        PendingIntent content = PendingIntent.getActivity(context, notificationId(t.id, 1), edit,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(context, remindersChannel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Blíži sa termín")
                .setContentText(t.title + " • o " + t.preAlertMinutes + " min")
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(notificationId(t.id, 1), n);
    }

    public static void showDue(Context context, Task t) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, notificationId(t.id, 2), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent done = new Intent(context, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_DONE)
                .putExtra(AlarmReceiver.EXTRA_TASK_ID, t.id);
        PendingIntent donePi = PendingIntent.getBroadcast(context, notificationId(t.id, 3), done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snooze = new Intent(context, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_SNOOZE_5)
                .putExtra(AlarmReceiver.EXTRA_TASK_ID, t.id);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, notificationId(t.id, 4), snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(context, remindersChannel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("TERMÍN: " + t.title)
                .setContentText("Úloha čaká na vybavenie")
                .setContentIntent(content)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "HOTOVO", donePi).build())
                .addAction(new Notification.Action.Builder(null, "ODLOŽIŤ 5 MIN", snoozePi).build())
                .build();

        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(notificationId(t.id, 2), n);
    }

    public static Notification foreground(Context context, Task t) {
        ensureChannels(context);
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, FOREGROUND_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(context, CHANNEL_OVERLAY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Aktívna úloha")
                .setContentText(t == null ? "Čaká na vybavenie" : t.title)
                .setContentIntent(content)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    public static int foregroundId() {
        return FOREGROUND_ID;
    }

    public static void cancelDue(Context context, long taskId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notificationId(taskId, 2));
    }

    public static void cancelAllForTask(Context context, long taskId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notificationId(taskId, 1));
        nm.cancel(notificationId(taskId, 2));
    }

    private static int notificationId(long taskId, int kind) {
        int hash = (int) (taskId ^ (taskId >>> 32));
        return 100_000 + Math.abs(hash * 17 + kind);
    }
}
