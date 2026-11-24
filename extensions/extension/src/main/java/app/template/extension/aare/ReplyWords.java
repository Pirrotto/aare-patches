package app.template.extension.aare;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The spoken answers of a voice reply, in Italian, English and German. Plain Java, for tests. */
final class ReplyWords {
    private static final Set<String> YES = new HashSet<>(Arrays.asList(
            "si", "invia", "inviala", "invialo", "manda", "mandala", "mandalo", "ok", "okay", "certo", "conferma",
            "yes", "yeah", "send", "sure", "ja", "jawohl", "genau", "klar", "senden", "schick", "schicken", "gerne"));
    private static final Set<String> NO = new HashSet<>(Arrays.asList(
            "no", "annulla", "cancella", "lascia", "stop", "basta", "cancel", "dont", "nein", "abbrechen", "nicht", "stopp"));

    /** Also a yes to "Do you want to reply?". */
    private static final Set<String> ASK_YES = new HashSet<>(Arrays.asList(
            "rispondi", "rispondere", "rispondo", "bene", "volentieri", "reply", "answer", "antworten", "antworte"));
    /** Also a no to "Do you want to reply?". */
    private static final Set<String> ASK_NO = new HashSet<>(Arrays.asList(
            "niente", "dopo", "later", "nope", "spater", "nachher"));

    /** "Change message" to "Do you want to send it?": dictated again. */
    private static final Set<String> CHANGE = new HashSet<>(Arrays.asList(
            "cambia", "cambiare", "cambiamo", "modifica", "modificare", "riscrivi", "correggi",
            "change", "edit", "rewrite", "andern", "andere", "korrigieren"));

    private ReplyWords() {
    }

    /**
     * Whether a partial answer to "Do you want to send it?" can be acted on before the user stops
     * talking: a yes, or "change". A "no" waits for the end, as it may be "no, change the message",
     * which dictates the message again instead of cancelling it.
     */
    static boolean actEarlyOnConfirmation(String partial) {
        return wantsChange(partial) || Boolean.TRUE.equals(yesOrNo(partial));
    }

    /** True if [text] asks to change the message ("cambia messaggio", "change message", "ändern"). */
    static boolean wantsChange(String text) {
        if (text == null) return false;
        for (String word : plain(text).split("\\s+")) {
            if (CHANGE.contains(word)) return true;
        }
        return false;
    }

    private static String plain(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z ]", " ");
    }

    /** To "Send it?": true for a yes, false for a no, null if neither or both were heard. */
    static Boolean yesOrNo(String text) {
        return answer(text, false);
    }

    /** To "Do you want to reply?": true, false, or null if neither or both were heard. */
    static Boolean askAnswer(String text) {
        return answer(text, true);
    }

    private static Boolean answer(String text, boolean ask) {
        if (text == null) return null;
        String plain = plain(text);
        boolean yes = false;
        boolean no = false;
        for (String word : plain.split("\\s+")) {
            if (YES.contains(word) || (ask && ASK_YES.contains(word))) yes = true;
            if (NO.contains(word) || (ask && ASK_NO.contains(word))) no = true;
        }
        if (yes == no) return null;
        return yes;
    }
}
