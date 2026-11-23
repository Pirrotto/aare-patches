package app.template.extension.aare;

import java.util.regex.Pattern;

/** The pieces of one dictation, joined, without hesitations ("eee", "ehm", "mmm"). Plain Java, for tests. */
final class DictationText {
    /** Whole words that are only hesitation, in Italian, English and German. A single "e" ("and") stays. */
    private static final Pattern FILLER = Pattern.compile(
            "(?iu)^(e{2,}h*|eh+|ehm+|em{2,}|m{2,}|hm+|mh+|uh+m*|um+|ah{2,}|äh+m*|öh+m*)[.,!?…]*$");

    private DictationText() {
    }

    /** [text] without hesitation words, spaces tidied; "" if nothing else was said. */
    static String clean(String text) {
        if (text == null) return "";
        StringBuilder b = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty() || FILLER.matcher(word).matches()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(word);
        }
        return b.toString();
    }

    /** The dictation so far followed by the next piece. */
    static String join(String sofar, String piece) {
        String a = sofar == null ? "" : sofar.trim();
        String c = piece == null ? "" : piece.trim();
        if (a.isEmpty()) return c;
        if (c.isEmpty()) return a;
        return a + " " + c;
    }
}
