package app.template.extension.aare;

/**
 * What a listening got from the speech recognizer, for the diagnostics: technical state only (whether
 * it started, whether a voice began, the loudest level), never what was said. Plain Java, for tests.
 */
final class ListenNote {
    private ListenNote() {
    }

    /** "started after 420 ms, voice heard, loudest 8 dB", or "never started". */
    static String of(boolean ready, long readyAfterMs, boolean heard, float loudestDb) {
        if (!ready) return "never started";
        return "started after " + Math.max(0, readyAfterMs) + " ms, " + (heard ? "voice heard" : "no voice heard")
                + (Float.isInfinite(loudestDb) || Float.isNaN(loudestDb) ? ", no level" : ", loudest " + Math.round(loudestDb) + " dB");
    }
}
