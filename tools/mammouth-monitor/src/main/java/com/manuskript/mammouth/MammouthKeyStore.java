package com.manuskript.mammouth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Optionaler API-Key nur für den Mammouth-Monitor (falls in Manuskript gerade ein anderer Provider aktiv ist).
 */
public final class MammouthKeyStore {

    private static final String PREF_NODE = "/com/manuskript/mammouth/monitor";
    private static final String PREF_KEY = "api_key";
    private static final String ENV_KEY = "MAMMOUTH_API_KEY";
    private static final String FILE_NAME = "mammouth-monitor.properties";
    private static final String FILE_KEY = "api_key";

    private MammouthKeyStore() {
    }

    public static String loadOverrideKey(Path configRoot) {
        String fromEnv = System.getenv(ENV_KEY);
        if (isNonBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromPrefs = readFromPreferences();
        if (isNonBlank(fromPrefs)) {
            return fromPrefs.trim();
        }
        String fromFile = readFromFile(configRoot);
        return isNonBlank(fromFile) ? fromFile.trim() : "";
    }

    public static void saveOverrideKey(Path configRoot, String key) {
        String value = key == null ? "" : key.trim();
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            if (value.isEmpty()) {
                prefs.remove(PREF_KEY);
            } else {
                prefs.put(PREF_KEY, value);
            }
            prefs.flush();
        } catch (Exception ignored) {
        }
        writeToFile(configRoot, value);
    }

    private static String readFromPreferences() {
        try {
            return Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFromFile(Path configRoot) {
        File file = configFile(configRoot);
        if (!file.isFile()) {
            return null;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file);
             var reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
            return props.getProperty(FILE_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeToFile(Path configRoot, String value) {
        File file = configFile(configRoot);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Properties props = new Properties();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file);
                 var reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (Exception ignored) {
            }
        }
        if (value.isEmpty()) {
            props.remove(FILE_KEY);
        } else {
            props.setProperty(FILE_KEY, value);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            props.store(w, "Mammouth Monitor – lokale Einstellungen");
        } catch (Exception ignored) {
        }
    }

    private static File configFile(Path configRoot) {
        return configRoot.resolve("config").resolve(FILE_NAME).toFile();
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
