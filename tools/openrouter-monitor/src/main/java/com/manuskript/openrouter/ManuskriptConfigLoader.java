package com.manuskript.openrouter;

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
 * Liest OpenRouter-relevante Parameter aus der Manuskript-Konfiguration.
 */
public final class ManuskriptConfigLoader {

    /** Entspricht Preferences.userNodeForPackage(ResourceManager.class) → Paket com.manuskript */
    private static final String PREF_NODE = "/com/manuskript";

    private ManuskriptConfigLoader() {
    }

    public record OpenRouterConfig(String apiKey, String apiUrl, Path configRoot, String keySource, String urlSource) {
        public boolean isOpenRouter() {
            return apiUrl != null && apiUrl.toLowerCase().contains("openrouter.ai");
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean isUsable() {
            return hasApiKey() && isOpenRouter();
        }

        public String normalizedBaseUrl() {
            String url = apiUrl == null || apiUrl.isBlank() ? "https://openrouter.ai/api/v1" : apiUrl.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }

        public String describeProblem() {
            if (!hasApiKey() && !isOpenRouter()) {
                return "Weder OpenRouter-API-Key noch OpenRouter-URL gefunden.";
            }
            if (!hasApiKey()) {
                return "OpenRouter-URL erkannt (" + urlSource + "), aber kein API-Key gefunden.";
            }
            if (!isOpenRouter()) {
                return "API-Key gefunden (" + keySource + "), aber URL ist nicht OpenRouter: " + apiUrl;
            }
            return "";
        }
    }

    public static OpenRouterConfig load(String[] args) {
        Path configRoot = resolveConfigRoot(args);
        Map<String, String> values = loadAllValues(configRoot);
        return resolveOpenRouterConfig(values, configRoot);
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
        return values;
    }

    private static OpenRouterConfig resolveOpenRouterConfig(Map<String, String> values, Path configRoot) {
        List<String[]> pairs = List.of(
                new String[]{"agent.openai.api_key", "agent.openai.api_url"},
                new String[]{"api.lektorat.api_key", "api.lektorat.base_url"},
                new String[]{"agent.api_key", "agent.api_url"}
        );

        for (String[] pair : pairs) {
            String key = values.get(pair[0]);
            String url = values.get(pair[1]);
            if (isNonBlank(key) && isOpenRouterUrl(url)) {
                return new OpenRouterConfig(key.trim(), url.trim(), configRoot, pair[0], pair[1]);
            }
        }

        String bestKey = null;
        String bestKeySource = null;
        String bestUrl = null;
        String bestUrlSource = null;

        for (String[] pair : pairs) {
            String key = values.get(pair[0]);
            String url = values.get(pair[1]);
            if (bestKey == null && isNonBlank(key)) {
                bestKey = key.trim();
                bestKeySource = pair[0];
            }
            if (bestUrl == null && isOpenRouterUrl(url)) {
                bestUrl = url.trim();
                bestUrlSource = pair[1];
            }
        }

        if (bestKey != null && bestUrl != null) {
            return new OpenRouterConfig(bestKey, bestUrl, configRoot, bestKeySource, bestUrlSource);
        }

        String fallbackUrl = firstNonBlank(values,
                "agent.openai.api_url", "api.lektorat.base_url", "agent.api_url");
        if (fallbackUrl == null) {
            fallbackUrl = "https://api.openai.com/v1";
        }
        String fallbackKey = firstNonBlank(values,
                "agent.openai.api_key", "api.lektorat.api_key", "agent.api_key");
        String keySource = sourceOf(values, fallbackKey,
                "agent.openai.api_key", "api.lektorat.api_key", "agent.api_key");
        String urlSource = sourceOf(values, fallbackUrl,
                "agent.openai.api_url", "api.lektorat.base_url", "agent.api_url");

        return new OpenRouterConfig(
                fallbackKey == null ? "" : fallbackKey.trim(),
                fallbackUrl.trim(),
                configRoot,
                keySource == null ? "—" : keySource,
                urlSource == null ? "—" : urlSource
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

    private static List<String> relevantKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("agent.openai.api_key");
        keys.add("agent.openai.api_url");
        keys.add("api.lektorat.api_key");
        keys.add("api.lektorat.base_url");
        keys.add("agent.api_key");
        keys.add("agent.api_url");
        return keys;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isOpenRouterUrl(String url) {
        return url != null && url.toLowerCase().contains("openrouter.ai");
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

    private static String sourceOf(Map<String, String> values, String needle, String... keys) {
        if (needle == null) return null;
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && value.trim().equals(needle.trim())) {
                return key;
            }
        }
        return null;
    }
}
