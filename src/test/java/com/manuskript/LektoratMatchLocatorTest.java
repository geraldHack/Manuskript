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
}
