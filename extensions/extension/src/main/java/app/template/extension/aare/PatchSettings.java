package app.template.extension.aare;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The "Settings" row these patches add to AARE's settings home, and the switches behind it.
 *
 * Every patch registers itself here when it is applied (the patches add a call to {@link #register}
 * in {@link #registerAll}), so the screen lists exactly the patches of the installed release: a new
 * release brings its new switches without anything else to update.
 */
public final class PatchSettings {
    private static final String TAG = "AarePatches";
    private static final String PREFS = "aare_patches";

    /** The name of the extra entry of AARE's Page enum. */
    public static final String PAGE_NAME = "AARE_PATCHES";

    public static final String VOICE_MESSAGES = "voice_messages";
    public static final String VOICE_REPLY = "voice_reply";
    public static final String TUNNEL_NAVIGATION = "tunnel_navigation";

    /** One switch of the screen. */
    static final class Feature {
        final String key;
        final String title;
        final String summary;
        /** A Material icon, as SVG path data on a 24 x 24 grid. */
        final String icon;

        Feature(String key, String title, String summary, String icon) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.icon = icon;
        }
    }

    private static final Set<String> registered = new LinkedHashSet<>();
    private static volatile boolean loaded;

    private PatchSettings() {
    }

    // region Registration

    /**
     * The patches add a register("key") call here, one per applied patch. They use the parameter's
     * register for the key, so the method needs no locals of its own.
     */
    private static void registerAll(String key) {
        register(key);
    }

    private static void register(String key) {
        if (key != null) registered.add(key);
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            registerAll(null);
        } catch (Throwable t) {
            Log.w(TAG, "registerAll", t);
        }
    }

    /** The patch with this key is part of this build. */
    static boolean isRegistered(String key) {
        load();
        return registered.contains(key);
    }

    /** The applied patches, in the order of {@link #known}. */
    static List<Feature> features() {
        load();
        List<Feature> list = new ArrayList<>();
        for (Feature f : known()) {
            if (registered.contains(f.key)) list.add(f);
        }
        return list;
    }

    // Material icons (Apache License 2.0): mic, navigation, reply.
    private static final String ICON_MIC = "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z"
            + "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z";
    static final String ICON_TUNNEL = "M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z";
    private static final String ICON_REPLY = "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z";

    private static List<Feature> known() {
        return Arrays.asList(
                new Feature(TUNNEL_NAVIGATION,
                        phrase("Tunnel navigation", "Navigazione in galleria"),
                        phrase("With the car's GPS for the phone: in tunnels the position keeps moving, estimated from the car's "
                                        + "speed, compass and gyroscope, until the GPS is back.",
                                "Col GPS dell'auto per il telefono: in galleria la posizione continua ad avanzare, stimata da "
                                        + "velocità, bussola e giroscopio dell'auto, finché torna il GPS."),
                        ICON_TUNNEL),
                new Feature(VOICE_MESSAGES,
                        phrase("Voice messages", "Messaggi vocali"),
                        phrase("Plays WhatsApp voice messages in the car when AARE reads a message. Needs Shizuku running.",
                                "Riproduce in auto i vocali di WhatsApp quando AARE legge un messaggio. Serve Shizuku attivo."),
                        ICON_MIC),
                new Feature(VOICE_REPLY,
                        phrase("Voice reply", "Risposta vocale"),
                        phrase("After reading a message, shows \"Reply\" for 15 seconds and in the notification centre: "
                                        + "dictate the answer, then confirm to send it. With AARE's Car microphone for "
                                        + "phone apps on, it listens through the car.",
                                "Dopo la lettura mostra «Rispondi» per 15 secondi e nel centro notifiche: "
                                        + "detti la risposta e confermi l'invio. Con «Microfono dell'auto per le app» "
                                        + "di AARE attivo, ascolta dal microfono dell'auto."),
                        ICON_REPLY));
    }

    // endregion

    // region Switches

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the user left the feature on. Every feature starts on. */
    /**
     * Features off until the user turns them on, as AARE would ship them: they change what other apps
     * get (tunnel navigation's estimated position) or read another app's files (voice messages).
     */
    private static final java.util.Set<String> OFF_BY_DEFAULT = new java.util.HashSet<>(java.util.Arrays.asList(TUNNEL_NAVIGATION, VOICE_MESSAGES));

    public static boolean isEnabled(Context context, String key) {
        try {
            if (context == null) context = application();
            if (context == null) return !OFF_BY_DEFAULT.contains(key);
            return prefs(context).getBoolean(key, !OFF_BY_DEFAULT.contains(key));
        } catch (Throwable t) {
            Log.w(TAG, "isEnabled", t);
            return !OFF_BY_DEFAULT.contains(key);
        }
    }

    static void setEnabled(Context context, String key, boolean enabled) {
        prefs(context).edit().putBoolean(key, enabled).apply();
    }

    // endregion


    // endregion

    // region Settings home row (called from AARE's code)

    /** The application, for the code that runs before any context is at hand (the Page enum). */
    @SuppressLint("PrivateApi")
    static Context application() {
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int string(String name) {
        Context app = application();
        if (app == null) return 0;
        return app.getResources().getIdentifier(name, "string", app.getPackageName());
    }

    /** The title of the row, a string the Settings patch adds to AARE. */
    public static int titleRes() {
        return string("aare_patches_settings_title");
    }

    /** The subtitle of the row. */
    public static int subtitleRes() {
        return string("aare_patches_settings_subtitle");
    }

    /**
     * Called at the end of the Page enum's static initializer: adds the row to the pages of the
     * settings home, unless its strings are missing (stringResource would fail on them).
     */
    public static Object[] withPage(Object[] pages, Object page) {
        if (titleRes() == 0 || subtitleRes() == 0) return pages;
        Object[] all = Arrays.copyOf(pages, pages.length + 1);
        all[pages.length] = page;
        return all;
    }

    private static Object openSettings;

    /**
     * Called at the start of AARE's CategoryRow: the row of this screen opens it, instead of
     * showing a page of AARE's own.
     *
     * @return the click action to use for the row.
     */
    public static Object onCategoryClick(Object page, Object onClick) {
        try {
            if (!(page instanceof Enum) || !PAGE_NAME.equals(((Enum<?>) page).name())) return onClick;
            if (openSettings == null) openSettings = function0(PatchSettings::open);
            return openSettings;
        } catch (Throwable t) {
            Log.w(TAG, "onCategoryClick", t);
            return onClick;
        }
    }

    /** A kotlin.jvm.functions.Function0 that runs the action, made without Kotlin on the classpath. */
    private static Object function0(Runnable action) throws ClassNotFoundException {
        Class<?> type = Class.forName("kotlin.jvm.functions.Function0", false, PatchSettings.class.getClassLoader());
        Object unit = null;
        try {
            unit = Class.forName("kotlin.Unit", true, PatchSettings.class.getClassLoader()).getField("INSTANCE").get(null);
        } catch (Throwable ignored) {
        }
        final Object result = unit;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "invoke":
                    action.run();
                    return result;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return "AarePatchesSettings";
            }
        });
    }

    private static void open() {
        Context app = application();
        if (app == null) return;
        Intent intent = new Intent(app, PatchSettingsActivity.class);
        // Same task affinity as AARE: the screen opens above AARE's settings, and Back returns to them.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(intent);
    }

    // endregion

    public static String phrase(String english, String italian) {
        return "it".equals(Locale.getDefault().getLanguage()) ? italian : english;
    }
}
