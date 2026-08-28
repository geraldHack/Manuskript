package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePluginIndexTest {

    @Test
    void parsesOfficialEntriesAndSkipsIllegalUrls() {
        String json = """
                {
                  "updated": "2026-08-28T08:00:00Z",
                  "plugins": [
                    {
                      "id": "openrouter-monitor",
                      "label": "OpenRouter-Monitor",
                      "version": "1.0.0",
                      "description": "Credits und API-Logs.",
                      "fileName": "openrouter-monitor.jar",
                      "jar": "https://spoteroxe.de/downloads/plugins/openrouter-monitor-1.0.0.jar",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "requires": "2.1.0"
                    },
                    {
                      "id": "evil",
                      "fileName": "evil.jar",
                      "jar": "https://evil.example/downloads/evil.jar",
                      "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    }
                  ]
                }
                """;
        RemotePluginIndex index = RemotePluginIndex.parse(json);
        assertEquals("2026-08-28T08:00:00Z", index.updated());
        assertEquals(1, index.plugins().size());
        RemotePluginIndex.Spec spec = index.plugins().get(0);
        assertEquals("openrouter-monitor", spec.id());
        assertEquals("OpenRouter-Monitor", spec.label());
        assertEquals("openrouter-monitor.jar", spec.fileName());
        assertEquals("2.1.0", spec.requires());
    }

    @Test
    void rejectsEmptyJson() {
        assertThrows(IllegalArgumentException.class, () -> RemotePluginIndex.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RemotePluginIndex.parse("[]"));
    }

    @Test
    void versionRequirement() {
        assertTrue(PluginVersions.meetsRequirement("2.1.71", "2.1.70"));
        assertTrue(PluginVersions.meetsRequirement("2.1.70", "2.1.70"));
        assertFalse(PluginVersions.meetsRequirement("2.1.69", "2.1.70"));
        assertTrue(PluginVersions.meetsRequirement("2.1.71", ""));
        assertEquals(-1, PluginVersions.compare("1.0.0", "1.0.1"));
    }

    @Test
    void sha256HexIsStable() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PluginCatalogClient.sha256Hex(new byte[0]));
    }
}
