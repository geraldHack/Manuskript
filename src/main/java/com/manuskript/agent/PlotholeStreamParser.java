package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sammelt gestreamte Modell-Ausgabe und liefert {@code <PROBLEM>}-Funde,
 * sobald ein Block mit {@code </PROBLEM>} vollständig ist.
 */
public final class PlotholeStreamParser {

    private final StringBuilder buffer = new StringBuilder();
    private int consumedTo;
    private final List<Finding> findings = new ArrayList<>();

    /**
     * Hängt Text an und gibt neu geschlossene Funde zurück (kann leer sein).
     */
    public List<Finding> append(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        buffer.append(chunk);
        return pollCompleteFindings();
    }

    /**
     * Liest alle inzwischen vollständigen Blöcke, die noch nicht gemeldet wurden.
     */
    public List<Finding> pollCompleteFindings() {
        List<Finding> newly = new ArrayList<>();
        String text = buffer.toString();
        String lower = text.toLowerCase(Locale.ROOT);
        while (true) {
            int start = lower.indexOf("<problem>", consumedTo);
            if (start < 0) {
                break;
            }
            int endTag = lower.indexOf("</problem>", start);
            if (endTag < 0) {
                break;
            }
            int blockEnd = endTag + "</problem>".length();
            String block = text.substring(start, blockEnd);
            List<Finding> parsed = PlotholeResponseParser.parseCompleteProblemBlocks(block);
            findings.addAll(parsed);
            newly.addAll(parsed);
            consumedTo = blockEnd;
        }
        return newly;
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public List<Finding> allFindings() {
        return List.copyOf(findings);
    }

    public String rawText() {
        return buffer.toString();
    }

    /**
     * Schließt den Stream: restliche vollständige Blöcke, sonst vollständige Parser-Logik
     * (KEINE_PROBLEME, JSON, unparseable).
     */
    public PlotholeParseResult complete() {
        pollCompleteFindings();
        if (!findings.isEmpty()) {
            return PlotholeParseResult.findings(allFindings());
        }
        return PlotholeResponseParser.parse(buffer.toString());
    }
}
