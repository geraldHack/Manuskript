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
}
