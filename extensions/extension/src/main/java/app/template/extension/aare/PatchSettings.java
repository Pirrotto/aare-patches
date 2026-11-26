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
        return Arrays.asList(
                new Feature(TUNNEL_NAVIGATION,
                        phrase("Tunnel navigation", "Navigazione in galleria"),
                        phrase("With the car's GPS for the phone: in tunnels the position keeps moving, estimated from the car's "
                                        + "speed, compass and gyroscope, for as long as it stays within 45 m (about 400 m of tunnel at first, longer as it measures the car's error). What it learns "
                                        + "about each car is kept, and deleted when switched off. With the tunnel maps per country downloaded below, it follows the tunnel's shape, and at a junction the side of the navigation's next maneuver.",
                                "Col GPS dell'auto per il telefono: in galleria la posizione continua ad avanzare, stimata da "
                                        + "velocità, bussola e giroscopio dell'auto, finché resta entro 45 m (all'inizio circa 400 m di galleria, di più man mano che misura l'errore dell'auto). Ricorda ciò "
                                        + "che impara di ogni auto e lo cancella quando la spegni. Con le mappe delle gallerie per paese scaricate qui sotto, segue la forma della galleria e, a un bivio, il lato della prossima manovra della navigazione.")),
                new Feature(TUNNEL_MAPS_AUTO,
                        phrase("Update the tunnel maps", "Aggiorna le mappe delle gallerie"),
                        phrase("The tunnel maps downloaded under \"Tunnel maps\" below are checked at most once a day, never while "
                                        + "a car is connected, and downloaded again when OpenStreetMap changed. It contacts only this "
                                        + "project's GitHub page and asks only for the map files, never your position.",
                                "Le mappe delle gallerie scaricate da «Mappe delle gallerie» qui sotto vengono controllate al massimo "
                                        + "una volta al giorno, mai con l'auto collegata, e riscaricate quando OpenStreetMap è cambiato. "
                                        + "Contatta solo la pagina GitHub del progetto e chiede solo i file delle mappe, mai la tua posizione."), TUNNEL_NAVIGATION),
                new Feature(TUNNEL_MAPS_HERE,
                        phrase("Add the country I'm in", "Aggiungi il paese in cui sono"),
                        phrase("At the same daily check, downloads the map of the mobile network's country if it isn't on the "
                                        + "phone (no GPS, no permission). GitHub then sees which country's file was asked for.",
                                "Allo stesso controllo giornaliero scarica la mappa del paese della rete mobile, se non è già sul "
                                        + "telefono (senza GPS e senza permessi). GitHub vede quindi di quale paese è il file chiesto."), TUNNEL_NAVIGATION),
                new Feature(LOCATION_CHECK,
                        phrase("Location check (test)", "Controllo posizione (prova)"),
                        phrase("Diagnostics only: for 5 minutes after the car connects, notes every 15 s whether AARE and the "
                                        + "navigation apps may read the location (needs Shizuku). For the black map at the start.",
                                "Solo diagnostica: per 5 minuti dopo il collegamento annota ogni 15 s se AARE e le app di "
                                        + "navigazione possono leggere la posizione (serve Shizuku). Per la mappa nera all'avvio."), TUNNEL_NAVIGATION),
                new Feature(LOCATION_FIX,
                        phrase("Location for navigation at the start (test)", "Posizione per la navigazione all'avvio (prova)"),
                        phrase("When the car connects with AARE in the background, the navigation app may get no location until "
                                        + "AARE is opened on the phone (black map). With this, the Shizuku shell starts a small location "
                                        + "service in AARE while the car is connected, as Android Auto's own start does, so AARE can "
                                        + "pass the location on. An extra AARE notification shows meanwhile.",
                                "Quando l'auto si collega con AARE in background, l'app di navigazione può restare senza "
                                        + "posizione finché non apri AARE sul telefono (mappa nera). Con questa opzione la shell di "
                                        + "Shizuku avvia in AARE un piccolo servizio di posizione mentre l'auto è collegata, come "
                                        + "fa Android Auto, così AARE può passare la posizione. Intanto compare una notifica di AARE."), TUNNEL_NAVIGATION),
                new Feature(VOICE_MESSAGES,
                        phrase("Voice messages", "Messaggi vocali"),
                        phrase("Plays WhatsApp voice messages in the car when AARE reads a message. Needs Shizuku running.",
                                "Riproduce in auto i vocali di WhatsApp quando AARE legge un messaggio. Serve Shizuku attivo.")),
                new Feature(VOICE_REPLY,
                        phrase("Voice reply", "Risposta vocale"),
                        phrase("After reading a message, shows \"Reply\" for 15 seconds and in the notification centre: "
                                        + "dictate the answer (with punctuation on Android 13+), then confirm to send it (\"retry\" dictates it again). With AARE's Car microphone for "
                                        + "phone apps on, it listens through the car. Off by default.",
                                "Dopo la lettura mostra «Rispondi» per 15 secondi e nel centro notifiche: "
                                        + "detti la risposta (con la punteggiatura da Android 13) e confermi l'invio («riprova» la fa dettare di nuovo). Con «Microfono dell'auto per le app» "
                                        + "di AARE attivo, ascolta dal microfono dell'auto. Disattivata di default.")),
                new Feature(VOICE_REPLY_ASK,
                        phrase("Ask by voice to reply", "Chiedi a voce se rispondere"),
                        phrase("With Voice reply: after reading a message that can be answered, asks \"Do you want to reply?\" "
                                        + "and listens a few seconds (the music pauses meanwhile). Say \"yes\" to dictate, "
                                        + "\"no\" to close; otherwise the Reply card stays.",
                                "Con Risposta vocale: dopo la lettura di un messaggio a cui si può rispondere chiede "
                                        + "«Vuoi rispondere?» e ascolta qualche secondo (intanto la musica va in pausa). "
                                        + "Di' «sì» per dettare, «no» per chiudere; altrimenti resta la scheda Rispondi."), VOICE_REPLY),
                new Feature(VOICE_REPLY_BEEP,
                        phrase("Listening beep", "Bip quando ascolta"),
                        phrase("With Voice reply: a short tone going up when it starts listening and going down when it stops, "
                                        + "as Android Auto's assistant does.",
                                "Con Risposta vocale: un breve suono che sale quando inizia ad ascoltare e che scende quando "
                                        + "smette, come l'assistente di Android Auto."), VOICE_REPLY),
                new Feature(RADIO_PAUSE,
                        phrase("Pause the car's radio while AARE speaks", "Pausa della radio dell'auto quando AARE parla"),
                        phrase("While a message is read aloud and during a voice reply, the car's own sound (radio) pauses "
                                        + "instead of only getting lower, and stays paused until AARE is done, as with Android "
                                        + "Auto's assistant. Navigation prompts are unchanged.",
                                "Mentre AARE legge un messaggio e durante la risposta vocale, il suono dell'auto (radio) va "
                                        + "in pausa invece di abbassarsi solo un po', e resta in pausa finché AARE ha finito, come "
                                        + "con l'assistente di Android Auto. Le indicazioni stradali restano come prima.")));
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
