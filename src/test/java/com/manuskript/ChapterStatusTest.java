package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChapterStatusTest {

    @Test
    void missingOrUnknownIdFallsBackToInArbeit() {
        assertEquals(ChapterStatus.IN_ARBEIT, ChapterStatus.fromId(null));
        assertEquals(ChapterStatus.IN_ARBEIT, ChapterStatus.fromId(""));
        assertEquals(ChapterStatus.IN_ARBEIT, ChapterStatus.fromId("unbekannt"));
    }

    @Test
    void knownIdsRoundTrip() {
        for (ChapterStatus status : ChapterStatus.values()) {
            assertEquals(status, ChapterStatus.fromId(status.id()));
        }
    }
}
