package app.template.extension.aare;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * Answering a message by voice, after AARE read it aloud: a "Reply" card shows on the car screen
 * for 15 seconds, and every answerable message in the notification centre gets a "Reply" button.
 * Replying asks for the answer, listens, shows the dictated text live on a card that stays,
 * reads it back and sends it after a spoken "yes" or a tap on "Send".
 *
 * Works on AARE's own objects through reflection (they are not obfuscated): the car UI state of
 * CarRuntime for the cards, and CarNotificationListener for the conversations and the reply.
 * Everything here runs on the main thread.
 */
@SuppressWarnings("unused")
public final class VoiceReply {
    private static final String TAG = "AareVoiceReply";

    private static final Handler main = new Handler(Looper.getMainLooper());

    /** The cards this class put on the car screen: AARE must not close them after 8 seconds. */
    static final Set<Object> sticky = Collections.newSetFromMap(new IdentityHashMap<>());

    private enum State { IDLE, OFFERED, ASKING, DICTATING, CONFIRMING, SENDING }

    private static WeakReference<Object> runtime = new WeakReference<>(null);
    private static Object listener;
    private static Object conversation;
    private static String title = "";
    private static Bitmap icon;
    private static String draft = "";
    private static State state = State.IDLE;

    private static Object card;
    private static long cardSeq;

    private static SpeechRecognizer recognizer;
    private static AudioFocusRequest listenFocus;

    private VoiceReply() {
    }

    // region Hooks

    /** Called at the start of CarRuntime.remember and CarRuntime.notice: the car session's runtime. */
    public static void onRuntime(Object carRuntime) {
        if (carRuntime != null && runtime.get() != carRuntime) runtime = new WeakReference<>(carRuntime);
    }

    /**
     * Called at the start of CarRuntime.remember, which keeps a message in the notification centre:
     * returns its actions with "Reply" added when the message's conversation can be answered.
     */
    public static List<?> withReply(Object carRuntime, String noticeTitle, String noticeText, Bitmap noticeIcon, List<?> actions) {
        onRuntime(carRuntime);
        try {
            Object l = VoiceNotes.listener();
            if (!PatchSettings.isEnabled(VoiceNotes.listener(), PatchSettings.VOICE_REPLY)) return actions;
            if (l == null || findConversation(l, noticeTitle, noticeText) == null) return actions;
            List<Object> withReply = new ArrayList<>(actions == null ? Collections.emptyList() : actions);
            withReply.add(action(phrase("Reply", "Rispondi"), () -> replyFromHistory(noticeTitle, noticeText)));
            return withReply;
        } catch (Throwable t) {
            Log.w(TAG, "withReply", t);
            return actions;
        }
    }

    /**
     * Called at the start of CarRuntime.notice: another card is about to show. An unanswered
     * "Reply" card gives way to it, so the newest message is the one that can be answered.
     */
    public static void onNotice(Object carRuntime) {
        onRuntime(carRuntime);
        if (state == State.ASKING) {
            PatchLog.add("reply", "asked, another card came: reply question dropped");
            stopListening();
            if (speakingOurs) {
                speakingOurs = false;
                speaker().stop();
            }
        }
        if (state == State.OFFERED || state == State.ASKING) {
            removeCard();
            state = State.IDLE;
        }
    }

    /**
     * Replaces the call that closes a card on the car screen 8 seconds after it showed: cards of
     * this class stay until tapped or closed.
     */
    public static void dismissUnlessSticky(Object carActions, Object notice) {
        if (notice != null && sticky.contains(notice)) return;
        try {
            Method dismiss = carActions.getClass().getMethod("dismissNotice", notice.getClass());
            dismiss.setAccessible(true);
            dismiss.invoke(carActions, notice);
        } catch (Throwable t) {
            Log.w(TAG, "dismissNotice", t);
        }
    }

    // endregion

    // region Matching a spoken text to its conversation

    /**
     * The conversation AARE is reading aloud with [spoken], if it can be answered: [spoken] is
     * either "Sender. text" of its newest message or the whole conversation (Conversation.spoken()).
     */
    static Object conversationFor(Object notificationListener, String spoken) {
        // Only with the "Voice reply" patch, which hands over the car session's runtime.
        if (runtime.get() == null) return null;
        if (!PatchSettings.isEnabled(notificationListener instanceof android.content.Context ? (android.content.Context) notificationListener : null, PatchSettings.VOICE_REPLY)) return null;
        try {
            Method conversations = notificationListener.getClass().getMethod("conversations", String.class);
            List<?> list = (List<?>) conversations.invoke(notificationListener, (Object) null);
            if (list == null) return null;
            String said = VoiceNoteMatch.comparable(spoken);
            for (Object c : list) {
                if (!(Boolean) call(c, "getCanReply")) continue;
                if (spoken.equals(call(c, "spoken"))) return c;
                List<?> lines = (List<?>) call(c, "getMessages");
                if (lines == null || lines.isEmpty()) continue;
                Object last = lines.get(lines.size() - 1);
                String text = (String) call(last, "getText");
                if (text != null && !text.isEmpty() && spoken.endsWith(text)) return c;
                // A voice message reposted with its duration added: compared without it.
                String lastText = VoiceNoteMatch.comparable(text);
                if (!lastText.isEmpty() && said.endsWith(lastText)) {
                    PatchLog.add("reply", "conversation found by its message without duration");
                    return c;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "conversations", t);
        }
        return null;
    }

    /**
     * The conversation of a message kept in the notification centre: AARE titles it with the
     * conversation's name ("Group · Sender" for groups) and shows the message's text.
     */
    private static Object findConversation(Object notificationListener, String noticeTitle, String noticeText) throws Exception {
        Method conversations = notificationListener.getClass().getMethod("conversations", String.class);
        List<?> list = (List<?>) conversations.invoke(notificationListener, (Object) null);
        if (list == null) return null;
        Object byText = null;
        for (Object c : list) {
            if (!(Boolean) call(c, "getCanReply")) continue;
            List<?> lines = (List<?>) call(c, "getMessages");
            if (lines == null) continue;
            boolean hasText = false;
            for (Object line : lines) {
                String text = (String) call(line, "getText");
                if (noticeText != null && (noticeText.equals(text) || VoiceNoteMatch.sameText(noticeText, text))) hasText = true;
            }
            if (!hasText) continue;
            String name = String.valueOf(call(c, "getTitle"));
            if (noticeTitle != null && !name.isEmpty() && noticeTitle.startsWith(name)) return c;
            if (byText == null) byText = c;
        }
        return byText;
    }

    // endregion

    // region Flow

    /** After a message was read aloud: offers to answer it with a card that stays. */
    static void offer(Object notificationListener, Object c) {
        main.post(() -> {
            if (state == State.DICTATING || state == State.CONFIRMING || state == State.SENDING) return;
            try {
                listener = notificationListener;
                conversation = c;
                title = String.valueOf(call(c, "getTitle"));
                icon = (Bitmap) call(c, "getIcon");
                draft = "";
                if (!showCard(title, phrase("Answer by voice?", "Vuoi rispondere?"),
                        action(phrase("Reply", "Rispondi"), VoiceReply::startDictation))) {
                    PatchLog.add("reply", "Reply card could not be shown");
                    return;
                }
                PatchLog.add("reply", "Reply card shown");
                if (state == State.ASKING) stopListening();
                if (PatchSettings.isEnabled(context(), PatchSettings.VOICE_REPLY_ASK)) {
                    // "Ask by voice to reply": a spoken yes starts the dictation, as tapping Reply does.
                    state = State.ASKING;
                    speakingOurs = true;
                    speaker().say(phrase("Do you want to reply?", "Vuoi rispondere?"), () -> {
                        speakingOurs = false;
                        listenFor(State.ASKING);
                    });
                    watchCard();
                } else {
                    state = State.OFFERED;
                    expireOffer();
                }
            } catch (Throwable t) {
                Log.w(TAG, "offer", t);
            }
        });
    }

    /** "Reply" tapped in the notification centre: dictation straight away. */
    private static void replyFromHistory(String noticeTitle, String noticeText) {
        if (state == State.DICTATING || state == State.CONFIRMING || state == State.SENDING) return;
        try {
            Object l = VoiceNotes.listener();
            Object c = l == null ? null : findConversation(l, noticeTitle, noticeText);
            if (c == null) {
                PatchLog.add("reply", "Reply from the notification centre: conversation not found" + (l == null ? " (no listener)" : ""));
                if (l != null) {
                    listener = l;
                    speaker().say(phrase("This message can't be answered any more.", "Non si può più rispondere a questo messaggio."), null);
                }
                return;
            }
            listener = l;
            conversation = c;
            title = String.valueOf(call(c, "getTitle"));
            icon = (Bitmap) call(c, "getIcon");
            removeCard();
            startDictation();
        } catch (Throwable t) {
            Log.w(TAG, "replyFromHistory", t);
        }
    }

    private static void startDictation() {
        if (context() == null) {
            PatchLog.add("reply", "Reply tapped but AARE's listener is gone");
            return;
        }
        if (state == State.ASKING) stopListening();
        state = State.DICTATING;
        draft = "";
        listenStarted = false;
        PatchLog.add("reply", "dictation starts");
        showCard(replyTitle(), phrase("Speak now…", "Parla ora…"), action(phrase("Cancel", "Annulla"), VoiceReply::cancel));
        speakingOurs = true;
        speaker().say(phrase("What's your answer to " + title + "?", "Cosa vuoi rispondere a " + title + "?"),
                () -> {
                    speakingOurs = false;
                    listen(false);
                });
        watchCard();
        // If the question is never finished (audio stuck after a voice message), listen anyway.
        main.postDelayed(() -> {
            if (state == State.DICTATING && !listenStarted) {
                PatchLog.add("reply", "question not finished after " + PROMPT_MS / 1000 + " s: listening anyway");
                listen(false);
            }
        }, PROMPT_MS);
    }

    /** The longest the question "What's your answer…" can take before listening starts anyway. */
    private static final long PROMPT_MS = 8_000L;

    /** Whether listening started for the current dictation. */
    private static boolean listenStarted;

    /** The question or the read-back of this reply is being spoken. */
    private static boolean speakingOurs;

    /** Tells the card watcher of an earlier reply to stop. */
    private static int watchToken;

    /**
     * While dictating or confirming: the card closed on the car screen (Back, closed, gone) ends the
     * reply. Nothing listens or sends afterwards, and the question or read-back being spoken stops.
     */
    private static void watchCard() {
        int token = ++watchToken;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (token != watchToken || (state != State.ASKING && state != State.DICTATING && state != State.CONFIRMING)) return;
                if (cardClosed()) {
                    closedOnCar();
                    return;
                }
                main.postDelayed(this, WATCH_MS);
            }
        }, WATCH_MS);
    }

    private static final long WATCH_MS = 500L;

    /** The reply's card was closed on the car screen: the reply ends, quietly. */
    private static void closedOnCar() {
        if (state != State.ASKING && state != State.DICTATING && state != State.CONFIRMING) return;
        PatchLog.add("reply", "card closed on the car screen in state " + state + ": reply ended");
        state = State.IDLE;
        draft = "";
        stopListening();
        removeCard();
        if (speakingOurs) {
            speakingOurs = false;
            speaker().stop();
        }
    }

    private static void onDictated(String text) {
        if (state != State.DICTATING) return;
        if (text == null || text.trim().isEmpty()) {
            PatchLog.add("reply", "dictation: nothing recognised");
            speaker().say(phrase("I didn't catch that.", "Non ho capito."), null);
            offerAgain();
            return;
        }
        draft = capitalize(text.trim());
        PatchLog.add("reply", "dictation recognised, asking to confirm");
        state = State.CONFIRMING;
        showCard(replyTitle(), draft,
                action(phrase("Send", "Invia"), VoiceReply::send),
                action(phrase("Cancel", "Annulla"), VoiceReply::cancel));
        speakingOurs = true;
        speaker().say(phrase("You said: " + draft + ". Send it?", "Ho scritto: " + draft + ". Lo invio?"),
                () -> {
                    speakingOurs = false;
                    listen(true);
                });
    }

    private static void onConfirmation(String text) {
        if (state != State.CONFIRMING) return;
        Boolean answer = ReplyWords.yesOrNo(text);
        if (Boolean.TRUE.equals(answer)) send();
        else if (Boolean.FALSE.equals(answer)) cancel();
        // Not understood: the card with Send and Cancel stays until one is tapped.
    }

    private static void send() {
        if (state != State.CONFIRMING || draft.isEmpty()) return;
        state = State.SENDING;
        stopListening();
        boolean sent = false;
        try {
            Method reply = listener.getClass().getMethod("reply", conversation.getClass(), String.class);
            sent = (Boolean) reply.invoke(listener, conversation, draft);
        } catch (Throwable t) {
            Log.w(TAG, "reply", t);
        }
        removeCard();
        state = State.IDLE;
        PatchLog.add("reply", sent ? "sent" : "sending failed");
        speaker().say(sent ? phrase("Message sent.", "Messaggio inviato.")
                : phrase("The message could not be sent.", "Non sono riuscito a inviare il messaggio."), null);
    }

    private static void cancel() {
        stopListening();
        removeCard();
        boolean wasActive = state == State.DICTATING || state == State.CONFIRMING;
        if (wasActive) PatchLog.add("reply", "cancelled in state " + state);
        state = State.IDLE;
        draft = "";
        if (wasActive) speaker().say(phrase("Cancelled.", "Annullato."), null);
    }

    /** The answer to "Do you want to reply?". */
    private static void onAskAnswer(String text) {
        if (state != State.ASKING) return;
        Boolean answer = ReplyWords.askAnswer(text);
        if (Boolean.TRUE.equals(answer)) {
            PatchLog.add("reply", "asked to reply: yes");
            startDictation();
        } else if (Boolean.FALSE.equals(answer)) {
            PatchLog.add("reply", "asked to reply: no");
            stopListening();
            removeCard();
            state = State.IDLE;
        } else {
            PatchLog.add("reply", "asked to reply: not understood, Reply card stays");
            backToOffer();
        }
    }

    /** No spoken answer to "Do you want to reply?": the "Reply" card stays, as without asking. */
    private static void backToOffer() {
        if (state != State.ASKING) return;
        stopListening();
        state = State.OFFERED;
        expireOffer();
    }

    /** After a failed dictation: the "Reply" card again, to try once more. */
    private static void offerAgain() {
        stopListening();
        state = State.OFFERED;
        showCard(title, phrase("Answer by voice?", "Vuoi rispondere?"),
                action(phrase("Reply", "Rispondi"), VoiceReply::startDictation));
        expireOffer();
    }

    /** How long an unused "Reply" card stays: the message can still be answered from the notification centre. */
    private static final long OFFER_MS = 15_000L;

    private static void expireOffer() {
        Object offered = card;
        main.postDelayed(() -> {
            if (state == State.OFFERED && card == offered) {
                removeCard();
                state = State.IDLE;
            }
        }, OFFER_MS);
    }

    private static String replyTitle() {
        return phrase("Reply to ", "Risposta a ") + title;
    }

    // endregion

    // region Speech recognition

    private static void listen(boolean confirming) {
        listenFor(confirming ? State.CONFIRMING : State.DICTATING);
    }

    private static void listenFor(State expected) {
        boolean confirming = expected == State.CONFIRMING;
        boolean asking = expected == State.ASKING;
        if (state != expected) return;
        if (expected == State.DICTATING) {
            if (listenStarted) return;
            listenStarted = true;
        }
        if (cardClosed()) {
            PatchLog.add("reply", "card closed before listening" + (card == null ? " (no card)" : runtime.get() == null ? " (no car session)" : ""));
            closedOnCar();
            return;
        }
        Context context = context();
        if (context == null) {
            PatchLog.add("reply", "no context to listen");
            return;
        }
        PatchLog.add("reply", asking ? "asked to reply, listening for yes/no" : confirming ? "listening for yes/no" : "listening for the answer");
        try {
            if (recognizer == null) {
                boolean onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
                recognizer = onDevice ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        : SpeechRecognizer.createSpeechRecognizer(context);
            }
            recognizer.setRecognitionListener(new Listener(expected));
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            if (expected == State.DICTATING) {
                // Let the user pause between words while dictating.
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            }
            requestListenFocus(context);
            // With AARE's "Car microphone for phone apps" on, the recognizer hears the car by itself.
            recognizer.startListening(intent);
            listenStartedAt = SystemClock.uptimeMillis();
            scheduleListenTimeout(expected, asking ? ASK_NO_SPEECH_MS : NO_SPEECH_MS);
        } catch (Throwable t) {
            Log.w(TAG, "listen", t);
            PatchLog.add("reply", "speech recognition can't start: " + t.getClass().getSimpleName());
            releaseListenFocus();
            if (asking) {
                backToOffer();
            } else if (!confirming) {
                speaker().say(phrase("Voice input isn't available.", "Il riconoscimento vocale non è disponibile."), null);
                offerAgain();
            }
        }
    }

    /** Longest wait for the user to start talking: the music is paused meanwhile. */
    private static final long NO_SPEECH_MS = 15_000L;

    /** After "Do you want to reply?": a short wait, the music is paused meanwhile. */
    private static final long ASK_NO_SPEECH_MS = 6_000L;

    /** Longest a listening can last once speech started, so a long reply fits but nothing hangs. */
    private static final long MAX_LISTEN_MS = 60_000L;

    private static long listenStartedAt;
    private static Runnable listenTimeout;

    private static void scheduleListenTimeout(State expected, long afterMs) {
        cancelListenTimeout();
        listenTimeout = () -> {
            listenTimeout = null;
            if (state != expected) return;
            PatchLog.add("reply", "speech recognizer gave no answer in time");
            if (expected == State.ASKING) {
                backToOffer();
                return;
            }
            stopListening();
            if (expected == State.DICTATING) {
                speaker().say(phrase("I didn't catch that.", "Non ho capito."), null);
                offerAgain();
            }
            // While confirming, the Send and Cancel buttons stay.
        };
        main.postDelayed(listenTimeout, Math.max(0, afterMs));
    }

    private static void cancelListenTimeout() {
        if (listenTimeout != null) main.removeCallbacks(listenTimeout);
        listenTimeout = null;
    }

    private static void stopListening() {
        cancelListenTimeout();
        try {
            if (recognizer != null) recognizer.cancel();
        } catch (Throwable ignored) {
        }
        releaseListenFocus();
    }

    private static final class Listener implements RecognitionListener {
        private final State expected;

        Listener(State expected) {
            this.expected = expected;
        }

        private boolean current() {
            return state == expected;
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
            // Speech started: the recognizer ends on the silence after it; only the overall limit stays.
            if (!current()) return;
            scheduleListenTimeout(expected, MAX_LISTEN_MS - (SystemClock.uptimeMillis() - listenStartedAt));
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            if (current()) cancelListenTimeout();
            releaseListenFocus();
            if (!current()) return;
            Log.i(TAG, "recognition error " + error);
            PatchLog.add("reply", "speech recognition error " + error);
            if (expected == State.ASKING) {
                backToOffer();
                return;
            }
            if (expected == State.DICTATING) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    speaker().say(phrase("Allow AARE to use the microphone.", "Concedi ad AARE il microfono."), null);
                } else {
                    speaker().say(phrase("I didn't catch that.", "Non ho capito."), null);
                }
                offerAgain();
            }
            // While confirming, the Send and Cancel buttons stay.
        }

        @Override
        public void onResults(Bundle results) {
            if (current()) cancelListenTimeout();
            releaseListenFocus();
            if (!current()) return;
            String text = first(results);
            if (expected == State.ASKING) onAskAnswer(text);
            else if (expected == State.DICTATING) onDictated(text);
            else onConfirmation(text);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (!current() || expected != State.DICTATING) return;
            if (cardClosed()) {
                closedOnCar();
                return;
            }
            String text = first(partialResults);
            if (text != null && !text.trim().isEmpty()) {
                showCard(replyTitle(), capitalize(text.trim()) + " …", action(phrase("Cancel", "Annulla"), VoiceReply::cancel));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        private static String first(Bundle b) {
            if (b == null) return null;
            ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return list == null || list.isEmpty() ? null : list.get(0);
        }
    }

    /** Pauses the music while the microphone listens. */
    private static void requestListenFocus(Context context) {
        if (listenFocus != null) return;
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return;
        listenFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .build();
        audio.requestAudioFocus(listenFocus);
    }

    private static void releaseListenFocus() {
        if (listenFocus == null) return;
        Context context = context();
        AudioManager audio = context == null ? null : context.getSystemService(AudioManager.class);
        if (audio != null) audio.abandonAudioFocusRequest(listenFocus);
        listenFocus = null;
    }

    // endregion

    // region Car screen cards

    private static Pair<String, Function0<Unit>> action(String label, Runnable run) {
        return new Pair<>(label, () -> {
            main.post(run);
            return Unit.INSTANCE;
        });
    }

    @SafeVarargs
    private static boolean showCard(String cardTitle, String text, Pair<String, Function0<Unit>>... actions) {
        try {
            List<Object> notices = notices();
            if (notices == null) return false;
            Object old = card;
            String key = old != null && notices.contains(old) ? (String) call(old, "getKey") : "aare-reply-" + (++cardSeq);
            Class<?> noticeClass = Class.forName("net.prototux.aare.ui.car.CarNotice", false, VoiceReply.class.getClassLoader());
            Constructor<?> make = noticeClass.getConstructor(String.class, String.class, String.class, Bitmap.class,
                    List.class, long.class, boolean.class);
            Object n = make.newInstance(key, cardTitle, text, icon, Arrays.asList(actions), System.currentTimeMillis(), false);
            sticky.add(n);
            int at = old == null ? -1 : notices.indexOf(old);
            if (at >= 0) notices.set(at, n);
            else notices.add(0, n);
            if (old != null) sticky.remove(old);
            card = n;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "showCard", t);
            return false;
        }
    }

    private static void removeCard() {
        Object old = card;
        card = null;
        if (old == null) return;
        sticky.remove(old);
        try {
            List<Object> notices = notices();
            if (notices != null) notices.remove(old);
        } catch (Throwable t) {
            Log.w(TAG, "removeCard", t);
        }
    }

    /** The card was closed on the car screen (its X), or the car session ended. */
    private static boolean cardClosed() {
        List<Object> notices = notices();
        return card == null || notices == null || !notices.contains(card);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> notices() {
        Object r = runtime.get();
        if (r == null) return null;
        try {
            Object ui = call(r, "getUi");
            return (List<Object>) call(ui, "getNotices");
        } catch (Throwable t) {
            Log.w(TAG, "notices", t);
            return null;
        }
    }

    // endregion

    // region Helpers

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static Context context() {
        return listener instanceof Context ? ((Context) listener).getApplicationContext() : null;
    }

    private static VoiceNotes.Player speaker() {
        return VoiceNotes.Player.get(context());
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private static String phrase(String english, String italian) {
        return "it".equals(Locale.getDefault().getLanguage()) ? italian : english;
    }

    // endregion
}
