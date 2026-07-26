package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GedankenstrichSupportTest {

    @Test
    void convertible_findsDoubleHyphen() {
        assertEquals(4, GedankenstrichSupport.convertibleDoubleHyphenStart("Wort--", 6));
    }

    @Test
    void convertible_rejectsTripleHyphen() {
        assertEquals(-1, GedankenstrichSupport.convertibleDoubleHyphenStart("Wort---", 7));
        assertEquals(-1, GedankenstrichSupport.convertibleDoubleHyphenStart("---", 3));
    }

    @Test
    void convertible_rejectsWhenNextCharIsHyphen() {
        assertEquals(-1, GedankenstrichSupport.convertibleDoubleHyphenStart("ab---", 4));
    }

    @Test
    void convertible_rejectsHorizontalRuleLine() {
        assertEquals(-1, GedankenstrichSupport.convertibleDoubleHyphenStart("---\n", 3));
    }

    @Test
    void convertible_allowsDoubleInProse() {
        assertEquals(5, GedankenstrichSupport.convertibleDoubleHyphenStart("Hallo-- Welt", 7));
    }
}
