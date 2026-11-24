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
     * In: a PendingIntent created by the caller (AARE) for its own service. The helper sends it with
     * the shell's right to start things from the background, so the service started this way may
     * read the location while in use (Android's exemption for starts by an app that holds
     * START_ACTIVITIES_FROM_BACKGROUND). Out: 1 if sent, 0 if refused or failed. Only the caller's
     * own PendingIntents are sent.
     */
    static final int TRANSACTION_SEND_OWN = FIRST_CALL_TRANSACTION + 2;
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
            case TRANSACTION_SEND_OWN: {
                data.enforceInterface(DESCRIPTOR);
                android.app.PendingIntent pi = android.app.PendingIntent.readPendingIntentOrNullFromParcel(data);
                reply.writeNoException();
                reply.writeInt(sendOwn(pi, Binder.getCallingUid()) ? 1 : 0);
                return true;
            }
            case TRANSACTION_DESTROY:
                System.exit(0);
                return true;
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    /** Sends [pi] only if the app that asks ([callingUid]) created it: never another app's. */
    private static boolean sendOwn(android.app.PendingIntent pi, int callingUid) {
        if (pi == null || pi.getCreatorUid() != callingUid) return false;
        try {
            android.os.Bundle options = null;
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // The sender must opt in to lend its background-start right (1: ALLOWED, 3: ALLOW_ALWAYS from 36).
                options = android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(android.os.Build.VERSION.SDK_INT >= 36 ? 3 : 1)
                        .toBundle();
            }
            pi.send(null, 0, null, null, null, null, options);
            return true;
        } catch (Exception e) {
            return false;
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
