package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotholeAgentFreeformTest {

    @Test
    void freeformMessageOmitsProblemFormatRules() {
        String message = PlotholeAgent.buildUserMessage("Der Held ging.", "=== WORLDBUILDING ===\nMagie", null, true);
        assertTrue(message.contains("=== MANUSKRIPT BEGINN ==="));
        assertTrue(message.contains("Der Held ging."));
        assertTrue(message.contains("Magie"));
        assertFalse(message.contains("KEINE_PROBLEME"));
        assertFalse(message.contains("<PROBLEM>"));
        assertFalse(message.contains("ANALYSE-SCOPE"));
    }

    @Test
    void structuredMessageKeepsProblemFormatRules() {
        String message = PlotholeAgent.buildUserMessage("Der Held ging.", "", null, false);
        assertTrue(message.contains("KEINE_PROBLEME"));
        assertTrue(message.contains("ANALYSE-SCOPE"));
    }
}
