package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LektoratMatchLocatorTest {

    @Test
    void locateSequential_findsSecondOccurrenceAfterFirst() {
        String text = "foo bar foo baz foo end";
        assertEquals(0, LektoratMatchLocator.locateSequential(text, "foo", 0));
        assertEquals(8, LektoratMatchLocator.locateSequential(text, "foo", 3));
        assertEquals(16, LektoratMatchLocator.locateSequential(text, "foo", 11));
    }

    @Test
    void resolveAllInPlace_usesHintForDuplicates() {
        String text = "Er sagte hallo. Später hallo wieder.";
        LektoratMatch first = new LektoratMatch(9, 5, "hallo", List.of("Hi"), "1", 3);
        LektoratMatch second = new LektoratMatch(23, 5, "hallo", List.of("Hi"), "2", 3);
        List<LektoratMatch> matches = new ArrayList<>(List.of(first, second));

        LektoratMatchLocator.resolveAllInPlace(text, matches);

        assertEquals(9, first.getOffset());
        assertEquals(23, second.getOffset());
    }

    @Test
    void resolveSpan_prefersNearestNonOverlappingOccurrence() {
        String text = "abc abc abc";
        LektoratMatch match = new LektoratMatch(10, 3, "abc", List.of("x"), "", 3);
        int[] span = LektoratMatchLocator.resolveSpan(text, match, List.of(new int[]{0, 7}));
        assertNotNull(span);
        assertEquals(8, span[0]);
        assertEquals(11, span[1]);
    }

    @Test
    void resolveSpan_returnsNullWhenOriginalMissing() {
        LektoratMatch match = new LektoratMatch(0, 3, "fehlt", List.of("x"), "", 3);
        assertNull(LektoratMatchLocator.resolveSpan("nur Text", match));
    }

    @Test
    void shiftAfterTextChange_movesMatchesAfterInsert() {
        LektoratMatch before = new LektoratMatch(0, 5, "Hallo", List.of("Hi"), "", 3);
        LektoratMatch after = new LektoratMatch(6, 4, "Welt", List.of("World"), "", 3);
        List<LektoratMatch> matches = new ArrayList<>(List.of(before, after));

        boolean removed = LektoratMatchLocator.shiftAfterTextChange(
                "Hallo Welt", "XHallo Welt", matches);

        assertEquals(false, removed);
        assertEquals(1, before.getOffset());
        assertEquals(7, after.getOffset());
    }

    @Test
    void shiftAfterTextChange_keepsMatchesBeforeInsert() {
        LektoratMatch before = new LektoratMatch(0, 5, "Hallo", List.of("Hi"), "", 3);
        List<LektoratMatch> matches = new ArrayList<>(List.of(before));

        boolean removed = LektoratMatchLocator.shiftAfterTextChange(
                "Hallo Welt", "Hallo XWelt", matches);

        assertEquals(false, removed);
        assertEquals(0, before.getOffset());
    }

    @Test
    void shiftAfterTextChange_removesOverlappingMatch() {
        LektoratMatch match = new LektoratMatch(6, 4, "Welt", List.of("World"), "", 3);
        LektoratMatch later = new LektoratMatch(11, 3, "end", List.of("Ende"), "", 3);
        List<LektoratMatch> matches = new ArrayList<>(List.of(match, later));

        boolean removed = LektoratMatchLocator.shiftAfterTextChange(
                "Hallo Welt end", "Hallo WELT end", matches);

        assertEquals(true, removed);
        assertEquals(1, matches.size());
        assertEquals(later, matches.get(0));
        assertEquals(11, later.getOffset());
    }

    @Test
    void shiftAfterTextChange_shiftsAfterDelete() {
        LektoratMatch match = new LektoratMatch(6, 4, "Welt", List.of("World"), "", 3);
        List<LektoratMatch> matches = new ArrayList<>(List.of(match));

        boolean removed = LektoratMatchLocator.shiftAfterTextChange(
                "Hallo Welt", "Welt", matches);

        assertEquals(false, removed);
        assertEquals(0, match.getOffset());
    }

    @Test
    void nextAfterCaret_picksFirstMatchAfterCursor() {
        LektoratMatch first = new LektoratMatch(5, 3, "aaa", List.of("x"), "", 3);
        LektoratMatch second = new LektoratMatch(20, 3, "bbb", List.of("y"), "", 3);
        LektoratMatch third = new LektoratMatch(40, 3, "ccc", List.of("z"), "", 3);
        List<LektoratMatch> matches = List.of(third, first, second);

        assertEquals(second, LektoratMatchLocator.nextAfterCaret(matches, 5));
        assertEquals(second, LektoratMatchLocator.nextAfterCaret(matches, 10));
        assertEquals(third, LektoratMatchLocator.nextAfterCaret(matches, 20));
    }

    @Test
    void nextAfterCaret_wrapsToFirstWhenPastLast() {
        LektoratMatch first = new LektoratMatch(5, 3, "aaa", List.of("x"), "", 3);
        LektoratMatch last = new LektoratMatch(40, 3, "ccc", List.of("z"), "", 3);

        assertEquals(first, LektoratMatchLocator.nextAfterCaret(List.of(first, last), 40));
        assertEquals(first, LektoratMatchLocator.nextAfterCaret(List.of(first, last), 80));
    }

    @Test
    void nextAfterCaret_returnsNullForEmpty() {
        assertNull(LektoratMatchLocator.nextAfterCaret(List.of(), 0));
    }
}
