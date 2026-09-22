package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOTD von spoteroxe.de: Fetch, Parse, Anzeige-Entscheidung und Persistenz.
 */
public final class MotdService {

    private static final Logger logger = LoggerFactory.getLogger(MotdService.class);

    public static final String PARAM_ENABLED = "motd.enabled";
    public static final String PARAM_LAST_SEEN_ID = "motd.last_seen_id";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BYTES = 64 * 1024;

    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "motd-fetch");
        t.setDaemon(true);
        return t;
    });

    public record Message(String id, String title, String body) {
        public boolean isValid() {
            return id != null && !id.isBlank() && body != null && !body.isBlank();
        }
    }

    private MotdService() {
    }

    public static boolean isEnabled() {
        if (OfflineMode.isEnabled()) {
            return false;
        }
        return Boolean.parseBoolean(ResourceManager.getParameter(PARAM_ENABLED, "true"));
    }

    public static void setEnabled(boolean enabled) {
        ResourceManager.saveParameter(PARAM_ENABLED, String.valueOf(enabled));
    }

    public static String lastSeenId() {
        String id = ResourceManager.getParameter(PARAM_LAST_SEEN_ID, "");
        return id == null ? "" : id.trim();
    }

    public static void markSeen(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        ResourceManager.saveParameter(PARAM_LAST_SEEN_ID, id.trim());
    }

    public static void clearLastSeenId() {
        ResourceManager.saveParameter(PARAM_LAST_SEEN_ID, "");
    }

    /** true, wenn die Nachricht dem Nutzer gezeigt werden soll. */
    public static boolean shouldShow(Message message) {
        if (!isEnabled() || message == null || !message.isValid()) {
            return false;
        }
        return !message.id().trim().equalsIgnoreCase(lastSeenId());
    }

    public static Optional<Message> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        int sep = text.indexOf("\n---\n");
        if (sep < 0 && text.endsWith("\n---")) {
            sep = text.length() - 4; // position of \n before ---
        }
        String header;
        String body;
        if (sep >= 0) {
            header = text.substring(0, sep).trim();
            int bodyStart = sep + 1;
            while (bodyStart < text.length() && text.charAt(bodyStart) == '-') {
                bodyStart++;
            }
            if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
                bodyStart++;
            }
            body = bodyStart < text.length() ? text.substring(bodyStart).trim() : "";
        } else if (text.startsWith("---\n")) {
            return Optional.empty();
        } else {
            // Fallback: erste Zeile = id, Rest = Body
            int nl = text.indexOf('\n');
            if (nl < 0) {
                return Optional.empty();
            }
            header = "id: " + text.substring(0, nl).trim();
            body = text.substring(nl + 1).trim();
            if ("---".equals(body) || body.startsWith("---\n")) {
                return Optional.empty();
            }
        }
        if (body.isBlank()) {
            return Optional.empty();
        }
        String id = "";
        String title = "";
        for (String line : header.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(colon + 1).trim();
            if ("id".equals(key)) {
                id = value;
            } else if ("title".equals(key)) {
                title = value;
            }
        }
        if (id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Message(id, title, body));
    }

    public static Optional<Message> fetch() {
        return fetch(MotdUrls.motdUri(), defaultClient());
    }

    static Optional<Message> fetch(URI uri, HttpClient client) {
        if (!MotdUrls.isAllowed(uri)) {
            logger.debug("MOTD-URL nicht erlaubt: {}", uri);
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/plain,*/*")
                    .header("User-Agent", "Manuskript/" + AppVersion.current() + " Motd")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return Optional.empty();
            }
            String body = response.body();
            if (body.length() > MAX_BYTES) {
                body = body.substring(0, MAX_BYTES);
            }
            return parse(body);
        } catch (Exception e) {
            logger.debug("MOTD nicht geladen: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Asynchron laden; Callback nur bei anzeigbarer Nachricht. */
    public static void fetchForDisplayAsync(java.util.function.Consumer<Message> onShow) {
        if (onShow == null || !isEnabled()) {
            return;
        }
        CompletableFuture.supplyAsync(MotdService::fetch, FETCH_EXECUTOR)
                .thenAccept(opt -> opt.filter(MotdService::shouldShow).ifPresent(onShow))
                .exceptionally(ex -> {
                    logger.debug("MOTD-Fetch fehlgeschlagen: {}", ex.toString());
                    return null;
                });
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
