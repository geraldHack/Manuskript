package com.manuskript.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Offizieller Plugin-Index von spoteroxe.de.
 */
public final class RemotePluginIndex {

    private static final Logger logger = LoggerFactory.getLogger(RemotePluginIndex.class);

    private final String updated;
    private final List<Spec> plugins;

    public RemotePluginIndex(String updated, List<Spec> plugins) {
        this.updated = updated == null ? "" : updated;
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public String updated() {
        return updated;
    }

    public List<Spec> plugins() {
        return plugins;
    }

    public static RemotePluginIndex parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Leerer Plugin-Katalog");
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Plugin-Katalog ist kein JSON-Objekt");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Plugin-Katalog ungültig: " + e.getMessage(), e);
        }
        String updated = text(root, "updated");
        JsonArray array = root.has("plugins") && root.get("plugins").isJsonArray()
                ? root.getAsJsonArray("plugins")
                : new JsonArray();
        List<Spec> specs = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        Set<String> seenFiles = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            Spec spec = parseSpec(element.getAsJsonObject());
            if (spec == null) {
                continue;
            }
            if (!seenIds.add(spec.id()) || !seenFiles.add(spec.fileName().toLowerCase())) {
                logger.warn("Doppelter Plugin-Eintrag ignoriert: {}", spec.id());
                continue;
            }
            specs.add(spec);
        }
        return new RemotePluginIndex(updated, specs);
    }

    private static Spec parseSpec(JsonObject obj) {
        String id = text(obj, "id");
        String fileName = text(obj, "fileName");
        String sha256 = PluginCatalogUrls.normalizeSha256(text(obj, "sha256"));
        String jar = text(obj, "jar");
        if (!PluginCatalogUrls.isAllowedId(id)
                || !PluginCatalogUrls.isAllowedFileName(fileName)
                || !PluginCatalogUrls.isAllowedSha256(sha256)) {
            logger.warn("Plugin-Eintrag unvollständig oder ungültig: id={}, fileName={}", id, fileName);
            return null;
        }
        URI jarUri;
        try {
            jarUri = URI.create(jar);
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültige JAR-URL für {}: {}", id, jar);
            return null;
        }
        if (!PluginCatalogUrls.isAllowed(jarUri)) {
            logger.warn("JAR-URL nicht erlaubt für {}: {}", id, jar);
            return null;
        }
        String label = text(obj, "label");
        if (label.isBlank()) {
            label = id;
        }
        return new Spec(
                id,
                label,
                text(obj, "version"),
                text(obj, "description"),
                fileName,
                jarUri,
                sha256,
                text(obj, "requires"));
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    public record Spec(
            String id,
            String label,
            String version,
            String description,
            String fileName,
            URI jar,
            String sha256,
            String requires) {
    }
}
