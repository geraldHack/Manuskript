package com.manuskript.discharge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DTOs für die Discharge-Chat-API. */
public final class DischargeModels {

    private DischargeModels() {
    }

    public record Identity(String id, String displayName, String token) {
        public boolean isValid() {
            return id != null && !id.isBlank()
                    && token != null && !token.isBlank();
        }
    }

    public record Peer(String id, String displayName, String status, boolean online) {
        public Peer(String id, String displayName, String status) {
            this(id, displayName, status, false);
        }
    }

    public record FriendsSnapshot(List<Peer> friends, List<Peer> incoming, List<Peer> outgoing) {
        public static FriendsSnapshot empty() {
            return new FriendsSnapshot(List.of(), List.of(), List.of());
        }
    }

    public record ChatMessage(long id, String channelId, String from, String to, String text, long createdAt) {
    }

    public record PollResult(
            long cursor,
            List<ChatMessage> messages,
            Map<String, Integer> unread,
            int pendingFriendRequests,
            long friendshipsRevision,
            int friendsAccepted,
            boolean hasAttention
    ) {
        public static PollResult empty(long cursor) {
            return new PollResult(cursor, List.of(), Map.of(), 0, 0, 0, false);
        }
    }

    static List<Peer> parsePeers(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Peer> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String id = str(map.get("id"));
            if (id.isBlank()) {
                continue;
            }
            out.add(new Peer(
                    id,
                    str(map.get("displayName")),
                    str(map.get("status")),
                    bool(map.get("online"))));
        }
        return Collections.unmodifiableList(out);
    }

    static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return o != null && "true".equalsIgnoreCase(String.valueOf(o).trim());
    }

    static List<ChatMessage> parseMessages(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ChatMessage> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            long id = num(map.get("id"));
            String text = str(map.get("text"));
            if (text.isBlank()) {
                continue;
            }
            out.add(new ChatMessage(
                    id,
                    str(map.get("channelId")),
                    str(map.get("from")),
                    str(map.get("to")),
                    text,
                    num(map.get("createdAt"))));
        }
        return Collections.unmodifiableList(out);
    }

    static Map<String, Integer> parseUnread(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            out.put(key, (int) num(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static long num(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
