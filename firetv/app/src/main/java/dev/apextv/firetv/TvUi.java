package dev.apextv.firetv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvUi {
    static final int BG = Color.rgb(16, 17, 21), PANEL = Color.rgb(31, 33, 40), ACCENT = Color.rgb(255, 73, 62);
    static int dp(Activity a, float value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    static TextView text(Activity a, String value, int size) {
        TextView view = new TextView(a); view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(a, 5), 0, dp(a, 5)); return view;
    }
    static LinearLayout column(Activity a) {
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG); root.setPadding(dp(a, 44), dp(a, 22), dp(a, 44), dp(a, 20));
        return root;
    }
    static Button button(Activity a, String title, Runnable action) {
        Button button = new Button(a); button.setText(title); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(Color.WHITE); button.setPadding(dp(a, 14), 0, dp(a, 14), 0);
        button.setOnClickListener(v -> action.run()); button.setMinHeight(dp(a, 44));
        button.setOnFocusChangeListener((v, focus) -> styleButton(a, v, focus)); styleButton(a, button, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(a, 48)); params.setMargins(0, dp(a, 3), dp(a, 10), dp(a, 3));
        button.setLayoutParams(params); return button;
    }
    private static void styleButton(Activity a, View view, boolean focused) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(focused ? ACCENT : PANEL); bg.setCornerRadius(dp(a, 5));
        if (focused) bg.setStroke(dp(a, 2), Color.WHITE); view.setBackground(bg);
    }
    static LinearLayout row(Activity a) { LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    static void heading(Activity a, LinearLayout root, String title) {
        TextView brand = text(a, "APEX TV", 13); brand.setTextColor(ACCENT); brand.setLetterSpacing(0.18f); root.addView(brand);
        TextView heading = text(a, title, 29); heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
    }
    static String error(Throwable e) {
        if (e instanceof java.net.UnknownHostException) return "Could not reach F1 TV. Check the network connection.";
        if (e instanceof java.net.SocketTimeoutException) return "F1 TV took too long to respond. Please retry.";
        if (e instanceof java.io.IOException || e instanceof IllegalArgumentException) {
            String value = e.getMessage();
            // Never surface signed URLs or credentials from a network exception.
            if (value != null && value.length() <= 220 && !value.contains("http") && !value.contains("eyJ")) return value;
        }
        return "This request could not be completed (" + e.getClass().getSimpleName() + "). Please retry.";
    }
    private TvUi() {}
}
