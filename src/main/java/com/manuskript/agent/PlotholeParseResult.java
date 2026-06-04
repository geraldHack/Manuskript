package com.manuskript.agent;

import java.util.Collections;
import java.util.List;

/**
 * Ergebnis der Plothole-Antwort-Auswertung (nicht mit „keine Probleme“ verwechseln).
 */
public final class PlotholeParseResult {

    public enum Outcome {
        /** Modell hat explizit KEINE_PROBLEME (o. ä.) gemeldet. */
        NO_PROBLEMS,
        /** Mindestens ein Fund wurde erkannt. */
        FINDINGS,
        /** Antwort vorhanden, aber Format nicht auswertbar. */
        UNPARSEABLE
    }

    private final Outcome outcome;
    private final List<Finding> findings;
    private final String detailMessage;

    private PlotholeParseResult(Outcome outcome, List<Finding> findings, String detailMessage) {
        this.outcome = outcome;
        this.findings = findings == null ? List.of() : List.copyOf(findings);
        this.detailMessage = detailMessage;
    }

    public static PlotholeParseResult noProblems() {
        return new PlotholeParseResult(Outcome.NO_PROBLEMS, List.of(), null);
    }

    public static PlotholeParseResult findings(List<Finding> findings) {
        return new PlotholeParseResult(Outcome.FINDINGS, findings, null);
    }

    public static PlotholeParseResult unparseable(String detailMessage) {
        return new PlotholeParseResult(Outcome.UNPARSEABLE, List.of(),
                detailMessage != null ? detailMessage : "Antwort konnte nicht ausgewertet werden.");
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public String getDetailMessage() {
        return detailMessage;
    }
}
