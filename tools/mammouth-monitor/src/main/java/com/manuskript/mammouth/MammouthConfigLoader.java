package com.manuskript.mammouth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Liest Mammouth-API-Key und URL aus der Manuskript-Konfiguration.
 */
public final class MammouthConfigLoader {

    private static final String PREF_NODE = "/com/manuskript";

    private MammouthConfigLoader() {
    }

    public record MammouthConfig(String apiKey, String apiUrl, Path configRoot, String keySource, String urlSource) {
        public boolean isMammouth() {
            return apiUrl != null && apiUrl.toLowerCase().contains("mammouth.ai");
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String normalizedBaseUrl() {
            return MammouthClient.normalizeBaseUrl(
                    apiUrl == null || apiUrl.isBlank() ? MammouthClient.DEFAULT_BASE : apiUrl);
        }
    }

    public static MammouthConfig load(String[] args) {
        Path configRoot = resolveConfigRoot(args);
        Map<String, String> values = loadAllValues(configRoot);
        return resolve(values, configRoot);
    }

    private static Path resolveConfigRoot(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--config-dir=")) {
                    return Path.of(arg.substring("--config-dir=".length()).trim()).toAbsolutePath().normalize();
                }
            }
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path found = findConfigRoot(cwd);
        return found != null ? found : cwd;
    }

    private static Path findConfigRoot(Path start) {
        Path current = start;
        for (int i = 0; i < 6 && current != null; i++) {
            if (Files.isRegularFile(current.resolve("config").resolve("parameters.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static Map<String, String> loadAllValues(Path configRoot) {
        Map<String, String> values = new LinkedHashMap<>();
        loadFromPreferences(values);
        loadFromPropertiesFile(configRoot, values);
        loadMammouthFromProviderProfiles(values);
        return values;
    }

    private static MammouthConfig resolve(Map<String, String> values, Path configRoot) {
        List<String[]> pairs = List.of(
                new String[]{"agent.openai.api_key", "agent.openai.api_url"},
                new String[]{"api.lektorat.api_key", "api.lektorat.base_url"},
                new String[]{"agent.api_key", "agent.api_url"},
                new String[]{"mammouth.profile.api_key", "mammouth.profile.api_url"}
        );
        for (String[] pair : pairs) {
            String key = values.get(pair[0]);
            String url = values.get(pair[1]);
            if (isNonBlank(key) && isMammouthUrl(url)) {
                return new MammouthConfig(key.trim(), url.trim(), configRoot, pair[0], pair[1]);
            }
        }
        String url = firstMammouthUrl(values, "agent.openai.api_url", "api.lektorat.base_url",
                "agent.api_url", "mammouth.profile.api_url");
        String key = firstNonBlank(values, "mammouth.profile.api_key", "agent.openai.api_key",
                "api.lektorat.api_key", "agent.api_key");
        if (url == null) {
            url = MammouthClient.DEFAULT_BASE;
        }
        return new MammouthConfig(
                key == null ? "" : key.trim(),
                url,
                configRoot,
                key == null ? "—" : sourceOf(values, key),
                sourceOf(values, url)
        );
    }

    private static void loadFromPreferences(Map<String, String> values) {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            for (String key : relevantKeys()) {
                String value = prefs.get(key, null);
                if (value != null) {
                    values.putIfAbsent(key, value);
                }
            }
            String profilesJson = prefs.get("agent.openai.provider_profiles", null);
            if (isNonBlank(profilesJson)) {
                putMammouthProfile(values, profilesJson);
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadFromPropertiesFile(Path configRoot, Map<String, String> values) {
        File configFile = configRoot.resolve("config").resolve("parameters.properties").toFile();
        if (!configFile.isFile()) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configFile);
             var reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
            for (String key : relevantKeys()) {
                String value = props.getProperty(key);
                if (value != null && !values.containsKey(key)) {
                    values.put(key, value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadMammouthFromProviderProfiles(Map<String, String> values) {
        for (Path file : providerProfileFiles()) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                putMammouthProfile(values, Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Path> providerProfileFiles() {
        Path home = Path.of(System.getProperty("user.home", "."), ".manuskript");
        return List.of(
                home.resolve("openai-provider-profiles.json"),
                home.resolve("standalone").resolve("openai-provider-profiles.json")
        );
    }

    private static void putMammouthProfile(Map<String, String> values, String json) {
        if (!isNonBlank(json) || values.containsKey("mammouth.profile.api_key")) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonArray()) {
                return;
            }
            JsonArray array = root.getAsJsonArray();
            JsonObject mammouth = null;
            for (JsonElement el : array) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                String name = text(obj, "name");
                String url = text(obj, "apiUrl");
                if ("mammouth".equalsIgnoreCase(name) || isMammouthUrl(url)) {
                    mammouth = obj;
                    if ("mammouth".equalsIgnoreCase(name)) {
                        break;
                    }
                }
            }
            if (mammouth == null) {
                return;
            }
            String key = text(mammouth, "apiKey");
            String url = text(mammouth, "apiUrl");
            if (isNonBlank(key)) {
                values.putIfAbsent("mammouth.profile.api_key", key);
            }
            if (isNonBlank(url)) {
                values.putIfAbsent("mammouth.profile.api_url", url);
            }
        } catch (Exception ignored) {
        }
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static List<String> relevantKeys() {
        return List.of(
                "agent.openai.api_key",
                "agent.openai.api_url",
                "api.lektorat.api_key",
                "api.lektorat.base_url",
                "agent.api_key",
                "agent.api_url"
        );
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    static boolean isMammouthUrl(String url) {
        return url != null && url.toLowerCase().contains("mammouth.ai");
    }

    private static String firstMammouthUrl(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (isMammouthUrl(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (isNonBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String sourceOf(Map<String, String> values, String needle) {
        if (needle == null) {
            return "—";
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null && entry.getValue().trim().equals(needle.trim())) {
                return entry.getKey();
            }
        }
        return "—";
    }
}
