package com.manuskript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownBlockSupportTest {

    @Test
    void parseUnorderedAndOrderedLists() {
        String md = "- Punkt\n  - Unterpunkt\n2. Zweiter\n";
        var lists = MarkdownBlockSupport.parseListLines(md);
        assertEquals(3, lists.size());
        assertEquals(MarkdownBlockSupport.ListKind.UNORDERED, lists.get(0).kind());
        assertEquals(1, lists.get(1).nestLevel());
        assertEquals(MarkdownBlockSupport.ListKind.ORDERED, lists.get(2).kind());
        assertEquals(2, lists.get(2).orderNumber());
    }

    @Test
    void computeOrderedDisplayNumbersLazyNumbering() {
        String md = "1. Erster\n1. Zweiter\n1. Dritter\n";
        var lists = MarkdownBlockSupport.parseListLines(md);
        var display = MarkdownBlockSupport.computeOrderedDisplayNumbers(md, lists);
        assertEquals(3, lists.size());
        assertEquals(1, display.get(lists.get(0).lineStart()));
        assertEquals(2, display.get(lists.get(1).lineStart()));
        assertEquals(3, display.get(lists.get(2).lineStart()));
    }

    @Test
    void computeOrderedDisplayNumbersNestedLevels() {
        String md = """
                1. Oben
                   1. Unten A
                   1. Unten B
                1. Oben wieder
                """;
        var lists = MarkdownBlockSupport.parseListLines(md);
        var display = MarkdownBlockSupport.computeOrderedDisplayNumbers(md, lists);
        assertEquals(4, lists.size());
        assertEquals(1, display.get(lists.get(0).lineStart()));
        assertEquals(1, display.get(lists.get(1).lineStart()));
        assertEquals(2, display.get(lists.get(2).lineStart()));
        assertEquals(2, display.get(lists.get(3).lineStart()));
        assertEquals(1, lists.get(1).nestLevel());
    }

    @Test
    void renumberOrderedListMarkersFixesLazyNumbering() {
        String md = "1. Erster\n1. Zweiter\n1. Dritter\n";
        assertEquals("1. Erster\n2. Zweiter\n3. Dritter\n", MarkdownBlockSupport.renumberOrderedListMarkers(md));
    }

    @Test
    void renumberOrderedListMarkersAfterSiblingIndented() {
        String md = "1. A\n   1. B\n3. C\n";
        assertEquals("1. A\n   1. B\n2. C\n", MarkdownBlockSupport.renumberOrderedListMarkers(md));
    }

    @Test
    void computeOrderedDisplayNumbersResetsAfterParagraph() {
        String md = "1. A\n\nAbsatz\n\n1. B\n";
        var lists = MarkdownBlockSupport.parseListLines(md);
        var display = MarkdownBlockSupport.computeOrderedDisplayNumbers(md, lists);
        assertEquals(2, lists.size());
        assertEquals(1, display.get(lists.get(0).lineStart()));
        assertEquals(1, display.get(lists.get(1).lineStart()));
    }

    @Test
    void parseTaskList() {
        String md = "- [x] Erledigt\n- [ ] Offen\n";
        var lists = MarkdownBlockSupport.parseListLines(md);
        assertEquals(2, lists.size());
        assertTrue(lists.get(0).taskChecked());
        assertFalse(lists.get(1).taskChecked());
    }

    @Test
    void mightHaveBlockStructuresIgnoresLonePipeInProse() {
        assertFalse(MarkdownBlockSupport.mightHaveBlockStructures("Er sagte: a | b und ging."));
        assertTrue(MarkdownBlockSupport.mightHaveBlockStructures("| A | B |\n|---|---|\n"));
    }

    @Test
    void mightHaveHorizontalRulesIgnoresTableSeparator() {
        assertFalse(MarkdownBlockSupport.mightHaveHorizontalRules("""
                | Spalte 1 | Spalte 2 |
                |----------|----------|
                | A | B |
                """));
        assertTrue(MarkdownBlockSupport.mightHaveHorizontalRules("Absatz\n\n---\n\nMehr"));
    }

    @Test
    void parseTableAndCodeFence() {
        String md = """
                | A | B |
                |---|---|
                | 1 | 2 |
                ```java
                int x = 1;
                ```
                """;
        var tables = MarkdownBlockSupport.parseTableLines(md);
        assertEquals(3, tables.size());
        assertTrue(tables.get(1).separator());
        var blocks = MarkdownBlockSupport.parseTableBlocks(md);
        assertEquals(1, blocks.size());
        assertEquals(3, blocks.get(0).rows().size());
        MarkdownBlockSupport.TableRow header = blocks.get(0).rows().get(0);
        assertEquals(2, header.cells().size());
        assertEquals("A", header.cells().get(0).text());
        assertTrue(blocks.get(0).rows().get(1).separator());
        var fences = MarkdownBlockSupport.parseCodeFences(md);
        assertEquals(1, fences.size());
        assertTrue(fences.get(0).contentEnd() > fences.get(0).contentStart());
    }

    @Test
    void parseCodeFenceClosingLine() {
        String md = "```\ncode\n```\n";
        var fences = MarkdownBlockSupport.parseCodeFences(md);
        assertEquals(1, fences.size());
        MarkdownBlockSupport.CodeFenceBlock block = fences.get(0);
        assertEquals("code", md.substring(block.contentStart(), block.contentEnd()).trim());
        assertTrue(block.contentEnd() < block.end());
        assertEquals("```", md.substring(block.contentEnd(), block.end()).trim());
    }

    @Test
    void mightHaveHorizontalRulesIgnoresLongDashTableSeparator() {
        assertFalse(MarkdownBlockSupport.mightHaveHorizontalRules("""
                | Spalte 1 | Spalte 2 |
                |----------|----------|
                | A | B |
                """));
        assertTrue(MarkdownBlockSupport.isTableMarkdownLine("|----------|----------|"));
    }

    @Test
    void parseTableBlocksWithWindowsLineEndings() {
        String md = "| A | B |\r\n|----------|----------|\r\n| 1 | 2 |\r\n";
        var blocks = MarkdownBlockSupport.parseTableBlocks(md);
        assertEquals(1, blocks.size());
        assertEquals(3, blocks.get(0).rows().size());
        assertTrue(blocks.get(0).rows().get(1).separator());
    }

    @Test
    void parseTableBlocksIgnoresSingleBlankLineBeforeSeparator() {
        String md = """
                | A | B |

                |---|---|
                | 1 | 2 |
                """;
        var blocks = MarkdownBlockSupport.parseTableBlocks(md);
        assertEquals(1, blocks.size());
        assertEquals(3, blocks.get(0).rows().size());
        assertTrue(blocks.get(0).rows().get(1).separator());
    }

    @Test
    void parseTableBlocksSplitOnNonTableLine() {
        String md = """
                | A | B |
                |---|---|

                Absatz dazwischen

                | X | Y |
                """;
        var blocks = MarkdownBlockSupport.parseTableBlocks(md);
        assertEquals(2, blocks.size());
    }
}
