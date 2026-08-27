package com.manuskript;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturePacksTest {

    private final Map<String, String> previous = new HashMap<>();

    @BeforeEach
    void rememberFlags() {
        for (FeaturePack pack : FeaturePack.values()) {
            previous.put(pack.key(), ResourceManager.getParameter(pack.key(), "true"));
        }
        previous.put("agent.enabled", ResourceManager.getParameter("agent.enabled", "true"));
    }

    @AfterEach
    void restoreFlags() {
        previous.forEach(ResourceManager::saveParameter);
    }

    @Test
    void aiMasterDisablesAgentsWithoutClearingStoredValue() {
        FeaturePacks.setEnabled(FeaturePack.AGENTS, true);
        FeaturePacks.setEnabled(FeaturePack.DICTATION, true);
        FeaturePacks.setEnabled(FeaturePack.AI, false);
        assertTrue(FeaturePacks.isStoredEnabled(FeaturePack.AGENTS));
        assertFalse(FeaturePacks.agentsEnabled());
        assertFalse(FeaturePacks.novelWizardEnabled());
        assertFalse(FeaturePacks.onlineLektoratEnabled());
        assertTrue(FeaturePacks.dictationEnabled());
        assertTrue(FeaturePacks.shouldHideParameterCategory("Agenten"));
        assertTrue(FeaturePacks.shouldHideParameterCategory("Online-Lektorat"));
    }

    @Test
    void functionsCategoryIsAlwaysHiddenInParameterAdmin() {
        assertTrue(FeaturePacks.shouldHideParameterCategory("Funktionen"));
    }

    @Test
    void dictationAndAudiobookStayIndependentOfAi() {
        FeaturePacks.setEnabled(FeaturePack.DICTATION, true);
        FeaturePacks.setEnabled(FeaturePack.AUDIOBOOK, true);
        FeaturePacks.setEnabled(FeaturePack.AI, false);
        assertTrue(FeaturePacks.dictationEnabled());
        assertTrue(FeaturePacks.audiobookEnabled());
    }
}
