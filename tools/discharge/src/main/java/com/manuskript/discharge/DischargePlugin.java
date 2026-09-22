package com.manuskript.discharge;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Toolbar-Plugin „Discharge“ — Poor-Man-Discord.
 */
public final class DischargePlugin implements ManuskriptPlugin {

    public static final String PLUGIN_ID = "discharge";

    private final AtomicBoolean backgroundStarted = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;
    private DischargeWindow window;
    private PluginHost host;
    private DischargeClient client;
    private DischargeStore store;
    private volatile boolean windowOpen;
    private AudioClip notifyClip;

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public String label() {
        return "Discharge";
    }

    @Override
    public boolean wantsBackgroundStart() {
        return true;
    }

    @Override
    public synchronized void startBackground(PluginHost host) {
        this.host = host;
        ensureServices(host);
        if (!backgroundStarted.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discharge-poll");
            t.setDaemon(true);
            return t;
        });
        // Häufiger pollen, damit Piep/Rahmen zeitnah kommen
        pollFuture = scheduler.scheduleAtFixedRate(this::pollQuietly, 3, 8, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void start(PluginHost host) {
        this.host = host;
        ensureServices(host);
        startBackground(host);
        host.setToolbarAttention(PLUGIN_ID, false);
        if (window == null) {
            window = new DischargeWindow(host, client, store, this::onWindowClosed);
        }
        windowOpen = true;
        window.show();
    }

    @Override
    public synchronized void stop() {
        windowOpen = false;
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        backgroundStarted.set(false);
        if (window != null) {
            window.close();
            window = null;
        }
    }

    private void onWindowClosed() {
        windowOpen = false;
    }

    private void ensureServices(PluginHost host) {
        if (store == null) {
            store = new DischargeStore(host.configDir());
        }
        if (client == null) {
            client = new DischargeClient(DischargeUrls.DEFAULT_BASE);
        }
        client.setShareOwnPresence(store.shareOwnPresence());
    }

    private void pollQuietly() {
        try {
            DischargeModels.Identity identity = store.loadIdentity();
            if (identity == null || !identity.isValid()) {
                return;
            }
            long since = store.pollCursor();
            DischargeModels.PollResult result = client.poll(identity.token(), since).join();
            if (result.cursor() > since) {
                store.setPollCursor(result.cursor());
            }
            for (DischargeModels.ChatMessage msg : result.messages()) {
                store.appendHistory(msg);
            }

            boolean incoming = false;
            for (DischargeModels.ChatMessage msg : result.messages()) {
                if (identity.id().equals(msg.to())) {
                    incoming = true;
                    break;
                }
            }
            boolean pendingFriends = result.pendingFriendRequests() > 0;
            boolean alert = incoming || pendingFriends;

            boolean dischargeFocused = windowOpen && window != null && window.isStageFocused();

            if (alert && !dischargeFocused) {
                // Gelber Rand am Toolbar-Button, solange Discharge nicht im Vordergrund ist
                host.setToolbarAttention(PLUGIN_ID, true);
                if (store.soundEnabled()) {
                    Platform.runLater(this::playAlertSound);
                }
            }

            if (windowOpen && window != null) {
                Platform.runLater(() -> window.onPolled(result));
            }
        } catch (Exception ignored) {
            // Hintergrund: still
        }
    }

    private void playAlertSound() {
        try {
            host.playNotificationSound();
        } catch (Exception ignored) {
            // Host-Piep optional
        }
        try {
            if (notifyClip == null) {
                URL url = DischargePlugin.class.getResource("/sound/notify.wav");
                if (url != null) {
                    notifyClip = new AudioClip(url.toExternalForm());
                }
            }
            if (notifyClip != null) {
                notifyClip.play();
            }
        } catch (Exception ignored) {
            // Kein Sound verfügbar
        }
    }
}
