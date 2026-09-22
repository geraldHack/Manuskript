package com.manuskript;

import java.util.Locale;
import java.util.Set;

/**
 * Offline-Modus: kein MOTD, kein Online-Katalog/Update-Check, nur wenige Standard-Plugins.
 * KI über externe Anbieter bleibt in den Parametern konfigurierbar.
 */
public final class OfflineMode {

    public static final String PARAM_KEY = "app.offline_mode";

    /** Plugins, die im Offline-Modus weiterhin sichtbar/nutzbar bleiben. */
    public static final Set<String> DEFAULT_PLUGIN_IDS = Set.of(
            "openrouter-monitor",
            "mammouth-monitor");

    private OfflineMode() {
    }

    public static boolean isEnabled() {
        String raw = ResourceManager.getParameter(PARAM_KEY, "false");
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    public static void setEnabled(boolean enabled) {
        ResourceManager.saveParameter(PARAM_KEY, Boolean.toString(enabled));
    }

    public static boolean isDefaultPluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return false;
        }
        return DEFAULT_PLUGIN_IDS.contains(pluginId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean allowsPluginId(String pluginId) {
        if (!isEnabled()) {
            return true;
        }
        return isDefaultPluginId(pluginId);
    }
}
