package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePluginIndexTest {

    @Test
    void listingPicksHighestVersionAndNotes() {
        URI dir = URI.create("https://spoteroxe.de/downloads/plugins/");
        PluginNotes notes = PluginNotes.parse("Backup\n1.0.1\n\nSSH und mehrere Ziele.\n");
        RemotePluginIndex index = RemotePluginIndex.fromListing(dir, List.of(
                "projekt-backup-1.0.0.jar",
                "projekt-backup-1.0.1.jar",
                "projekt-backup-1.0.1.txt",
                "../"), Map.of("projekt-backup-1.0.1.jar", notes));
        assertEquals(1, index.plugins().size());
        RemotePluginIndex.Spec spec = index.plugins().get(0);
        assertEquals("projekt-backup", spec.id());
        assertEquals("Backup", spec.label());
        assertEquals("1.0.1", spec.version());
        assertEquals("projekt-backup.jar", spec.fileName());
        assertEquals("SSH und mehrere Ziele.", spec.description());
        assertEquals(URI.create("https://spoteroxe.de/downloads/plugins/projekt-backup-1.0.1.jar"), spec.jar());
    }

    @Test
    void ignoresForeignHostsViaResolve() {
        URI dir = URI.create("https://spoteroxe.de/downloads/plugins/");
        RemotePluginIndex index = RemotePluginIndex.fromListing(dir, List.of("ok.jar"), Map.of());
        assertEquals(1, index.plugins().size());
        assertEquals("ok", index.plugins().get(0).id());
    }

    @Test
    void versionRequirement() {
        assertTrue(PluginVersions.meetsRequirement("2.1.71", "2.1.70"));
        assertTrue(PluginVersions.meetsRequirement("2.1.70", "2.1.70"));
        assertFalse(PluginVersions.meetsRequirement("2.1.69", "2.1.70"));
        assertTrue(PluginVersions.meetsRequirement("2.1.71", ""));
        assertEquals(-1, PluginVersions.compare("1.0.0", "1.0.1"));
        assertEquals("1.0.1", PluginVersions.nextPatch("1.0.0"));
        assertEquals("1.1.0", PluginVersions.nextMinor("1.0.5"));
        assertEquals("2.0.0", PluginVersions.nextMajor("1.4.2"));
        assertTrue(PluginVersions.successorVersions("1.0.0").contains("1.0.1"));
        assertTrue(PluginVersions.successorVersions("1.0.0").contains("1.1.0"));
        assertTrue(PluginVersions.successorVersions("1.0.0").contains("2.0.0"));
        assertEquals("1.0.1", PluginCatalogClient.versionFromNotesFileName("schreib-statistik-1.0.1.txt"));
        assertTrue(PluginCatalogClient.noteCandidates("schreib-statistik").contains("schreib-statistik.txt"));
    }

    @Test
    void sha256HexIsStable() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PluginCatalogClient.sha256Hex(new byte[0]));
    }
}
