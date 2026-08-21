package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotholeStreamParserTest {

    @Test
    void emitsFindingOnlyAfterClosingTag() {
        PlotholeStreamParser parser = new PlotholeStreamParser();
        List<Finding> first = parser.append("<PROBLEM> SCHWEREGRAD: 3 ZITAT: \"Er ging.\" PROBLEM: Unklar. VORSCHLÄGE: \"A\", \"B\"");
        assertTrue(first.isEmpty());

        List<Finding> second = parser.append(" </PROBLEM>");
        assertEquals(1, second.size());
        assertEquals("Er ging.", second.get(0).getQuote());
        assertEquals(1, parser.allFindings().size());
    }

    @Test
    void emitsTwoFindingsFromChunkedStream() {
        String block1 = "<PROBLEM> SCHWEREGRAD: 2 ZITAT: \"Eins.\" PROBLEM: Erster Fund. VORSCHLÄGE: \"A1\", \"A2\" </PROBLEM>";
        String block2 = "\n\n<PROBLEM> SCHWEREGRAD: 4 ZITAT: \"Zwei.\" PROBLEM: Zweiter Fund. VORSCHLÄGE: \"B1\", \"B2\" </PROBLEM>";
        PlotholeStreamParser parser = new PlotholeStreamParser();
        List<Finding> collected = new ArrayList<>();
        for (int i = 0; i < block1.length(); i++) {
            collected.addAll(parser.append(block1.substring(i, i + 1)));
        }
        assertEquals(1, collected.size());
        collected.addAll(parser.append(block2));
        assertEquals(2, collected.size());
        assertEquals("Zwei.", collected.get(1).getQuote());
        assertEquals(PlotholeParseResult.Outcome.FINDINGS, parser.complete().getOutcome());
    }

    @Test
    void completeFallsBackToNoProblems() {
        PlotholeStreamParser parser = new PlotholeStreamParser();
        parser.append("KEINE_PROBLEME");
        PlotholeParseResult result = parser.complete();
        assertEquals(PlotholeParseResult.Outcome.NO_PROBLEMS, result.getOutcome());
    }
}
