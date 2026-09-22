package com.manuskript.discharge;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTPS-Client für die Discharge-Chat-API.
 */
public final class DischargeClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "discharge-http");
        t.setDaemon(true);
        return t;
    });

    private final HttpClient http;
    private final String baseUrl;
    /** Wenn false: Server aktualisiert last_seen nicht → für Freunde offline. */
    private volatile boolean shareOwnPresence = true;

    public DischargeClient(String baseUrl) {
        String base = baseUrl == null || baseUrl.isBlank() ? DischargeUrls.DEFAULT_BASE : baseUrl.trim();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        if (!DischargeUrls.isAllowedBase(base)) {
            throw new IllegalArgumentException("Chat-API-URL nicht erlaubt: " + base);
        }
        this.baseUrl = base;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Eigenen Online-Status an Freunde melden (Default: true). */
    public void setShareOwnPresence(boolean share) {
        this.shareOwnPresence = share;
    }

    public boolean shareOwnPresence() {
        return shareOwnPresence;
    }

    /** Leichter Auth-Call, um Presence sofort zu setzen/löschen. */
    public CompletableFuture<Void> touchPresence(String token) {
        return getJson("me", token, Map.of()).thenAccept(DischargeClient::requireOk);
    }

    public CompletableFuture<DischargeModels.Identity> register(String displayName) {
        Map<String, Object> body = Map.of("displayName", displayName == null ? "" : displayName);
        return postJson("register", null, body).thenApply(map -> {
            requireOk(map);
            return new DischargeModels.Identity(
                    DischargeModels.str(map.get("id")),
                    DischargeModels.str(map.get("displayName")),
                    DischargeModels.str(map.get("token")));
        });
    }

    public CompletableFuture<DischargeModels.FriendsSnapshot> friends(String token) {
        return getJson("friends", token, Map.of()).thenApply(map -> {
            requireOk(map);
            return new DischargeModels.FriendsSnapshot(
                    DischargeModels.parsePeers(map.get("friends")),
                    DischargeModels.parsePeers(map.get("incoming")),
                    DischargeModels.parsePeers(map.get("outgoing")));
        });
    }

    public CompletableFuture<Void> requestFriend(String token, String toId) {
        return postJson("friends/request", token, Map.of("to", toId == null ? "" : toId))
                .thenAccept(DischargeClient::requireOk);
    }

    public CompletableFuture<Void> respondFriend(String token, String fromId, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", fromId == null ? "" : fromId);
        body.put("action", action == null ? "" : action);
        return postJson("friends/respond", token, body).thenAccept(DischargeClient::requireOk);
    }

    public CompletableFuture<DischargeModels.ChatMessage> send(String token, String toId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", toId == null ? "" : toId);
        body.put("text", text == null ? "" : text);
        return postJson("messages/send", token, body).thenApply(map -> {
            requireOk(map);
            Map<String, Object> msg = DischargeModels.asMap(map.get("message"));
            return new DischargeModels.ChatMessage(
                    DischargeModels.num(msg.get("id")),
                    DischargeModels.str(msg.get("channelId")),
                    DischargeModels.str(msg.get("from")),
                    DischargeModels.str(msg.get("to")),
                    DischargeModels.str(msg.get("text")),
                    DischargeModels.num(msg.get("createdAt")));
        });
    }

    public CompletableFuture<DischargeModels.PollResult> poll(String token, long since) {
        return getJson("messages/poll", token, Map.of("since", String.valueOf(Math.max(0, since))))
                .thenApply(map -> {
                    requireOk(map);
                    long cursor = DischargeModels.num(map.get("cursor"));
                    int pending = (int) DischargeModels.num(map.get("pendingFriendRequests"));
                    long friendshipsRevision = DischargeModels.num(map.get("friendshipsRevision"));
                    int friendsAccepted = (int) DischargeModels.num(map.get("friendsAccepted"));
                    boolean attention = Boolean.TRUE.equals(map.get("hasAttention"))
                            || pending > 0
                            || !DischargeModels.parseMessages(map.get("messages")).isEmpty();
                    return new DischargeModels.PollResult(
                            cursor,
                            DischargeModels.parseMessages(map.get("messages")),
                            DischargeModels.parseUnread(map.get("unread")),
                            pending,
                            friendshipsRevision,
                            friendsAccepted,
                            attention);
                });
    }

    private CompletableFuture<Map<String, Object>> getJson(String path, String token, Map<String, String> query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(buildGet(path, token, query));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new DischargeApiException("Netzwerkfehler: " + e.getMessage(), e);
            }
        }, EXECUTOR);
    }

    private CompletableFuture<Map<String, Object>> postJson(String path, String token, Map<String, Object> body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(buildPost(path, token, body));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new DischargeApiException("Netzwerkfehler: " + e.getMessage(), e);
            }
        }, EXECUTOR);
    }

    private HttpRequest buildGet(String path, String token, Map<String, String> query) {
        String op = pathToOp(path);
        StringBuilder url = new StringBuilder(baseUrl).append("index.php?op=")
                .append(URLEncoder.encode(op, StandardCharsets.UTF_8));
        if (query != null) {
            for (Map.Entry<String, String> e : query.entrySet()) {
                url.append('&')
                        .append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
            b.header("X-Discharge-Token", token.trim());
            b.header("X-Discharge-Presence", shareOwnPresence ? "1" : "0");
        }
        return b.build();
    }

    private HttpRequest buildPost(String path, String token, Map<String, Object> body) {
        String op = pathToOp(path);
        String json = SimpleJson.stringify(body == null ? Map.of() : body);
        String url = baseUrl + "index.php?op=" + URLEncoder.encode(op, StandardCharsets.UTF_8);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
            b.header("X-Discharge-Token", token.trim());
            b.header("X-Discharge-Presence", shareOwnPresence ? "1" : "0");
        }
        return b.build();
    }

    private static String pathToOp(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.trim().replace('/', '_');
    }

    private Map<String, Object> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> map = SimpleJson.parseObject(response.body() == null ? "{}" : response.body());
        if (response.statusCode() >= 400) {
            String err = DischargeModels.str(map.get("message"));
            if (err.isBlank()) {
                err = DischargeModels.str(map.get("error"));
            }
            if (err.isBlank()) {
                err = "HTTP " + response.statusCode();
            }
            throw new DischargeApiException(err);
        }
        return map;
    }

    private static void requireOk(Map<String, Object> map) {
        if (map == null) {
            throw new DischargeApiException("Leere Antwort");
        }
        Object ok = map.get("ok");
        if (Boolean.FALSE.equals(ok) || "false".equalsIgnoreCase(String.valueOf(ok))) {
            String msg = DischargeModels.str(map.get("message"));
            if (msg.isBlank()) {
                msg = DischargeModels.str(map.get("error"));
            }
            throw new DischargeApiException(msg.isBlank() ? "API-Fehler" : msg);
        }
    }

    public static final class DischargeApiException extends RuntimeException {
        public DischargeApiException(String message) {
            super(message);
        }

        public DischargeApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
