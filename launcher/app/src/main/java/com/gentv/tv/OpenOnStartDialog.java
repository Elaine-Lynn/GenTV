package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The startup-app picker as a sheet in the control center's smoked glass: Stay on Home, then every launchable app.
 * Focus turns a row white like the control center's tiles; OK saves at once, Back leaves the choice as it was.
 */
final class OpenOnStartDialog {
    private static final int INK = 0xFFF5F7FA;
    private static final int INK_DARK = 0xFF121418;
    private static final int INK_SOFT = 0x99FFFFFF;
    private static final int INK_FAINT = 0x73FFFFFF;

    private final Activity activity;
    private final float density;
    private final boolean motion;
    private final Typeface medium, regular;
    private final Dialog dialog;
    private final String current;
    private final List<ImageView> appIcons = new ArrayList<>();
    private volatile boolean closed;
    private View initialFocus;

    static void show(Activity activity) {
        new OpenOnStartDialog(activity).dialog.show();
    }

    private OpenOnStartDialog(Activity activity) {
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;
        Config config = Config.load(activity);
        motion = config.animations && !config.reducedMotion;
        medium = UiFont.get(activity, R.string.font_medium);
        regular = UiFont.get(activity, R.string.font_regular);
        current = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "");
        Map<String, String> apps = OpenOnStart.launchableApps(activity);

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        // The sheet clips its children: the list must not draw its rows past its own bottom edge.
        sheet.setPadding(dp(22), dp(22), dp(22), dp(16));
        sheet.setBackground(QuickSettingsGlass.dialogSurface(26 * density, density));

        sheet.addView(header());

        LimitedScroll scroll = new LimitedScroll(activity,
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * .58f));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Room for the focused row's 1.02 zoom, so its edge is never cut by the scroll's bounds.
        scroll.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        scrollParams.topMargin = dp(14);
        scrollParams.leftMargin = scrollParams.rightMargin = -dp(4);
        sheet.addView(scroll, scrollParams);

        View off = row("", activity.getString(R.string.open_on_start_off_row),
                activity.getString(R.string.open_on_start_off_sub));
        list.addView(off, params(0));
        initialFocus = off;

        TextView section = text(activity.getString(R.string.open_on_start_apps, apps.size()), 11.5f, INK_FAINT, medium);
        if (Build.VERSION.SDK_INT >= 21) section.setLetterSpacing(.06f);
        section.setPadding(dp(4), 0, 0, 0);
        LinearLayout.LayoutParams sectionParams = params(18);
        sectionParams.bottomMargin = dp(2);
        list.addView(section, sectionParams);

        for (Map.Entry<String, String> app : apps.entrySet()) {
            boolean chosen = app.getKey().equals(current);
            View row = row(app.getKey(), app.getValue(),
                    chosen ? activity.getString(R.string.open_on_start_current) : null);
            list.addView(row, params(8));
            if (chosen) initialFocus = row;
        }

        TextView footer = text(activity.getString(R.string.open_on_start_remote_hint), 11.5f, INK_FAINT, regular);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(12);
        sheet.addView(footer, footerParams);

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> closed = true);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(.42f);
                int available = activity.getResources().getDisplayMetrics().widthPixels - dp(48);
                window.setLayout(Math.min(available, dp(440)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            initialFocus.requestFocus();
            if (motion) {
                sheet.setAlpha(0f);
                sheet.setTranslationY(8 * density);
                sheet.animate().alpha(1f).translationY(0f).setDuration(180)
                        .setInterpolator(CompatApi.easeOut()).start();
            }
        });
        loadIcons(new ArrayList<>(apps.keySet()));
    }

    // ---- header: glyph badge, title and when it applies ------------------------------------------------------

    private View header() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(activity);
        badge.setBackground(shape(21, 0x18FFFFFF, 0x22FFFFFF));
        ImageView glyph = new ImageView(activity);
        glyph.setImageResource(R.drawable.ic_qs_startup);
        CompatApi.imageTint(glyph, ColorStateList.valueOf(INK));
        badge.addView(glyph, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(activity.getString(R.string.open_on_start_title), 17.5f, 0xFFFFFFFF, medium));
        TextView hint = text(activity.getString(R.string.open_on_start_hint), 12.5f, INK_SOFT, regular);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(2);
        words.addView(hint, hintParams);
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        wordsParams.leftMargin = dp(14);
        header.addView(words, wordsParams);
        return header;
    }

    // ---- rows: a white pill on focus, a quiet glass pill otherwise --------------------------------------------

    private View row(String pkg, String label, String note) {
        boolean chosen = pkg.equals(current);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(10), dp(8), dp(14), dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        CompatApi.disableDefaultFocusHighlight(row);
        StateListDrawable fill = new StateListDrawable();
        fill.addState(new int[]{android.R.attr.state_focused}, shape(16, 0xF7FFFFFF, 0));
        fill.addState(new int[]{android.R.attr.state_pressed}, shape(16, 0xDDFFFFFF, 0));
        fill.addState(new int[]{}, chosen ? shape(16, 0x26FFFFFF, 0x40FFFFFF) : shape(16, 0x12FFFFFF, 0x1EFFFFFF));
        row.setBackground(fill);

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (pkg.isEmpty()) {
            icon.setImageResource(R.drawable.ic_qs_home);
            icon.setPadding(dp(7), dp(7), dp(7), dp(7));
            icon.setBackground(shape(10, 0x1AFFFFFF, 0));
            CompatApi.imageTint(icon, ColorStateList.valueOf(INK));
        } else {
            icon.setTag(pkg);
            appIcons.add(icon);
            CompatApi.roundClip(icon, 8 * density);
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 14.5f, INK, medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        TextView sub = null;
        if (note != null) {
            sub = text(note, 11.5f, INK_SOFT, regular);
            sub.setSingleLine(true);
            words.addView(sub);
        }
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        wordsParams.leftMargin = dp(12);
        wordsParams.rightMargin = dp(10);
        row.addView(words, wordsParams);

        Mark mark = new Mark(activity, chosen);
        row.addView(mark, new LinearLayout.LayoutParams(dp(20), dp(20)));

        final TextView subtitle = sub;
        row.setOnFocusChangeListener((v, focused) -> {
            name.setTextColor(focused ? INK_DARK : INK);
            if (subtitle != null) subtitle.setTextColor(focused ? 0x99121418 : INK_SOFT);
            if (pkg.isEmpty()) CompatApi.imageTint(icon, ColorStateList.valueOf(focused ? INK_DARK : INK));
            if (pkg.isEmpty()) icon.setBackground(shape(10, focused ? 0x14000000 : 0x1AFFFFFF, 0));
            mark.setInverted(focused);
            CompatApi.elevation(row, focused ? 6 * density : 0);
            row.animate().cancel();
            float scale = focused ? 1.02f : 1f;
            if (motion) row.animate().scaleX(scale).scaleY(scale).setDuration(160)
                    .setInterpolator(CompatApi.easeOut()).start();
            else { row.setScaleX(scale); row.setScaleY(scale); }
        });
        row.setOnClickListener(v -> choose(pkg, label));
        return row;
    }

    private void choose(String pkg, String label) {
        if (!OpenOnStart.save(activity, pkg)) {
            Toast.makeText(activity, R.string.open_on_start_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, pkg.isEmpty() ? activity.getString(R.string.open_on_start_removed_generic)
                : activity.getString(R.string.open_on_start_saved, label), Toast.LENGTH_SHORT).show();
        dialog.dismiss();
    }

    // ---- app icons, off the main thread ----------------------------------------------------------------------

    private void loadIcons(List<String> packages) {
        PackageManager pm = activity.getPackageManager();
        new Thread(() -> {
            for (String pkg : packages) {
                if (closed) return;
                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(pkg);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                activity.runOnUiThread(() -> {
                    if (closed) return;
                    for (ImageView v : appIcons) if (pkg.equals(v.getTag())) v.setImageDrawable(icon);
                });
            }
        }, "open-on-start-icons").start();
    }

    // ---- small parts -----------------------------------------------------------------------------------------

    private TextView text(String value, float sp, int color, Typeface face) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private LinearLayout.LayoutParams params(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(topDp);
        return p;
    }

    private GradientDrawable shape(int radiusDp, int fill, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * density);
        if (border != 0) d.setStroke(Math.max(1, dp(1)), border);
        return d;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** A scroll that grows with its list up to a cap, so a short list keeps the sheet compact. */
    private static final class LimitedScroll extends ScrollView {
        private final int cap;

        LimitedScroll(Context c, int cap) {
            super(c);
            this.cap = cap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    /** The row's radio: a thin ring, or a filled disc with a check for the saved choice; inverted on a white row. */
    private static final class Mark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = new Path();
        private final boolean on;
        private boolean inverted;

        Mark(Context c, boolean on) {
            super(c);
            this.on = on;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setInverted(boolean inverted) {
            if (this.inverted == inverted) return;
            this.inverted = inverted;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float r = Math.min(getWidth(), getHeight()) / 2f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (!on) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setColor(inverted ? 0x55121418 : 0x55FFFFFF);
                canvas.drawCircle(cx, cy, r - d, paint);
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(inverted ? INK_DARK : 0xFFFFFFFF);
            canvas.drawCircle(cx, cy, r, paint);
            check.reset();
            check.moveTo(cx - r * .42f, cy + r * .02f);
            check.lineTo(cx - r * .10f, cy + r * .34f);
            check.lineTo(cx + r * .45f, cy - r * .30f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * d);
            paint.setColor(inverted ? 0xFFFFFFFF : INK_DARK);
            canvas.drawPath(check, paint);
        }
    }
}
