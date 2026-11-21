package app.template.extension.aare.adb;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds the port of the phone's own wireless debugging service, which Android announces over mDNS
 * and changes each time. Adapted from Shizuku's AdbMdns (Apache-2.0).
 */
final class AdbMdns {
    static final String TLS_CONNECT = "_adb-tls-connect._tcp";
    static final String TLS_PAIRING = "_adb-tls-pairing._tcp";
    private static final String TAG = "AarePatches";

    /** Something to notify of each port found while the search runs. */
    interface Listener {
        void onPort(int port);
    }

    private final NsdManager nsd;
    private final String type;
    private final Listener listener;
    private volatile boolean running;

    private final NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.w(TAG, "mdns: start failed " + errorCode);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
        }

        @Override
        public void onServiceFound(NsdServiceInfo info) {
            try {
                //noinspection deprecation
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.w(TAG, "mdns: resolve failed " + errorCode);
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolved) {
                        //noinspection deprecation
                        InetAddress host = resolved.getHost();
                        if (running && host != null && isThisPhone(host) && isListening(resolved.getPort())) {
                            listener.onPort(resolved.getPort());
                        }
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "mdns: resolve", t);
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo info) {
        }
    };

    AdbMdns(Context context, String type, Listener listener) {
        this.nsd = context.getSystemService(NsdManager.class);
        this.type = type;
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
    }

    void stop() {
        if (!running) return;
        running = false;
        try {
            nsd.stopServiceDiscovery(discovery);
        } catch (Throwable ignored) {
        }
    }

    /** Waits for the first port of the service, or -1 after the timeout. */
    static int find(Context context, String type, long timeoutMillis) {
        AtomicInteger port = new AtomicInteger(-1);
        CountDownLatch found = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(context, type, p -> {
            if (port.compareAndSet(-1, p)) found.countDown();
        });
        mdns.start();
        try {
            found.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            mdns.stop();
        }
        return port.get();
    }

    /** Only the service of this phone, not of another phone on the same Wi-Fi. */
    private static boolean isThisPhone(InetAddress host) {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.getHostAddress() != null && a.getHostAddress().equals(host.getHostAddress())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** The port is in use on this phone (by adbd), so it isn't a stale announcement. */
    private static boolean isListening(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.bind(new InetSocketAddress("127.0.0.1", port), 1);
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
