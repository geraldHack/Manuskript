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
    void parseTableAndCodeFence() {
        String md = """
                | A | B |
                |---|---|
                | 1 | 2 |
                ```java
                int x = 1;
                ```
                """;
        assertEquals(3, MarkdownBlockSupport.parseTableLines(md).size());
        var fences = MarkdownBlockSupport.parseCodeFences(md);
        assertEquals(1, fences.size());
        assertTrue(fences.get(0).contentEnd() > fences.get(0).contentStart());
    }
}
