package app.template.extension.aare;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The "Settings" screen of the patches, drawn with plain views as a copy of AARE's own pages
 * (MainActivity's Section, AppGroup, AppRow and Material 3 Switch): same palette, sizes and spacing.
 */
public final class PatchSettingsActivity extends Activity {

    /** AARE's palette (net.prototux.aare.ui.theme.AareTheme), night and day. */
    private static final class Palette {
        final int background, surfaceContainer, surfaceContainerHighest, onSurface, onSurfaceVariant;
        final int primary, onPrimary, primaryContainer, onPrimaryContainer, outline;

        Palette(boolean night) {
            if (night) {
                background = 0xFF0B0F0F;
                surfaceContainer = 0xFF161D1C;
                surfaceContainerHighest = 0xFF26302E;
                onSurface = 0xFFE3E9E7;
                onSurfaceVariant = 0xFFAFBAB7;
                primary = 0xFF3DDBC6;
                onPrimary = 0xFF00201C;
                primaryContainer = 0xFF0E3B36;
                onPrimaryContainer = 0xFFB9F5EA;
                outline = 0xFF5E6B68;
            } else {
                background = 0xFFF3F7F6;
                surfaceContainer = 0xFFE6ECEA;
                surfaceContainerHighest = 0xFFD9E1DF;
                onSurface = 0xFF161D1C;
                onSurfaceVariant = 0xFF3F4947;
                primary = 0xFF00695C;
                onPrimary = 0xFFFFFFFF;
                primaryContainer = 0xFFB9F5EA;
                onPrimaryContainer = 0xFF00201C;
                outline = 0xFF6F7977;
            }
        }
    }

    /** Material 3 type scale: size in sp, letter spacing in sp, medium weight. */
    private enum Type {
        TITLE_LARGE(22, 0f, false),
        TITLE_MEDIUM(16, 0.15f, true),
        LABEL_LARGE(14, 0.1f, true),
        BODY_LARGE(16, 0.5f, false),
        BODY_SMALL(12, 0.4f, false);

        final int sp;
        final float tracking;
        final boolean medium;

        Type(int sp, float tracking, boolean medium) {
            this.sp = sp;
            this.tracking = tracking;
            this.medium = medium;
        }
    }

    private Palette colors;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        colors = new Palette(night);

        Window window = getWindow();
        window.getDecorView().setBackgroundColor(colors.background);
        if (Build.VERSION.SDK_INT < 35) {
            window.setStatusBarColor(colors.background);
            window.setNavigationBarColor(colors.background);
        }
        if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            window.getInsetsController().setSystemBarsAppearance(night ? 0 : light, light);
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(colors.background);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        page.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), 0, dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(section());

        setContentView(page);
    }

    /** Back arrow and title, as at the top of AARE's pages. */
    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        ImageView back = new ImageView(this);
        back.setImageDrawable(new ArrowBack(colors.onSurface, dp(24)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription(PatchSettings.phrase("Back", "Indietro"));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, oval(0xFFFFFFFF)));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("Settings", Type.TITLE_LARGE, colors.onSurface);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        bar.addView(title, titleParams);
        return bar;
    }

    /** One card like AARE's Section: title, hint, a group label and the rows. */
    private View section() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colors.surfaceContainer);
        shape.setCornerRadius(dp(24));
        card.setBackground(shape);
        card.setClipToOutline(true);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        card.addView(text(PatchSettings.phrase("Morphe patches", "Patch Morphe"), Type.TITLE_MEDIUM, colors.onSurface));
        TextView hint = text(PatchSettings.phrase(
                "Turn off the features you don't want in the car. Each new release of the patches adds its own here.",
                "Disattiva le funzioni che non vuoi in auto. Ogni nuova release delle patch aggiunge qui le sue."),
                Type.BODY_SMALL, colors.onSurfaceVariant);
        LinearLayout.LayoutParams hintParams = wrap();
        hintParams.topMargin = dp(8);
        card.addView(hint, hintParams);

        TextView group = text(PatchSettings.phrase("Installed patches", "Patch installate"), Type.LABEL_LARGE, colors.primary);
        LinearLayout.LayoutParams groupParams = wrap();
        groupParams.topMargin = dp(16);
        groupParams.bottomMargin = dp(4);
        card.addView(group, groupParams);

        List<PatchSettings.Feature> features = PatchSettings.features();
        if (features.isEmpty()) {
            card.addView(text(PatchSettings.phrase("No patch with settings is installed.",
                    "Nessuna patch con impostazioni installata."), Type.BODY_SMALL, colors.onSurfaceVariant));
        }
        for (PatchSettings.Feature f : features) card.addView(row(f));
        refreshDependents();

        // Technical state only (see PatchLog): for reporting a problem.
        TextView copy = text(PatchSettings.phrase("Copy diagnostics", "Copia diagnostica"), Type.LABEL_LARGE, colors.primary);
        copy.setPadding(0, dp(12), 0, dp(4));
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AARE Patches", diagnostics()));
            android.widget.Toast.makeText(this, PatchSettings.phrase("Copied", "Copiata"), android.widget.Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyParams = wrap();
        copyParams.topMargin = dp(8);
        card.addView(copy, copyParams);

        // The user's own data: it can be seen (copied) and deleted.
        TextView clear = text(PatchSettings.phrase("Delete diagnostics", "Cancella diagnostica"), Type.LABEL_LARGE, colors.primary);
        clear.setPadding(0, dp(12), 0, dp(4));
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage(PatchSettings.phrase("Delete the patches' diagnostics on this phone?",
                        "Cancellare la diagnostica delle patch su questo telefono?"))
                .setPositiveButton(PatchSettings.phrase("Delete", "Cancella"), (d, w) -> {
                    PatchLog.clear();
                    android.widget.Toast.makeText(this, PatchSettings.phrase("Deleted", "Cancellata"), android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(PatchSettings.phrase("Cancel", "Annulla"), null)
                .show());
        card.addView(clear, wrap());

        // Which patches these are: the release, or the test build's date, time and change.
        TextView version = text(PatchSettings.phrase("Patches: ", "Patch: ") + PatchVersion.LABEL, Type.BODY_SMALL, colors.onSurfaceVariant);
        LinearLayout.LayoutParams versionParams = wrap();
        versionParams.topMargin = dp(12);
        card.addView(version, versionParams);
        return card;
    }

    private String diagnostics() {
        StringBuilder b = new StringBuilder("AARE Patches diagnostics\n");
        b.append("patches=").append(PatchVersion.LABEL).append('\n');
        b.append("sdk=").append(Build.VERSION.SDK_INT);
        try {
            b.append(" aare=").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Throwable ignored) {
        }
        b.append('\n');
        for (PatchSettings.Feature f : PatchSettings.features()) {
            b.append(f.key).append('=').append(PatchSettings.isEnabled(this, f.key) ? "on" : "off").append('\n');
        }
        b.append("--- log\n").append(PatchLog.dump());
        return b.toString();
    }

    /** The rows of the settings that depend on another one, with the key they depend on. */
    private final java.util.Map<View, String> dependents = new java.util.LinkedHashMap<>();

    /** A setting that depends on one turned off is greyed out and can't be changed. */
    /** Rows of features this phone's Android version can't run: always greyed out. */
    private final java.util.Set<View> unavailable = new java.util.HashSet<>();

    private void refreshDependents() {
        for (java.util.Map.Entry<View, String> e : dependents.entrySet()) {
            if (unavailable.contains(e.getKey())) continue;
            boolean on = PatchSettings.isEnabled(this, e.getValue());
            e.getKey().setEnabled(on);
            e.getKey().setAlpha(on ? 1f : 0.38f);
        }
    }

    /** A row like AARE's AppRow: round icon, title and kind, switch. */
    private View row(PatchSettings.Feature feature) {
        LinearLayout row = new LinearLayout(this);
        if (feature.parent != null) {
            // Below the setting it depends on, indented.
            row.setPadding(dp(28), 0, 0, 0);
            dependents.put(row, feature.parent);
        }
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(row.getPaddingLeft(), dp(8), 0, dp(8));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, null));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new RoundIcon(feature.icon, colors.primaryContainer, colors.onPrimaryContainer, dp(40), dp(24)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(feature.title, Type.BODY_LARGE, colors.onSurface));
        texts.addView(text(feature.summary, Type.BODY_SMALL, colors.onSurfaceVariant));
        boolean available = PatchSettings.available(feature.key);
        if (!available) {
            texts.addView(text(PatchSettings.phrase("Needs Android 8 or later.", "Richiede Android 8 o successivo."),
                    Type.BODY_SMALL, colors.onSurface));
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(12);
        textParams.rightMargin = dp(12);
        row.addView(texts, textParams);

        M3Switch toggle = new M3Switch(this, colors);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(32)));

        toggle.setChecked(PatchSettings.isEnabled(this, feature.key), false);
        row.setOnClickListener(v -> {
            if (!row.isEnabled()) return;
            boolean on = !toggle.isChecked();
            toggle.setChecked(on, true);
            PatchSettings.setEnabled(this, feature.key, on);
            refreshDependents();
        });
        toggle.setOnClickListener(v -> row.performClick());
        if (!available) {
            unavailable.add(row);
            row.setEnabled(false);
            row.setAlpha(0.38f);
        }
        return row;
    }

    // region Helpers

    private TextView text(String value, Type type, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, type.sp);
        t.setLetterSpacing(type.tracking / type.sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        t.setTypeface(Typeface.create(type.medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static Drawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    // endregion

    // region Drawing

    /** Material 3 Switch: 52 x 32 dp track, 16 dp thumb off with an outline, 24 dp thumb on. */
    private static final class M3Switch extends View {
        private final Palette colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean checked;
        /** 0 off, 1 on. */
        private float position;
        private ValueAnimator animator;

        M3Switch(Context context, Palette colors) {
            super(context);
            this.colors = colors;
            setClickable(true);
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            if (animator != null) animator.cancel();
            float target = value ? 1f : 0f;
            if (!animate) {
                position = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(position, target);
            animator.setDuration(150);
            animator.addUpdateListener(a -> {
                position = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            float radius = h / 2f;

            // Track.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(colors.surfaceContainerHighest, colors.primary, position));
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (position < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(withAlpha(colors.outline, Math.round(255 * (1f - position))));
                rect.set(d, d, w - d, h - d);
                canvas.drawRoundRect(rect, radius - d, radius - d, paint);
            }

            // Thumb.
            float thumb = (16 + 8 * position) * d / 2f;
            float cx = radius + (w - 2 * radius) * position;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(colors.outline, colors.onPrimary, position));
            canvas.drawCircle(cx, h / 2f, thumb, paint);
        }

        private static int blend(int from, int to, float t) {
            int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
            int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
            int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
            int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /** A 40 dp circle in primaryContainer with a 24 dp Material icon, like AARE's app rows without an icon. */
    private static class RoundIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph;
        private final int background, foreground, size, glyphSize;

        RoundIcon(String pathData, int background, int foreground, int size, int glyphSize) {
            this.glyph = SvgPath.parse(pathData);
            this.background = background;
            this.foreground = foreground;
            this.size = size;
            this.glyphSize = glyphSize;
        }

        @Override
        public void draw(Canvas canvas) {
            float cx = getBounds().exactCenterX(), cy = getBounds().exactCenterY();
            paint.setColor(background);
            canvas.drawCircle(cx, cy, Math.min(getBounds().width(), getBounds().height()) / 2f, paint);
            if (glyph == null) return;
            Path p = new Path(glyph);
            Matrix m = new Matrix();
            float scale = glyphSize / 24f;
            m.setScale(scale, scale);
            m.postTranslate(cx - glyphSize / 2f, cy - glyphSize / 2f);
            p.transform(m);
            paint.setColor(foreground);
            canvas.drawPath(p, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Material's rounded "arrow back", as in AARE's top bar. */
    private static final class ArrowBack extends RoundIcon {
        ArrowBack(int color, int size) {
            super("M19,11H7.83l4.88,-4.88c0.39,-0.39 0.39,-1.03 0,-1.42 -0.39,-0.39 -1.02,-0.39 -1.41,0l-6.59,6.59"
                    + "c-0.39,0.39 -0.39,1.02 0,1.41l6.59,6.59c0.39,0.39 1.02,0.39 1.41,0 0.39,-0.39 0.39,-1.02 0,-1.41L7.83,13H19"
                    + "c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1z", 0x00000000, color, size, size);
        }
    }

    /** The subset of SVG path data that Material icons use without arcs: M L H V C S Q T Z. */
    private static final class SvgPath {
        private final String s;
        private int i;

        private SvgPath(String s) {
            this.s = s;
        }

        static Path parse(String data) {
            try {
                return new SvgPath(data).run();
            } catch (Throwable t) {
                return null;
            }
        }

        private Path run() {
            Path p = new Path();
            float x = 0, y = 0, startX = 0, startY = 0, lastCx = 0, lastCy = 0;
            char cmd = 0, prev = 0;
            while (true) {
                skip();
                if (i >= s.length()) break;
                char c = s.charAt(i);
                if (Character.isLetter(c)) {
                    cmd = c;
                    i++;
                } else if (cmd == 'M') {
                    cmd = 'L';
                } else if (cmd == 'm') {
                    cmd = 'l';
                }
                boolean rel = Character.isLowerCase(cmd);
                float ox = rel ? x : 0, oy = rel ? y : 0;
                switch (Character.toUpperCase(cmd)) {
                    case 'M':
                        x = ox + num();
                        y = oy + num();
                        p.moveTo(x, y);
                        startX = x;
                        startY = y;
                        break;
                    case 'L':
                        x = ox + num();
                        y = oy + num();
                        p.lineTo(x, y);
                        break;
                    case 'H':
                        x = ox + num();
                        p.lineTo(x, y);
                        break;
                    case 'V':
                        y = oy + num();
                        p.lineTo(x, y);
                        break;
                    case 'C': {
                        float x1 = ox + num(), y1 = oy + num(), x2 = ox + num(), y2 = oy + num();
                        x = ox + num();
                        y = oy + num();
                        p.cubicTo(x1, y1, x2, y2, x, y);
                        lastCx = x2;
                        lastCy = y2;
                        break;
                    }
                    case 'S': {
                        boolean smooth = "CcSs".indexOf(prev) >= 0;
                        float x1 = smooth ? 2 * x - lastCx : x, y1 = smooth ? 2 * y - lastCy : y;
                        float x2 = ox + num(), y2 = oy + num();
                        x = ox + num();
                        y = oy + num();
                        p.cubicTo(x1, y1, x2, y2, x, y);
                        lastCx = x2;
                        lastCy = y2;
                        break;
                    }
                    case 'Q': {
                        float x1 = ox + num(), y1 = oy + num();
                        x = ox + num();
                        y = oy + num();
                        p.quadTo(x1, y1, x, y);
                        lastCx = x1;
                        lastCy = y1;
                        break;
                    }
                    case 'T': {
                        boolean smooth = "QqTt".indexOf(prev) >= 0;
                        float x1 = smooth ? 2 * x - lastCx : x, y1 = smooth ? 2 * y - lastCy : y;
                        x = ox + num();
                        y = oy + num();
                        p.quadTo(x1, y1, x, y);
                        lastCx = x1;
                        lastCy = y1;
                        break;
                    }
                    case 'Z':
                        p.close();
                        x = startX;
                        y = startY;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported path command " + cmd);
                }
                prev = cmd;
            }
            return p;
        }

        private void skip() {
            while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
        }

        private float num() {
            skip();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) {
                    i++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && i + 1 < s.length()) {
                    i++;
                    if (s.charAt(i) == '-' || s.charAt(i) == '+') i++;
                } else {
                    break;
                }
            }
            return Float.parseFloat(s.substring(start, i));
        }
    }

    // endregion
}
