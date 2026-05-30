package com.manuskript.agent;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generiert literarische Szenen basierend auf Outline, Stil und Worldbuilding.
 */
public class SceneWritingAgent {

    private static final Logger logger = LoggerFactory.getLogger(SceneWritingAgent.class);

    private static final Pattern SCENE_PATTERN = Pattern.compile(
            "<SCENE>\\s*([\\s\\S]*?)\\s*</SCENE>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_PATTERN = Pattern.compile(
            "<META>\\s*([\\s\\S]*?)\\s*</META>", Pattern.CASE_INSENSITIVE);

    public static final String DEFAULT_SYSTEM_PROMPT =
        "Du bist ein professioneller Co-Autor für literarische Prosa in deutscher Sprache.\n\n" +
        "DEINE AUFGABE:\n" +
        "Schreibe eine einzelne Szene als Fließprosa, die sich nahtlos in das bestehende Manuskript einfügt.\n\n" +
        "STIL-TREUE (höchste Priorität):\n" +
        "- Halte dich exakt an Satzlänge, Wortwahl, Perspektive, Erzähltempo und Tonfall aus dem Abschnitt === STIL ===.\n" +
        "- Dialoge müssen dem etablierten Dialogstil entsprechen (Anführungszeichen, Tags, Rhythmus).\n" +
        "- Keine generische KI-Prosa — schreibe wie der Autor, nicht wie ein Assistent.\n\n" +
        "WORLDBUILDING-TREUE:\n" +
        "- Respektiere alle Regeln, Orte, Kulturen, Magie, Technologie und Gesellschaft aus === WORLDBUILDING ===.\n" +
        "- Erfinde keine Widersprüche zu etablierten Weltregeln.\n" +
        "- Nutze konkrete Details aus dem Worldbuilding für Atmosphäre und Handlung.\n\n" +
        "CHARAKTER-KONSISTENZ:\n" +
        "- Jede Figur spricht und handelt gemäß === CHARAKTERE === (Motivation, Stimme, Beziehungen).\n" +
        "- Berücksichtige den emotionalen Zustand und die Ereignisse aus den letzten Kapiteln.\n\n" +
        "KONTINUITÄT:\n" +
        "- Die Szene muss logisch an den bisherigen Text anschließen.\n" +
        "- Wenn === ZIEL-SZENE === gesetzt ist: das ist die verbindliche Handlungsvorgabe — wortgetreu umsetzen.\n" +
        "- Beachte die Szenen-Outline und die Benutzer-Anweisung (Länge, Fokus, Nummer).\n\n" +
        "QUALITÄT:\n" +
        "- Zeige statt erzähle: Emotionen durch Körpersprache, Sinneswahrnehmung, Dialog.\n" +
        "- Konkrete Bilder statt Abstraktionen.\n" +
        "- Jeder Absatz treibt die Szene voran.\n\n" +
        "FORMATIERUNG IM SZENENTEXT:\n" +
        "- Markdown-Inline-Formatierung ist erlaubt und erwünscht, wenn sie zum Manuskript passt (z. B. *kursiv*, **fett**).\n" +
        "- Orientiere dich am Formatierungsstil des bestehenden Kapiteltexts und === STIL ===.\n\n" +
        "VERBOTEN im Szenentext:\n" +
        "- Überschriften, Aufzählungen, nummerierte Listen, Meta-Kommentare, Autorenhinweise\n" +
        "- Einleitungen wie \"Hier ist die Szene:\" oder Zusammenfassungen danach\n" +
        "- Erklärungen deiner Entscheidungen innerhalb der Prosa\n\n" +
        "AUSGABEFORMAT (strikt einhalten):\n\n" +
        "<META>\n" +
        "Optional: 1–3 kurze Sätze zu deinen kreativen Entscheidungen (nur für den Autor, nicht zum Einfügen).\n" +
        "</META>\n" +
        "<SCENE>\n" +
        "Der vollständige Szenentext als Fließprosa — nur das, was ins Manuskript gehört.\n" +
        "</SCENE>\n\n" +
        "Das Feld <SCENE> ist Pflicht. <META> ist optional.\n" +
        "Gib ausschließlich diese Tags aus — keinen Text davor oder danach.";

    public static class GenerationResult {
        private final String sceneText;
        private final String metaText;
        private final boolean parsedFromTags;
        private final String rawResponse;

        public GenerationResult(String sceneText, String metaText, boolean parsedFromTags, String rawResponse) {
            this.sceneText = sceneText;
            this.metaText = metaText;
            this.parsedFromTags = parsedFromTags;
            this.rawResponse = rawResponse;
        }

        public String getSceneText() { return sceneText; }
        public String getMetaText() { return metaText; }
        public boolean isParsedFromTags() { return parsedFromTags; }
        public String getRawResponse() { return rawResponse; }
    }

    private final AIBackend backend;
    private String customSystemPrompt;

    public SceneWritingAgent(AIBackend backend) {
        this.backend = backend;
    }

    public void setSystemPrompt(String prompt) {
        this.customSystemPrompt = prompt;
    }

    public String getEffectiveSystemPrompt() {
        if (customSystemPrompt != null && !customSystemPrompt.isBlank()) {
            return customSystemPrompt;
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    public CompletableFuture<GenerationResult> generate(SceneContextLoader.Context context, int maxTokens) {
        String userMessage = SceneContextLoader.buildUserMessage(context);
        if (userMessage.isBlank()) {
            return CompletableFuture.completedFuture(
                new GenerationResult("", "", false, ""));
        }
        String systemPrompt = getEffectiveSystemPrompt();
        logger.info("Szene generieren: User-Message {} Zeichen", userMessage.length());
        return backend.chat(systemPrompt, userMessage, maxTokens)
            .thenApply(this::parseResponse);
    }

    GenerationResult parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new GenerationResult("", "", false, raw);
        }
        String trimmed = raw.trim();
        Matcher sceneMatcher = SCENE_PATTERN.matcher(trimmed);
        if (sceneMatcher.find()) {
            String scene = sceneMatcher.group(1).trim();
            String meta = "";
            Matcher metaMatcher = META_PATTERN.matcher(trimmed);
            if (metaMatcher.find()) {
                meta = metaMatcher.group(1).trim();
            }
            return new GenerationResult(scene, meta, true, raw);
        }
        logger.warn("Keine <SCENE>-Tags in Antwort — verwende Rohtext");
        return new GenerationResult(trimmed, "", false, raw);
    }
}
