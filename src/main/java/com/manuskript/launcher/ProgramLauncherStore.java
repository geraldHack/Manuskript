package com.manuskript.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.manuskript.ApplicationPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Liest und schreibt {@code config/launchers.json}.
 */
public final class ProgramLauncherStore {

    private static final Logger logger = LoggerFactory.getLogger(ProgramLauncherStore.class);
    private static final String RELATIVE = "config/launchers.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProgramLauncherStore() {
    }

    public static Path filePath() {
        return ApplicationPaths.resolveConfigPath(RELATIVE).toPath();
    }

    public static List<ProgramLauncher> load() {
        Path path = filePath();
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            FileModel model = GSON.fromJson(json, FileModel.class);
            if (model == null || model.launchers == null) {
                return new ArrayList<>();
            }
            List<ProgramLauncher> out = new ArrayList<>();
            for (ProgramLauncher launcher : model.launchers) {
                if (launcher == null) {
                    continue;
                }
                if (launcher.getId() == null || launcher.getId().isBlank()) {
                    launcher.setId(newId());
                }
                out.add(launcher);
            }
            return out;
        } catch (Exception e) {
            logger.warn("launchers.json konnte nicht gelesen werden: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void save(List<ProgramLauncher> launchers) {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            FileModel model = new FileModel();
            model.launchers = launchers != null ? new ArrayList<>(launchers) : new ArrayList<>();
            Files.writeString(path, GSON.toJson(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("launchers.json konnte nicht geschrieben werden: {}", e.getMessage());
            throw new IllegalStateException("Starter konnten nicht gespeichert werden: " + e.getMessage(), e);
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private static final class FileModel {
        List<ProgramLauncher> launchers;
    }
}
