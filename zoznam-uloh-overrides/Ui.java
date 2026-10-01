package sk.zoznamuloh.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private static final String PREFS = "appearance_prefs";
    private static final String KEY_THEME = "theme";

    public static int NAVY;
    public static int NAVY_DARK;
    public static int CARD;
    public static int CARD_SOFT;
    public static int ACCENT;
    public static int ACCENT_LIGHT;
    public static int TEXT;
    public static int MUTED;
    public static int DANGER;
    public static int SUCCESS;

    static {
        applyPalette(0);
    }

    private Ui() {}

    public static int getTheme(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0);
    }

    public static void setTheme(Context context, int theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_THEME, theme).apply();
        applyPalette(theme);
    }

    public static void applyTheme(Context context) {
        applyPalette(getTheme(context));
    }

    private static void applyPalette(int theme) {
        if (theme == 1) {
            // Graphite
            NAVY = Color.rgb(27, 28, 31);
            NAVY_DARK = Color.rgb(17, 18, 20);
            CARD = Color.rgb(45, 47, 52);
            CARD_SOFT = Color.rgb(36, 38, 42);
            ACCENT = Color.rgb(102, 126, 234);
            ACCENT_LIGHT = Color.rgb(181, 192, 255);
            TEXT = Color.rgb(246, 247, 249);
            MUTED = Color.rgb(174, 179, 188);
            DANGER = Color.rgb(255, 117, 126);
            SUCCESS = Color.rgb(112, 218, 169);
        } else if (theme == 2) {
            // Light
            NAVY = Color.rgb(241, 245, 248);
            NAVY_DARK = Color.rgb(255, 255, 255);
            CARD = Color.rgb(255, 255, 255);
            CARD_SOFT = Color.rgb(232, 238, 243);
            ACCENT = Color.rgb(29, 126, 196);
            ACCENT_LIGHT = Color.rgb(74, 151, 207);
            TEXT = Color.rgb(28, 42, 54);
            MUTED = Color.rgb(103, 120, 134);
            DANGER = Color.rgb(205, 67, 76);
            SUCCESS = Color.rgb(48, 159, 104);
        } else {
            // Midnight blue
            NAVY = Color.rgb(8, 31, 48);
            NAVY_DARK = Color.rgb(5, 21, 34);
            CARD = Color.rgb(15, 56, 80);
            CARD_SOFT = Color.rgb(12, 45, 66);
            ACCENT = Color.rgb(36, 153, 218);
            ACCENT_LIGHT = Color.rgb(163, 218, 248);
            TEXT = Color.rgb(243, 249, 252);
            MUTED = Color.rgb(171, 199, 216);
            DANGER = Color.rgb(255, 112, 118);
            SUCCESS = Color.rgb(116, 218, 171);
        }
    }

    public static void applySystemBars(Activity activity) {
        applyTheme(activity);
        activity.getWindow().setStatusBarColor(NAVY_DARK);
        activity.getWindow().setNavigationBarColor(NAVY_DARK);
        int flags = 0;
        if (getTheme(activity) == 2) {
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, (int) radiusDp));
        return d;
    }

    public static GradientDrawable roundedStroke(int color, int strokeColor, int strokeDp, float radiusDp, Context c) {
        GradientDrawable d = rounded(color, radiusDp, c);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    public static TextView text(Context c, String value, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFontFeatureSettings("kern");
        return v;
    }

    public static TextView button(Context c, String value, int bg, int fg) {
        TextView v = text(c, value, 15, fg);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setBackground(rounded(bg, 18, c));
        v.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static void margin(LinearLayout.LayoutParams lp, Context c, int l, int t, int r, int b) {
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static void setVisible(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
