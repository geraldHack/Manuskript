package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginNotesTest {

    @Test
    void parsesLabelVersionAndBody() {
        PluginNotes notes = PluginNotes.parse("""
                Backup
                1.0.1
                requires: 2.1.72

                Mehrere Ziele und SSH.
                Zweite Zeile.
                """);
        assertEquals("Backup", notes.label());
        assertEquals("1.0.1", notes.version());
        assertEquals("2.1.72", notes.requires());
        assertTrue(notes.description().contains("Mehrere Ziele"));
        assertTrue(notes.description().contains("Zweite Zeile."));
    }

    @Test
    void treatsVersionlessNotesAsDescription() {
        PluginNotes notes = PluginNotes.parse("Nur Text ohne Version");
        assertEquals("Nur Text ohne Version", notes.label());
        assertEquals("", notes.version());
        assertEquals("", notes.description());
    }

    @Test
    void looksLikeVersion() {
        assertTrue(PluginNotes.looksLikeVersion("1.0.1"));
        assertTrue(PluginNotes.looksLikeVersion("2"));
    }
}
