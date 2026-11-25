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

    /** One switch of the screen. */
    static final class Feature {
        final String key;
        final String title;
        final String summary;
        /** A Material icon, as SVG path data on a 24 x 24 grid. */
        final String icon;
        /** The switch this one depends on (shown below it, indented), or null. */
        final String parent;

        Feature(String key, String title, String summary, String icon) {
            this(key, title, summary, icon, null);
        }

        Feature(String key, String title, String summary, String icon, String parent) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.icon = icon;
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
                    : LOCATION_CHECK.equals(f.key) || LOCATION_FIX.equals(f.key) ? TUNNEL_NAVIGATION : f.key;
            if (registered.contains(patch)) list.add(f);
        }
        return list;
    }

    // Material icons (Apache License 2.0): navigation, reply, graphic_eq, record_voice_over,
    // notifications_active, volume_down, place, near_me.
    static final String ICON_TUNNEL = "M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z";
    private static final String ICON_REPLY = "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z";
    private static final String ICON_VOICE_MESSAGE = "M7 18h2V6H7v12zm4 4h2V2h-2v20zm-8-8h2v-4H3v4zm12 4h2V6h-2v12zm4-8v4h2v-4h-2z";
    private static final String ICON_ASK = "M9 13c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2"
            + "c0-2.66-5.33-4-8-4zm7.76-9.64l-1.68 1.69c.84 1.18.84 2.71 0 3.89l1.68 1.69c2.02-2.02 2.02-5.07 0-7.27z"
            + "M20.07 2l-1.63 1.63c2.77 3.02 2.77 7.56 0 10.74L20.07 16c3.9-3.89 3.91-9.95 0-14z";
    private static final String ICON_LOCATION_CHECK = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z";
    private static final String ICON_LOCATION_FIX = "M21 3L3 10.53v.98l6.84 2.65L12.48 21h.98L21 3z";
    private static final String ICON_RADIO_PAUSE = "M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z";
    private static final String ICON_BEEP = "M7.58 4.08L6.15 2.65C3.75 4.48 2.17 7.3 2.03 10.5h2c.15-2.65 1.51-4.97 3.55-6.42z"
            + "m12.39 6.42h2c-.15-3.2-1.73-6.02-4.12-7.85l-1.42 1.43c2.02 1.45 3.39 3.77 3.54 6.42z"
            + "M18 11c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2v-5z"
            + "m-6 11c.14 0 .27-.01.4-.04.65-.14 1.18-.58 1.44-1.18.1-.24.15-.5.15-.78h-4c.01 1.1.9 2 2.01 2z";

    static List<Feature> known() {
        return Arrays.asList(
                new Feature(TUNNEL_NAVIGATION,
                        phrase("Tunnel navigation", "Navigazione in galleria"),
                        phrase("With the car's GPS for the phone: in tunnels the position keeps moving, estimated from the car's "
                                        + "speed, compass and gyroscope, for as long as it stays within 45 m (about 400 m of tunnel). What it learns "
                                        + "about each car is kept, and deleted when switched off.",
                                "Col GPS dell'auto per il telefono: in galleria la posizione continua ad avanzare, stimata da "
                                        + "velocità, bussola e giroscopio dell'auto, finché resta entro 45 m (circa 400 m di galleria). Ricorda ciò "
                                        + "che impara di ogni auto e lo cancella quando la spegni."),
                        ICON_TUNNEL),
                new Feature(LOCATION_CHECK,
                        phrase("Location check (test)", "Controllo posizione (prova)"),
                        phrase("Diagnostics only: for 5 minutes after the car connects, notes every 15 s whether AARE and the "
                                        + "navigation apps may read the location (needs Shizuku). For the black map at the start.",
                                "Solo diagnostica: per 5 minuti dopo il collegamento annota ogni 15 s se AARE e le app di "
                                        + "navigazione possono leggere la posizione (serve Shizuku). Per la mappa nera all'avvio."),
                        ICON_LOCATION_CHECK, TUNNEL_NAVIGATION),
                new Feature(LOCATION_FIX,
                        phrase("Location for navigation at the start (test)", "Posizione per la navigazione all'avvio (prova)"),
                        phrase("When the car connects with AARE in the background, the navigation app may get no location until "
                                        + "AARE is opened on the phone (black map). With this, the Shizuku shell starts a small location "
                                        + "service in AARE while the car is connected, as Android Auto's own start does, so AARE can "
                                        + "pass the location on. An extra AARE notification shows meanwhile.",
                                "Quando l'auto si collega con AARE in background, l'app di navigazione può restare senza "
                                        + "posizione finché non apri AARE sul telefono (mappa nera). Con questa opzione la shell di "
                                        + "Shizuku avvia in AARE un piccolo servizio di posizione mentre l'auto è collegata, come "
                                        + "fa Android Auto, così AARE può passare la posizione. Intanto compare una notifica di AARE."),
                        ICON_LOCATION_FIX, TUNNEL_NAVIGATION),
                new Feature(VOICE_MESSAGES,
                        phrase("Voice messages", "Messaggi vocali"),
                        phrase("Plays WhatsApp voice messages in the car when AARE reads a message. Needs Shizuku running.",
                                "Riproduce in auto i vocali di WhatsApp quando AARE legge un messaggio. Serve Shizuku attivo."),
                        ICON_VOICE_MESSAGE),
                new Feature(VOICE_REPLY,
                        phrase("Voice reply", "Risposta vocale"),
                        phrase("After reading a message, shows \"Reply\" for 15 seconds and in the notification centre: "
                                        + "dictate the answer (with punctuation on Android 13+), then confirm to send it (\"retry\" dictates it again). With AARE's Car microphone for "
                                        + "phone apps on, it listens through the car. Off by default.",
                                "Dopo la lettura mostra «Rispondi» per 15 secondi e nel centro notifiche: "
                                        + "detti la risposta (con la punteggiatura da Android 13) e confermi l'invio («riprova» la fa dettare di nuovo). Con «Microfono dell'auto per le app» "
                                        + "di AARE attivo, ascolta dal microfono dell'auto. Disattivata di default."),
                        ICON_REPLY),
                new Feature(VOICE_REPLY_ASK,
                        phrase("Ask by voice to reply", "Chiedi a voce se rispondere"),
                        phrase("With Voice reply: after reading a message that can be answered, asks \"Do you want to reply?\" "
                                        + "and listens a few seconds (the music pauses meanwhile). Say \"yes\" to dictate, "
                                        + "\"no\" to close; otherwise the Reply card stays.",
                                "Con Risposta vocale: dopo la lettura di un messaggio a cui si può rispondere chiede "
                                        + "«Vuoi rispondere?» e ascolta qualche secondo (intanto la musica va in pausa). "
                                        + "Di' «sì» per dettare, «no» per chiudere; altrimenti resta la scheda Rispondi."),
                        ICON_ASK, VOICE_REPLY),
                new Feature(VOICE_REPLY_BEEP,
                        phrase("Listening beep", "Bip quando ascolta"),
                        phrase("With Voice reply: a short tone going up when it starts listening and going down when it stops, "
                                        + "as Android Auto's assistant does.",
                                "Con Risposta vocale: un breve suono che sale quando inizia ad ascoltare e che scende quando "
                                        + "smette, come l'assistente di Android Auto."),
                        ICON_BEEP, VOICE_REPLY),
                new Feature(RADIO_PAUSE,
                        phrase("Pause the car's radio while AARE speaks", "Pausa della radio dell'auto quando AARE parla"),
                        phrase("While a message is read aloud and during a voice reply, the car's own sound (radio) pauses "
                                        + "instead of only getting lower, and stays paused until AARE is done, as with Android "
                                        + "Auto's assistant. Navigation prompts are unchanged.",
                                "Mentre AARE legge un messaggio e durante la risposta vocale, il suono dell'auto (radio) va "
                                        + "in pausa invece di abbassarsi solo un po', e resta in pausa finché AARE ha finito, come "
                                        + "con l'assistente di Android Auto. Le indicazioni stradali restano come prima."),
                        ICON_RADIO_PAUSE));
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
    private static final java.util.Set<String> OFF_BY_DEFAULT = new java.util.HashSet<>(java.util.Arrays.asList(TUNNEL_NAVIGATION, VOICE_MESSAGES, VOICE_REPLY, VOICE_REPLY_ASK, VOICE_REPLY_BEEP, RADIO_PAUSE, LOCATION_CHECK, LOCATION_FIX));

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
