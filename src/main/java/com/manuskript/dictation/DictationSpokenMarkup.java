package com.manuskript.dictation;

import com.manuskript.GedankenstrichSupport;
import com.manuskript.QuotationMarkSupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nachbearbeitung von Diktat-Text: gesprochene Befehle und LLM-Anführungszeichen
 * in den gewählten Editor-Stil bringen.
 */
final class DictationSpokenMarkup {

    private static final String QUOTE_CHARS = "[\u201E\u201C\u201D\u201A\u2018\u2019\"'«»‹›]";

    private static final String QUOTE_CMD = "anf(?:ue|[uü])hrungszeichen";

    /**
     * „Die Glühlampen bitte in einfache Anführungszeichen setzen …“
     * → Die ‚Glühlampen‘ …
     */
    private static final Pattern WRAP_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:\\b(die|der|das|den|dem|des|ein|eine|einen|einem|einer)\\s+)?"
                    + "(\\S+?)\\s+"
                    + "(?:bitte\\s+)?in\\s+(einfache\\s+|doppelte\\s+)?"
                    + QUOTE_CMD
                    + "\\s+setzen\\b");

    private static final Pattern OPEN_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + "(?:" + QUOTE_CMD + "\\s+unten|" + QUOTE_CMD + "\\s+auf|"
                    + "öffnende\\s+" + QUOTE_CMD + "|" + QUOTE_CMD + "\\s+öffnen|"
                    + QUOTE_CMD + "\\s+open)"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private static final Pattern CLOSE_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + "(?:" + QUOTE_CMD + "\\s+oben|" + QUOTE_CMD + "\\s+zu|"
                    + "schließende\\s+" + QUOTE_CMD + "|" + QUOTE_CMD + "\\s+schließen|"
                    + QUOTE_CMD + "\\s+close)"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private static final Pattern SMART_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + QUOTE_CMD
                    + "(?!\\s+(?:unten|auf|oben|zu|öffnen|schließen|öffnend|schließend|open|close|setzen))"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private static final Pattern GEDANKENSTRICH_WORD = Pattern.compile(
            "(?iu)\\bgedankenstrich\\b");
    /** Genau zwei Bindestriche, nicht Teil von --- */
    private static final Pattern DOUBLE_HYPHEN = Pattern.compile("(?<!-)-{2}(?!-)");

    /** Kursiv-Befehle inkl. STT-Varianten. */
    private static final String ITALIC_CMD =
            "kursiv|schrägschrift|schraegschrift|schräg|schraeg|italic";
    /** Fett-Befehle. */
    private static final String BOLD_CMD = "fett|fettschrift|bold";

    /**
     * „wirklich in kursiv“ / „Die Glühlampen bitte in kursiv setzen“
     * → *wirklich* / Die *Glühlampen*
     */
    private static final Pattern WRAP_ITALIC_COMMAND = Pattern.compile(
            "(?iu)(?:\\b(die|der|das|den|dem|des|ein|eine|einen|einem|einer)\\s+)?"
                    + "(\\S+?)\\s+"
                    + "(?:bitte\\s+)?in\\s+(?:" + ITALIC_CMD + ")(?:\\s+setzen)?\\b");

    private static final Pattern WRAP_BOLD_COMMAND = Pattern.compile(
            "(?iu)(?:\\b(die|der|das|den|dem|des|ein|eine|einen|einem|einer)\\s+)?"
                    + "(\\S+?)\\s+"
                    + "(?:bitte\\s+)?in\\s+(?:" + BOLD_CMD + ")(?:\\s+setzen)?\\b");

    /**
     * „kursiv Phrase kursiv“ / „in kursiv Phrase kursiv aus“
     */
    private static final Pattern TOGGLE_ITALIC = Pattern.compile(
            "(?iu)(?:in\\s+)?(?:" + ITALIC_CMD + ")(?:\\s+auf)?\\s+"
                    + "(.+?)\\s+"
                    + "(?:in\\s+)?(?:" + ITALIC_CMD + ")(?:\\s+(?:aus|zu|ende|off))?");

    private static final Pattern TOGGLE_BOLD = Pattern.compile(
            "(?iu)(?:in\\s+)?(?:" + BOLD_CMD + ")(?:\\s+auf)?\\s+"
                    + "(.+?)\\s+"
                    + "(?:in\\s+)?(?:" + BOLD_CMD + ")(?:\\s+(?:aus|zu|ende|off))?");

    private static final Pattern FORMAT_CMD_WORD = Pattern.compile(
            "(?iu)\\b(?:" + ITALIC_CMD + "|" + BOLD_CMD + ")\\b");

    /**
     * Verben der Figurenrede (Inquit). Reihenfolge: längere Mehrwort-Formen zuerst.
     */
    private static final String SPEECH_VERBS =
            "rief\\s+aus|fügte\\s+hinzu|sagte|fragte|rief|flüsterte|antwortete|meinte|"
                    + "erwiderte|murmelte|schrie|entgegnete|bemerkte|erklärte|versetzte|"
                    + "hauchte|stammelte|brummte|knurrte|zischte|keuchte|stöhnte";

    /** Indirekte Rede / Nebensatz nach Komma — nicht quoten. */
    private static final String INDIRECT_START =
            "dass|daß|ob|wenn|weil|als|bevor|nachdem|während|obwohl|falls|indem";

    /**
     * „Er sagte, diese Sache ist erledigt“ / „Sie flüsterte: komm her“
     * (max. 6 Wörter vor dem Verb, inkl. Namen).
     */
    private static final Pattern INQUIT_FIRST = Pattern.compile(
            "(?iu)^((?:\\S+\\s+){0,6}(?:" + SPEECH_VERBS + "))\\s*[,:]\\s+"
                    + "(?!(?:" + INDIRECT_START + ")\\b)(.+)$");

    /**
     * „Das ist auch gar nicht nötig sagte er“
     */
    private static final Pattern INQUIT_AFTER = Pattern.compile(
            "(?iu)^(.+?)\\s+(" + SPEECH_VERBS + ")\\s+"
                    + "(er|sie|es|ich|man|[A-ZÄÖÜ][\\wÄÖÜäöüß'-]{1,40})"
                    + "([.!?]*)\\s*$");

    private static final Pattern SENTENCE_GAP = Pattern.compile("(?<=[.!?…])\\s+");

    private DictationSpokenMarkup() {
    }

    /**
     * Einzige Nachbearbeitung nach LLM/STT: Befehle auflösen, dann Stil vereinheitlichen.
     *
     * @param generatedText      vom LLM erzeugter Text (wird eingefügt)
     * @param textBeforeInsert   Editor-Text vor dem Cursor (für offen/schließen)
     * @param quoteStyleIndex    Toolbar-Einstellung
     */
    static String finish(String generatedText, String textBeforeInsert, int quoteStyleIndex) {
        if (generatedText == null || generatedText.isBlank()) {
            return generatedText != null ? generatedText : "";
        }
        // 1. LLM-Text: vorhandene Anführungszeichen-Paare in den Zielstil
        String normalized = QuotationMarkSupport.convertTextToStyle(generatedText, quoteStyleIndex);
        // 2. Verwaiste Dialog-Quotes reparieren (z. B. fehlendes öffnendes nach LLM-Hülle)
        normalized = repairOrphanDialogueQuotes(normalized, quoteStyleIndex);
        // 3. Gesprochene Befehle mit Editor-Kontext (inkl. „… in Anführungszeichen setzen“)
        String resolved = resolveSpokenQuoteCommands(normalized, textBeforeInsert, quoteStyleIndex);
        // 4. Kursiv/Fett („in kursiv“, „kursiv … kursiv“, …)
        resolved = resolveSpokenFormatCommands(resolved);
        resolved = resolveGedankenstrich(resolved);
        // 5. Direkte Rede ohne Anführungszeichen (STT/LLM) nachziehen
        resolved = resolveDirectSpeech(resolved, quoteStyleIndex);
        return trimSpacesAroundQuotes(resolved, quoteStyleIndex);
    }

    /**
     * Repariert typische Diktat-/LLM-Fehler bei direkter Rede:
     * <ul>
     *   <li>{@code Rede…", sagte X.} → {@code „Rede…“, sagte X.}</li>
     *   <li>{@code "Rede…", sagte X."} → {@code „Rede…“, sagte X.}</li>
     * </ul>
     */
    static String repairOrphanDialogueQuotes(String text, int quoteStyleIndex) {
        if (text == null || text.isBlank()) {
            return text != null ? text : "";
        }
        String open = openingQuote(quoteStyleIndex);
        String close = closingQuote(quoteStyleIndex);

        // "Rede…", sagte Name."  (Hülle + Dialog, oft nach fehlgeschlagenem Unwrap)
        Pattern wrapped = Pattern.compile(
                "(?iu)^\\s*\"(.+?)\"\\s*,\\s*(" + SPEECH_VERBS + ")\\s+"
                        + "(er|sie|es|ich|man|[A-ZÄÖÜ][\\wÄÖÜäöüß'-]{1,40})"
                        + "([.!?]*)\"?\\s*$");
        Matcher wrappedMatcher = wrapped.matcher(text);
        if (wrappedMatcher.matches()) {
            String speech = capitalizeFirstLetter(wrappedMatcher.group(1).strip());
            return open + speech + close + ", " + wrappedMatcher.group(2) + " "
                    + wrappedMatcher.group(3) + nullToEmpty(wrappedMatcher.group(4));
        }

        // Rede…", sagte Name. / Rede…" sagte Name.  (nur schließendes Quote)
        Pattern orphan = Pattern.compile(
                "(?iu)^\\s*(?![\"\\u201E\\u00BB\\u00AB])(.+?)\\s*[\"\\u201C\\u201D\\u00AB]\\s*,\\s*"
                        + "(" + SPEECH_VERBS + ")\\s+"
                        + "(er|sie|es|ich|man|[A-ZÄÖÜ][\\wÄÖÜäöüß'-]{1,40})"
                        + "([.!?]*)\\s*$");
        Matcher orphanMatcher = orphan.matcher(text);
        if (orphanMatcher.matches()) {
            String speech = capitalizeFirstLetter(orphanMatcher.group(1).strip());
            if (!speech.isEmpty() && !containsQuoteChar(speech)) {
                return open + speech + close + ", " + orphanMatcher.group(2) + " "
                        + orphanMatcher.group(3) + nullToEmpty(orphanMatcher.group(4));
            }
        }

        // Trailing Extra-Quote nach Inquit: … sagte Jomar."
        text = text.replaceAll(
                "(?iu)(,\\s*(?:" + SPEECH_VERBS + ")\\s+"
                        + "(?:er|sie|es|ich|man|[A-ZÄÖÜ][\\wÄÖÜäöüß'-]{1,40})[.!?]*)\"\\s*$",
                "$1");
        return text;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    /**
     * Wandelt gesprochene Format-Befehle in Manuskript-Markdown um (*kursiv*, **fett**).
     */
    static String resolveSpokenFormatCommands(String text) {
        if (text == null || text.isBlank()) {
            return text != null ? text : "";
        }
        // Zuerst Paare („kursiv … kursiv“), danach Einzelwort („Wort in kursiv“) —
        // sonst frisst „in kursiv“ den Toggle-Anfang.
        String resolved = replaceToggleFormatCommands(text, TOGGLE_BOLD, "**");
        resolved = replaceToggleFormatCommands(resolved, TOGGLE_ITALIC, "*");
        resolved = replaceWrapFormatCommands(resolved, WRAP_BOLD_COMMAND, "**");
        resolved = replaceWrapFormatCommands(resolved, WRAP_ITALIC_COMMAND, "*");
        return resolved;
    }

    private static String replaceWrapFormatCommands(String text, Pattern pattern, String marker) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String article = matcher.group(1);
            String word = matcher.group(2);
            if (word == null || word.isBlank() || isFormatCommandWord(word)) {
                result.append(text, matcher.start(), matcher.end());
            } else {
                if (article != null && !article.isBlank()) {
                    result.append(article).append(' ');
                }
                result.append(wrapWithMarker(word, marker));
            }
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String replaceToggleFormatCommands(String text, Pattern pattern, String marker) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String phrase = matcher.group(1) != null ? matcher.group(1).strip() : "";
            if (phrase.isEmpty() || containsFormatCommand(phrase)) {
                // Kein sinnvoller Inhalt / verschachtelte Befehle — Original behalten
                result.append(text, matcher.start(), matcher.end());
            } else {
                result.append(wrapWithMarker(phrase, marker));
            }
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String wrapWithMarker(String content, String marker) {
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            return content;
        }
        if (trimmed.startsWith(marker) && trimmed.endsWith(marker) && trimmed.length() > marker.length() * 2) {
            return trimmed;
        }
        return marker + trimmed + marker;
    }

    private static boolean isFormatCommandWord(String word) {
        return word != null && word.matches(
                "(?iu)(?:" + ITALIC_CMD + "|" + BOLD_CMD + "|bitte|setzen|auf|aus|zu|ende|off|in)");
    }

    private static boolean containsFormatCommand(String phrase) {
        return FORMAT_CMD_WORD.matcher(phrase).find();
    }

    /**
     * Setzt Anführungszeichen bei klarer direkter Rede (Inquit vor/nach der Rede).
     * Indirekte Rede („sagte, dass …“) bleibt unberührt; bereits gequoteter Text ebenfalls.
     */
    static String resolveDirectSpeech(String text, int quoteStyleIndex) {
        if (text == null || text.isBlank()) {
            return text != null ? text : "";
        }
        String open = openingQuote(quoteStyleIndex);
        String close = closingQuote(quoteStyleIndex);
        Matcher gap = SENTENCE_GAP.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 8);
        int last = 0;
        while (gap.find()) {
            out.append(transformSpeechSentence(text.substring(last, gap.start()), open, close));
            out.append(text, gap.start(), gap.end());
            last = gap.end();
        }
        out.append(transformSpeechSentence(text.substring(last), open, close));
        return out.toString();
    }

    private static String transformSpeechSentence(String sentence, String open, String close) {
        if (sentence == null || sentence.isBlank()) {
            return sentence != null ? sentence : "";
        }
        if (containsQuoteChar(sentence)) {
            return sentence;
        }
        Matcher first = INQUIT_FIRST.matcher(sentence);
        if (first.matches()) {
            String inquit = first.group(1).stripTrailing();
            String speech = capitalizeFirstLetter(first.group(2).strip());
            if (speech.isEmpty()) {
                return sentence;
            }
            return inquit + ": " + open + speech + close;
        }
        Matcher after = INQUIT_AFTER.matcher(sentence);
        if (after.matches()) {
            String speech = after.group(1).strip();
            String verb = after.group(2);
            String subject = after.group(3);
            String endPunct = after.group(4) != null ? after.group(4) : "";
            if (speech.isEmpty() || looksLikeNarrativeBeforeInquit(speech)) {
                return sentence;
            }
            // Endpunktuation in die Rede, Komma vor Inquit (deutsche Typografie)
            String speechBody = speech;
            String trailing = "";
            if (speechBody.endsWith(".") || speechBody.endsWith("!") || speechBody.endsWith("?")) {
                trailing = speechBody.substring(speechBody.length() - 1);
                speechBody = speechBody.substring(0, speechBody.length() - 1).stripTrailing();
            }
            if (speechBody.isEmpty()) {
                return sentence;
            }
            return open + speechBody + trailing + close + ", " + verb + " " + subject + endPunct;
        }
        return sentence;
    }

    /** Vermeidet False Positives bei „… nicht sagte er“-ähnlichen Bruchstücken. */
    private static boolean looksLikeNarrativeBeforeInquit(String speech) {
        String trimmed = speech.strip();
        if (trimmed.length() < 2) {
            return true;
        }
        // Reine Korrekturfragmente / zu kurz für Rede
        return trimmed.equalsIgnoreCase("nicht")
                || trimmed.equalsIgnoreCase("nein")
                || trimmed.equalsIgnoreCase("ja");
    }

    private static boolean containsQuoteChar(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            switch (cp) {
                case '\u201E', '\u201C', '\u201D', '\u201A', '\u2018', '\u2019',
                     '"', '\'', '\u00AB', '\u00BB', '\u2039', '\u203A' -> {
                    return true;
                }
                default -> {
                }
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length()) {
            return text;
        }
        int cp = text.codePointAt(i);
        int upper = Character.toUpperCase(cp);
        if (cp == upper) {
            return text;
        }
        return text.substring(0, i)
                + new String(Character.toChars(upper))
                + text.substring(i + Character.charCount(cp));
    }

    private static String resolveGedankenstrich(String text) {
        String withWord = GEDANKENSTRICH_WORD.matcher(text)
                .replaceAll(Matcher.quoteReplacement(GedankenstrichSupport.GEDANKENSTRICH));
        return DOUBLE_HYPHEN.matcher(withWord)
                .replaceAll(Matcher.quoteReplacement(GedankenstrichSupport.GEDANKENSTRICH));
    }

    private static String resolveSpokenQuoteCommands(
            String text, String textBeforeInsert, int quoteStyleIndex) {
        StringBuilder context = new StringBuilder(textBeforeInsert != null ? textBeforeInsert : "");
        String afterWrap = replaceWrapCommands(text, context, quoteStyleIndex);
        String openQuote = openingQuote(quoteStyleIndex);
        String closeQuote = closingQuote(quoteStyleIndex);
        String afterOpen = replaceWithContext(OPEN_QUOTE_COMMAND, afterWrap, context, openQuote);
        String afterClose = replaceWithContext(CLOSE_QUOTE_COMMAND, afterOpen, context, closeQuote);
        return replaceWithSmartQuotes(SMART_QUOTE_COMMAND, afterClose, context, quoteStyleIndex);
    }

    private static String replaceWrapCommands(String text, StringBuilder context, int quoteStyleIndex) {
        Matcher matcher = WRAP_QUOTE_COMMAND.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            context.append(text, lastEnd, matcher.start());

            String article = matcher.group(1);
            String word = matcher.group(2);
            String kind = matcher.group(3);
            boolean single = kind != null && kind.toLowerCase(java.util.Locale.ROOT).startsWith("einfach");

            String open = single ? openingSingleQuote(quoteStyleIndex) : openingQuote(quoteStyleIndex);
            String close = single ? closingSingleQuote(quoteStyleIndex) : closingQuote(quoteStyleIndex);

            StringBuilder wrapped = new StringBuilder();
            if (article != null && !article.isBlank()) {
                wrapped.append(article).append(' ');
            }
            wrapped.append(open).append(word).append(close);

            result.append(wrapped);
            context.append(wrapped);
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String replaceWithContext(
            Pattern pattern, String text, StringBuilder context, String replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            context.append(text, lastEnd, matcher.start());
            result.append(replacement);
            context.append(replacement);
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String replaceWithSmartQuotes(
            Pattern pattern, String text, StringBuilder context, int quoteStyleIndex) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            context.append(text, lastEnd, matcher.start());
            String quote = QuotationMarkSupport.resolveTypedQuote(
                    context.toString(), context.length(), "\"", quoteStyleIndex);
            result.append(quote);
            context.append(quote);
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String openingQuote(int quoteStyleIndex) {
        return QuotationMarkSupport.resolveTypedQuote("", 0, "\"", quoteStyleIndex);
    }

    private static String closingQuote(int quoteStyleIndex) {
        String open = openingQuote(quoteStyleIndex);
        return QuotationMarkSupport.resolveTypedQuote(open, open.length(), "\"", quoteStyleIndex);
    }

    private static String openingSingleQuote(int quoteStyleIndex) {
        return QuotationMarkSupport.resolveTypedQuote("", 0, "'", quoteStyleIndex);
    }

    private static String closingSingleQuote(int quoteStyleIndex) {
        String open = openingSingleQuote(quoteStyleIndex);
        return QuotationMarkSupport.resolveTypedQuote(open, open.length(), "'", quoteStyleIndex);
    }

    private static String trimSpacesAroundQuotes(String text, int quoteStyleIndex) {
        String open = openingQuote(quoteStyleIndex);
        String close = closingQuote(quoteStyleIndex);
        if (open.equals(close)) {
            return trimSameCharQuoteSpaces(text, open);
        }
        String result = text;
        result = result.replaceAll(Pattern.quote(open) + "\\s+", Matcher.quoteReplacement(open));
        result = result.replaceAll("\\s+" + Pattern.quote(close), Matcher.quoteReplacement(close));
        result = result.replaceAll("(?<=\\S)" + Pattern.quote(open), " " + open);
        String openSingle = openingSingleQuote(quoteStyleIndex);
        String closeSingle = closingSingleQuote(quoteStyleIndex);
        if (!openSingle.equals(open)) {
            result = result.replaceAll(Pattern.quote(openSingle) + "\\s+", Matcher.quoteReplacement(openSingle));
            result = result.replaceAll("\\s+" + Pattern.quote(closeSingle), Matcher.quoteReplacement(closeSingle));
        }
        return result;
    }

    private static String trimSameCharQuoteSpaces(String text, String quote) {
        StringBuilder result = new StringBuilder();
        int quoteCount = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith(quote, i)) {
                if (quoteCount % 2 == 0) {
                    result.append(quote);
                    i += quote.length();
                    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                } else {
                    while (result.length() > 0 && Character.isWhitespace(result.charAt(result.length() - 1))) {
                        result.deleteCharAt(result.length() - 1);
                    }
                    result.append(quote);
                    i += quote.length();
                }
                quoteCount++;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    static String spokenMarkupHint() {
        return "Dialog: „Er sagte, …“ wird zu Er sagte: „…“; "
                + "Format: „Wort in kursiv“, „bitte in kursiv setzen“, „kursiv … kursiv“ "
                + "(ebenso fett); "
                + "„Anführungszeichen“ um Dialog; „Wort bitte in Anführungszeichen setzen“ "
                + "bzw. „… in einfache Anführungszeichen setzen“ → passend zum Toolbar-Stil; "
                + "„Gedankenstrich“ / „--“ → –";
    }
}
