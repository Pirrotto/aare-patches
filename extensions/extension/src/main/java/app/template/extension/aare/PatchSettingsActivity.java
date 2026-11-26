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

        for (View card : sections()) {
            LinearLayout.LayoutParams p = wrap();
            p.bottomMargin = dp(16); // AARE's Column(spacedBy(16.dp)) between Sections
            content.addView(card, p);
        }

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

    /** AARE's Section: a rounded card (24 dp, surfaceContainer, 20 dp padding) with a title. */
    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colors.surfaceContainer);
        shape.setCornerRadius(dp(24));
        card.setBackground(shape);
        card.setClipToOutline(true);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(text(title, Type.TITLE_MEDIUM, colors.onSurface));
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        return card;
    }

    /** Which card a setting goes in, as AARE groups its own settings. */
    private static String group(String key) {
        if (PatchSettings.VOICE_REPLY.equals(key) || PatchSettings.VOICE_REPLY_ASK.equals(key)
                || PatchSettings.VOICE_REPLY_BEEP.equals(key) || PatchSettings.VOICE_MESSAGES.equals(key)
                || PatchSettings.RADIO_PAUSE.equals(key)) {
            return PatchSettings.phrase("Messages", "Messaggi");
        }
        if (PatchSettings.TUNNEL_NAVIGATION.equals(key) || PatchSettings.LOCATION_CHECK.equals(key)
                || PatchSettings.LOCATION_FIX.equals(key) || PatchSettings.TUNNEL_MAPS_AUTO.equals(key)
                || PatchSettings.TUNNEL_MAPS_HERE.equals(key)) {
            return PatchSettings.phrase("Location", "Posizione");
        }
        if (PatchSettings.ASSISTANT_CAR_SCREEN.equals(key) || PatchSettings.ASSISTANT_CAR_SCREEN_DRIVING.equals(key)) {
            return PatchSettings.phrase("Assistant", "Assistente");
        }
        if (PatchSettings.MAP_AREA_NOTE.equals(key)) {
            return PatchSettings.phrase("Car screen", "Schermo dell'auto");
        }
        return PatchSettings.phrase("Other", "Altro");
    }

    /** The cards: one per group of settings, then the tunnel maps and the diagnostics, as AARE's pages. */
    private List<View> sections() {
        List<View> out = new java.util.ArrayList<>();
        List<PatchSettings.Feature> features = PatchSettings.features();
        java.util.Map<String, LinearLayout> cards = new java.util.LinkedHashMap<>();
        for (PatchSettings.Feature f : features) {
            String g = group(f.key);
            LinearLayout c = cards.get(g);
            if (c == null) {
                c = card(g);
                cards.put(g, c);
            }
            c.addView(row(f));
            for (View check : requirements(f.key)) c.addView(check);
        }
        if (features.isEmpty()) {
            LinearLayout c = card(PatchSettings.phrase("Morphe patches", "Patch Morphe"));
            c.addView(text(PatchSettings.phrase("No patch with settings is installed.",
                    "Nessuna patch con impostazioni installata."), Type.BODY_SMALL, colors.onSurfaceVariant));
            out.add(c);
        }
        out.addAll(cards.values());
        refreshDependents();

        if (PatchSettings.isRegistered(PatchSettings.TUNNEL_NAVIGATION)) {
            LinearLayout maps = card(PatchSettings.phrase("Tunnel maps", "Mappe delle gallerie"));
            maps.addView(mapsRow());
            out.add(maps);
            TunnelMaps.refresh(this);
        }

        LinearLayout diag = card(PatchSettings.phrase("Diagnostics", "Diagnostica"));
        // Technical state only (see PatchLog): for reporting a problem.
        diag.addView(action(PatchSettings.phrase("Copy diagnostics", "Copia diagnostica"), v -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AARE Patches", diagnostics()));
            toast(PatchSettings.phrase("Copied", "Copiata"));
        }));
        // The user's own data: it can be seen (copied) and deleted.
        diag.addView(action(PatchSettings.phrase("Delete diagnostics", "Cancella diagnostica"), v -> new android.app.AlertDialog.Builder(this)
                .setMessage(PatchSettings.phrase("Delete the patches' diagnostics on this phone?",
                        "Cancellare la diagnostica delle patch su questo telefono?"))
                .setPositiveButton(PatchSettings.phrase("Delete", "Cancella"), (d, w) -> {
                    PatchLog.clear();
                    toast(PatchSettings.phrase("Deleted", "Cancellata"));
                })
                .setNegativeButton(PatchSettings.phrase("Cancel", "Annulla"), null)
                .show()));
        // Which patches these are: the release, or the test build's date, time and change.
        TextView version = text(PatchSettings.phrase("Patches: ", "Patch: ") + PatchVersion.LABEL, Type.BODY_SMALL, colors.onSurfaceVariant);
        LinearLayout.LayoutParams versionParams = wrap();
        versionParams.topMargin = dp(8);
        diag.addView(version, versionParams);
        out.add(diag);
        return out;
    }

    /** A text button in AARE's primary colour (labelLarge), like its TextButtons. */
    private View action(String label, View.OnClickListener click) {
        TextView t = text(label, Type.LABEL_LARGE, colors.primary);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, null));
        t.setOnClickListener(click);
        return t;
    }

    // region Tunnel maps

    private TextView mapsSummary;

    /** "Tunnel maps": which countries are on the phone, and a tap to choose them. */
    private View mapsRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        mapsSummary = text("", Type.BODY_SMALL, colors.onSurfaceVariant);
        box.addView(mapsSummary);
        updateMapsSummary();
        box.addView(action(PatchSettings.phrase("Choose the countries", "Scegli i paesi"), v -> loadMapList()));
        return box;
    }

    private void updateMapsSummary() {
        List<String> on = TunnelMaps.installed(this);
        StringBuilder b = new StringBuilder();
        for (String c : on) {
            if (b.length() > 0) b.append(", ");
            b.append(TunnelMaps.name(c)).append(" (").append(kb(TunnelMaps.size(this, c))).append(')');
        }
        mapsSummary.setText(on.isEmpty()
                ? PatchSettings.phrase("None on the phone. Tap to choose the countries. Data © OpenStreetMap contributors.",
                        "Nessuna sul telefono. Tocca per scegliere i paesi. Dati © OpenStreetMap contributors.")
                : PatchSettings.phrase("On the phone: ", "Sul telefono: ") + b
                        + PatchSettings.phrase(". Data © OpenStreetMap contributors.", ". Dati © OpenStreetMap contributors."));
    }

    private static String kb(long bytes) {
        return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    /** Asked by the user: the signed list of countries is loaded, then shown to choose from. */
    private void loadMapList() {
        if (!TunnelMaps.begin()) {
            toast(PatchSettings.phrase("The maps are being downloaded, try again in a moment.",
                    "Le mappe si stanno scaricando, riprova tra poco."));
            return;
        }
        toast(PatchSettings.phrase("Loading the list of countries…", "Carico l'elenco dei paesi…"));
        new Thread(() -> {
            try {
                java.util.Map<String, TunnelMapFile.Entry> index = TunnelMaps.index();
                runOnUiThread(() -> chooseMaps(index));
            } catch (TunnelMaps.Failure f) {
                PatchLog.add("maps", "list not loaded (" + f.getMessage() + ")");
                runOnUiThread(() -> failed(f));
            } finally {
                TunnelMaps.end();
            }
        }, "aare-tunnel-maps").start();
    }

    private void chooseMaps(java.util.Map<String, TunnelMapFile.Entry> index) {
        if (isFinishing()) return;
        List<String> installed = TunnelMaps.installed(this);
        java.util.List<String> codes = new java.util.ArrayList<>(index.keySet());
        for (String c : installed) if (!codes.contains(c)) codes.add(c); // on the phone, gone from the list
        java.util.Collections.sort(codes, (a, b) -> TunnelMaps.name(a).compareToIgnoreCase(TunnelMaps.name(b)));
        String[] labels = new String[codes.size()];
        boolean[] checked = new boolean[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            String c = codes.get(i);
            TunnelMapFile.Entry e = index.get(c);
            labels[i] = TunnelMaps.name(c) + (e != null ? " (" + kb(e.bytes) + ")" : "");
            checked[i] = installed.contains(c);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(PatchSettings.phrase("Tunnel maps", "Mappe delle gallerie"))
                .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(PatchSettings.phrase("Save", "Salva"), (d, w) -> applyMaps(codes, checked, index))
                .setNegativeButton(PatchSettings.phrase("Cancel", "Annulla"), null)
                .setNeutralButton(PatchSettings.phrase("From file…", "Da file…"), (d, w) -> pickMapFiles())
                .show();
    }

    private static final int PICK_MAPS = 4711;

    /** Android's file picker (no permission): the user picks a map and its .sig together. */
    private void pickMapFiles() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, PICK_MAPS);
            toast(PatchSettings.phrase("Pick the country's .aaretun file", "Scegli il file .aaretun del paese"));
        } catch (android.content.ActivityNotFoundException e) {
            toast(PatchSettings.phrase("No file picker on this phone.", "Su questo telefono manca la scelta dei file."));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MAPS || resultCode != RESULT_OK || data == null) return;
        java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (!TunnelMaps.begin()) {
            toast(PatchSettings.phrase("The maps are being downloaded, try again in a moment.",
                    "Le mappe si stanno scaricando, riprova tra poco."));
            return;
        }
        new Thread(() -> {
            String result;
            try {
                result = installPicked(uris);
            } finally {
                TunnelMaps.end();
            }
            final String r = result;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                updateMapsSummary();
                toast(r);
            });
        }, "aare-tunnel-maps").start();
    }

    /** Installs each picked map file ("Switzerland.aaretun"). Blocking. Returns the message to show. */
    private String installPicked(java.util.List<android.net.Uri> uris) {
        java.util.List<String> added = new java.util.ArrayList<>();
        int bad = 0;
        boolean signature = false;
        for (android.net.Uri u : uris) {
            try {
                added.add(TunnelMaps.install(this, readPicked(u, TunnelMapFile.MAX_FILE_BYTES)));
            } catch (TunnelMaps.Failure f) {
                bad++;
                signature |= f.signature;
                PatchLog.add("maps", "a map from a file failed (" + f.getMessage() + ")");
            }
        }
        PatchLog.add("maps", added.size() + " maps added from files, " + bad + " refused");
        String done = mapsMessage(added, java.util.Collections.emptyList());
        if (bad == 0) return done;
        String refused = signature
                ? PatchSettings.phrase(bad + " refused: wrong signature", bad + " rifiutate: firma sbagliata")
                : PatchSettings.phrase(bad + " refused: not a tunnel map", bad + " rifiutate: non sono mappe delle gallerie");
        return added.isEmpty() ? refused : done + " · " + refused;
    }

    /** Short: "Switzerland downloaded", "3 maps downloaded", "1 downloaded, 1 deleted", "Already up to date". */
    private static String mapsMessage(java.util.List<String> got, java.util.List<String> removed) {
        int g = got.size(), r = removed.size();
        if (g == 0 && r == 0) return PatchSettings.phrase("Already up to date", "Già aggiornate");
        if (r == 0) {
            return g == 1 ? TunnelMaps.name(got.get(0)) + PatchSettings.phrase(" downloaded", " scaricata")
                    : g + PatchSettings.phrase(" maps downloaded", " mappe scaricate");
        }
        if (g == 0) {
            return r == 1 ? TunnelMaps.name(removed.get(0)) + PatchSettings.phrase(" deleted", " cancellata")
                    : r + PatchSettings.phrase(" maps deleted", " mappe cancellate");
        }
        return PatchSettings.phrase(g + " downloaded, " + r + " deleted", g + (g == 1 ? " scaricata, " : " scaricate, ")
                + r + (r == 1 ? " cancellata" : " cancellate"));
    }

    private String displayName(android.net.Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException e) {
            PatchLog.add("maps", "a picked file's name can't be read");
        }
        return null;
    }

    /** A picked file, at most [max] bytes (a bigger one is refused, not cut). */
    private byte[] readPicked(android.net.Uri u, int max) throws TunnelMaps.Failure {
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            if (in == null) throw new TunnelMaps.Failure("the file can't be opened", false);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > max) throw new TunnelMaps.Failure("the file is too big", false);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (java.io.IOException | SecurityException e) {
            throw new TunnelMaps.Failure("the file can't be opened", false);
        }
    }

    /** Downloads the countries ticked and not on the phone, deletes the ones unticked. */
    private void applyMaps(java.util.List<String> codes, boolean[] checked, java.util.Map<String, TunnelMapFile.Entry> index) {
        if (!TunnelMaps.begin()) {
            toast(PatchSettings.phrase("The maps are being downloaded, try again in a moment.",
                    "Le mappe si stanno scaricando, riprova tra poco."));
            return;
        }
        java.util.List<String> installed = TunnelMaps.installed(this);
        new Thread(() -> {
            java.util.List<String> got = new java.util.ArrayList<>(), removed = new java.util.ArrayList<>();
            TunnelMaps.Failure failure = null;
            try {
                for (int i = 0; i < codes.size(); i++) {
                    String c = codes.get(i);
                    if (checked[i] && !TunnelMaps.current(this, c, index)) {
                        try {
                            TunnelMaps.download(this, c, index);
                            got.add(c);
                        } catch (TunnelMaps.Failure f) {
                            failure = f;
                            PatchLog.add("maps", "a map failed (" + f.getMessage() + ")");
                        }
                    } else if (!checked[i] && installed.contains(c)) {
                        if (TunnelMaps.delete(this, c)) removed.add(c);
                    }
                }
            } finally {
                TunnelMaps.end();
            }
            PatchLog.add("maps", got.size() + " maps downloaded, " + removed.size() + " deleted" + (failure != null ? ", some failed" : ""));
            final TunnelMaps.Failure f = failure;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                updateMapsSummary();
                if (f != null) {
                    failed(f);
                } else {
                    toast(mapsMessage(got, removed));
                }
            });
        }, "aare-tunnel-maps").start();
    }

    private void failed(TunnelMaps.Failure f) {
        if (isFinishing()) return;
        new android.app.AlertDialog.Builder(this)
                .setMessage(f.signature
                        ? PatchSettings.phrase("A tunnel map didn't pass the signature check, so it wasn't used. Nothing changed on the phone.",
                                "Una mappa delle gallerie non ha passato il controllo della firma e non è stata usata. Sul telefono non è cambiato niente.")
                        : PatchSettings.phrase("The tunnel maps couldn't be loaded (" + f.getMessage() + "). Check the connection and try again.",
                                "Non è stato possibile caricare le mappe delle gallerie (" + f.getMessage() + "). Controlla la connessione e riprova."))
                .setPositiveButton("OK", null)
                .show();
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    // endregion

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

    /** Rows of features this phone's Android version can't run: always greyed out. */
    private final java.util.Set<View> unavailable = new java.util.HashSet<>();

    // region Requirements (AARE's Check)

    private static final String CHECK_CIRCLE = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z";
    private static final String RADIO_UNCHECKED = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z";
    private static final String SHIZUKU = "moe.shizuku.privileged.api";
    /** AARE's own settings file and its "assistants without a car app on the car screen" switch (Settings.kt). */
    private static final String AARE_SETTINGS = "settings";
    private static final String AARE_ASSISTANT_ON_CAR_SCREEN = "assistant_on_car_screen";

    /** Brings each requirement row up to date (after a switch changed, or back from Android's settings). */
    private final java.util.List<Runnable> requirementUpdates = new java.util.ArrayList<>();

    @Override
    protected void onResume() {
        super.onResume();
        refreshDependents();
    }

    /** What a setting needs from the phone, as AARE's Check rows show it; empty if nothing. */
    private List<View> requirements(String key) {
        List<View> out = new java.util.ArrayList<>();
        if (PatchSettings.VOICE_MESSAGES.equals(key) || PatchSettings.LOCATION_FIX.equals(key)
                || PatchSettings.ASSISTANT_CAR_SCREEN.equals(key)) {
            out.add(shizukuCheck(key));
        }
        if (PatchSettings.ASSISTANT_CAR_SCREEN.equals(key)) {
            // AARE's own switch shows the assistant on the car screen; without it, it opens on the phone.
            out.add(check(key, PatchSettings.phrase("Assistant on the car screen in AARE", "Assistente sullo schermo dell'auto in AARE"),
                    PatchSettings.phrase("AARE > Assistant > Open assistants without a car app on the car screen",
                            "AARE > Assistente > Apri sullo schermo dell'auto gli assistenti senza app per l'auto"),
                    () -> getSharedPreferences(AARE_SETTINGS, MODE_PRIVATE).getBoolean(AARE_ASSISTANT_ON_CAR_SCREEN, false),
                    () -> startSafely(getPackageManager().getLaunchIntentForPackage(getPackageName()))));
        }
        if (PatchSettings.VOICE_REPLY.equals(key)) {
            out.add(check(key, PatchSettings.phrase("Microphone allowed", "Microfono consentito"),
                    PatchSettings.phrase("Permissions > Microphone > Allow", "Autorizzazioni > Microfono > Consenti"),
                    () -> checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    () -> startSafely(appDetails())));
        }
        return out;
    }

    private View shizukuCheck(String key) {
        return check(key, PatchSettings.phrase("Shizuku running, allowed for AARE", "Shizuku attivo e consentito ad AARE"),
                PatchSettings.phrase("Shizuku > Start; then allow AARE", "Shizuku > Avvia; poi consenti ad AARE"),
                PatchSettingsActivity::shizukuReady, () -> {
                    android.content.Intent i = getPackageManager().getLaunchIntentForPackage(SHIZUKU);
                    startSafely(i != null ? i : appDetails());
                });
    }

    /** Whether a requirement is met (java.util.function's BooleanSupplier needs Android 7). */
    private interface Condition {
        boolean getAsBoolean();
    }

    private static boolean shizukuReady() {
        try {
            return rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false; // no Shizuku in this AARE, or not running
        }
    }

    /**
     * AARE's Check: a filled circle when [done], else an empty one and "Set up", which opens where
     * to fix it. Shown, like the setting's sub-options, only while [key] (and its parent) are on.
     */
    private View check(String key, String title, String subtitle, Condition done, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, null));
        ImageView icon = new ImageView(this);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, Type.BODY_LARGE, colors.onSurface));
        texts.addView(text(subtitle, Type.BODY_SMALL, colors.onSurfaceVariant));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(14);
        row.addView(texts, tp);
        TextView setUp = text(PatchSettings.phrase("Set up", "Configura"), Type.LABEL_LARGE, colors.primary);
        row.addView(setUp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PatchSettings.Feature feature = null;
        for (PatchSettings.Feature f : PatchSettings.features()) if (f.key.equals(key)) feature = f;
        final String parent = feature == null ? null : feature.parent;
        requirementUpdates.add(() -> {
            boolean shown = PatchSettings.isEnabled(this, key) && (parent == null || PatchSettings.isEnabled(this, parent));
            row.setVisibility(shown ? View.VISIBLE : View.GONE);
            if (!shown) return;
            boolean ok = done.getAsBoolean();
            icon.setImageDrawable(new RoundIcon(ok ? CHECK_CIRCLE : RADIO_UNCHECKED, 0x00000000,
                    ok ? colors.primary : colors.outline, dp(24), dp(24)));
            setUp.setVisibility(ok ? View.GONE : View.VISIBLE);
            row.setClickable(!ok);
            row.setOnClickListener(ok ? null : v -> fix.run());
        });
        return row;
    }

    private android.content.Intent appDetails() {
        return new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", getPackageName(), null));
    }

    /** Opens a settings screen, or says there is none, instead of crashing (as AARE's startSafely). */
    private void startSafely(android.content.Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            toast(PatchSettings.phrase("Nothing to open on this phone.", "Su questo telefono non c'è niente da aprire."));
        }
    }

    // endregion

    /** As in AARE: a setting that depends on another is shown only while that one is on. */
    private void refreshDependents() {
        for (java.util.Map.Entry<View, String> e : dependents.entrySet()) {
            e.getKey().setVisibility(PatchSettings.isEnabled(this, e.getValue()) ? View.VISIBLE : View.GONE);
        }
        for (Runnable r : requirementUpdates) r.run();
    }

    /** AARE's Toggle: title (bodyLarge), subtitle (bodySmall, onSurfaceVariant), Switch; no icon. */
    private View row(PatchSettings.Feature feature) {
        LinearLayout row = new LinearLayout(this);
        if (feature.parent != null) dependents.put(row, feature.parent);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.onSurface, 0x1F)), null, null));

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
