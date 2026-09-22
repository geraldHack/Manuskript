package com.manuskript.discharge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lokale Persistenz: Identity, Settings, Chat-Verläufe. */
public final class DischargeStore {

    private final Path root;

    public DischargeStore(Path configDir) {
        this.root = configDir.resolve("discharge");
    }

    public Path root() {
        return root;
    }

    public DischargeModels.Identity loadIdentity() {
        Path file = root.resolve("identity.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            Map<String, Object> map = SimpleJson.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            DischargeModels.Identity id = new DischargeModels.Identity(
                    DischargeModels.str(map.get("id")),
                    DischargeModels.str(map.get("displayName")),
                    DischargeModels.str(map.get("token")));
            return id.isValid() ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void saveIdentity(DischargeModels.Identity identity) throws IOException {
        ensureRoot();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", identity.id());
        map.put("displayName", identity.displayName());
        map.put("token", identity.token());
        Files.writeString(root.resolve("identity.json"), SimpleJson.stringify(map), StandardCharsets.UTF_8);
    }

    public boolean soundEnabled() {
        Map<String, Object> s = loadSettings();
        Object v = s.get("soundEnabled");
        if (v == null) {
            return true;
        }
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    public void setSoundEnabled(boolean enabled) throws IOException {
        Map<String, Object> s = new LinkedHashMap<>(loadSettings());
        s.put("soundEnabled", enabled);
        saveSettings(s);
    }

    public boolean alwaysExpandMessages() {
        Map<String, Object> s = loadSettings();
        Object v = s.get("alwaysExpandMessages");
        if (v == null) {
            return false;
        }
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    public void setAlwaysExpandMessages(boolean enabled) throws IOException {
        Map<String, Object> s = new LinkedHashMap<>(loadSettings());
        s.put("alwaysExpandMessages", enabled);
        saveSettings(s);
    }

    /** Eigenen Online-Status an Freunde melden (Standard: an). */
    public boolean shareOwnPresence() {
        Map<String, Object> s = loadSettings();
        Object v = s.get("shareOwnPresence");
        if (v == null) {
            // ältere Einstellung
            v = s.get("showPresence");
        }
        if (v == null) {
            return true;
        }
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    public void setShareOwnPresence(boolean enabled) throws IOException {
        Map<String, Object> s = new LinkedHashMap<>(loadSettings());
        s.put("shareOwnPresence", enabled);
        s.remove("showPresence");
        saveSettings(s);
    }

    /** @deprecated Nutze {@link #shareOwnPresence()}. */
    @Deprecated
    public boolean showPresence() {
        return shareOwnPresence();
    }

    /** @deprecated Nutze {@link #setShareOwnPresence(boolean)}. */
    @Deprecated
    public void setShowPresence(boolean enabled) throws IOException {
        setShareOwnPresence(enabled);
    }

    /** Gespeicherte Fenstergeometrie; fehlende Werte = Defaults / zentriert. */
    public record WindowGeometry(double width, double height, Double x, Double y) {
        static final double DEFAULT_WIDTH = 920.0;
        static final double DEFAULT_HEIGHT = 620.0;
        static final double MIN_WIDTH = 760.0;
        static final double MIN_HEIGHT = 520.0;
        static final double MAX_WIDTH = 3200.0;
        static final double MAX_HEIGHT = 2400.0;

        public static WindowGeometry defaults() {
            return new WindowGeometry(DEFAULT_WIDTH, DEFAULT_HEIGHT, null, null);
        }

        WindowGeometry clamped() {
            double w = clamp(width, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH);
            double h = clamp(height, MIN_HEIGHT, MAX_HEIGHT, DEFAULT_HEIGHT);
            Double px = finiteOrNull(x);
            Double py = finiteOrNull(y);
            return new WindowGeometry(w, h, px, py);
        }

        private static double clamp(double value, double min, double max, double fallback) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static Double finiteOrNull(Double value) {
            if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        }
    }

    public WindowGeometry loadWindowGeometry() {
        Map<String, Object> s = loadSettings();
        if (!s.containsKey("windowWidth") && !s.containsKey("windowHeight")) {
            return WindowGeometry.defaults();
        }
        return new WindowGeometry(
                dbl(s.get("windowWidth"), WindowGeometry.DEFAULT_WIDTH),
                dbl(s.get("windowHeight"), WindowGeometry.DEFAULT_HEIGHT),
                optionalDbl(s.get("windowX")),
                optionalDbl(s.get("windowY"))).clamped();
    }

    public void saveWindowGeometry(double width, double height, double x, double y) throws IOException {
        WindowGeometry g = new WindowGeometry(width, height, x, y).clamped();
        Map<String, Object> s = new LinkedHashMap<>(loadSettings());
        s.put("windowWidth", g.width());
        s.put("windowHeight", g.height());
        if (g.x() != null) {
            s.put("windowX", g.x());
        } else {
            s.remove("windowX");
        }
        if (g.y() != null) {
            s.put("windowY", g.y());
        } else {
            s.remove("windowY");
        }
        saveSettings(s);
    }

    private static double dbl(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Double optionalDbl(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public long pollCursor() {
        return DischargeModels.num(loadSettings().get("pollCursor"));
    }

    public void setPollCursor(long cursor) throws IOException {
        Map<String, Object> s = new LinkedHashMap<>(loadSettings());
        s.put("pollCursor", cursor);
        saveSettings(s);
    }

    public void appendHistory(DischargeModels.ChatMessage message) throws IOException {
        if (message == null || message.channelId() == null || message.channelId().isBlank()) {
            return;
        }
        ensureRoot();
        Path dir = root.resolve("history");
        Files.createDirectories(dir);
        Path file = dir.resolve(safeFileName(message.channelId()) + ".jsonl");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.id());
        row.put("channelId", message.channelId());
        row.put("from", message.from());
        row.put("to", message.to());
        row.put("text", message.text());
        row.put("createdAt", message.createdAt());
        Files.writeString(file, SimpleJson.stringify(row) + "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    public List<DischargeModels.ChatMessage> loadHistory(String channelId) {
        Path file = root.resolve("history").resolve(safeFileName(channelId) + ".jsonl");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<DischargeModels.ChatMessage> out = new ArrayList<>();
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                Map<String, Object> map = SimpleJson.parseObject(line);
                out.add(new DischargeModels.ChatMessage(
                        DischargeModels.num(map.get("id")),
                        DischargeModels.str(map.get("channelId")),
                        DischargeModels.str(map.get("from")),
                        DischargeModels.str(map.get("to")),
                        DischargeModels.str(map.get("text")),
                        DischargeModels.num(map.get("createdAt"))));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> loadSettings() {
        Path file = root.resolve("settings.json");
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(SimpleJson.parseObject(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void saveSettings(Map<String, Object> settings) throws IOException {
        ensureRoot();
        Files.writeString(root.resolve("settings.json"), SimpleJson.stringify(settings), StandardCharsets.UTF_8);
    }

    private void ensureRoot() throws IOException {
        Files.createDirectories(root);
    }

    private static String safeFileName(String channelId) {
        return channelId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
