package com.manuskript.novelwizard;

import com.manuskript.OllamaService;
import com.manuskript.ResourceManager;
import com.manuskript.agent.AIBackend;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NovelWizardAiService {
    private static final int DEFAULT_MAX_TOKENS = 1800;

    private final AIBackend backend;
    private final NovelWizardPromptRegistry promptRegistry;

    public NovelWizardAiService(Path promptConfig) {
        this.backend = createBackend();
        this.promptRegistry = new NovelWizardPromptRegistry(promptConfig);
    }

    public CompletableFuture<NovelWizardTurn> nextTurn(NovelWizardSession session, String existingContext) {
        return nextTurn(session, existingContext, 0);
    }

    /**
     * Erzeugt aus dem Figuren-Interview strukturierte Character Sheets fuer characters.txt.
     */
    public CompletableFuture<String> generateCharacterSheets(NovelWizardSession session, String existingContext,
                                                                String phaseDialogue) {
        String systemPrompt = """
                Du bist Figurenentwickler. Erstelle aus dem Figuren-Interview strukturierte Character Sheets auf Deutsch.
                Antworte ausschliesslich in diesem Format:
                <CONTENT>
                Markdown-Inhalt
                </CONTENT>
                <SUMMARY>Kurze Einordnung (1–3 Saetze)</SUMMARY>
                
                VERBOTEN: Rueckfragen, Interview-Format, **Frage:**/**Antwort:**-Listen, Stichpunkt-Antworten aus dem Dialog.
                
                Pro wichtiger Figur genau EIN Sheet mit Ueberschrift ## Vorname Nachname (vollstaendiger Name) und diesen Feldern:
                **Rolle:** (Protagonist, Antagonist, …)
                **Alter / Aussehen:**
                **Persoenlichkeit:**
                **Hintergrund:**
                **Ziele:**
                **Schwaechen / innere Konflikte:**
                **Beziehungen:** (mit konkreten Namen anderer Figuren)
                **Character Arc:**
                """;
        StringBuilder user = new StringBuilder();
        user.append("Erstelle jetzt die vollstaendigen Character Sheets aus dem Figuren-Interview.\n\n");
        if (existingContext != null && !existingContext.isBlank()) {
            user.append("<EXISTING_CONTEXT>\n").append(existingContext).append("\n</EXISTING_CONTEXT>\n\n");
        }
        if (session.getProjectSummary() != null && !session.getProjectSummary().isBlank()) {
            user.append("<PROJECT_SUMMARY>\n").append(session.getProjectSummary()).append("\n</PROJECT_SUMMARY>\n\n");
        }
        if (phaseDialogue != null && !phaseDialogue.isBlank()) {
            user.append("<FIGUREN_INTERVIEW>\n").append(phaseDialogue).append("\n</FIGUREN_INTERVIEW>\n\n");
        }
        String corrections = collectAuthorCorrections(session);
        if (!corrections.isBlank()) {
            user.append("<AUTOR_KORREKTUREN>\n").append(corrections).append("\n</AUTOR_KORREKTUREN>\n\n");
        }
        user.append("<COLLECTED>\n");
        for (Map.Entry<String, String> entry : session.getCollected().entrySet()) {
            user.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        user.append("</COLLECTED>\n");
        return backend.chat(systemPrompt, user.toString(), maxTokensForPhase(NovelWizardPhase.SYNOPSIS))
                .thenApply(raw -> {
                    NovelWizardTurn turn = NovelWizardResponseParser.parse(raw, true);
                    String content = turn.getContent();
                    return content == null ? "" : content.trim();
                });
    }

    private CompletableFuture<NovelWizardTurn> nextTurn(NovelWizardSession session, String existingContext,
                                                         int attempt) {
        NovelWizardPhase phase = session.getCurrentPhase();
        NovelWizardPromptRegistry.PromptDef prompt = promptRegistry.get(phase);
        boolean contentPhase = isContentPhase(phase);
        String systemPrompt = buildSystemPrompt(prompt, phase);
        String userPrompt = buildUserPrompt(session, prompt, existingContext, attempt > 0);
        session.addPromptLog(phase, systemPrompt, userPrompt);

        return backend.chat(systemPrompt, userPrompt, maxTokensForPhase(phase))
                .thenCompose(raw -> {
                    NovelWizardTurn turn = finalizeTurn(NovelWizardResponseParser.parse(raw, contentPhase), phase);
                    int maxAttempts = contentPhase ? 3 : 2;
                    if (contentPhase && turn.getContent().isBlank() && attempt + 1 < maxAttempts) {
                        return nextTurn(session, existingContext, attempt + 1);
                    }
                    if (!contentPhase && attempt == 0
                            && isDuplicateQuestion(turn.getQuestion(), collectAskedQuestions(session, phase))) {
                        return nextTurn(session, existingContext, attempt + 1);
                    }
                    if (contentPhase && turn.getContent().isBlank() && attempt + 1 >= maxAttempts) {
                        turn.setHint(appendHint(turn.getHint(),
                                "Nach mehreren Versuchen kein Entwurf. „Entwurf erneut anfordern“ oder "
                                        + "stärkeres KI-Modell (Agent-Einstellungen)."));
                    }
                    if (!contentPhase && attempt > 0
                            && isDuplicateQuestion(turn.getQuestion(), collectAskedQuestions(session, phase))) {
                        turn.setHint(appendHint(turn.getHint(),
                                "Diese Frage wurde schon gestellt. Nutze „Eigene Antwort“ oder „Phase abschließen“, "
                                        + "wenn du genug Material hast."));
                    }
                    return CompletableFuture.completedFuture(turn);
                });
    }

    private static NovelWizardTurn finalizeTurn(NovelWizardTurn turn, NovelWizardPhase phase) {
        if (turn.getQuestion().isBlank() && turn.getContent().isBlank()) {
            if (isContentPhase(phase)) {
                turn.setQuestion("Der Entwurf konnte nicht gelesen werden. Bitte erneut anfordern oder überarbeiten.");
            } else {
                turn.setQuestion("Wie moechtest du in dieser Phase weitermachen?");
            }
        }
        return turn;
    }

    private static boolean isQuestionPhase(NovelWizardPhase phase) {
        return !isContentPhase(phase);
    }

    private static boolean isContentPhase(NovelWizardPhase phase) {
        return phase == NovelWizardPhase.SYNOPSIS
                || phase == NovelWizardPhase.STRUCTURE
                || phase == NovelWizardPhase.CHAPTERS;
    }

    private static AIBackend createBackend() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        AIBackend backend;
        if ("OpenAI".equalsIgnoreCase(backendType)) {
            backend = new OpenAIBackend();
            backend.setCurrentModel(ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini"));
            backend.setTemperature(ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7));
        } else {
            backend = new OllamaBackend(new OllamaService());
            backend.setCurrentModel(ResourceManager.getParameter("agent.ollama.model", "gemma3:4b"));
            backend.setTemperature(ResourceManager.getDoubleParameter("ollama.temperature", 0.5));
        }
        return backend;
    }

    private static int maxTokens() {
        try {
            return Integer.parseInt(ResourceManager.getParameter("novelwizard.max_tokens", String.valueOf(DEFAULT_MAX_TOKENS)));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_TOKENS;
        }
    }

    private static int maxTokensForPhase(NovelWizardPhase phase) {
        if (isContentPhase(phase)) {
            try {
                return Integer.parseInt(ResourceManager.getParameter("novelwizard.content_max_tokens", "4096"));
            } catch (NumberFormatException e) {
                return 4096;
            }
        }
        return maxTokens();
    }

    private String buildSystemPrompt(NovelWizardPromptRegistry.PromptDef prompt, NovelWizardPhase phase) {
        boolean contentPhase = isContentPhase(phase);
        String format = contentPhase
                ? """
                Antworte ausschliesslich in diesem Format:
                <CONTENT>
                Markdown-Inhalt
                </CONTENT>
                <SUMMARY>Kurze Einordnung (1–3 Saetze)</SUMMARY>
                
                VERBOTEN in dieser Phase: <QUESTION>, <OPTIONS>, <HINT> oder Rueckfragen an den Autor.
                """
                : """
                Antworte ausschliesslich in diesem Format:
                <QUESTION>Eine klare Frage</QUESTION>
                <OPTIONS>
                  <OPTION id="1">Option</OPTION>
                  <OPTION id="2">Option</OPTION>
                  <OPTION id="3">Option</OPTION>
                  <OPTION id="4">Option</OPTION>
                </OPTIONS>
                <HINT>Kurzer Hinweis</HINT>
                <SUMMARY>Kurze Einordnung des bisherigen Stands</SUMMARY>
                """;
        StringBuilder sb = new StringBuilder();
        sb.append(prompt.systemPrompt).append("\n\n").append("Arbeite auf Deutsch.\n");
        if (contentPhase) {
            sb.append("""
                    Dies ist eine Schreibphase: Erzeuge einen vollstaendigen Markdown-Entwurf aus dem bisherigen Material.
                    Keine Rueckfragen, keine Einzelfragen zu Plotpunkten – schreibe den Text direkt in <CONTENT>.
                    Autoren-Korrekturen in <AUTOR_KORREKTUREN> sind verbindlich.
                    """);
            if (phase == NovelWizardPhase.SYNOPSIS) {
                sb.append("""
                        
                        Synopsis-Inhalt (ca. 800–1500 Woerter, durchgaengiger Fliesstext oder Abschnitte):
                        Praemisse, Setting, Konflikt, Eskalation, Wendepunkte, Aufloesung/Ende.
                        Hauptfiguren nur kurz (Namen + je ein Satz Rolle) – keine ausfuehrlichen Character Sheets
                        (die stehen in characters.txt). Spoiler sind erwuenscht. Keine Plot-Rueckfragen.
                        """);
            } else if (phase == NovelWizardPhase.STRUCTURE) {
                sb.append("""
                        
                        Akt-Struktur mit Markdown-Ueberschriften (## Akte …, ### Abschnitt …), Wendepunkte und Stakes.
                        """);
            }
        } else {
            sb.append("Die Optionen muessen KI-generiert und auf das konkrete Projekt bezogen sein. ")
                    .append("Keine Option fuer Freitext, eigene Antwort oder Aehnliches (dafuer gibt es ein separates Eingabefeld). ")
                    .append("Wiederhole keine bereits gestellte Frage. Jede neue Frage muss einen noch nicht geklaerten Aspekt ")
                    .append("der aktuellen Phase vertiefen.\n")
                    .append("Autoren-Korrekturen in <AUTOR_KORREKTUREN> sind verbindlich und ueberschreiben fruehere KI-Annahmen "
                    + "in Fragen und Optionen.\n");
        }
        if (phase == NovelWizardPhase.BRAINSTORM) {
            sb.append("""
                    
                    Brainstorm-Phase – Pflicht-Grunddaten ZUERST (explizit benennen, nicht nur in Hinweisen erwaehnen):
                    Genre, Subgenre, Praemisse, Umfang (konkrete Wortzahl), Stil, Ton, Themen.
                    Erst wenn alle geklaert sind: vertiefende Grundgeruest-Fragen. Keine Handlungs-/Quest-Details vorher.
                    """);
        }
        if (phase == NovelWizardPhase.CHARACTERS) {
            sb.append("""
                    
                    Figuren-Phase – Pflicht je wichtiger Figur:
                    - Vollstaendiger Name (Vor- und Nachname)
                    - Rolle (Protagonist, Antagonist, Mentor, …)
                    - Aeussere Beschreibung (Alter, Aussehen, Stil)
                    - Persoenlichkeit und Hintergrund
                    - Beziehungen zu anderen Figuren (mit Namen)
                    Frage gezielt nach dem naechsten fehlenden Punkt; Optionen immer mit konkreten Namen und Beschreibungen.
                    """);
        }
        sb.append("\n").append(format);
        return sb.toString();
    }

    private String buildUserPrompt(NovelWizardSession session, NovelWizardPromptRegistry.PromptDef prompt,
                                   String existingContext, boolean duplicateRetry) {
        NovelWizardPhase phase = session.getCurrentPhase();
        StringBuilder sb = new StringBuilder();
        sb.append("Aktuelle Phase: ").append(phase.getTitle()).append("\n");
        sb.append("Anweisung: ").append(prompt.instruction).append("\n\n");
        if (isContentPhase(phase)) {
            sb.append("""
                    MODUS: Schreibphase (kein Interview).
                    Liefere jetzt einen vollstaendigen Entwurf in <CONTENT>.
                    Nutze EXISTING_CONTEXT und PROJECT_SUMMARY als Quelle.
                    Wenn der Autor im PHASE_DIALOG Überarbeitungswünsche genannt hat, setze diese im Entwurf um.
                    
                    """);
            if (duplicateRetry) {
                sb.append("WICHTIG: Deine letzte Antwort enthielt keinen gueltigen <CONTENT>-Block "
                        + "oder war eine Frage statt eines Entwurfs. Liefere jetzt ausschliesslich <CONTENT> und <SUMMARY>.\n\n");
            }
        } else if (duplicateRetry) {
            sb.append("WICHTIG: Deine letzte Frage wurde bereits in dieser Phase gestellt. "
                    + "Stelle jetzt eine deutlich andere, neue Frage zu einem noch offenen Aspekt.\n\n");
        }
        if (existingContext != null && !existingContext.isBlank()) {
            sb.append("<EXISTING_CONTEXT>\n").append(existingContext).append("\n</EXISTING_CONTEXT>\n\n");
        }
        if (!session.getProjectSummary().isBlank()) {
            sb.append("<PROJECT_SUMMARY>\n").append(session.getProjectSummary()).append("\n</PROJECT_SUMMARY>\n\n");
        }
        List<String> asked = collectAskedQuestions(session, phase);
        if (!asked.isEmpty() && isQuestionPhase(phase)) {
            sb.append("<BEREITS_GESTELLTE_FRAGEN>\n");
            for (String question : asked) {
                sb.append("- ").append(question).append("\n");
            }
            sb.append("</BEREITS_GESTELLTE_FRAGEN>\n\n");
        }
        String phaseDialogue = buildPhaseDialogue(session, phase);
        if (!phaseDialogue.isBlank()) {
            sb.append("<PHASE_DIALOG>\n").append(phaseDialogue).append("</PHASE_DIALOG>\n\n");
        }
        String corrections = collectAuthorCorrections(session);
        if (!corrections.isBlank()) {
            sb.append("<AUTOR_KORREKTUREN>\n").append(corrections).append("</AUTOR_KORREKTUREN>\n\n");
        }
        sb.append("<COLLECTED>\n");
        for (Map.Entry<String, String> entry : session.getCollected().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("</COLLECTED>\n");
        if (phase == NovelWizardPhase.BRAINSTORM) {
            sb.append("\n<FEHLENDE_GRUNDDATEN>\n");
            sb.append(BrainstormGrunddaten.buildMissingAspectsInstruction(session.getChatHistory()));
            sb.append("\n</FEHLENDE_GRUNDDATEN>\n");
        }
        if (phase == NovelWizardPhase.CHARACTERS) {
            sb.append("""
                    
                    Pruefe in EXISTING_CONTEXT und PHASE_DIALOG, welche Figuren schon Namen und Beschreibungen haben.
                    Frage als Naechstes explizit nach dem fehlenden Feld (z.B. Name des Protagonisten, Aussehen der Antagonistin).
                    """);
        }
        return sb.toString();
    }

    static List<String> collectAskedQuestions(NovelWizardSession session, NovelWizardPhase phase) {
        List<String> questions = new ArrayList<>();
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry == null || entry.phase != phase || !"assistant".equals(entry.role)) {
                continue;
            }
            String question = extractQuestion(entry);
            if (question.isBlank()) {
                continue;
            }
            boolean known = false;
            for (String existing : questions) {
                if (questionsEquivalent(existing, question)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                questions.add(question);
            }
        }
        return questions;
    }

    private static String extractQuestion(NovelWizardSession.ChatEntry entry) {
        if (entry.parsed != null && !entry.parsed.getQuestion().isBlank()) {
            return entry.parsed.getQuestion().trim();
        }
        return entry.raw == null ? "" : entry.raw.trim();
    }

    static String buildPhaseDialogue(NovelWizardSession session, NovelWizardPhase phase) {
        StringBuilder sb = new StringBuilder();
        String lastQuestion = "";
        int index = 0;
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry == null || entry.phase != phase) {
                continue;
            }
            if ("assistant".equals(entry.role)) {
                String question = extractQuestion(entry);
                if (!question.isBlank()) {
                    lastQuestion = question;
                }
            } else if ("user".equals(entry.role) && entry.choice != null && !entry.choice.isBlank()) {
                index++;
                if (!lastQuestion.isBlank()) {
                    sb.append(index).append(". Frage: ").append(lastQuestion).append("\n");
                } else {
                    sb.append(index).append(". Frage: (unbekannt)\n");
                }
                sb.append("   Antwort: ").append(entry.choice.trim()).append("\n\n");
                lastQuestion = "";
            }
        }
        return sb.toString().trim();
    }

    static boolean isDuplicateQuestion(String candidate, List<String> asked) {
        if (candidate == null || candidate.isBlank() || asked == null || asked.isEmpty()) {
            return false;
        }
        for (String existing : asked) {
            if (questionsEquivalent(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    static boolean questionsEquivalent(String a, String b) {
        String na = normalizeQuestion(a);
        String nb = normalizeQuestion(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        return na.length() > 20 && nb.length() > 20 && (na.contains(nb) || nb.contains(na));
    }

    static String normalizeQuestion(String question) {
        return question == null ? "" : question.toLowerCase()
                .replaceAll("[?!.,:;\"'„“”]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String collectAuthorCorrections(NovelWizardSession session) {
        StringBuilder sb = new StringBuilder();
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry == null || !"user".equals(entry.role) || entry.choice == null) {
                continue;
            }
            String choice = entry.choice.trim();
            if (choice.startsWith(NovelWizardSession.CORRECTION_PREFIX)) {
                sb.append("- ").append(choice.substring(NovelWizardSession.CORRECTION_PREFIX.length()).trim())
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String appendHint(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + "\n\n" + addition;
    }
}
