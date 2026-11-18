package com.flashlink.p2p;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Material 3 深色主题组件（纯Java，零依赖） */
final class M3 {
    // M3 深色配色
    static final int PRIMARY = Color.parseColor("#D0BCFF");
    static final int ON_PRIMARY = Color.parseColor("#381E72");
    static final int PRIMARY_CONTAINER = Color.parseColor("#4F378B");
    static final int ON_PRIMARY_CONTAINER = Color.parseColor("#EADDFF");
    static final int SURFACE = Color.parseColor("#141218");
    static final int SURFACE_VARIANT = Color.parseColor("#302D38");
    static final int ON_SURFACE = Color.parseColor("#E6E1E5");
    static final int ON_SURFACE_VARIANT = Color.parseColor("#CAC4D0");
    static final int OUTLINE = Color.parseColor("#938F99");
    static final int ERROR = Color.parseColor("#F2B8B5");
    static final int ERROR_CONTAINER = Color.parseColor("#4C1D1A");
    static final int SUCCESS = Color.parseColor("#6DD58C");

    private M3() {}

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static Button filledButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(ON_PRIMARY);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setBackground(rounded(PRIMARY, 20, c));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return b;
    }

    static Button outlinedButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(PRIMARY);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        GradientDrawable d = rounded(Color.TRANSPARENT, 20, c);
        d.setStroke(dp(c, 1), OUTLINE);
        b.setBackground(d);
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(SURFACE_VARIANT, 16, c));
        box.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        return box;
    }

    static TextView headline(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ON_SURFACE);
        t.setTextSize(24);
        t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ON_SURFACE_VARIANT);
        t.setTextSize(14);
        t.setLineSpacing(dp(c, 4), 1);
        return t;
    }

    static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(PRIMARY);
        t.setTextSize(12);
        t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static EditText textField(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(ON_SURFACE);
        e.setHintTextColor(OUTLINE);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setBackground(rounded(SURFACE_VARIANT, 12, c));
        e.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        return e;
    }

    static View space(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, hDp)));
        return v;
    }
}