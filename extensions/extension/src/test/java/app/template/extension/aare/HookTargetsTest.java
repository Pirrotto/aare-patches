package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Every class and method the patches call from AARE's own code must be public: AARE's classes are in
 * other packages, and a package-private target is refused by Android with an IllegalAccessError that
 * stops AARE (TunnelManeuver.onTrip, 26.11: AARE stopped when a destination was set).
 */
public class HookTargetsTest {
    private static final Pattern CALL = Pattern.compile("Lapp/template/extension/aare/([A-Za-z]+);->([A-Za-z]+)\\(");
    /** `const val NAME = "Lapp/template/extension/aare/X;"`, used in the smali as `$NAME->method(`. */
    private static final Pattern CONST = Pattern.compile("const val ([A-Z_]+) = \"Lapp/template/extension/aare/([A-Za-z]+);\"");
    private static final Pattern CONST_CALL = Pattern.compile("\\$\\{?([A-Z_]+)\\}?->([A-Za-z]+)\\(");

    private static List<String[]> calls() throws IOException {
        Path dir = Paths.get("../../patches/src/main/kotlin");
        List<String[]> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".kt"))::iterator) {
                String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                Matcher m = CALL.matcher(text);
                while (m.find()) out.add(new String[] {m.group(1), m.group(2), f.getFileName().toString()});
                java.util.Map<String, String> consts = new java.util.HashMap<>();
                Matcher c = CONST.matcher(text);
                while (c.find()) consts.put(c.group(1), c.group(2));
                Matcher cc = CONST_CALL.matcher(text);
                while (cc.find()) {
                    String cls = consts.get(cc.group(1));
                    if (cls != null) out.add(new String[] {cls, cc.group(2), f.getFileName().toString()});
                }
            }
        }
        return out;
    }

    @Test
    public void everyHookTargetIsPublic() throws Exception {
        List<String[]> calls = calls();
        assertFalse("no hook found: wrong path?", calls.isEmpty());
        for (String[] c : calls) {
            // Not initialised: its static code may need Android, which unit tests only stub.
            Class<?> k = Class.forName("app.template.extension.aare." + c[0], false, getClass().getClassLoader());
            assertTrue(c[0] + " (called from " + c[2] + ") must be public", Modifier.isPublic(k.getModifiers()));
            boolean found = false;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(c[1])) continue;
                found = true;
                assertTrue(c[0] + "." + c[1] + " (called from " + c[2] + ") must be public static",
                        Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()));
            }
            assertTrue(c[0] + "." + c[1] + " (called from " + c[2] + ") doesn't exist", found);
        }
    }
}
