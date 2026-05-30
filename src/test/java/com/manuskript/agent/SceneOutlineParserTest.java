package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SceneOutlineParserTest {

    @Test
    void parseSceneNumberFromInstruction() {
        assertEquals(7, SceneOutlineParser.parseSceneNumberFromInstruction("Schreibe Szene 7"));
        assertEquals(7, SceneOutlineParser.parseSceneNumberFromInstruction("szene 7 bitte"));
        assertEquals(3, SceneOutlineParser.parseSceneNumberFromInstruction("Scene Nr. 3"));
        assertNull(SceneOutlineParser.parseSceneNumberFromInstruction("Schreibe den Anfang"));
    }

    @Test
    void extractScene() {
        String outline = """
            1. Er öffnet die Tür.
            2. Sie wartet im Flur.
            7. Er konfrontiert den Verräter auf dem Dachboden.
            8. Flucht über die Dächer.
            """;
        assertEquals("7. Er konfrontiert den Verräter auf dem Dachboden.",
            SceneOutlineParser.extractScene(outline, 7));
        assertEquals("", SceneOutlineParser.extractScene(outline, 99));
    }
}
