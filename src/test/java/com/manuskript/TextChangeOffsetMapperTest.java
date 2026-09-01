package com.manuskript;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextChangeOffsetMapperTest {

    @Test
    void mapOffsetAfterSuffixEditShiftsCaret() {
        String before = "aaaaBBBBcccc";
        String after = "aaaaBBcccc";
        assertEquals(10, TextChangeOffsetMapper.mapOffsetThroughTextChange(before, after, 12));
    }

    @Test
    void mapOffsetBeforeEditUnchanged() {
        String before = "aaaaBBBBcccc";
        String after = "aaaaBBcccc";
        assertEquals(2, TextChangeOffsetMapper.mapOffsetThroughTextChange(before, after, 2));
    }

    @Test
    void mapReadingAnchorNotCaretAfterMiddleReplace() {
        String before = "111111111122222222223333333333";
        String after = "1111111111REPLACED3333333333";
        assertEquals(0, TextChangeOffsetMapper.mapOffsetThroughTextChange(before, after, 0));
        assertEquals(23, TextChangeOffsetMapper.mapOffsetThroughTextChange(before, after, 25));
    }

    @Test
    void mapOffsetThroughRangeReplaceShiftsAfterInsertion() {
        int markerStart = 10;
        int markerEnd = 18;
        int inserted = 120;
        assertEquals(2, TextChangeOffsetMapper.mapOffsetThroughRangeReplace(2, markerStart, markerEnd, inserted));
        assertEquals(markerStart, TextChangeOffsetMapper.mapOffsetThroughRangeReplace(12, markerStart, markerEnd, inserted));
        assertEquals(25 + inserted - (markerEnd - markerStart),
                TextChangeOffsetMapper.mapOffsetThroughRangeReplace(25, markerStart, markerEnd, inserted));
    }
}
