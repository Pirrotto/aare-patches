package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class ProcessCapsTest {
    private static final String DUMP = String.join("\n",
            "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)",
            "  *APP* UID 10211 ProcessRecord{1a2b3c 4321:net.prototux.aare/u0a211}",
            "    user #0 uid=10211 gids={3003}",
            "    curSchedGroup=2 setSchedGroup=2 systemNoUi=false",
            "    curProcState=4 mRepProcState=4 pid=4321",
            "    curCapability=LCMN------ setCapability=LCMN------",
            "  *APP* UID 10211 ProcessRecord{9f9f9f 4400:net.prototux.aare:voicenotes/u0a211}",
            "    curProcState=19",
            "    curCapability=---------",
            "  *APP* UID 10197 ProcessRecord{5e6f7a 5555:app.vela/u0a197}",
            "    curProcState=5 mRepProcState=5",
            "    curCapability=---N------",
            "  Process LRU list (sorted by oom_adj, 40 total, non-act at 2, non-svc at 2):",
            "    PERS #39: sys    F/ /PER  LCMNFUBA t: 0 1234:system/1000 (fixed)",
            "  *APP* UID 10197 ProcessRecord{5e6f7a 5555:app.vela/u0a197}",
            "    curCapability=LCMN------");

    private static Map<String, ProcessCaps.State> parse(String... packages) throws Exception {
        return ProcessCaps.parse(new BufferedReader(new StringReader(DUMP)), Arrays.asList(packages));
    }

    @Test
    public void readsTheMainProcessOfEachApp() throws Exception {
        Map<String, ProcessCaps.State> m = parse("net.prototux.aare", "app.vela", "app.comaps");
        assertEquals("4", m.get("net.prototux.aare").procState);
        assertTrue(m.get("net.prototux.aare").location());
        // Vela's first record counts, not a later repeat; its sub-process-free name only.
        assertEquals("5", m.get("app.vela").procState);
        assertFalse(m.get("app.vela").location());
        // Not running: absent.
        assertFalse(m.containsKey("app.comaps"));
    }

    @Test
    public void anotherProcessOfTheAppIsNotTaken() throws Exception {
        Map<String, ProcessCaps.State> m = parse("net.prototux.aare");
        assertEquals("LCMN------", m.get("net.prototux.aare").capability);
    }

    @Test
    public void unknownFieldsStayUnknown() throws Exception {
        Map<String, ProcessCaps.State> m = ProcessCaps.parse(new BufferedReader(new StringReader(
                "  *APP* UID 1 ProcessRecord{a 1:app.vela/u0a1}\n    something else\n")), Arrays.asList("app.vela"));
        assertEquals("?", m.get("app.vela").procState);
        assertNull(m.get("app.vela").location());
    }

    @Test
    public void onlyPackageNamesAreAccepted() {
        assertTrue(ProcessCaps.PACKAGE.matcher("app.vela").matches());
        assertFalse(ProcessCaps.PACKAGE.matcher("app.vela; rm -rf /").matches());
        assertFalse(ProcessCaps.PACKAGE.matcher("vela").matches());
    }
}
