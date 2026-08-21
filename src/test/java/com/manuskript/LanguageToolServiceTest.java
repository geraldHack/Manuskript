package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LanguageToolServiceTest {

    @Test
    void nextAfterCaret_picksFirstMatchAfterCursor() {
        LanguageToolService.Match first = matchAt(5);
        LanguageToolService.Match second = matchAt(20);
        LanguageToolService.Match third = matchAt(40);
        List<LanguageToolService.Match> matches = List.of(third, first, second);

        assertEquals(second, LanguageToolService.nextAfterCaret(matches, 5));
        assertEquals(second, LanguageToolService.nextAfterCaret(matches, 10));
        assertEquals(third, LanguageToolService.nextAfterCaret(matches, 20));
    }

    @Test
    void nextAfterCaret_wrapsToFirstWhenPastLast() {
        LanguageToolService.Match first = matchAt(5);
        LanguageToolService.Match last = matchAt(40);

        assertEquals(first, LanguageToolService.nextAfterCaret(List.of(first, last), 40));
        assertEquals(first, LanguageToolService.nextAfterCaret(List.of(first, last), 80));
    }

    @Test
    void nextAfterCaret_returnsNullForEmpty() {
        assertNull(LanguageToolService.nextAfterCaret(List.of(), 0));
    }

    private static LanguageToolService.Match matchAt(int offset) {
        LanguageToolService.Match match = new LanguageToolService.Match();
        match.setOffset(offset);
        match.setLength(3);
        return match;
    }
}
