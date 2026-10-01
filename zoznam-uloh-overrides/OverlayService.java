package sk.zoznamuloh.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String ACTION_SHOW = "sk.zoznamuloh.OVERLAY_SHOW";
    public static final String ACTION_REFRESH = "sk.zoznamuloh.OVERLAY_REFRESH";

    private static final String PREFS = "overlay_prefs";
    private static final String PREF_Y = "overlay_y";

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams overlayParams;
    private DBHelper db;
    private AlarmScheduler scheduler;
    private SharedPreferences prefs;
    private long displayedTaskId = -1L;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new DBHelper(this);
        scheduler = new AlarmScheduler(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        startAsForeground(null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        refreshOverlay();
        return START_STICKY;
    }

    private void startAsForeground(Task task) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NotificationHelper.foregroundId(),
                    NotificationHelper.foreground(this, task),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NotificationHelper.foregroundId(), NotificationHelper.foreground(this, task));
        }
    }

    private void refreshOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay();
            stopSelf();
            return;
        }

        List<Task> due = db.getActiveDueTasks(System.currentTimeMillis());
        if (due.isEmpty()) {
            removeOverlay();
            stopSelf();
            return;
        }

        Task t = due.get(0);
        displayedTaskId = t.id;
        startAsForeground(t);
        removeOverlay();
        overlayView = buildOverlay(t, due.size());

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        overlayParams.y = prefs.getInt(PREF_Y, Ui.dp(this, 7));
        windowManager.addView(overlayView, overlayParams);
        overlayView.post(this::clampAndUpdateOverlay);
    }

    private View buildOverlay(Task t, int dueCount) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.rounded(Ui.NAVY_DARK, 24, this));
        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView grip = Ui.text(this, "━━━━", 15, Ui.MUTED);
        grip.setGravity(Gravity.CENTER);
        grip.setAlpha(0.72f);
        LinearLayout.LayoutParams gripLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 22));
        card.addView(grip, gripLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView badge = Ui.text(this, "⏰  TERMÍN", 13, Ui.ACCENT_LIGHT);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(badge, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView count = Ui.text(this, dueCount > 1 ? dueCount + " čakajú" : "PRESUŇ ↕", 11, Ui.MUTED);
        top.addView(count);

        View.OnTouchListener dragListener = makeDragListener();
        grip.setOnTouchListener(dragListener);
        top.setOnTouchListener(dragListener);

        TextView title = Ui.text(this, t.title, 20, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(titleLp, this, 0, 7, 0, 1);
        card.addView(title, titleLp);

        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.getDefault());
        TextView due = Ui.text(this, "Termín bol " + time.format(new Date(t.dueAt)), 13, Ui.MUTED);
        card.addView(due);

        if (t.note != null && !t.note.trim().isEmpty()) {
            TextView note = Ui.text(this, t.note.trim(), 13, Ui.MUTED);
            note.setMaxLines(2);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            Ui.margin(noteLp, this, 0, 5, 0, 0);
            card.addView(note, noteLp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(actionsLp, this, 0, 13, 0, 0);
        card.addView(actions, actionsLp);

        TextView done = Ui.button(this, "✓  HOTOVO", Ui.SUCCESS, Ui.NAVY_DARK);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        Ui.margin(half, this, 0, 0, 5, 0);
        actions.addView(done, half);

        TextView snooze = Ui.button(this, "⏱  ODLOŽIŤ", Ui.ACCENT, Ui.TEXT);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        Ui.margin(half2, this, 5, 0, 0, 0);
        actions.addView(snooze, half2);

        LinearLayout snoozePanel = new LinearLayout(this);
        snoozePanel.setOrientation(LinearLayout.VERTICAL);
        snoozePanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams snoozePanelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(snoozePanelLp, this, 0, 10, 0, 0);
        card.addView(snoozePanel, snoozePanelLp);

        TextView ask = Ui.text(this, "Na kedy odložiť?", 14, Ui.TEXT);
        ask.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        snoozePanel.addView(ask);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(quickLp, this, 0, 8, 0, 0);
        snoozePanel.addView(quick, quickLp);

        addSnoozeButton(quick, "+5 min", 5, t.id);
        addSnoozeButton(quick, "+10 min", 10, t.id);
        addSnoozeButton(quick, "+30 min", 30, t.id);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(bottomLp, this, 0, 7, 0, 0);
        snoozePanel.addView(bottom, bottomLp);

        addSnoozeButton(bottom, "+1 hod", 60, t.id);
        addSnoozeButton(bottom, "+1 deň", 24 * 60, t.id);
        TextView custom = smallButton("Vlastný čas");
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
        Ui.margin(customLp, this, 4, 0, 0, 0);
        bottom.addView(custom, customLp);

        done.setOnClickListener(v -> {
            db.markDone(t.id);
            scheduler.cancelTask(t.id);
            NotificationHelper.cancelAllForTask(this, t.id);
            refreshOverlay();
        });

        snooze.setOnClickListener(v -> {
            actions.setVisibility(View.GONE);
            snoozePanel.setVisibility(View.VISIBLE);
            overlayView.post(this::clampAndUpdateOverlay);
        });

        custom.setOnClickListener(v -> {
            Intent i = new Intent(this, SnoozeActivity.class);
            i.putExtra(AlarmReceiver.EXTRA_TASK_ID, t.id);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        return outer;
    }

    private View.OnTouchListener makeDragListener() {
        return new View.OnTouchListener() {
            private float downRawY;
            private int startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (overlayParams == null || overlayView == null) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawY = event.getRawY();
                        startY = overlayParams.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dy = Math.round(event.getRawY() - downRawY);
                        overlayParams.y = clampY(startY + dy);
                        try {
                            windowManager.updateViewLayout(overlayView, overlayParams);
                        } catch (Exception ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        overlayParams.y = clampY(overlayParams.y);
                        prefs.edit().putInt(PREF_Y, overlayParams.y).apply();
                        try {
                            windowManager.updateViewLayout(overlayView, overlayParams);
                        } catch (Exception ignored) {}
                        return true;
                }
                return false;
            }
        };
    }

    private int clampY(int proposed) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int viewHeight = overlayView != null && overlayView.getHeight() > 0
                ? overlayView.getHeight()
                : Ui.dp(this, 230);
        int min = 0;
        int max = Math.max(min, screenHeight - viewHeight - Ui.dp(this, 16));
        return Math.max(min, Math.min(proposed, max));
    }

    private void clampAndUpdateOverlay() {
        if (overlayView == null || overlayParams == null) return;
        overlayParams.y = clampY(overlayParams.y);
        prefs.edit().putInt(PREF_Y, overlayParams.y).apply();
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
        } catch (Exception ignored) {}
    }

    private void addSnoozeButton(LinearLayout parent, String label, int minutes, long taskId) {
        TextView b = smallButton(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
        Ui.margin(lp, this, parent.getChildCount() == 0 ? 0 : 4, 0, 0, 0);
        parent.addView(b, lp);
        b.setOnClickListener(v -> {
            long until = System.currentTimeMillis() + minutes * 60_000L;
            scheduler.snoozeTask(taskId, until);
            refreshOverlay();
        });
    }

    private TextView smallButton(String label) {
        TextView b = Ui.button(this, label, Ui.CARD_SOFT, Ui.TEXT);
        b.setTextSize(13);
        return b;
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
            overlayParams = null;
            displayedTaskId = -1L;
        }
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
