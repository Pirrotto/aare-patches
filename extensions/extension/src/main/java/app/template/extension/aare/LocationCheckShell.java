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
import java.util.concurrent.TimeUnit;

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
            case TRANSACTION_DESTROY:
                System.exit(0);
                return true;
            default:
                return super.onTransact(code, data, reply, flags);
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
            process.waitFor(5, TimeUnit.SECONDS);
            return found;
        } catch (Exception e) {
            return new java.util.LinkedHashMap<>();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
