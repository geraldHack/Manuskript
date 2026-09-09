package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HiddenMarkupDeleteSupportTest {

    @Test
    void deleteAtEndOfItalicSkipsClosingMarkerAndDeletesNextChar() {
        // *huhu*X  Caret auf schließendem *
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertArrayEquals(new int[]{6, 7}, HiddenMarkupDeleteSupport.forwardRange(5, 7, hidden, zones));
    }

    @Test
    void deleteAtEndOfItalicWithNothingAfterIsNoOp() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertNull(HiddenMarkupDeleteSupport.forwardRange(5, 6, hidden, zones));
    }

    @Test
    void deleteAtVisualStartRemovesVisibleLetter() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertArrayEquals(new int[]{1, 2}, HiddenMarkupDeleteSupport.forwardRange(1, 6, hidden, zones));
    }

    @Test
    void deleteOnOpeningMarkerSkipsItAndRemovesLetter() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertArrayEquals(new int[]{1, 2}, HiddenMarkupDeleteSupport.forwardRange(0, 6, hidden, zones));
    }

    @Test
    void deleteLastVisibleCharRemovesEmptyItalicWrappers() {
        // *h*
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(2, 3));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 3));
        assertArrayEquals(new int[]{0, 3}, HiddenMarkupDeleteSupport.forwardRange(1, 3, hidden, zones));
    }

    @Test
    void deleteLastVisibleCharRemovesStackedWrappers() {
        // <u>*h*</u>
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(
                span(0, 3), span(3, 4), span(5, 6), span(6, 10));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 10), span(3, 6));
        assertArrayEquals(new int[]{0, 10}, HiddenMarkupDeleteSupport.forwardRange(4, 10, hidden, zones));
    }

    @Test
    void backspaceAtVisualStartSkipsOpeningMarkerAndDeletesPreviousChar() {
        // X*huhu*
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(1, 2), span(6, 7));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(1, 7));
        assertArrayEquals(new int[]{0, 1}, HiddenMarkupDeleteSupport.backwardRange(2, hidden, zones));
    }

    @Test
    void backspaceAtStartOfDocumentItalicIsNoOp() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertNull(HiddenMarkupDeleteSupport.backwardRange(1, hidden, zones));
    }

    @Test
    void backspaceAfterItalicDeletesLastVisibleLetter() {
        // *huhu*  Caret hinter dem Wort (Offset 6)
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(5, 6));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 6));
        assertArrayEquals(new int[]{4, 5}, HiddenMarkupDeleteSupport.backwardRange(6, hidden, zones));
    }

    @Test
    void backspaceLastVisibleCharRemovesEmptyItalicWrappers() {
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(2, 3));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 3));
        assertArrayEquals(new int[]{0, 3}, HiddenMarkupDeleteSupport.backwardRange(2, hidden, zones));
    }

    @Test
    void deleteInMiddleOfLinkTextKeepsUrl() {
        // [ab](u)
        List<HiddenMarkupEnterSupport.Span> hidden = List.of(span(0, 1), span(3, 7));
        List<HiddenMarkupEnterSupport.Span> zones = List.of(span(0, 7));
        assertArrayEquals(new int[]{1, 2}, HiddenMarkupDeleteSupport.forwardRange(1, 7, hidden, zones));
    }

    private static HiddenMarkupEnterSupport.Span span(int start, int end) {
        return new HiddenMarkupEnterSupport.Span(start, end);
    }
}
