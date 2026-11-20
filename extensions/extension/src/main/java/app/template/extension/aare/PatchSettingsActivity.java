package app.template.extension.aare;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Settings" screen of the patches, drawn with plain views in the look of AARE's own settings:
 * its teal palette, a back arrow and title, and a rounded card of switches with a short description.
 */
public final class PatchSettingsActivity extends Activity {

    /** AARE's palette (net.prototux.aare.ui.theme.AareTheme), night and day. */
    private static final class Palette {
        final int background, surfaceContainer, onSurface, onSurfaceVariant, primary, onPrimary, outline, outlineVariant;

        Palette(boolean night) {
            if (night) {
                background = 0xFF0B0F0F;
                surfaceContainer = 0xFF161D1C;
                onSurface = 0xFFE3E9E7;
                onSurfaceVariant = 0xFFAFBAB7;
                primary = 0xFF3DDBC6;
                onPrimary = 0xFF00201C;
                outline = 0xFF5E6B68;
                outlineVariant = 0xFF2E3836;
            } else {
                background = 0xFFF3F7F6;
                surfaceContainer = 0xFFE6ECEA;
                onSurface = 0xFF161D1C;
                onSurfaceVariant = 0xFF3F4947;
                primary = 0xFF00695C;
                onPrimary = 0xFFFFFFFF;
                outline = 0xFF6F7977;
                outlineVariant = 0xFFBEC9C6;
            }
        }
    }

    private Palette colors;
    private final List<Runnable> refreshers = new ArrayList<>();

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
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), 0, dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(card());

        TextView note = text(PatchSettings.phrase(
                "These switches come from the Morphe patches installed in AARE. Each new release of the patches adds "
                        + "its own here.",
                "Questi interruttori arrivano dalle patch Morphe installate in AARE. Ogni nuova release delle patch "
                        + "aggiunge qui i suoi."), 12, colors.onSurfaceVariant, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(16);
        note.setPadding(dp(8), 0, dp(8), 0);
        content.addView(note, noteParams);

        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The location permission may have changed in the system settings.
        for (Runnable r : refreshers) r.run();
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        ImageView back = new ImageView(this);
        back.setImageDrawable(new ArrowBack(colors.onSurface, dp(2)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription(PatchSettings.phrase("Back", "Indietro"));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x33)), null, oval()));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(PatchSettings.phrase("Settings", "Settings"), 22, colors.onSurface, false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        bar.addView(title, titleParams);
        return bar;
    }

    /** The rounded card of switches, like AARE's Section. */
    private View card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colors.surfaceContainer);
        shape.setCornerRadius(dp(24));
        card.setBackground(shape);
        card.setClipToOutline(true);
        card.setPadding(dp(20), dp(20), dp(20), dp(10));

        card.addView(text(PatchSettings.phrase("Morphe patches", "Patch Morphe"), 16, colors.onSurface, true));
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));

        List<PatchSettings.Feature> features = PatchSettings.features();
        if (features.isEmpty()) {
            card.addView(text(PatchSettings.phrase("No patch with settings is installed.",
                    "Nessuna patch con impostazioni installata."), 12, colors.onSurfaceVariant, false));
        }
        for (PatchSettings.Feature f : features) card.addView(row(f));
        return card;
    }

    /** A switch with its title and description, like AARE's Toggle. */
    private View row(PatchSettings.Feature feature) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, null));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(feature.title, 16, colors.onSurface, false));
        texts.addView(text(feature.summary, 12, colors.onSurfaceVariant, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{colors.onPrimary, colors.outline}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{colors.primary, colors.outlineVariant}));
        toggle.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
        toggle.setClickable(false);
        toggle.setFocusable(false);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.leftMargin = dp(12);
        row.addView(toggle, switchParams);

        if (feature.permission) {
            // Android grants this one: the switch shows it, and the row opens the permission.
            Runnable refresh = () -> toggle.setChecked(
                    checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
            refreshers.add(refresh);
            refresh.run();
            row.setOnClickListener(v -> openAppSettings());
        } else {
            toggle.setChecked(PatchSettings.isEnabled(this, feature.key));
            row.setOnClickListener(v -> {
                boolean on = !toggle.isChecked();
                toggle.setChecked(on);
                PatchSettings.setEnabled(this, feature.key, on);
            });
        }
        return row;
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
        } catch (Throwable ignored) {
        }
    }

    // region Helpers

    private TextView text(String value, int sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static Drawable oval() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xFFFFFFFF);
        return d;
    }

    /** The rounded "arrow back" of AARE's top bar, drawn as a 24 dp icon. */
    private static final class ArrowBack extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        ArrowBack(int color, int stroke) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            size = stroke * 12;
        }

        @Override
        public void draw(Canvas canvas) {
            float u = getBounds().width() / 24f;
            float x0 = getBounds().left, y0 = getBounds().top;
            Path p = new Path();
            p.moveTo(x0 + 19 * u, y0 + 12 * u);
            p.lineTo(x0 + 5 * u, y0 + 12 * u);
            p.moveTo(x0 + 11 * u, y0 + 6 * u);
            p.lineTo(x0 + 5 * u, y0 + 12 * u);
            p.lineTo(x0 + 11 * u, y0 + 18 * u);
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

    // endregion
}
