package com.manuskript.publish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lädt/speichert Publish-Daten und liest vorhandene Projektquellen.
 */
public final class PublishPackageStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    private PublishPackageStore() {
    }

    public static Path packageFile(Path projectRoot) {
        return projectRoot.resolve("data").resolve("publish_package.json");
    }

    public static Path pandocMetadataFile(Path projectRoot) {
        return projectRoot.resolve("data").resolve("pandoc_metadata.json");
    }

    public static Path selectionFile(Path projectRoot) {
        return projectRoot.resolve("data").resolve(".manuskript_selection.json");
    }

    public static Path defaultCover(Path projectRoot) {
        return projectRoot.resolve("cover_image.png");
    }

    public static PublishPackageModel loadOrSeed(Path projectRoot) {
        Path file = packageFile(projectRoot);
        PublishPackageModel model;
        if (Files.isRegularFile(file)) {
            model = load(file);
        } else {
            model = new PublishPackageModel();
        }
        seedFromPandoc(projectRoot, model);
        if (model.coverPath == null || model.coverPath.isBlank()) {
            Path cover = defaultCover(projectRoot);
            if (Files.isRegularFile(cover)) {
                model.coverPath = cover.toString();
            }
        }
        model.ensureKeywordSlots();
        return model;
    }

    public static PublishPackageModel load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PublishPackageModel model = GSON.fromJson(reader, PublishPackageModel.class);
            if (model == null) {
                return new PublishPackageModel();
            }
            model.ensureKeywordSlots();
            return model;
        } catch (Exception e) {
            return new PublishPackageModel();
        }
    }

    public static void save(Path projectRoot, PublishPackageModel model) throws IOException {
        if (model == null) {
            return;
        }
        model.ensureKeywordSlots();
        Path file = packageFile(projectRoot);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(model, writer);
        }
    }

    /**
     * Schreibt den Klappentext als {@code abstract} in {@code data/pandoc_metadata.json}
     * (Quelle für „Buch exportieren“), wenn {@link PublishPackageModel#exportBlurb()} aktiv ist.
     */
    public static void syncBlurbToExport(Path projectRoot, PublishPackageModel model) throws IOException {
        if (projectRoot == null || model == null || !model.exportBlurb()) {
            return;
        }
        Path pandoc = pandocMetadataFile(projectRoot);
        Map<String, String> map = new LinkedHashMap<>(loadPandocMap(projectRoot));
        map.put("abstract", model.blurb == null ? "" : model.blurb.trim());
        Files.createDirectories(pandoc.getParent());
        try (Writer writer = Files.newBufferedWriter(pandoc, StandardCharsets.UTF_8)) {
            GSON.toJson(map, writer);
        }
    }

    static void seedFromPandoc(Path projectRoot, PublishPackageModel model) {
        Path pandoc = pandocMetadataFile(projectRoot);
        if (!Files.isRegularFile(pandoc)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(pandoc, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (blank(model.title)) {
                model.title = text(obj, "title");
            }
            if (blank(model.subtitle)) {
                model.subtitle = text(obj, "subtitle");
            }
            if (blank(model.author)) {
                model.author = text(obj, "author");
            }
            if (blank(model.blurb)) {
                model.blurb = text(obj, "abstract");
            }
            if (blank(model.coverPath)) {
                model.coverPath = text(obj, "coverImage");
            }
        } catch (Exception ignored) {
            // Seed ist optional
        }
    }

    public static List<String> loadSelection(Path projectRoot) {
        Path file = selectionFile(projectRoot);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> names = GSON.fromJson(reader, STRING_LIST);
            return names == null ? Collections.emptyList() : names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static Map<String, String> loadPandocMap(Path projectRoot) {
        Path pandoc = pandocMetadataFile(projectRoot);
        if (!Files.isRegularFile(pandoc)) {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(pandoc, StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> map = GSON.fromJson(reader, mapType);
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
