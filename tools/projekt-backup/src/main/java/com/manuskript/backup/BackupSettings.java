package com.manuskript.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen unter {@code config/projekt-backup.json}.
 */
public final class BackupSettings {

    public List<BackupTarget> targets = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path file(Path configDir) {
        Path root = configDir == null ? Path.of(".") : configDir;
        return root.resolve("config").resolve("projekt-backup.json");
    }

    public static BackupSettings load(Path configDir) {
        Path file = file(configDir);
        if (!Files.isRegularFile(file)) {
            return new BackupSettings();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return new BackupSettings();
            }
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("targets") && obj.get("targets").isJsonArray()) {
                BackupSettings settings = GSON.fromJson(obj, BackupSettings.class);
                if (settings.targets == null) {
                    settings.targets = new ArrayList<>();
                }
                ensureIds(settings);
                return settings;
            }
            return migrateLegacy(obj);
        } catch (Exception e) {
            return new BackupSettings();
        }
    }

    public void save(Path configDir) throws IOException {
        Path file = file(configDir);
        Files.createDirectories(file.getParent());
        if (targets == null) {
            targets = new ArrayList<>();
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    public BackupTarget findById(String id) {
        if (id == null || targets == null) {
            return null;
        }
        for (BackupTarget target : targets) {
            if (id.equals(target.id)) {
                return target;
            }
        }
        return null;
    }

    static BackupSettings migrateLegacy(JsonObject obj) {
        BackupSettings settings = new BackupSettings();
        BackupTarget target = new BackupTarget();
        target.name = "Standard";
        target.destination = string(obj, "destination");
        target.compress = bool(obj, "compress", true);
        target.encrypt = bool(obj, "encrypt", false);
        target.schedule = string(obj, "schedule");
        if (target.schedule.isBlank()) {
            target.schedule = BackupSchedule.OFF.name();
        }
        target.keep = obj.has("keep") && obj.get("keep").isJsonPrimitive()
                ? obj.get("keep").getAsInt()
                : 10;
        target.lastBackupIso = string(obj, "lastBackupIso");
        target.lastBackupFile = string(obj, "lastBackupFile");
        settings.targets.add(target);
        return settings;
    }

    private static void ensureIds(BackupSettings settings) {
        for (BackupTarget target : settings.targets) {
            if (target.id == null || target.id.isBlank()) {
                target.id = java.util.UUID.randomUUID().toString();
            }
            if (target.sshPort <= 0) {
                target.sshPort = 22;
            }
            if (target.keep <= 0) {
                target.keep = 10;
            }
        }
    }

    private static String string(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean bool(JsonObject obj, String key, boolean fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }
}
