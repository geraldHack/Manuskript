package com.manuskript.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.manuskript.ApplicationPreferences;
import com.manuskript.ResourceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gespeicherte OpenAI-kompatible Provider-Vorlagen (Name, URL, API-Key, Modell)
 * für schnelles Umschalten in den Parametern.
 * <p>
 * Persistenz: Preferences + Datei {@code ~/.manuskript[/standalone]/openai-provider-profiles.json}.
 */
public final class OpenAiProviderProfiles {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiProviderProfiles.class);

    public static final String PREF_PROFILES = "agent.openai.provider_profiles";
    public static final String PREF_ACTIVE = "agent.openai.provider_profile";

    private static final Gson GSON = new Gson();

    public record Profile(String name, String apiUrl, String apiKey, String model) {
        public Profile {
            name = name == null ? "" : name.trim();
            apiUrl = apiUrl == null ? "" : apiUrl.trim();
            apiKey = apiKey == null ? "" : apiKey;
            model = model == null ? "" : model.trim();
        }

        public boolean hasName() {
            return !name.isBlank();
        }
    }

    private OpenAiProviderProfiles() {
    }

    public static List<Profile> defaultProfiles() {
        List<Profile> defaults = new ArrayList<>();
        defaults.add(new Profile("OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini"));
        defaults.add(new Profile("Mammouth", "https://api.mammouth.ai/v1", "", ""));
        defaults.add(new Profile("OpenRouter", "https://openrouter.ai/api/v1", "", ""));
        return defaults;
    }

    public static List<Profile> load() {
        String json = readProfilesFile();
        if (json == null || json.isBlank()) {
            json = ResourceManager.getParameter(PREF_PROFILES, "");
        }
        List<Profile> loaded = parse(json);
        if (loaded.isEmpty()) {
            return defaultProfiles();
        }
        List<Profile> merged = mergeWithDefaults(loaded);
        if (needsHealRewrite(loaded, merged)) {
            // Einmalig korrigierte Cloud-URLs zurückschreiben
            save(merged);
        }
        return merged;
    }

    private static boolean needsHealRewrite(List<Profile> before, List<Profile> after) {
        if (before == null || after == null) {
            return false;
        }
        for (Profile healed : after) {
            Profile old = findByName(before, healed.name());
            if (old != null && !Objects.equals(old.apiUrl(), healed.apiUrl())) {
                return true;
            }
        }
        return false;
    }

    public static void save(List<Profile> profiles) {
        String error = saveOrError(profiles);
        if (error != null) {
            logger.warn("OpenAI-Provider-Profile speichern fehlgeschlagen: {}", error);
        }
    }

    /**
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String saveOrError(List<Profile> profiles) {
        List<Profile> cleaned = new ArrayList<>();
        Map<String, Profile> byName = new LinkedHashMap<>();
        if (profiles != null) {
            for (Profile profile : profiles) {
                if (profile == null || !profile.hasName()) {
                    continue;
                }
                byName.put(normalizeName(profile.name()), profile);
            }
        }
        cleaned.addAll(byName.values());
        String json = GSON.toJson(toJsonArray(cleaned));
        try {
            Preferences preferences = ApplicationPreferences.resourceManagerNode();
            if (json.length() > Preferences.MAX_VALUE_LENGTH) {
                // Zu groß für Preferences – Datei reicht; Prefs nur mit leerem Marker.
                preferences.remove(PREF_PROFILES);
            } else {
                preferences.put(PREF_PROFILES, json);
            }
            preferences.flush();
        } catch (BackingStoreException | IllegalArgumentException | IllegalStateException e) {
            logger.warn("Provider-Profile Preferences-Schreiben fehlgeschlagen: {}", e.getMessage());
        }
        try {
            writeProfilesFile(json);
            return null;
        } catch (IOException e) {
            return "Datei konnte nicht geschrieben werden: " + e.getMessage();
        }
    }

    public static String loadActiveName() {
        return ResourceManager.getParameter(PREF_ACTIVE, "");
    }

    public static void saveActiveName(String name) {
        ResourceManager.saveParameter(PREF_ACTIVE, name == null ? "" : name.trim());
        try {
            ApplicationPreferences.resourceManagerNode().flush();
        } catch (BackingStoreException e) {
            logger.debug("flush active provider profile: {}", e.getMessage());
        }
    }

    public static Profile findByName(List<Profile> profiles, String name) {
        if (profiles == null || name == null || name.isBlank()) {
            return null;
        }
        String needle = normalizeName(name);
        for (Profile profile : profiles) {
            if (profile != null && needle.equals(normalizeName(profile.name()))) {
                return profile;
            }
        }
        return null;
    }

    /** Ersetzt oder fügt ein Profil unter dem Namen hinzu. */
    public static List<Profile> upsert(List<Profile> profiles, Profile profile) {
        List<Profile> next = new ArrayList<>();
        if (profiles != null) {
            next.addAll(profiles);
        }
        if (profile == null || !profile.hasName()) {
            return next;
        }
        String needle = normalizeName(profile.name());
        boolean replaced = false;
        for (int i = 0; i < next.size(); i++) {
            Profile existing = next.get(i);
            if (existing != null && needle.equals(normalizeName(existing.name()))) {
                next.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            next.add(profile);
        }
        return next;
    }

    public static List<Profile> removeByName(List<Profile> profiles, String name) {
        List<Profile> next = new ArrayList<>();
        if (profiles == null || name == null || name.isBlank()) {
            return profiles == null ? next : new ArrayList<>(profiles);
        }
        String needle = normalizeName(name);
        for (Profile profile : profiles) {
            if (profile == null || !needle.equals(normalizeName(profile.name()))) {
                next.add(profile);
            }
        }
        return next;
    }

    static Path profilesFile() {
        Path dir = Path.of(System.getProperty("user.home", "."), ".manuskript");
        if (ApplicationPreferences.isPackagedApplication()) {
            dir = dir.resolve("standalone");
        }
        return dir.resolve("openai-provider-profiles.json");
    }

    static List<Profile> parse(String json) {
        List<Profile> profiles = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return profiles;
        }
        try {
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            if (root == null || !root.isJsonArray()) {
                return profiles;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                Profile profile = new Profile(
                        text(obj, "name"),
                        text(obj, "apiUrl"),
                        text(obj, "apiKey"),
                        text(obj, "model"));
                if (profile.hasName()) {
                    profiles.add(profile);
                }
            }
        } catch (JsonSyntaxException | IllegalStateException ignored) {
            return new ArrayList<>();
        }
        return profiles;
    }

    private static List<Profile> mergeWithDefaults(List<Profile> loaded) {
        Map<String, Profile> byName = new LinkedHashMap<>();
        for (Profile profile : defaultProfiles()) {
            byName.put(normalizeName(profile.name()), profile);
        }
        for (Profile profile : loaded) {
            if (profile == null || !profile.hasName()) {
                continue;
            }
            // Veraltetes lokales TurboFieldfare-Profil nicht mehr anbieten
            if ("turbofieldfare".equals(normalizeName(profile.name()))) {
                continue;
            }
            byName.put(normalizeName(profile.name()), healKnownProfile(profile));
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * Verhindert, dass Cloud-Profile (OpenAI/Mammouth/OpenRouter) eine Loopback-URL
     * behalten, wenn sie durch Auto-Save mit lokalen Feldern überschrieben wurden.
     */
    static Profile healKnownProfile(Profile profile) {
        if (profile == null || !profile.hasName()) {
            return profile;
        }
        Profile defaults = findByName(defaultProfiles(), profile.name());
        if (defaults == null) {
            return profile;
        }
        boolean savedIsLoopback = OpenAIBackend.isLoopbackOpenAiUrl(profile.apiUrl());
        boolean defaultIsLoopback = OpenAIBackend.isLoopbackOpenAiUrl(defaults.apiUrl());
        if (savedIsLoopback && !defaultIsLoopback) {
            String model = profile.model().isBlank() ? defaults.model() : profile.model();
            return new Profile(profile.name(), defaults.apiUrl(), profile.apiKey(), model);
        }
        if ((profile.apiUrl() == null || profile.apiUrl().isBlank()) && !defaults.apiUrl().isBlank()) {
            return new Profile(profile.name(), defaults.apiUrl(), profile.apiKey(),
                    profile.model().isBlank() ? defaults.model() : profile.model());
        }
        return profile;
    }

    private static JsonArray toJsonArray(List<Profile> profiles) {
        JsonArray array = new JsonArray();
        for (Profile profile : profiles) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", profile.name());
            obj.addProperty("apiUrl", profile.apiUrl());
            obj.addProperty("apiKey", profile.apiKey());
            obj.addProperty("model", profile.model());
            array.add(obj);
        }
        return array;
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return Objects.toString(obj.get(key).getAsString(), "");
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String readProfilesFile() {
        Path file = profilesFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Provider-Profile Datei lesen fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    private static void writeProfilesFile(String json) throws IOException {
        Path file = profilesFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, json == null ? "[]" : json, StandardCharsets.UTF_8);
    }
}
