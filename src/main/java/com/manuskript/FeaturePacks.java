package com.manuskript;

import java.util.HashSet;
import java.util.Set;

/**
 * Liest und schreibt Feature-Pakete. Defaults sind an, bestehende Nutzer verlieren nichts.
 */
public final class FeaturePacks {

    private FeaturePacks() {
    }

    public static boolean isEnabled(FeaturePack pack) {
        if (pack == null) {
            return true;
        }
        boolean self = readFlag(pack.key(), true);
        if (pack.requiresAi() && pack != FeaturePack.AI) {
            return self && isEnabled(FeaturePack.AI);
        }
        return self;
    }

    public static boolean isStoredEnabled(FeaturePack pack) {
        return pack != null && readFlag(pack.key(), true);
    }

    public static void setEnabled(FeaturePack pack, boolean enabled) {
        if (pack == null) {
            return;
        }
        ResourceManager.saveParameter(pack.key(), Boolean.toString(enabled));
        syncLegacyAgentFlag();
    }

    public static boolean aiEnabled() {
        return isEnabled(FeaturePack.AI);
    }

    public static boolean agentsEnabled() {
        return isEnabled(FeaturePack.AGENTS);
    }

    public static boolean novelWizardEnabled() {
        return isEnabled(FeaturePack.NOVEL_WIZARD);
    }

    public static boolean onlineLektoratEnabled() {
        return isEnabled(FeaturePack.ONLINE_LEKTORAT);
    }

    public static boolean dictationEnabled() {
        return isEnabled(FeaturePack.DICTATION);
    }

    public static boolean audiobookEnabled() {
        return isEnabled(FeaturePack.AUDIOBOOK);
    }

    public static boolean shouldHideParameterCategory(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        if ("Funktionen".equals(category)) {
            return true;
        }
        for (FeaturePack pack : FeaturePack.values()) {
            if (!isEnabled(pack)) {
                for (String hidden : pack.parameterCategories()) {
                    if (category.equals(hidden)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static Set<String> hiddenParameterCategories() {
        Set<String> hidden = new HashSet<>();
        hidden.add("Funktionen");
        for (FeaturePack pack : FeaturePack.values()) {
            if (!isEnabled(pack)) {
                for (String category : pack.parameterCategories()) {
                    hidden.add(category);
                }
            }
        }
        return hidden;
    }

    private static void syncLegacyAgentFlag() {
        ResourceManager.saveParameter("agent.enabled", Boolean.toString(agentsEnabled()));
    }

    private static boolean readFlag(String key, boolean defaultValue) {
        String raw = ResourceManager.getParameter(key, Boolean.toString(defaultValue));
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
