package sk.zoznamuloh.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final int REQ_SOUND = 7101;
    private static final int REQ_NOTIFICATIONS = 7102;

    private LinearLayout content;
    private TextView soundValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applySystemBars(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.NAVY);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        top.setBackgroundColor(Ui.NAVY_DARK);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 66)));

        TextView back = Ui.button(this, "←", Ui.CARD_SOFT, Ui.TEXT);
        back.setTextSize(22);
        back.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        back.setOnClickListener(v -> finish());

        TextView title = Ui.text(this, "Nastavenia", 22, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 30));
        scroll.addView(content);

        addSectionTitle("VZHĽAD");
        addThemeOption(0, "Modrá noc", "Tmavomodrý moderný vzhľad");
        addThemeOption(1, "Grafit", "Neutrálna tmavá sivá");
        addThemeOption(2, "Svetlá", "Čistý svetlý vzhľad");

        addSectionTitle("UPOZORNENIA");
        LinearLayout soundCard = card();
        content.addView(soundCard, cardLp());
        TextView soundTitle = Ui.text(this, "Zvuk upozornenia", 16, Ui.TEXT);
        soundTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        soundCard.addView(soundTitle);
        soundValue = Ui.text(this, currentSoundName(), 13, Ui.MUTED);
        LinearLayout.LayoutParams sv = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(sv, this, 0, 4, 0, 10);
        soundCard.addView(soundValue, sv);
        TextView chooseSound = Ui.button(this, "VYBRAŤ ZVUK", Ui.ACCENT, Ui.TEXT);
        soundCard.addView(chooseSound, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
        chooseSound.setOnClickListener(v -> pickSound());

        Switch vibrate = new Switch(this);
        vibrate.setText("Vibrovať pri upozornení");
        vibrate.setTextColor(Ui.TEXT);
        vibrate.setTextSize(15);
        vibrate.setChecked(AppSettings.vibration(this));
        LinearLayout.LayoutParams vibLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        Ui.margin(vibLp, this, 0, 8, 0, 0);
        soundCard.addView(vibrate, vibLp);
        vibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVibration(this, isChecked);
            NotificationHelper.ensureChannels(this);
        });

        addSectionTitle("OPRÁVNENIA");
        LinearLayout permissions = card();
        content.addView(permissions, cardLp());
        addPermissionRow(permissions, "Notifikácie", notificationReady(), () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        });
        addPermissionRow(permissions, "Panel nad ostatnými aplikáciami", Settings.canDrawOverlays(this), () -> {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        });
        addPermissionRow(permissions, "Presné alarmy", canScheduleExact(), () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        });

        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.applyTheme(this);
    }

    private void addSectionTitle(String text) {
        TextView t = Ui.text(this, text, 13, Ui.MUTED);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(lp, this, 2, 15, 0, 8);
        content.addView(t, lp);
    }

    private void addThemeOption(int id, String name, String description) {
        boolean selected = Ui.getTheme(this) == id;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15), Ui.dp(this, 13));
        box.setBackground(selected
                ? Ui.roundedStroke(Ui.CARD, Ui.ACCENT, 2, 20, this)
                : Ui.rounded(Ui.CARD, 20, this));
        box.setClickable(true);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        box.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView n = Ui.text(this, name, 16, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(n);

        TextView d = Ui.text(this, description, 12, Ui.MUTED);
        texts.addView(d);

        TextView mark = Ui.text(this, selected ? "✓" : "", 22, Ui.SUCCESS);
        mark.setGravity(Gravity.CENTER);
        box.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));

        LinearLayout.LayoutParams lp = cardLp();
        Ui.margin(lp, this, 0, 0, 0, 9);
        content.addView(box, lp);

        box.setOnClickListener(v -> {
            if (Ui.getTheme(this) != id) {
                Ui.setTheme(this, id);
                recreate();
            }
        });
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
        c.setBackground(Ui.rounded(Ui.CARD, 20, this));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void addPermissionRow(LinearLayout parent, String name, boolean ready, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        parent.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        TextView label = Ui.text(this, (ready ? "✓  " : "○  ") + name, 14, ready ? Ui.SUCCESS : Ui.TEXT);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        if (!ready) {
            TextView b = Ui.button(this, "POVOLIŤ", Ui.CARD_SOFT, Ui.ACCENT_LIGHT);
            row.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 94), Ui.dp(this, 40)));
            b.setOnClickListener(v -> action.run());
        }
    }

    private void pickSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Vyber zvuk upozornenia");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AppSettings.soundUri(this));
        startActivityForResult(i, REQ_SOUND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SOUND && resultCode == RESULT_OK && data != null) {
            Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (picked == null) {
                AppSettings.setSoundValue(this, "silent");
            } else {
                AppSettings.setSoundValue(this, picked.toString());
            }
            NotificationHelper.ensureChannels(this);
            soundValue.setText(currentSoundName());
            Toast.makeText(this, "Zvuk uložený", Toast.LENGTH_SHORT).show();
        }
    }

    private String currentSoundName() {
        Uri uri = AppSettings.soundUri(this);
        if (uri == null) return "Bez zvuku";
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            String title = ringtone != null ? ringtone.getTitle(this) : null;
            return title == null || title.trim().isEmpty() ? "Vybraný zvuk" : title;
        } catch (Exception e) {
            return "Predvolený alarm";
        }
    }

    private boolean notificationReady() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canScheduleExact() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
    }
}
