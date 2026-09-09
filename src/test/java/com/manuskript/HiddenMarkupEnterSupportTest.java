package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HiddenMarkupEnterSupportTest {

    @Test
    void enterAtVisualStartOfItalicMovesBeforeOpeningMarker() {
        // *huhu*  Caret visuell vor h, logisch bei Offset 1
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertEquals(0, HiddenMarkupEnterSupport.insertionOffset(1, 0, 6, hidden, zones));
    }

    @Test
    void enterAlreadyBeforeOpeningMarkerStays() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertEquals(0, HiddenMarkupEnterSupport.insertionOffset(0, 0, 6, hidden, zones));
    }

    @Test
    void enterInMiddleOfItalicDoesNotMove() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertEquals(3, HiddenMarkupEnterSupport.insertionOffset(3, 0, 6, hidden, zones));
    }

    @Test
    void enterAtVisualEndOfItalicMovesAfterClosingMarker() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertEquals(6, HiddenMarkupEnterSupport.insertionOffset(5, 0, 6, hidden, zones));
    }

    @Test
    void enterAtStartOfItalicWordAfterPlainText() {
        // hello *huhu*
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(6, 7), span(11, 12));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(6, 12));
        assertEquals(6, HiddenMarkupEnterSupport.insertionOffset(7, 0, 12, hidden, zones));
    }

    @Test
    void enterDoesNotStealClosingMarkupOfPreviousSpan() {
        // *foo* *bar*  Caret visuell vor b (Offset 7)
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(
                span(0, 1), span(4, 5), span(6, 7), span(10, 11));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 5), span(6, 11));
        assertEquals(6, HiddenMarkupEnterSupport.insertionOffset(7, 0, 11, hidden, zones));
    }

    @Test
    void enterPullsStackedOpeningMarkers() {
        // <u>*huhu*</u>
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(
                span(0, 3), span(3, 4), span(8, 9), span(9, 13));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 13), span(3, 9));
        assertEquals(0, HiddenMarkupEnterSupport.insertionOffset(4, 0, 13, hidden, zones));
    }

    @Test
    void enterPullsBoldMarkers() {
        // **fett**
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 2), span(6, 8));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 8));
        assertEquals(0, HiddenMarkupEnterSupport.insertionOffset(2, 0, 8, hidden, zones));
    }

    @Test
    void enterDoesNotCrossLineStart() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertEquals(4, HiddenMarkupEnterSupport.insertionOffset(4, 4, 6, hidden, zones));
    }

    @Test
    void footnoteOpeningBracketIsNotPulledBecauseZoneStartsAtCaret() {
        // ^[hello]  Zone [0,8), hidden [1,2) und [7,8), Inhalt ab 2
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(1, 2), span(7, 8));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 8));
        assertEquals(2, HiddenMarkupEnterSupport.insertionOffset(2, 0, 8, hidden, zones));
    }

    private static HiddenMarkupEnterSupport.Span span(int start, int end) {
        return new HiddenMarkupEnterSupport.Span(start, end);
    }
}
