package app.template.extension.aare;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    /** A setting of the Voice reply patch: after the reading, "Do you want to reply?" by voice. */
    public static final String VOICE_REPLY_ASK = "voice_reply_ask";
    /** A setting of the Voice reply patch: a short tone each time it starts listening. */
    public static final String VOICE_REPLY_BEEP = "voice_reply_beep";
    /** Pause the car's radio while AARE speaks (Car radio pause patch). */
    public static final String RADIO_PAUSE = "radio_pause";
    /** Test diagnostics: whether AARE and the navigation apps may read the location (Tunnel navigation patch). */
    public static final String LOCATION_CHECK = "location_check";
    /** Test fix: AARE's service restarted through Shizuku so navigation apps get the location (Tunnel navigation patch). */
    public static final String LOCATION_FIX = "location_fix";
    /** Tunnel maps: the downloaded countries updated by themselves, at most once a day (Tunnel navigation patch). */
    public static final String TUNNEL_MAPS_AUTO = "tunnel_maps_auto";
    /** Tunnel maps: the mobile network's country added by itself (Tunnel navigation patch). */
    public static final String TUNNEL_MAPS_HERE = "tunnel_maps_here";

    /** One switch of the screen. */
    static final class Feature {
        final String key;
        final String title;
        final String summary;
        /** The switch this one depends on (shown below it only while that one is on, as in AARE), or null. */
        final String parent;

        Feature(String key, String title, String summary) {
            this(key, title, summary, null);
        }

        Feature(String key, String title, String summary, String parent) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.parent = parent;
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
            // "Ask by voice to reply" comes with the Voice reply patch.
            String patch = VOICE_REPLY_ASK.equals(f.key) || VOICE_REPLY_BEEP.equals(f.key) ? VOICE_REPLY
                    : LOCATION_CHECK.equals(f.key) || LOCATION_FIX.equals(f.key) || TUNNEL_MAPS_AUTO.equals(f.key)
                    || TUNNEL_MAPS_HERE.equals(f.key) ? TUNNEL_NAVIGATION : f.key;
            if (registered.contains(patch)) list.add(f);
        }
        return list;
    }

    static List<Feature> known() {
        // Short, as AARE's own switches (one or two lines): the details are in the README.
        return Arrays.asList(
                new Feature(TUNNEL_NAVIGATION,
                        phrase("Tunnel navigation", "Navigazione in galleria"),
                        phrase("In tunnels the position keeps moving, estimated from the car's sensors.",
                                "In galleria la posizione continua ad avanzare, stimata dai sensori dell'auto.")),
                new Feature(TUNNEL_MAPS_AUTO,
                        phrase("Update the tunnel maps", "Aggiorna le mappe delle gallerie"),
                        phrase("Once a day, never with the car connected. Only from the maps' GitHub page.",
                                "Una volta al giorno, mai con l'auto collegata. Solo dalla pagina GitHub delle mappe."), TUNNEL_NAVIGATION),
                new Feature(TUNNEL_MAPS_HERE,
                        phrase("Add the country I'm in", "Aggiungi il paese in cui sono"),
                        phrase("Downloads the map of the mobile network's country, without GPS.",
                                "Scarica la mappa del paese della rete mobile, senza GPS."), TUNNEL_NAVIGATION),
                new Feature(LOCATION_CHECK,
                        phrase("Location check (test)", "Controllo posizione (prova)"),
                        phrase("Notes in the diagnostics whether the apps may read the location.",
                                "Annota nella diagnostica se le app possono leggere la posizione."), TUNNEL_NAVIGATION),
                new Feature(LOCATION_FIX,
                        phrase("Location for navigation at the start (test)", "Posizione per la navigazione all'avvio (prova)"),
                        phrase("Avoids the black map when AARE starts in the background.",
                                "Evita la mappa nera quando AARE parte in background."), TUNNEL_NAVIGATION),
                new Feature(VOICE_MESSAGES,
                        phrase("Voice messages", "Messaggi vocali"),
                        phrase("Plays WhatsApp voice messages in the car.",
                                "Riproduce in auto i vocali di WhatsApp.")),
                new Feature(VOICE_REPLY,
                        phrase("Voice reply", "Risposta vocale"),
                        phrase("Dictate the answer, hear it read back and confirm to send it.",
                                "Detti la risposta, la senti rileggere e confermi l'invio.")),
                new Feature(VOICE_REPLY_ASK,
                        phrase("Ask by voice to reply", "Chiedi a voce se rispondere"),
                        phrase("After reading, asks \"Do you want to reply?\".",
                                "Dopo la lettura chiede «Vuoi rispondere?»."), VOICE_REPLY),
                new Feature(VOICE_REPLY_BEEP,
                        phrase("Listening beep", "Bip quando ascolta"),
                        phrase("A short tone when it starts and stops listening.",
                                "Un breve suono quando inizia e smette di ascoltare."), VOICE_REPLY),
                new Feature(RADIO_PAUSE,
                        phrase("Pause the radio", "Pausa della radio"),
                        phrase("The radio pauses while AARE reads or listens, instead of getting lower.",
                                "La radio va in pausa mentre AARE legge o ascolta, invece di abbassarsi.")));
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
    private static final java.util.Set<String> OFF_BY_DEFAULT = new java.util.HashSet<>(java.util.Arrays.asList(TUNNEL_NAVIGATION, VOICE_MESSAGES, VOICE_REPLY, VOICE_REPLY_ASK, VOICE_REPLY_BEEP, RADIO_PAUSE, LOCATION_CHECK, LOCATION_FIX, TUNNEL_MAPS_AUTO, TUNNEL_MAPS_HERE));

    /**
     * The voice features use Android 8's audio focus requests; AARE also runs on Android 6 and 7,
     * where they stay off (and are shown as such on the Settings screen).
     */
    /** Whether [key] starts off until the user switches it on (new features are opt-in, as AARE wants). */
    static boolean offByDefault(String key) {
        return OFF_BY_DEFAULT.contains(key);
    }

    private static final Set<String> NEEDS_ANDROID_8 = new HashSet<>(Arrays.asList(
            VOICE_MESSAGES, VOICE_REPLY, VOICE_REPLY_ASK, VOICE_REPLY_BEEP, RADIO_PAUSE));

    /** Whether [key]'s feature can run on this phone's Android version. */
    public static boolean available(String key) {
        return !NEEDS_ANDROID_8.contains(key) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isEnabled(Context context, String key) {
        if (!available(key)) return false;
        try {
            if (context == null) context = application();
            if (context == null) return !offByDefault(key);
            return prefs(context).getBoolean(key, !offByDefault(key));
        } catch (Throwable t) {
            Log.w(TAG, "isEnabled", t);
            return !offByDefault(key);
        }
    }

    static void setEnabled(Context context, String key, boolean enabled) {
        prefs(context).edit().putBoolean(key, enabled).apply();
        // Switching tunnel navigation off also deletes what it learned about the cars.
        if (TUNNEL_NAVIGATION.equals(key) && !enabled) TunnelNavigation.forgetLearned(context);
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
