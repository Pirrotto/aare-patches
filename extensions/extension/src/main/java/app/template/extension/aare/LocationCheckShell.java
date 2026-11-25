package app.template.extension.aare;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The helper Shizuku runs with shell rights for the location check (test diagnostics, see
 * {@link LocationCheck}). It answers one question: "for these apps, the process state and the
 * capabilities of their main process?", read from "dumpsys activity processes". Nothing else of
 * the output leaves it. Plain Binder protocol, like {@link VoiceNotesShell}.
 */
@SuppressWarnings("unused")
public class LocationCheckShell extends Binder {
    static final String DESCRIPTOR = "app.template.extension.aare.LocationCheckShell";

    /** In: count (at most 8), then package names. Out: count, then for each: package, process state, capability. */
    static final int TRANSACTION_CHECK = FIRST_CALL_TRANSACTION + 1;
    /**
     * In: the caller's package name. The helper, as the shell, starts that package's
     * {@link LocationBridgeService} as a foreground service: a start by the shell (which holds
     * START_ACTIVITIES_FROM_BACKGROUND) lets it read the location while in use. Only the caller's
     * own package is accepted. Out: the exit code of "am start-foreground-service" (0: started),
     * -1 if refused.
     */
    static final int TRANSACTION_START_BRIDGE = FIRST_CALL_TRANSACTION + 2;
    static final String BRIDGE_CLASS = "app.template.extension.aare.LocationBridgeService";
    /** Shizuku's user-service teardown call. */
    static final int TRANSACTION_DESTROY = FIRST_CALL_TRANSACTION + 16777114;

    public LocationCheckShell() {
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        switch (code) {
            case TRANSACTION_CHECK: {
                data.enforceInterface(DESCRIPTOR);
                int n = Math.min(Math.max(data.readInt(), 0), 8);
                List<String> packages = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    String p = data.readString();
                    if (p != null && ProcessCaps.PACKAGE.matcher(p).matches()) packages.add(p);
                }
                Map<String, ProcessCaps.State> found = check(packages);
                reply.writeNoException();
                reply.writeInt(found.size());
                for (Map.Entry<String, ProcessCaps.State> e : found.entrySet()) {
                    reply.writeString(e.getKey());
                    reply.writeString(e.getValue().procState);
                    reply.writeString(e.getValue().capability);
                }
                return true;
            }
            case TRANSACTION_START_BRIDGE: {
                data.enforceInterface(DESCRIPTOR);
                String pkg = data.readString();
                reply.writeNoException();
                reply.writeInt(startBridge(pkg, Binder.getCallingUid()));
                return true;
            }
            case TRANSACTION_DESTROY:
                System.exit(0);
                return true;
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    /** Starts [pkg]'s bridge, only if [pkg] is the package of the app that asks ([callingUid]). */
    private static int startBridge(String pkg, int callingUid) {
        if (pkg == null || !ProcessCaps.PACKAGE.matcher(pkg).matches() || !isPackageOfUid(pkg, callingUid)) return -1;
        return run("am", "start-foreground-service", "-n", pkg + "/" + BRIDGE_CLASS);
    }

    /** "package:net.prototux.aare uid:10211" from "cmd package list packages -U pkg". */
    private static boolean isPackageOfUid(String pkg, int uid) {
        Process process = null;
        try {
            process = new ProcessBuilder("cmd", "package", "list", "packages", "-U", pkg).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.trim().equals("package:" + pkg + " uid:" + uid)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    /** Runs a command and returns its exit code, or -1; its output is read and dropped. */
    private static int run(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    // Drained so the command can finish; nothing of it leaves the helper.
                }
            }
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static Map<String, ProcessCaps.State> check(List<String> packages) {
        Process process = null;
        try {
            process = new ProcessBuilder("dumpsys", "activity", "processes").redirectErrorStream(true).start();
            Map<String, ProcessCaps.State> found;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                found = ProcessCaps.parse(r, packages);
            }
            // The whole output was read: dumpsys is done (destroyed below in any case).
            return found;
        } catch (Exception e) {
            return new java.util.LinkedHashMap<>();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
