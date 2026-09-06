package com.manuskript.mindmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MindmapStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MindmapStore() {
    }

    public static Path file(Path projectRoot) {
        return projectRoot.resolve("data").resolve("mindmap.json");
    }

    public static MindmapModel load(Path projectRoot) {
        Path file = file(projectRoot);
        if (!Files.isRegularFile(file)) {
            return new MindmapModel();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MindmapModel model = GSON.fromJson(reader, MindmapModel.class);
            return model != null ? model : new MindmapModel();
        } catch (Exception e) {
            return new MindmapModel();
        }
    }

    public static void save(Path projectRoot, MindmapModel model) throws IOException {
        Path file = file(projectRoot);
        Files.createDirectories(file.getParent());
        if (model.nodes == null) {
            model.nodes = new java.util.ArrayList<>();
        }
        if (model.edges == null) {
            model.edges = new java.util.ArrayList<>();
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(model, writer);
        }
    }

    public static String toJson(MindmapModel model) {
        return GSON.toJson(model);
    }
}
