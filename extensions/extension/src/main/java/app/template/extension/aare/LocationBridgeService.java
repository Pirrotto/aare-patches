package app.template.extension.aare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * The bridge for "Location for navigation at the start" (test). AARE's projection service starts
 * from the background when the car connects, and Android doesn't give such a foreground service
 * the "location while in use" capability, so AARE has none to pass to the navigation app it shows
 * (black map until AARE is opened on the phone). Android gives it to a foreground service started
 * by an app holding START_ACTIVITIES_FROM_BACKGROUND, as Android Auto does: the Shizuku helper
 * (shell) starts this service, of type location, inside AARE's process, and the capability is the
 * process's, so the car app gets it too.
 *
 * Exported only to holders of INTERACT_ACROSS_USERS_FULL (the system and the shell; no ordinary
 * app), like AARE's own Shizuku provider. It reads nothing and returns nothing; started without a
 * car connected it stops at once, and it stops when the car disconnects.
 */
public class LocationBridgeService extends Service {
    private static final String TAG = "AareLocationBridge";
    private static final String CHANNEL = "aare_patches_location";
    private static final int NOTIFICATION_ID = 0x4c4f43;
    private static final long CHECK_MS = 10_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (!TunnelNavigation.connected()) {
                PatchLog.add("location", "bridge: car disconnected, stopped");
                stopSelf();
                return;
            }
            main.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A start for a foreground service must reach startForeground, even when it stops at once.
        try {
            goForeground();
        } catch (Throwable t) {
            Log.w(TAG, "startForeground", t);
            PatchLog.add("location", "bridge: could not start (" + t.getClass().getSimpleName() + ")");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!PatchSettings.isEnabled(this, PatchSettings.LOCATION_FIX) || !TunnelNavigation.connected()) {
            PatchLog.add("location", "bridge: started without a car connected or switched off, stopped");
            stopSelf();
            return START_NOT_STICKY;
        }
        main.removeCallbacks(check);
        main.postDelayed(check, CHECK_MS);
        PatchLog.add("location", "bridge: running while the car is connected");
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                        PatchSettings.phrase("Location for navigation", "Posizione per la navigazione"),
                        NotificationManager.IMPORTANCE_MIN));
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(PatchSettings.phrase("Location for navigation", "Posizione per la navigazione"))
                .setContentText(PatchSettings.phrase("AARE passes the location to the navigation app while the car is connected",
                        "AARE passa la posizione all'app di navigazione mentre l'auto è collegata"))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(check);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
