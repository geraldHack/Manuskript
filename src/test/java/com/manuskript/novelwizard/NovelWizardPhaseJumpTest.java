package com.manuskript.novelwizard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovelWizardPhaseJumpTest {

    @Test
    void lastQuestionFromOtherPhaseIsDetected() {
        NovelWizardSession session = NovelWizardSession.create("NEW");
        session.setCurrentPhase(NovelWizardPhase.WORLD);
        NovelWizardTurn worldTurn = new NovelWizardTurn();
        worldTurn.setQuestion("Welche Magieregeln gelten in dieser Welt?");
        session.addAssistantTurn(NovelWizardPhase.WORLD, "", worldTurn);

        session.setCurrentPhase(NovelWizardPhase.CHARACTERS);
        assertEquals("Welche Magieregeln gelten in dieser Welt?",
                NovelWizardAiService.lastQuestionFromOtherPhases(session, NovelWizardPhase.CHARACTERS));
        assertTrue(NovelWizardAiService.isOffTopicOrDuplicateQuestion(
                "Welche Magieregeln gelten in dieser Welt?", session, NovelWizardPhase.CHARACTERS));
        assertFalse(NovelWizardAiService.isOffTopicOrDuplicateQuestion(
                "Wie heißt die Protagonistin mit vollem Namen?", session, NovelWizardPhase.CHARACTERS));
    }

    @Test
    void normalizeDoesNotRetagOlderPhasesWithCurrentPhase() {
        NovelWizardSession session = NovelWizardSession.create("NEW");
        NovelWizardTurn worldTurn = new NovelWizardTurn();
        worldTurn.setQuestion("Wie sieht die Hauptstadt aus?");
        session.addAssistantTurn(NovelWizardPhase.WORLD, "", worldTurn);
        session.setCurrentPhase(NovelWizardPhase.CHARACTERS);
        session.normalizeChatPhases();

        assertEquals(NovelWizardPhase.WORLD, session.getChatHistory().getFirst().phase);
        assertEquals(NovelWizardPhase.CHARACTERS, session.getCurrentPhase());
    }

    @Test
    void standingInstructionsGoToPromptButStayOutOfBlankWhenEmpty() {
        NovelWizardSession session = NovelWizardSession.create("NEW");
        assertEquals("", NovelWizardAiService.standingInstructionsBlock(session));

        session.setStandingInstructions("Keine Metaphern. Keine Ein-Wort-Sätze.");
        String block = NovelWizardAiService.standingInstructionsBlock(session);
        assertTrue(block.contains("<AUTOR_ANWEISUNGEN>"));
        assertTrue(block.contains("Keine Metaphern."));
        assertTrue(block.contains("Nicht zitieren"));
    }
}
