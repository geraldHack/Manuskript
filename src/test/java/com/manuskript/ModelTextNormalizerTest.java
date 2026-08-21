package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelTextNormalizerTest {

    @Test
    void decodesJsonStyleEscapes() {
        // Backslash + "u003c" (Java-Quelltext darf "\\u003c" nicht wörtlich enthalten)
        String escaped = "\\" + "u003cQUESTION\\" + "u003eHallo\\" + "u003c/QUESTION\\" + "u003e";
        assertEquals("<QUESTION>Hallo</QUESTION>", ModelTextNormalizer.normalize(escaped));
    }

    @Test
    void decodesBareUnicodeQuirk() {
        assertEquals("<CONTENT>äöü</CONTENT>",
                ModelTextNormalizer.normalize(
                        "u003cCONTENTu003eu00e4u00f6u00fcu003c/CONTENTu003e"));
    }

    @Test
    void leavesNormalGermanAlone() {
        assertEquals("Der Junge ging nach Hause.",
                ModelTextNormalizer.normalize("Der Junge ging nach Hause."));
    }

    @Test
    void nullSafe() {
        assertNull(ModelTextNormalizer.normalize(null));
        assertEquals("", ModelTextNormalizer.normalize(""));
    }
}
