package com.manuskript.dictation;

import com.manuskript.QuotationMarkSupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System- und User-Prompts für den Diktat-Interpreter (LLM).
 */
public final class DictationPromptBuilder {

    private static final int MAX_CONTEXT_CHARS = 1500;
    private static final int MAX_INSTRUCTION_CONTEXT_CHARS = 3500;

    /** Anweisung:, Befehl:, Kommando: (STT setzt Doppelpunkt oft nicht). */
    private static final Pattern INSTRUCTION_PREFIX = Pattern.compile(
            "(?is)(?:^|\\b)(anweisung|befehl|kommando)\\s*:?\\s+(.+)$");

    private DictationPromptBuilder() {
    }

    /**
     * Erkennt gesprochene Kommandos am Anfang des Transkripts.
     */
    public static TranscriptAnalysis analyzeTranscript(String rawTranscript) {
        if (rawTranscript == null || rawTranscript.isBlank()) {
            return new TranscriptAnalysis(DictationMode.TRANSCRIPTION, null, "");
        }
        String trimmed = rawTranscript.trim();
        Matcher matcher = INSTRUCTION_PREFIX.matcher(trimmed);
        if (matcher.find()) {
            String instruction = matcher.group(2).trim();
            if (!instruction.isBlank()) {
                return new TranscriptAnalysis(DictationMode.INSTRUCTION, instruction, trimmed);
            }
        }
        return new TranscriptAnalysis(DictationMode.TRANSCRIPTION, null, trimmed);
    }

    public static String buildSystemPrompt() {
        return buildSystemPrompt(0);
    }

    public static String buildSystemPrompt(int quoteStyleIndex) {
        String quoteStyleLabel = QuotationMarkSupport.styleLabel(quoteStyleIndex);
        return """
                Du bist ein Diktat-Interpreter für deutschsprachige Belletristik.
                Du erhältst ein Rohtranskript aus Spracherkennung plus optionalen Editor-Kontext.

                Aufgabe:
                1. Wandle gesprochenen Manuskripttext in sauberen Fließtext um.
                2. Wende gesprochene Korrekturen an (z. B. „nein, nicht X, schreibe Y“, „streiche das“, „ersetze … durch …").
                3. Ignoriere Meta-Anweisungen und Diktierbefehle im Ausgabetext.
                4. Wende Format-Befehle als Manuskript-Markdown bzw. Typografie an:
                   - kursiv / italic → *Wort* oder *Phrase*
                   - fett / bold → **Wort**
                   - neuer Absatz / Absatz → Leerzeile (\\n\\n)
                   - Zeilenumbruch → <br>\\n
                   - Gesprochene Anführungszeichen-Befehle in echte Zeichen umsetzen:
                     · „Anführungszeichen … Anführungszeichen“ → Text dazwischen in Anführungszeichen
                     · „Wort bitte in Anführungszeichen setzen“ / „… in einfache Anführungszeichen setzen“
                       → das genannte Wort in (einfache) Anführungszeichen
                   - „Gedankenstrich“ oder „--“ (nicht „---“) → Gedankenstrich (–)
                   - Dialoge automatisch setzen, wenn klar gesprochen wird, z. B.
                     „Das ist auch gar nicht nötig sagte er“
                     → „Das ist auch gar nicht nötig“, sagte er
                     (Rede in Anführungszeichen, Inquit danach; Stil siehe unten).
                   - Das Wort „Anführungszeichen“ und Formulierungen wie „bitte in … setzen“
                     dürfen NICHT wörtlich im Ausgabetext stehen bleiben.
                5. Entferne Füllwörter wie „äh“, „ähm“, „also“ wenn sie keinen Sinn tragen.
                6. Korrigiere offensichtliche Diktierfehler, wenn der Autor sie nicht selbst korrigiert hat.
                7. Behalte Stil und Satzstellung des Autors bei; erfinde keinen neuen Inhalt.
                8. Figurennamen, Orte und Begriffe aus dem Projekt-Glossar exakt schreiben.
                9. Rohtranskript mit Glossar abgleichen: phonetische Fehler und zusammengezogene Wörter korrigieren.
                10. Editor-Kontext NUR zur Disambiguierung — NIEMALS wiederholen oder aus dem Kontext neu schreiben.
                11. Ausgabe = ausschließlich der aus dem Rohtranskript abgeleitete NEUE Text (wird an der Cursorposition angehängt).

                Ausgabeformat:
                - Nur der fertige NEUE Text, ohne Erklärung, ohne Anführungszeichen um den gesamten Text.
                - Kein Markdown-Codeblock, kein JSON.
                - Keine Wiederholung von Sätzen aus dem Editor-Kontext.
                - Anführungszeichen-Stil des Autors: %s — diesen Stil für Dialog und Hervorhebungen verwenden.
                """.formatted(quoteStyleLabel);
    }

    public static String buildInstructionSystemPrompt() {
        return buildInstructionSystemPrompt(0);
    }

    public static String buildInstructionSystemPrompt(int quoteStyleIndex) {
        String quoteStyleLabel = QuotationMarkSupport.styleLabel(quoteStyleIndex);
        return """
                Du bist ein Schreibassistent für deutschsprachige Belletristik.
                Der Autor diktiert eine ANWEISUNG — keinen fertigen Manuskripttext.

                Aufgabe:
                1. Setze die Anweisung in passenden Fließtext um (z. B. „Schreibe, dass …“ → der gewünschte Absatz).
                2. Nutze den Manuskript-Kontext für Stil, Erzählperspektive, Zeitform, Namen und Situation.
                3. Schreibe nur den neuen Text, der an der Cursorposition eingefügt werden soll.
                4. Keine Meta-Kommentare, keine Wiederholung der Anweisung, keine Erklärungen.
                5. Manuskript-Markdown wenn sinnvoll: *kursiv*, **fett**, Absätze mit Leerzeile.
                6. Halte Ton, Register und Satzlänge des bestehenden Manuskripts ein.
                7. Wenn die Anweisung unklar ist, wähle die naheliegendste literarische Umsetzung im Kontext.
                8. Nutze Figurennamen und Begriffe aus dem Projekt-Glossar exakt.

                Ausgabeformat:
                - Nur der fertige Manuskripttext.
                - Kein Markdown-Codeblock, kein JSON.
                - Anführungszeichen-Stil des Autors: %s.
                """.formatted(quoteStyleLabel);
    }

    public static String buildUserMessage(String rawTranscript, String editorContext) {
        return buildUserMessage(rawTranscript, editorContext, DictationVocabulary.empty());
    }

    public static String buildUserMessage(String rawTranscript, String editorContext,
                                          DictationVocabulary vocabulary) {
        StringBuilder sb = new StringBuilder();
        appendVocabulary(sb, vocabulary);
        sb.append("Rohtranskript:\n");
        sb.append(rawTranscript != null ? rawTranscript.trim() : "");
        sb.append("\n\n");
        String ctx = trimContext(editorContext, MAX_CONTEXT_CHARS);
        if (!ctx.isEmpty()) {
            sb.append("Editor-Kontext (Text vor dem Cursor, zur Disambiguierung von „das“, „dieses Wort“ usw.):\n");
            sb.append(ctx);
            sb.append("\n\n");
        }
        sb.append("Gib nur den verarbeiteten Manuskripttext aus — nur das Diktat, keinen Text aus dem Editor-Kontext wiederholen.");
        return sb.toString();
    }

    public static String buildInstructionUserMessage(String instruction, String editorContext) {
        return buildInstructionUserMessage(instruction, editorContext, DictationVocabulary.empty());
    }

    public static String buildInstructionUserMessage(String instruction, String editorContext,
                                                     DictationVocabulary vocabulary) {
        StringBuilder sb = new StringBuilder();
        appendVocabulary(sb, vocabulary);
        sb.append("Anweisung des Autors:\n");
        sb.append(instruction != null ? instruction.trim() : "");
        sb.append("\n\n");
        String ctx = trimContext(editorContext, MAX_INSTRUCTION_CONTEXT_CHARS);
        if (!ctx.isEmpty()) {
            sb.append("Manuskript-Kontext (Text vor dem Cursor — Stil, Figuren, Situation):\n");
            sb.append(ctx);
            sb.append("\n\n");
        } else {
            sb.append("Manuskript-Kontext: (leer — schreibe neutral im Stil der Anweisung)\n\n");
        }
        sb.append("Setze die Anweisung um. Gib nur den neuen Manuskripttext aus.");
        return sb.toString();
    }

    private static void appendVocabulary(StringBuilder sb, DictationVocabulary vocabulary) {
        if (vocabulary == null || vocabulary.isEmpty()) {
            sb.append("""
                    Hinweis: Kein Projekt-Glossar geladen. Unter data/dictation-glossary.txt \
                    Begriffe anlegen (Glossar-Button im Editor) oder Figuren/Worldbuilding \
                    mit ##-Überschriften pflegen.
                    
                    """);
            return;
        }
        String block = vocabulary.llmGlossaryBlock();
        if (!block.isBlank()) {
            sb.append(block).append("\n\n");
        }
    }

    /**
     * Extrahiert den Text vor der Cursorposition als Kontext (letzter Absatz oder Tail).
     */
    public static String extractEditorContext(String fullText, int caretPosition) {
        return extractEditorContext(fullText, caretPosition, MAX_CONTEXT_CHARS);
    }

    public static String extractEditorContextForInstruction(String fullText, int caretPosition) {
        return extractEditorContext(fullText, caretPosition, MAX_INSTRUCTION_CONTEXT_CHARS);
    }

    public static String extractEditorContext(String fullText, int caretPosition, int maxChars) {
        if (fullText == null || fullText.isEmpty() || caretPosition <= 0 || maxChars <= 0) {
            return "";
        }
        int end = Math.min(caretPosition, fullText.length());
        int start = Math.max(0, end - maxChars);
        String slice = fullText.substring(start, end);
        int paraBreak = slice.lastIndexOf("\n\n");
        if (paraBreak >= 0 && paraBreak < slice.length() - 1) {
            slice = slice.substring(paraBreak + 2);
        }
        return slice.trim();
    }

    private static String trimContext(String editorContext, int maxChars) {
        if (editorContext == null || editorContext.isBlank()) {
            return "";
        }
        String ctx = editorContext.trim();
        if (ctx.length() > maxChars) {
            ctx = ctx.substring(ctx.length() - maxChars);
        }
        return ctx;
    }

    /**
     * Bereinigt die LLM-Antwort (Code-Fences, Anführungszeichen am Rand).
     */
    public static String cleanLlmOutput(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            if (firstNl > 0) {
                text = text.substring(firstNl + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        if ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    /**
     * Entfernt Wiederholungen des Editor-Kontexts (LLM wiederholt oft den letzten Satz).
     */
    public static String deduplicateAgainstContext(String processed, String editorContext, String rawTranscript) {
        if (processed == null || processed.isBlank()) {
            return processed != null ? processed : "";
        }
        String output = processed.trim();
        String ctx = editorContext != null ? editorContext.trim() : "";
        if (ctx.isEmpty()) {
            return output;
        }

        if (ctx.endsWith(output) || normalizeWs(ctx).endsWith(normalizeWs(output))) {
            return fallbackTranscript(rawTranscript);
        }

        String lastSentence = extractLastSentence(ctx);
        if (!lastSentence.isBlank()) {
            if (equalsNormalized(output, lastSentence)) {
                return fallbackTranscript(rawTranscript);
            }
            String stripped = stripLeadingDuplicate(output, lastSentence);
            if (!stripped.equals(output) && !stripped.isBlank()) {
                return stripped;
            }
            if (stripped.isBlank()) {
                return fallbackTranscript(rawTranscript);
            }
        }

        String lastParagraph = extractLastParagraph(ctx);
        if (!lastParagraph.isBlank() && equalsNormalized(output, lastParagraph.trim())) {
            return fallbackTranscript(rawTranscript);
        }

        return output;
    }

    private static String stripLeadingDuplicate(String output, String duplicate) {
        String o = output.trim();
        String d = duplicate.trim();
        if (o.startsWith(d)) {
            String rest = o.substring(d.length()).trim();
            if (rest.startsWith(".") || rest.startsWith(",") || rest.startsWith(";")) {
                rest = rest.substring(1).trim();
            }
            return rest;
        }
        if (normalizeWs(o).startsWith(normalizeWs(d))) {
            return o.substring(Math.min(d.length(), o.length())).trim();
        }
        return output;
    }

    private static String extractLastSentence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int sentStart = 0;
        for (int i = trimmed.length() - 2; i >= 0; i--) {
            char ch = trimmed.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
                int next = i + 1;
                while (next < trimmed.length() && Character.isWhitespace(trimmed.charAt(next))) {
                    next++;
                }
                if (next < trimmed.length()) {
                    sentStart = next;
                    break;
                }
            }
        }
        return trimmed.substring(sentStart).trim();
    }

    private static String extractLastParagraph(String text) {
        String trimmed = text.trim();
        int breakIdx = trimmed.lastIndexOf("\n\n");
        if (breakIdx >= 0 && breakIdx < trimmed.length() - 2) {
            return trimmed.substring(breakIdx + 2).trim();
        }
        return trimmed;
    }

    private static String fallbackTranscript(String rawTranscript) {
        if (rawTranscript == null || rawTranscript.isBlank()) {
            return "";
        }
        return rawTranscript.trim();
    }

    private static boolean equalsNormalized(String a, String b) {
        return normalizeWs(a).equals(normalizeWs(b));
    }

    private static String normalizeWs(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    public record TranscriptAnalysis(DictationMode mode, String instructionText, String rawTranscript) {
    }
}
