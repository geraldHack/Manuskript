package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneContextLoaderRevisionTest {

    @Test
    void buildRevisionUserMessage_includesDraftFeedbackAndTask() {
        SceneContextLoader.Context ctx = new SceneContextLoader.Context();
        ctx.instruction = "Schreibe Szene 2";

        String message = SceneContextLoader.buildRevisionUserMessage(
                ctx, "Alter Entwurf.", "Dialog klingt steif.");

        assertTrue(message.contains("=== ANWEISUNG ==="));
        assertTrue(message.contains("Schreibe Szene 2"));
        assertTrue(message.contains("=== BISHERIGER ENTWURF"));
        assertTrue(message.contains("Alter Entwurf."));
        assertTrue(message.contains("=== AUTOREN-FEEDBACK ==="));
        assertTrue(message.contains("Dialog klingt steif."));
        assertTrue(message.contains("<SCENE>"));
    }
}
