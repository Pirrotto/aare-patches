package app.template.extension.aare;

import android.app.Notification;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays WhatsApp voice messages in the car instead of reading "Voice message (0:05)" aloud.
 *
 * AARE reads messages aloud with CarNotificationListener.speak(String). The patch calls
 * {@link #onPosted} for every notification, so the voice messages and their times are known, and
 * {@link #onSpeak} before AARE speaks: when the text holds voice messages, this class speaks the
 * text around them and plays the matching recordings, and AARE's own text-to-speech is skipped.
 *
 * WhatsApp keeps received voice messages in
 * Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/[year+week]/PTT-*.opus, folders
 * with a .nomedia file, so they need "All files access" (MANAGE_EXTERNAL_STORAGE), which the patch
 * declares. Playback uses the media usage, like AARE's text-to-speech, so it reaches the car.
 */
@SuppressWarnings("unused")
public final class VoiceNotes {
    private static final String TAG = "AareVoiceNotes";

    /** WhatsApp and WhatsApp Business. */
    private static final Set<String> PACKAGES = new HashSet<>(Arrays.asList("com.whatsapp", "com.whatsapp.w4b"));

    /** U+1F3A4 MICROPHONE, which WhatsApp puts before "Voice message (0:05)". */
    private static final String MIC = "🎤";

    /** "Voice message (0:05)" without the emoji: a short text ending with a duration. */
    private static final Pattern DURATION = Pattern.compile("^.{1,40}\\(\\d{1,3}:\\d{2}\\)$");

    /** A voice message in a spoken text when it isn't among the records (AARE was restarted). */
    private static final Pattern MIC_SEGMENT = Pattern.compile(MIC + "[^.\\n]*");

    /** How far a recording's file time may be from the message time. */
    private static final long BEFORE_MS = 2 * 60_000L;
    private static final long AFTER_MS = 60 * 60_000L;

    private static final int MAX_RECORDS = 200;

    /** A voice message seen in a notification. */
    private static final class Record {
        final String sender;
        final String text;
        final long timeMs;

        Record(String sender, String text, long timeMs) {
            this.sender = sender;
            this.text = text;
            this.timeMs = timeMs;
        }
    }

    /** Newest last. Only touched on the main thread, where notifications and speak() arrive. */
    private static final ArrayDeque<Record> records = new ArrayDeque<>();

    private static boolean accessHintSpoken;

    private VoiceNotes() {
    }

    // region Hooks

    /** Called at the start of CarNotificationListener.onNotificationPosted. */
    public static void onPosted(Context context, StatusBarNotification sbn) {
        try {
            if (sbn == null || !PACKAGES.contains(sbn.getPackageName())) return;
            Bundle extras = sbn.getNotification().extras;
            if (extras == null) return;
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages == null) return;
            for (Parcelable p : messages) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                CharSequence text = m.getCharSequence("text");
                if (text == null || !isVoice(text.toString())) continue;
                remember(new Record(senderOf(m), text.toString().trim(), m.getLong("time")));
            }
        } catch (Throwable t) {
            Log.w(TAG, "onPosted", t);
        }
    }

    /**
     * Called at the start of CarNotificationListener.speak(String).
     *
     * @return true if the text was handled here, so AARE must not speak it.
     */
    public static boolean onSpeak(Context context, String text) {
        try {
            if (text == null) return false;
            List<Segment> voices = findVoices(text);
            if (voices.isEmpty()) return false;

            Player player = Player.get(context);
            if (!hasFileAccess()) {
                player.say(replaceVoices(text, voices, phrase("voice message", "messaggio vocale")));
                if (!accessHintSpoken) {
                    accessHintSpoken = true;
                    player.say(phrase(
                            "To hear voice messages, allow AARE all files access in the phone settings.",
                            "Per ascoltare i vocali, concedi ad AARE l'accesso a tutti i file nelle impostazioni del telefono."));
                }
                return true;
            }

            Set<File> used = new HashSet<>();
            int start = 0;
            for (Segment v : voices) {
                player.say(text.substring(start, v.start));
                File file = findRecording(v.record, used);
                if (file != null) {
                    used.add(file);
                    player.play(file);
                } else {
                    player.say(phrase("Voice message not downloaded yet.", "Messaggio vocale non ancora scaricato."));
                }
                start = v.end;
            }
            player.say(text.substring(start));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "onSpeak", t);
            return false;
        }
    }

    // endregion

    // region Matching

    private static boolean isVoice(String text) {
        String t = text.trim();
        return t.startsWith(MIC) || DURATION.matcher(t).matches();
    }

    private static String senderOf(Bundle m) {
        CharSequence sender = m.getCharSequence("sender");
        if (sender == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Object person = m.getParcelable("sender_person");
            if (person instanceof android.app.Person) sender = ((android.app.Person) person).getName();
        }
        return sender == null ? "" : sender.toString();
    }

    private static void remember(Record r) {
        for (Record old : records) {
            if (old.timeMs == r.timeMs && old.text.equals(r.text) && old.sender.equals(r.sender)) return;
        }
        records.addLast(r);
        while (records.size() > MAX_RECORDS) records.removeFirst();
    }

    /** A voice message inside a spoken text, with the record it stands for if known. */
    private static final class Segment {
        final int start;
        final int end;
        final Record record;

        Segment(int start, int end, Record record) {
            this.start = start;
            this.end = end;
            this.record = record;
        }
    }

    /**
     * The voice messages in a text AARE is about to speak, in reading order. AARE speaks
     * "Sender. text" for a new message and "Title. text. Sender: text…" for a conversation, oldest
     * first, so the same text found several times stands for its newest records, in time order.
     */
    private static List<Segment> findVoices(String spoken) {
        List<Segment> found = new ArrayList<>();
        Set<String> texts = new HashSet<>();
        for (Record r : records) texts.add(r.text);

        for (String voiceText : texts) {
            List<Integer> positions = new ArrayList<>();
            for (int i = spoken.indexOf(voiceText); i >= 0; i = spoken.indexOf(voiceText, i + voiceText.length())) {
                positions.add(i);
            }
            if (positions.isEmpty()) continue;

            List<Record> candidates = new ArrayList<>();
            for (Record r : records) {
                if (r.text.equals(voiceText) && (r.sender.isEmpty() || spoken.contains(r.sender))) candidates.add(r);
            }
            Collections.sort(candidates, (a, b) -> Long.compare(a.timeMs, b.timeMs));
            List<Record> newest = candidates.subList(Math.max(0, candidates.size() - positions.size()), candidates.size());
            int offset = positions.size() - newest.size();
            for (int k = 0; k < positions.size(); k++) {
                int pos = positions.get(k);
                Record r = k - offset >= 0 ? newest.get(k - offset) : null;
                found.add(new Segment(pos, pos + voiceText.length(), r));
            }
        }

        // Voice messages not among the records, for example after AARE restarted.
        Matcher m = MIC_SEGMENT.matcher(spoken);
        while (m.find()) {
            boolean covered = false;
            for (Segment s : found) {
                if (m.start() < s.end && s.start < m.end()) covered = true;
            }
            if (!covered) found.add(new Segment(m.start(), m.end(), null));
        }

        Collections.sort(found, (a, b) -> Integer.compare(a.start, b.start));
        return found;
    }

    private static String replaceVoices(String text, List<Segment> voices, String with) {
        StringBuilder b = new StringBuilder();
        int start = 0;
        for (Segment v : voices) {
            b.append(text, start, v.start).append(with);
            start = v.end;
        }
        return b.append(text.substring(start)).toString();
    }

    private static boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return false;
    }

    /** The voice note folders of WhatsApp and WhatsApp Business, current and legacy locations. */
    private static List<File> voiceFolders() {
        File storage = Environment.getExternalStorageDirectory();
        List<File> folders = new ArrayList<>();
        folders.add(new File(storage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"));
        folders.add(new File(storage, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"));
        folders.add(new File(storage, "WhatsApp/Media/WhatsApp Voice Notes"));
        folders.add(new File(storage, "WhatsApp Business/Media/WhatsApp Business Voice Notes"));
        return folders;
    }

    /** Received voice notes (PTT-…) of the latest weekly folders. */
    private static List<File> recentRecordings() {
        List<File> files = new ArrayList<>();
        for (File folder : voiceFolders()) {
            File[] weeks = folder.listFiles(File::isDirectory);
            if (weeks == null) continue;
            Arrays.sort(weeks, (a, b) -> b.getName().compareTo(a.getName()));
            for (int i = 0; i < Math.min(3, weeks.length); i++) {
                File[] notes = weeks[i].listFiles((dir, name) -> name.startsWith("PTT-"));
                if (notes != null) files.addAll(Arrays.asList(notes));
            }
        }
        return files;
    }

    /**
     * The recording of a voice message: the received voice note saved closest after the message
     * time. Without a record, the newest one of the last day.
     */
    private static File findRecording(Record record, Set<File> used) {
        File best = null;
        long bestScore = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (File f : recentRecordings()) {
            if (used.contains(f) || f.length() == 0) continue;
            long modified = f.lastModified();
            long score;
            if (record != null && record.timeMs > 0) {
                long delta = modified - record.timeMs;
                if (delta < -BEFORE_MS || delta > AFTER_MS) continue;
                // A file saved before the message time is a weaker match than one after it.
                score = delta >= 0 ? delta : -delta * 10;
            } else {
                long age = now - modified;
                if (age < 0 || age > 24 * 60 * 60_000L) continue;
                score = age;
            }
            if (score < bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    private static String phrase(String english, String italian) {
        return "it".equals(Locale.getDefault().getLanguage()) ? italian : english;
    }

    // endregion

    // region Playback

    /** Speaks texts and plays recordings one after the other, on the main thread. */
    private static final class Player {
        private static Player instance;

        static Player get(Context context) {
            if (instance == null) instance = new Player(context.getApplicationContext());
            return instance;
        }

        private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        private final Context context;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ArrayDeque<Object> queue = new ArrayDeque<>();
        private TextToSpeech tts;
        /** null while starting, true once ready, false if there's no text-to-speech. */
        private Boolean ttsReady;
        private boolean busy;
        private int utterance;
        private AudioFocusRequest focus;
        private MediaPlayer media;

        private Player(Context context) {
            this.context = context;
        }

        void say(String text) {
            String t = text.trim();
            while (t.startsWith(".") || t.startsWith(":") || t.startsWith(",")) t = t.substring(1).trim();
            if (t.isEmpty() || t.equals(".")) return;
            enqueue(t);
        }

        void play(File file) {
            enqueue(file);
        }

        private void enqueue(Object item) {
            main.post(() -> {
                queue.addLast(item);
                if (!busy) next();
            });
        }

        private void next() {
            Object item = queue.pollFirst();
            if (item == null) {
                busy = false;
                releaseFocus();
                return;
            }
            busy = true;
            requestFocus();
            if (item instanceof File) playFile((File) item);
            else speak((String) item);
        }

        private void done() {
            main.post(this::next);
        }

        private void speak(String text) {
            if (Boolean.FALSE.equals(ttsReady)) {
                done();
                return;
            }
            if (tts == null) {
                queue.addFirst(text);
                tts = new TextToSpeech(context, status -> main.post(() -> {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (ttsReady) {
                        tts.setAudioAttributes(ATTRIBUTES);
                        tts.setLanguage(Locale.getDefault());
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String id) {
                            }

                            @Override
                            public void onDone(String id) {
                                done();
                            }

                            @Override
                            public void onError(String id) {
                                done();
                            }
                        });
                    } else {
                        Log.w(TAG, "no text-to-speech");
                    }
                    next();
                }));
                return;
            }
            if (ttsReady == null) {
                // Still starting: retry once it's ready.
                queue.addFirst(text);
                busy = false;
                return;
            }
            String id = "aare-voice-" + (++utterance);
            if (tts.speak(text, TextToSpeech.QUEUE_ADD, new Bundle(), id) != TextToSpeech.SUCCESS) done();
        }

        private void playFile(File file) {
            try {
                MediaPlayer player = new MediaPlayer();
                media = player;
                player.setAudioAttributes(ATTRIBUTES);
                player.setDataSource(file.getAbsolutePath());
                player.setOnPreparedListener(MediaPlayer::start);
                player.setOnCompletionListener(p -> {
                    p.release();
                    if (media == p) media = null;
                    done();
                });
                player.setOnErrorListener((p, what, extra) -> {
                    Log.w(TAG, "cannot play " + file + ": " + what + "/" + extra);
                    p.release();
                    if (media == p) media = null;
                    done();
                    return true;
                });
                player.prepareAsync();
            } catch (Throwable t) {
                Log.w(TAG, "cannot play " + file, t);
                if (media != null) {
                    media.release();
                    media = null;
                }
                done();
            }
        }

        /** Pauses the music while a message plays, as Android Auto's assistant does. */
        private void requestFocus() {
            if (focus != null) return;
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio == null) return;
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(ATTRIBUTES)
                    .build();
            audio.requestAudioFocus(focus);
        }

        private void releaseFocus() {
            if (focus == null) return;
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio != null) audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    // endregion
}
