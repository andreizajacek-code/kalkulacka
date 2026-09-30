package sk.zoznamuloh.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int NAVY = Color.rgb(7, 39, 61);
    public static final int NAVY_DARK = Color.rgb(5, 29, 47);
    public static final int CARD = Color.rgb(13, 63, 94);
    public static final int CARD_SOFT = Color.rgb(11, 52, 79);
    public static final int ACCENT = Color.rgb(30, 150, 211);
    public static final int ACCENT_LIGHT = Color.rgb(166, 221, 251);
    public static final int TEXT = Color.rgb(241, 248, 252);
    public static final int MUTED = Color.rgb(170, 198, 216);
    public static final int DANGER = Color.rgb(255, 112, 118);
    public static final int SUCCESS = Color.rgb(116, 218, 171);

    private Ui() {}

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
