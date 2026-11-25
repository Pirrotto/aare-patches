package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** Every switch in the patches' Settings has its own icon, drawable by the settings screen's parser. */
public class FeatureIconsTest {
    /** The path commands PatchSettingsActivity draws (no arcs: A/a would be skipped). */
    private static final String SUPPORTED = "MmLlHhVvCcSsQqTtZz";

    @Test
    public void eachOptionHasItsOwnDrawableIcon() {
        Set<String> seen = new HashSet<>();
        for (PatchSettings.Feature f : PatchSettings.known()) {
            assertNotNull(f.key, f.icon);
            assertFalse(f.key + " has no icon", f.icon.isEmpty());
            assertTrue(f.key + " shares its icon with another option", seen.add(f.icon));
            for (char c : f.icon.toCharArray()) {
                if (Character.isLetter(c)) assertTrue(f.key + " uses the unsupported path command " + c, SUPPORTED.indexOf(c) >= 0);
            }
        }
    }
}
