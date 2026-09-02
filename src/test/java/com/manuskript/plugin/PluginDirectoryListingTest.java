package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDirectoryListingTest {

    @Test
    void readsJarAndTxtHrefs() {
        String html = """
                <html><body>
                <a href="../">Parent</a>
                <a href="projekt-backup-1.0.1.jar">jar</a>
                <a href="projekt-backup-1.0.1.txt">txt</a>
                <a href="?C=M;O=A">sort</a>
                </body></html>
                """;
        List<String> names = PluginDirectoryListing.fileNames(html);
        assertEquals(2, names.size());
        assertTrue(names.contains("projekt-backup-1.0.1.jar"));
        assertTrue(names.contains("projekt-backup-1.0.1.txt"));
    }
}
