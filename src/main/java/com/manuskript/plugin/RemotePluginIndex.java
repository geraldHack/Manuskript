package com.manuskript.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plugins im öffentlichen Ordner auf spoteroxe.de: jede {@code .jar} plus gleichnamige {@code .txt}.
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

    /**
     * Wählt pro Plugin-ID die höchste Versions-JAR und hängt die Notes an.
     */
    public static RemotePluginIndex fromListing(URI directory, List<String> fileNames, Map<String, PluginNotes> notesByJar) {
        if (directory == null) {
            throw new IllegalArgumentException("Plugin-Ordner fehlt");
        }
        Map<String, Candidate> latest = new LinkedHashMap<>();
        if (fileNames != null) {
            for (String name : fileNames) {
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                PluginJarName.Parsed parsed = PluginJarName.parse(name);
                if (parsed == null) {
                    continue;
                }
                URI jarUri = directory.resolve(name);
                if (!PluginCatalogUrls.isAllowed(jarUri)) {
                    logger.warn("JAR-URL nicht erlaubt: {}", name);
                    continue;
                }
                Candidate current = latest.get(parsed.id());
                if (current == null || PluginVersions.compare(current.version(), parsed.version()) < 0) {
                    latest.put(parsed.id(), new Candidate(parsed, name, jarUri));
                }
            }
        }
        List<Spec> specs = new ArrayList<>();
        for (Candidate candidate : latest.values()) {
            PluginNotes notes = notesByJar != null
                    ? notesByJar.getOrDefault(candidate.remoteFileName().toLowerCase(Locale.ROOT), PluginNotes.empty())
                    : PluginNotes.empty();
            String label = !notes.label().isBlank() ? notes.label() : candidate.parsed().id();
            String version = !notes.version().isBlank() ? notes.version() : candidate.version();
            specs.add(new Spec(
                    candidate.parsed().id(),
                    label,
                    version,
                    notes.description(),
                    candidate.parsed().localFileName(),
                    candidate.jarUri(),
                    "",
                    notes.requires()));
        }
        return new RemotePluginIndex("", specs);
    }

    private record Candidate(PluginJarName.Parsed parsed, String remoteFileName, URI jarUri) {
        String version() {
            return parsed.version();
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
