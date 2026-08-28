package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCatalogItemTest {

    @Test
    void mergePrefersRemoteOrderAndKeepsLocalOnly() {
        PluginCatalog.Entry localOr = new PluginCatalog.Entry(
                new File("missing-openrouter.jar"),
                "openrouter-monitor.jar",
                "openrouter-monitor",
                "OpenRouter-Monitor",
                false);
        PluginCatalog.Entry extra = new PluginCatalog.Entry(
                new File("missing-extra.jar"),
                "extra.jar",
                "extra-tool",
                "Extra",
                true);
        RemotePluginIndex index = new RemotePluginIndex("", List.of(new RemotePluginIndex.Spec(
                "openrouter-monitor",
                "OpenRouter-Monitor",
                "1.0.0",
                "Credits.",
                "openrouter-monitor.jar",
                URI.create("https://spoteroxe.de/downloads/plugins/openrouter-monitor-1.0.0.jar"),
                "a".repeat(64),
                "2.1.70")));

        List<PluginCatalogItem> items = PluginCatalogItem.merge(List.of(localOr, extra), index);
        assertEquals(2, items.size());
        assertEquals("openrouter-monitor", items.get(0).id());
        assertTrue(items.get(0).updateAvailable());
        assertEquals("extra-tool", items.get(1).id());
        assertFalse(items.get(1).canInstall());
        assertTrue(items.get(1).canEnable());
    }

    @Test
    void remoteOnlyCanBeInstalled() {
        RemotePluginIndex index = new RemotePluginIndex("", List.of(new RemotePluginIndex.Spec(
                "mammouth-monitor",
                "Mammouth-Monitor",
                "1.0.0",
                "",
                "mammouth-monitor.jar",
                URI.create("https://spoteroxe.de/downloads/plugins/mammouth-monitor-1.0.0.jar"),
                "b".repeat(64),
                "")));
        List<PluginCatalogItem> items = PluginCatalogItem.merge(List.of(), index);
        assertEquals(1, items.size());
        assertTrue(items.get(0).canInstall());
        assertFalse(items.get(0).canEnable());
        assertEquals("Nicht installiert — kann geladen werden", items.get(0).statusText());
    }
}
