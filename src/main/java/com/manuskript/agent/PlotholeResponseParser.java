package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Wandelt Plothole-Modellantworten in {@link Finding}-Listen um (XML-Format, JSON, Markdown).
 */
public final class PlotholeResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(PlotholeResponseParser.class);

    /** Vorschau in normalen INFO-Logs. */
    private static final int LOG_PREVIEW_CHARS = 4000;
    /** Bei UNPARSEABLE: vollständige Antwort fürs Debugging (Obergrenze). */
    private static final int LOG_FULL_ANALYSIS_CHARS = 120_000;

    private static final Pattern STRICT_BLOCK = Pattern.compile(
            "<PROBLEM>\\s*SCHWEREGRAD:\\s*(\\d+)\\s*ZITAT:\\s*\"([^\"]*)\"\\s*PROBLEM:\\s*(.*?)</PROBLEM>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FLEX_BLOCK = Pattern.compile(
            "<PROBLEM>\\s*SCHWEREGRAD:\\s*(\\d+)\\s*ZITAT:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^<\\n]+?))\\s*PROBLEM:\\s*(.*?)</PROBLEM>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern PROBLEM_BLOCK = Pattern.compile(
            "<PROBLEM>(.*?)</PROBLEM>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PlotholeResponseParser() {
    }

    public static PlotholeParseResult parse(String response) {
        if (response == null || response.trim().isEmpty()) {
            logger.warn("Plothole: leere Antwort");
            return PlotholeParseResult.unparseable("Leere Antwort vom Modell.");
        }

        String trimmed = unwrapMarkdownFence(response.trim());
        logger.info("Plothole-Rohantwort Vorschau ({} Zeichen):\n{}", trimmed.length(),
                truncateForLog(trimmed, LOG_PREVIEW_CHARS));

        if (reportsNoProblems(trimmed)) {
            logger.info("Plothole: explizit keine Probleme");
            return PlotholeParseResult.noProblems();
        }

        String normalized = trimmed.replace("\r\n", "\n").replace("\r", "\n");
        List<Finding> findings = new ArrayList<>();
        findings.addAll(parseProblemBlocks(normalized));
        if (findings.isEmpty()) {
            findings.addAll(parseStrictBlocks(normalized));
        }
        if (findings.isEmpty()) {
            findings.addAll(parseFlexBlocks(normalized));
        }
        if (findings.isEmpty()) {
            findings.addAll(parseJsonFindings(normalized));
        }
        if (findings.isEmpty()) {
            findings.addAll(parseLooseLines(normalized));
        }

        if (!findings.isEmpty()) {
            logger.info("Plothole: {} Fund(e) erkannt", findings.size());
            return PlotholeParseResult.findings(findings);
        }

        if (likelyUnparseable(normalized)) {
            logFullResponseForAnalysis("UNPARSEABLE", normalized);
            String preview = normalized.length() > 280
                    ? normalized.substring(0, 280).replace('\n', ' ') + "…"
                    : normalized.replace('\n', ' ');
            return PlotholeParseResult.unparseable(
                    "Die Modell-Antwort konnte nicht ausgewertet werden (falsches Format, z. B. JSON statt <PROBLEM>-Blöcke). "
                            + "Bitte erneut analysieren oder anderes Modell wählen. "
                            + "Vollständige Antwort steht im Log (WARN PlotholeResponseParser). Kurzauszug: " + preview);
        }

        if (normalized.length() > 200) {
            logFullResponseForAnalysis("NO_FINDINGS_BUT_LONG_RESPONSE", normalized);
        }
        logger.info("Plothole: keine Funde, Antwort wirkt wie „keine Probleme“ ({} Zeichen)", normalized.length());
        return PlotholeParseResult.noProblems();
    }

    /**
     * Schreibt die komplette Modell-Antwort ins Log (für Analyse bei falschem Format).
     * Logger: {@code com.manuskript.agent.PlotholeResponseParser}, Level WARN.
     */
    private static void logFullResponseForAnalysis(String reason, String body) {
        String logged = truncateForLog(body, LOG_FULL_ANALYSIS_CHARS);
        boolean truncated = body.length() > LOG_FULL_ANALYSIS_CHARS;
        logger.warn("Plothole-Analyse [{}] — Modell-Antwort zum Debuggen ({} Zeichen{}):\n--- Plothole RAW BEGIN ---\n{}\n--- Plothole RAW END ---",
                reason,
                body.length(),
                truncated ? ", im Log auf " + LOG_FULL_ANALYSIS_CHARS + " gekürzt" : "",
                logged);
    }

    private static String truncateForLog(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n… [" + text.length() + " Zeichen gesamt, gekürzt für Log]";
    }

    private static boolean reportsNoProblems(String text) {
        String compact = text.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (compact.startsWith("KEINE_PROBLEME")) {
            return true;
        }
        if (compact.length() < 120
                && (compact.contains("KEINE_WIDERSPR") || compact.contains("KEINE_PROBLEM")
                || compact.matches(".*\\bNO_PROBLEMS?\\b.*"))) {
            return true;
        }
        return false;
    }

    private static boolean likelyUnparseable(String normalized) {
        if (normalized.length() < 40) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("<problem") || lower.contains("schweregrad:")
                || lower.contains("zitat:") || lower.contains("widerspruch")
                || lower.contains("plotlücke") || lower.contains("plotloch")
                || lower.contains("inkonsistenz")) {
            return true;
        }
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            return true;
        }
        if (lower.contains("\"problem\"") || lower.contains("\"quote\"")
                || lower.contains("\"zitat\"") || lower.contains("\"findings\"")) {
            return true;
        }
        return lower.matches("(?s).*\\b\\d+[.)]\\s+.{10,}.*");
    }

    private static String unwrapMarkdownFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNl = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstNl > 0 && lastFence > firstNl) {
            return text.substring(firstNl + 1, lastFence).trim();
        }
        return text.replace("```", "").trim();
    }

    private static List<Finding> parseProblemBlocks(String normalized) {
        List<Finding> findings = new ArrayList<>();
        Matcher blockMatcher = PROBLEM_BLOCK.matcher(normalized);
        while (blockMatcher.find()) {
            Finding finding = parseProblemBlockInner(blockMatcher.group(1).trim());
            if (finding != null) {
                findings.add(finding);
            }
        }
        return findings;
    }

    /**
     * Parst einen {@code <PROBLEM>}-Inhalt robust — auch bei Anführungszeichen im Zitat
     * und mehrzeiligen Vorschlägen.
     */
    private static Finding parseProblemBlockInner(String inner) {
        if (inner.isEmpty()) {
            return null;
        }
        Matcher severityMatcher = Pattern.compile("(?i)SCHWEREGRAD:\\s*(\\d+)").matcher(inner);
        if (!severityMatcher.find()) {
            return null;
        }
        int severity;
        try {
            severity = clampSeverity(Integer.parseInt(severityMatcher.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }

        int zitatIdx = indexOfIgnoreCase(inner, "ZITAT:");
        if (zitatIdx < 0) {
            return null;
        }
        int afterZitatLabel = zitatIdx + "ZITAT:".length();
        int problemIdx = indexOfIgnoreCase(inner, "PROBLEM:", afterZitatLabel);
        if (problemIdx < 0) {
            return null;
        }
        String quote = parseFieldValue(inner.substring(afterZitatLabel, problemIdx).trim());

        int afterProblemLabel = problemIdx + "PROBLEM:".length();
        int suggestionIdx = indexOfAnyIgnoreCase(inner, afterProblemLabel, "VORSCHLÄGE:", "VORSCHLÄGE", "VORSCHLAG:");
        if (suggestionIdx < 0) {
            return null;
        }
        String problem = inner.substring(afterProblemLabel, suggestionIdx).trim();
        if (problem.isEmpty()) {
            return null;
        }
        problem = AgentResponseText.normalizeModelText(problem);

        String suggestionsText = inner.substring(suggestionIdx).trim();
        Finding finding = new Finding(severity, quote, problem, "");
        finding.setSuggestions(extractTopLevelQuotedStrings(stripSuggestionLabel(suggestionsText)));
        if (finding.getSuggestions().isEmpty()) {
            finding.setSuggestions(extractSuggestions(suggestionsText));
        }
        finding.setSuggestionIndex(finding.getSuggestions().isEmpty() ? -1 : 0);
        return finding.getSuggestions().isEmpty() ? null : finding;
    }

    private static String parseFieldValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.toUpperCase(java.util.Locale.ROOT).startsWith("(MARKIERT)")) {
            return SelectionRevisionSupport.MARKED_QUOTE_PLACEHOLDER;
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return AgentResponseText.normalizeModelText(raw.substring(1, raw.length() - 1));
        }
        if (raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
            return AgentResponseText.normalizeModelText(raw.substring(1, raw.length() - 1));
        }
        return AgentResponseText.normalizeModelText(raw);
    }

    private static String stripSuggestionLabel(String suggestionsText) {
        if (suggestionsText == null) {
            return "";
        }
        return suggestionsText
                .replaceFirst("(?i)VORSCHLÄGE:\\s*", "")
                .replaceFirst("(?i)VORSCHLÄGE\\s*", "")
                .replaceFirst("(?i)VORSCHLAG:\\s*", "")
                .trim();
    }

    /** Liest kommagetrennte Top-Level-Strings in doppelten Anführungszeichen (inkl. Zeilenumbrüche). */
    private static List<String> extractTopLevelQuotedStrings(String text) {
        List<String> results = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return results;
        }
        int indexPos = text.indexOf("INDEX:");
        if (indexPos >= 0) {
            text = text.substring(0, indexPos).trim();
        }
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ',')) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) != '"') {
                break;
            }
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"', '\\' -> sb.append(next);
                        default -> sb.append(c);
                    }
                    i += 2;
                } else if (c == '"') {
                    i++;
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            addSuggestion(results, sb.toString());
        }
        return results;
    }

    private static int indexOfAnyIgnoreCase(String text, int fromIndex, String... needles) {
        int best = -1;
        for (String needle : needles) {
            int idx = text.toLowerCase(java.util.Locale.ROOT)
                    .indexOf(needle.toLowerCase(java.util.Locale.ROOT), fromIndex);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static List<Finding> parseStrictBlocks(String normalized) {
        List<Finding> findings = new ArrayList<>();
        Matcher m = STRICT_BLOCK.matcher(normalized);
        while (m.find()) {
            try {
                int severity = clampSeverity(Integer.parseInt(m.group(1)));
                String quote = AgentResponseText.normalizeModelText(m.group(2) != null ? m.group(2) : "");
                Finding finding = buildFinding(severity, quote, m.group(3).trim());
                if (finding != null) {
                    findings.add(finding);
                }
            } catch (NumberFormatException e) {
                logger.warn("Plothole: Schweregrad nicht lesbar: {}", m.group(1));
            }
        }
        return findings;
    }

    private static List<Finding> parseFlexBlocks(String normalized) {
        List<Finding> findings = new ArrayList<>();
        Matcher m = FLEX_BLOCK.matcher(normalized);
        while (m.find()) {
            try {
                int severity = clampSeverity(Integer.parseInt(m.group(1)));
                String quote = AgentResponseText.normalizeModelText(pickFirstNonEmpty(m.group(2), m.group(3), m.group(4)));
                Finding finding = buildFinding(severity, quote, m.group(5).trim());
                if (finding != null) {
                    findings.add(finding);
                }
            } catch (NumberFormatException e) {
                logger.warn("Plothole: Schweregrad nicht lesbar: {}", m.group(1));
            }
        }
        return findings;
    }

    private static String pickFirstNonEmpty(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                String normalized = AgentResponseText.normalizeModelText(p);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return "";
    }

    private static Finding buildFinding(int severity, String quote, String problemAndSuggestions) {
        int suggestionIndex = indexOfIgnoreCase(problemAndSuggestions, "VORSCHLÄGE:");
        if (suggestionIndex < 0) {
            suggestionIndex = indexOfIgnoreCase(problemAndSuggestions, "VORSCHLAG:");
        }
        String problem;
        String suggestionsText;
        if (suggestionIndex >= 0) {
            problem = problemAndSuggestions.substring(0, suggestionIndex).trim();
            suggestionsText = problemAndSuggestions.substring(suggestionIndex).trim();
        } else {
            problem = problemAndSuggestions.trim();
            suggestionsText = "";
        }
        if (problem.isEmpty()) {
            return null;
        }
        problem = AgentResponseText.normalizeModelText(problem);
        Finding finding = new Finding(severity, quote, problem, "");
        finding.setSuggestions(extractSuggestions(suggestionsText));
        finding.setSuggestionIndex(finding.getSuggestions().isEmpty() ? -1 : 0);
        return finding;
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int indexOfIgnoreCase(String text, String needle, int fromIndex) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), fromIndex);
    }

    private static List<String> extractSuggestions(String suggestionsText) {
        List<String> suggestions = new ArrayList<>();
        if (suggestionsText == null || suggestionsText.isBlank()) {
            return suggestions;
        }
        String cleanText = suggestionsText
                .replaceFirst("(?i)VORSCHLÄGE:\\s*", "")
                .replaceFirst("(?i)VORSCHLAG:\\s*", "");
        int indexPos = cleanText.indexOf("INDEX:");
        if (indexPos >= 0) {
            cleanText = cleanText.substring(0, indexPos).trim();
        }
        suggestions.addAll(extractQuotedStrings(cleanText));
        if (suggestions.isEmpty()) {
            Matcher qm = Pattern.compile("\"([^\"]+)\"", Pattern.DOTALL).matcher(cleanText);
            while (qm.find()) {
                addSuggestion(suggestions, qm.group(1));
            }
        }
        if (suggestions.isEmpty()) {
            Matcher sm = Pattern.compile("VORSCHLAG:\\s*([^<]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(suggestionsText);
            while (sm.find()) {
                addSuggestion(suggestions, sm.group(1));
            }
        }
        if (suggestions.isEmpty() && !cleanText.isEmpty()) {
            for (String part : cleanText.split(",")) {
                addSuggestion(suggestions, part.replaceAll("^\"|\"$", ""));
            }
        }
        return suggestions;
    }

    /** Liest doppelt-quotierte Strings inkl. Zeilenumbrüche und {@code \\n}-Escapes. */
    private static List<String> extractQuotedStrings(String text) {
        List<String> results = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return results;
        }
        int i = 0;
        while (i < text.length()) {
            int quoteStart = text.indexOf('"', i);
            if (quoteStart < 0) {
                break;
            }
            StringBuilder sb = new StringBuilder();
            i = quoteStart + 1;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"', '\\' -> sb.append(next);
                        default -> sb.append(c);
                    }
                    i += 2;
                } else if (c == '"') {
                    i++;
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            addSuggestion(results, sb.toString());
        }
        return results;
    }

    private static void addSuggestion(List<String> suggestions, String raw) {
        if (raw == null) {
            return;
        }
        String normalized = AgentResponseText.normalizeModelText(raw.strip());
        if (!normalized.isEmpty()) {
            suggestions.add(normalized);
        }
    }

    private static List<Finding> parseJsonFindings(String text) {
        List<Finding> findings = new ArrayList<>();
        String json = extractJsonSlice(text);
        if (json == null) {
            return findings;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            collectJsonFindings(root, findings);
            if (!findings.isEmpty()) {
                logger.info("Plothole: {} Fund(e) aus JSON", findings.size());
            }
        } catch (Exception e) {
            logger.debug("Plothole: JSON-Parsing fehlgeschlagen: {}", e.getMessage());
        }
        return findings;
    }

    private static String extractJsonSlice(String text) {
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            return t;
        }
        int obj = t.indexOf('{');
        int arr = t.indexOf('[');
        int start = -1;
        if (obj >= 0 && (arr < 0 || obj < arr)) {
            start = obj;
        } else if (arr >= 0) {
            start = arr;
        }
        if (start < 0) {
            return null;
        }
        char open = t.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return t.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static void collectJsonFindings(JsonElement root, List<Finding> findings) {
        if (root == null || root.isJsonNull()) {
            return;
        }
        if (root.isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray()) {
                addJsonObject(el, findings);
            }
            return;
        }
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        for (String key : new String[]{"problems", "findings", "issues", "items", "results", "widersprueche", "widersprüche"}) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray(key)) {
                    addJsonObject(el, findings);
                }
                return;
            }
        }
        addJsonObject(obj, findings);
    }

    private static void addJsonObject(JsonElement el, List<Finding> findings) {
        if (el == null || !el.isJsonObject()) {
            return;
        }
        JsonObject o = el.getAsJsonObject();
        String problem = jsonString(o, "problem", "beschreibung", "description", "issue", "text");
        if (problem == null || problem.isBlank()) {
            return;
        }
        int severity = clampSeverity(jsonInt(o, 3, "severity", "schweregrad", "level", "weight"));
        String quote = jsonString(o, "quote", "zitat", "excerpt", "original", "passage");
        if (quote == null) {
            quote = "";
        }
        Finding f = new Finding(severity, quote.trim(), problem.trim(), "");
        f.setSuggestions(jsonStringList(o, "suggestions", "vorschläge", "vorschlaege", "vorschlag"));
        if (f.getSuggestions().isEmpty()) {
            String single = jsonString(o, "suggestion", "vorschlag", "fix");
            if (single != null && !single.isBlank()) {
                f.setSuggestions(List.of(single.trim()));
            }
        }
        f.setSuggestionIndex(f.getSuggestions().isEmpty() ? -1 : 0);
        findings.add(f);
    }

    private static String jsonString(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                return o.get(key).getAsString();
            }
        }
        return null;
    }

    private static int jsonInt(JsonObject o, int defaultValue, String... keys) {
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                try {
                    return clampSeverity(o.get(key).getAsInt());
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return defaultValue;
    }

    private static List<String> jsonStringList(JsonObject o, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) {
                continue;
            }
            JsonElement el = o.get(key);
            if (el.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement item : el.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        String s = item.getAsString().trim();
                        if (!s.isEmpty()) {
                            list.add(s);
                        }
                    }
                }
                return list;
            }
            if (el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) {
                    return List.of(s);
                }
            }
        }
        return List.of();
    }

    private static int clampSeverity(int severity) {
        return Math.max(1, Math.min(5, severity));
    }

    private static List<Finding> parseLooseLines(String response) {
        List<Finding> findings = new ArrayList<>();
        String cleaned = response.replaceAll("\\*\\*\\*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("#", "");
        String[] lines = cleaned.split("\\n");
        Finding current = null;
        StringBuilder problemBuilder = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("^\\d+[.)]\\s.*")
                    || line.toLowerCase(Locale.ROOT).contains("schweregrad")
                    || line.toLowerCase(Locale.ROOT).contains("widerspruch")
                    || line.toLowerCase(Locale.ROOT).contains("problem:")) {
                storeLooseFinding(findings, current, problemBuilder);
                current = new Finding();
                current.setSeverity(3);
                problemBuilder = new StringBuilder();
                String problemText = line.replaceFirst("^\\d+[.)]\\s*", "")
                        .replaceFirst("(?i)widerspruch:\\s*", "")
                        .replaceFirst("(?i)problem:\\s*", "")
                        .replaceFirst("(?i)ungereimtheit:\\s*", "")
                        .trim();
                problemBuilder.append(problemText);
                if (line.toLowerCase(Locale.ROOT).contains("vorschlag:")
                        || line.toLowerCase(Locale.ROOT).contains("suggestion:")) {
                    String[] parts = line.split("(?i)vorschlag:|suggestion:", 2);
                    if (parts.length > 1) {
                        current.setSuggestion(parts[1].trim());
                    }
                }
            } else if (current != null) {
                if (line.toLowerCase(Locale.ROOT).startsWith("zitat:")) {
                    current.setQuote(line.substring(6).trim().replaceAll("^[\"']|[\"']$", ""));
                } else if (line.toLowerCase(Locale.ROOT).startsWith("vorschlag:")) {
                    current.setSuggestion(line.substring(10).trim());
                } else if (line.toLowerCase(Locale.ROOT).startsWith("suggestion:")) {
                    current.setSuggestion(line.substring(11).trim());
                } else if (line.contains("\"")) {
                    Matcher qm = Pattern.compile("\"([^\"]+)\"").matcher(line);
                    if (qm.find()) {
                        current.setQuote(qm.group(1));
                    }
                } else {
                    if (!problemBuilder.isEmpty()) {
                        problemBuilder.append(' ');
                    }
                    problemBuilder.append(line);
                }
            }
        }
        storeLooseFinding(findings, current, problemBuilder);
        return findings;
    }

    private static void storeLooseFinding(List<Finding> findings, Finding current, StringBuilder problemBuilder) {
        if (current == null) {
            return;
        }
        if (!problemBuilder.isEmpty()) {
            current.setProblem(problemBuilder.toString().trim());
        }
        if (current.getProblem() != null && !current.getProblem().isEmpty()) {
            List<String> suggestions = new ArrayList<>();
            if (current.getSuggestion() != null && !current.getSuggestion().isEmpty()) {
                suggestions.add(current.getSuggestion());
            }
            current.setSuggestions(suggestions);
            current.setSuggestionIndex(suggestions.isEmpty() ? -1 : 0);
            findings.add(current);
        }
    }
}
