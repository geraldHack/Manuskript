package com.manuskript.novelwizard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainstormGrunddatenTest {

    @Test
    void buildKurzprofilExtractsGenreAndUmfangFromTypicalAnswers() {
        List<NovelWizardSession.ChatEntry> history = new ArrayList<>();

        NovelWizardSession.ChatEntry q1 = NovelWizardSession.ChatEntry.assistant(
                NovelWizardPhase.BRAINSTORM, "", turn("Welche zentrale Geschichte möchtest du erzählen?"));
        NovelWizardSession.ChatEntry a1 = NovelWizardSession.ChatEntry.user(
                NovelWizardPhase.BRAINSTORM, "Junge Frau in der Traumwelt, Coming of Age", false);
        NovelWizardSession.ChatEntry q2 = NovelWizardSession.ChatEntry.assistant(
                NovelWizardPhase.BRAINSTORM, "", turn("Welches Genre soll dein Roman haben?"));
        NovelWizardSession.ChatEntry a2 = NovelWizardSession.ChatEntry.user(
                NovelWizardPhase.BRAINSTORM, "Abenteuer-Fantasy mit Romantik", false);
        NovelWizardSession.ChatEntry q3 = NovelWizardSession.ChatEntry.assistant(
                NovelWizardPhase.BRAINSTORM, "", turn("Wie viele Wörter soll der Roman ungefähr haben?"));
        NovelWizardSession.ChatEntry a3 = NovelWizardSession.ChatEntry.user(
                NovelWizardPhase.BRAINSTORM, "Klassischer Drei-Akt-Aufbau: ~120.000 Wörter", false);

        history.add(q1);
        history.add(a1);
        history.add(q2);
        history.add(a2);
        history.add(q3);
        history.add(a3);

        String kurz = BrainstormGrunddaten.buildKurzprofil(history);

        assertTrue(kurz.contains("**Genre:**"), kurz);
        assertTrue(kurz.contains("Fantasy"), kurz);
        assertTrue(kurz.contains("**Umfang:**"), kurz);
        assertTrue(kurz.contains("120.000"), kurz);
        assertTrue(kurz.contains("**Prämisse:**"), kurz);
    }

    private static NovelWizardTurn turn(String question) {
        NovelWizardTurn t = new NovelWizardTurn();
        t.setQuestion(question);
        return t;
    }
}
