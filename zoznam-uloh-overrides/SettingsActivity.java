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
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applySystemBars(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        Ui.applyTheme(this);

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
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 34));
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
        LinearLayout.LayoutParams vibLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(vibLp, this, 0, 12, 0, 0);
        soundCard.addView(vibrate, vibLp);
        vibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVibration(this, isChecked);
            NotificationHelper.ensureChannels(this);
        });

        addSectionTitle("OPRÁVNENIA");
        addPermissionCard("Notifikácie", "Zvuk a upozornenia na termíny", notificationReady(), () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        });

        addPermissionCard("Panel nad aplikáciami", "Aby pripomienka ostala na ploche, kým ju vybavíš", Settings.canDrawOverlays(this), () -> {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        });

        addPermissionCard("Presné alarmy", "Aby upozornenie prišlo presne v nastavenom čase", canScheduleExact(), () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        });

        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.applySystemBars(this);
        if (firstResume) {
            firstResume = false;
        } else {
            setContentView(buildUi());
        }
    }

    private void addSectionTitle(String text) {
        TextView t = Ui.text(this, text, 13, Ui.MUTED);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(lp, this, 2, 16, 0, 8);
        content.addView(t, lp);
    }

    private void addThemeOption(int id, String name, String description) {
        boolean selected = Ui.getTheme(this) == id;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
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
        d.setPadding(0, Ui.dp(this, 3), Ui.dp(this, 8), 0);
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

    private void addPermissionCard(String name, String description, boolean ready, Runnable action) {
        LinearLayout box = card();
        LinearLayout.LayoutParams lp = cardLp();
        Ui.margin(lp, this, 0, 0, 0, 9);
        content.addView(box, lp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView nameView = Ui.text(this, name, 15, Ui.TEXT);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(nameView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView status = Ui.text(this, ready ? "✓ Povolené" : "Nepovolené", 12, ready ? Ui.SUCCESS : Ui.DANGER);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = Ui.text(this, description, 12, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(descLp, this, 0, 5, 0, 0);
        box.addView(desc, descLp);

        if (!ready) {
            TextView allow = Ui.button(this, "POVOLIŤ", Ui.ACCENT, Ui.TEXT);
            LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 44));
            Ui.margin(allowLp, this, 0, 12, 0, 0);
            box.addView(allow, allowLp);
            allow.setOnClickListener(v -> action.run());
        }
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
