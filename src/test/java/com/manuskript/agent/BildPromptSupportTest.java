package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BildPromptSupportTest {

    @Test
    void effectiveSystemPromptAppendsStopRule() {
        String prompt = BildPromptSupport.effectiveSystemPrompt("Mach ein Bild.");
        assertTrue(prompt.startsWith("Mach ein Bild."));
        assertTrue(prompt.contains("STOPP-REGEL"));
        assertTrue(prompt.contains("Nichts erfinden"));
    }

    @Test
    void effectiveSystemPromptInjectsBindingExtra() {
        String prompt = BildPromptSupport.effectiveSystemPrompt(null, "nah, Gegenlicht");
        assertTrue(prompt.contains("VERBINDLICHER ZUSATZPROMPT"));
        assertTrue(prompt.contains("nah, Gegenlicht"));
        assertTrue(prompt.contains("Vorrang für Blickwinkel"));
        assertTrue(prompt.indexOf("nah, Gegenlicht") < prompt.indexOf("STOPP-REGEL"));
    }

    @Test
    void clampMaxTokensCapsEndlessBudget() {
        assertEquals(768, BildPromptSupport.clampMaxTokens(11520));
        assertEquals(768, BildPromptSupport.clampMaxTokens(0));
        assertEquals(256, BildPromptSupport.clampMaxTokens(100));
        assertEquals(512, BildPromptSupport.clampMaxTokens(512));
    }

    @Test
    void trimContextKeepsHeadAndTail() {
        String ctx = "A".repeat(8_000) + "MITTE" + "Z".repeat(8_000);
        String trimmed = BildPromptSupport.trimContext(ctx);
        assertTrue(trimmed.length() < ctx.length());
        assertTrue(trimmed.startsWith("AAAA"));
        assertTrue(trimmed.endsWith("ZZZZ"));
        assertTrue(trimmed.contains("KONTEXT GEKÜRZT"));
    }

    @Test
    void defaultPromptIsProjectAgnosticAndUsesWorldEditor() {
        String prompt = BildPromptSupport.DEFAULT_SYSTEM;
        assertTrue(prompt.contains("World-Editor"));
        assertTrue(prompt.contains("Keine Eigennamen"));
        assertTrue(prompt.contains("geöffneten Kapitel"));
        assertFalse(prompt.contains("Szene"));
        assertTrue(prompt.contains("Genau ein Prompt"));
        assertFalse(prompt.contains("silver hair"));
        assertFalse(prompt.contains("space opera"));
        assertFalse(prompt.contains("Stilvorgaben"));
        assertFalse(prompt.contains("so viel Markdown"));
    }

    @Test
    void migrateReplacesProjectSpecificPrompt() {
        AgentConfig config = new AgentConfig();
        config.setId(AgentConfigManager.BILD_PROMPT_AGENT_ID);
        config.setName("Bild-Prompt");
        config.setSystemPrompt("""
                Sieh dir das Kapitel an und mache ein stimmungsvolles, passenden Prompt für eine Bild-KI  dazu. benutze so viel Markdown, wie du willst.

                Stilvorgaben

                - **Stil:** silver hair, vent, space opera

                ## Negativ-Prompt

                no holograms""");
        config.setDefaultPrompt("Du bist ein Analysemodul zur Erkennung von Plotlöchern");
        config.setMaxTokens(11520);
        config.setFreeform(false);

        assertTrue(BildPromptSupport.migrateExisting(config));
        assertEquals(BildPromptSupport.DEFAULT_SYSTEM, config.getDefaultPrompt());
        assertEquals(BildPromptSupport.DEFAULT_SYSTEM, config.getSystemPrompt());
        assertEquals(768, config.getMaxTokens());
        assertTrue(config.isFreeform());
        assertFalse(config.getSystemPrompt().contains("so viel Markdown"));
        assertFalse(config.getSystemPrompt().contains("silver hair"));
        assertFalse(config.getSystemPrompt().contains("no holograms"));
    }

    @Test
    void migrateReplacesPlotHolePrompt() {
        AgentConfig config = new AgentConfig();
        config.setSystemPrompt("Du bist ein Analysemodul zur Erkennung von Plotlöchern.\nKEINE_PROBLEME");
        assertTrue(BildPromptSupport.migrateExisting(config));
        assertEquals(BildPromptSupport.DEFAULT_SYSTEM, config.getSystemPrompt());
    }

    @Test
    void combineAuthorInstructionAppendsExtraPrompt() {
        assertEquals(BildPromptSupport.AUTHOR_INSTRUCTION,
                BildPromptSupport.combineAuthorInstruction("  "));
        String combined = BildPromptSupport.combineAuthorInstruction("nah, Gegenlicht");
        assertTrue(combined.contains("VERBINDLICHER ZUSATZPROMPT"));
        assertTrue(combined.contains("nah, Gegenlicht"));
        assertTrue(combined.contains("Setze den VERBINDLICHEN ZUSATZPROMPT"));
        assertFalse(combined.contains("MARKIERUNG"));
    }

    @Test
    void combineAuthorInstructionPutsSelectionAfterExtraPrompt() {
        String combined = BildPromptSupport.combineAuthorInstruction(
                "nah, Gegenlicht", "Die Figur kauert am Gitter.");
        assertTrue(combined.contains("VERBINDLICHER ZUSATZPROMPT"));
        assertTrue(combined.contains("nah, Gegenlicht"));
        assertTrue(combined.contains("=== MARKIERUNG BEGINN ==="));
        assertTrue(combined.contains("Die Figur kauert am Gitter."));
        assertTrue(combined.indexOf("nah, Gegenlicht") < combined.indexOf("MARKIERUNG"));
    }

    @Test
    void isBildPromptMatchesIdAndName() {
        AgentConfig byId = new AgentConfig();
        byId.setId(AgentConfigManager.BILD_PROMPT_AGENT_ID);
        assertTrue(BildPromptSupport.isBildPrompt(byId));

        AgentConfig byName = new AgentConfig();
        byName.setName("Bild-Prompt");
        assertTrue(BildPromptSupport.isBildPrompt(byName));

        AgentConfig other = new AgentConfig();
        other.setName("Plothole-Agent");
        assertFalse(BildPromptSupport.isBildPrompt(other));
        assertFalse(BildPromptSupport.isBildPrompt(null));
    }
}
