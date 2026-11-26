package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The crash note names the error and its code, never its message. */
public class CrashNoteTest {
    private static Throwable thrown() {
        try {
            throw new IllegalStateException("message with Marco's text",
                    new java.io.IOException("/storage/emulated/0/secret path"));
        } catch (IllegalStateException e) {
            return e;
        }
    }

    @Test
    public void namesWithoutMessages() {
        String d = CrashNote.describe(thrown());
        assertTrue(d, d.startsWith("java.lang.IllegalStateException at app.template.extension.aare.CrashNoteTest.thrown:"));
        assertTrue(d, d.contains(" | caused by java.io.IOException"));
        assertFalse(d, d.contains("Marco"));
        assertFalse(d, d.contains("secret"));
        assertFalse(d, d.contains("message"));
    }

    @Test
    public void framesAndCausesAreBounded() {
        Throwable e = new RuntimeException();
        for (int i = 0; i < 10; i++) e = new RuntimeException(e);
        String d = CrashNote.describe(e);
        int causes = d.split("caused by", -1).length - 1;
        assertTrue(d, causes == CrashNote.CAUSES);
        assertTrue(d.split(" < ", -1).length - 1 <= (CrashNote.FRAMES - 1) * (CrashNote.CAUSES + 1));
    }
}
