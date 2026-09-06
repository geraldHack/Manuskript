package com.manuskript.mindmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fenstergröße/-position unter {@code config/mindmap-ui.json}. */
public final class MindmapUiSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final double DEFAULT_WIDTH = 960.0;
    static final double DEFAULT_HEIGHT = 640.0;
    static final double MIN_WIDTH = 640.0;
    static final double MIN_HEIGHT = 480.0;
    static final double MAX_WIDTH = 3200.0;
    static final double MAX_HEIGHT = 2400.0;

    public double width = DEFAULT_WIDTH;
    public double height = DEFAULT_HEIGHT;
    public Double x;
    public Double y;

    public static Path file(Path configDir) {
        Path root = configDir == null ? Path.of(".") : configDir;
        return root.resolve("config").resolve("mindmap-ui.json");
    }

    public static MindmapUiSettings load(Path configDir) {
        Path file = file(configDir);
        if (!Files.isRegularFile(file)) {
            return new MindmapUiSettings();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MindmapUiSettings settings = GSON.fromJson(reader, MindmapUiSettings.class);
            return settings != null ? settings.clamped() : new MindmapUiSettings();
        } catch (Exception e) {
            return new MindmapUiSettings();
        }
    }

    public void save(Path configDir) throws IOException {
        clamped();
        Path file = file(configDir);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    MindmapUiSettings clamped() {
        width = clamp(width, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH);
        height = clamp(height, MIN_HEIGHT, MAX_HEIGHT, DEFAULT_HEIGHT);
        if (x != null && (Double.isNaN(x) || Double.isInfinite(x))) {
            x = null;
        }
        if (y != null && (Double.isNaN(y) || Double.isInfinite(y))) {
            y = null;
        }
        return this;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
