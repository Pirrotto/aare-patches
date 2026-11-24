package app.template.extension.aare;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads, from "dumpsys activity processes", the state of a few apps' main processes: their process
 * state and capabilities (the first letter, L, is the right to read the location while not in the
 * foreground). Pure, so it can be unit-tested; nothing else of the output is kept.
 */
final class ProcessCaps {
    /** A package name, so nothing else can be asked about. */
    static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    /** "ProcessRecord{1a2b3c 12345:app.vela/u0a197}": the process name before the slash. */
    private static final Pattern RECORD = Pattern.compile("ProcessRecord\\{\\S+ \\d+:([^/\\s}]+)/");
    private static final Pattern PROC_STATE = Pattern.compile("\\bcurProcState=(-?\\d+)");
    private static final Pattern CAPABILITY = Pattern.compile("\\bcurCapability=([A-Za-z-]+)");

    /** One app's main process. */
    static final class State {
        String procState = "?";
        String capability = "?";

        /** Whether the capabilities include location ("L" first), null if unknown. */
        Boolean location() {
            if (capability.isEmpty() || "?".equals(capability)) return null;
            return capability.charAt(0) == 'L';
        }
    }

    private ProcessCaps() {
    }

    /** The state of each of [packages]' main process found in [dump]; a package not running is absent. */
    static Map<String, State> parse(BufferedReader dump, Collection<String> packages) throws IOException {
        Map<String, State> found = new LinkedHashMap<>();
        State current = null;
        for (String line; (line = dump.readLine()) != null; ) {
            Matcher r = RECORD.matcher(line);
            if (r.find()) {
                // Only the first record of a process: dumpsys lists some processes twice (LRU list).
                current = packages.contains(r.group(1)) && !found.containsKey(r.group(1)) ? new State() : null;
                if (current != null) found.put(r.group(1), current);
                continue;
            }
            if (current == null) continue;
            Matcher p = PROC_STATE.matcher(line);
            if (p.find() && "?".equals(current.procState)) current.procState = p.group(1);
            Matcher c = CAPABILITY.matcher(line);
            if (c.find() && "?".equals(current.capability)) current.capability = c.group(1);
        }
        return found;
    }
}
