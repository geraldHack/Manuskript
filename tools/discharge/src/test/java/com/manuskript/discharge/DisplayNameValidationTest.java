package com.manuskript.discharge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisplayNameValidationTest {

    @Test
    void acceptsLettersAndDigits() {
        assertNull(DischargeWindow.validateDisplayName("Gerald"));
        assertNull(DischargeWindow.validateDisplayName("Anna42"));
        assertNull(DischargeWindow.validateDisplayName("Müller"));
    }

    @Test
    void rejectsDotSuffixAndJunk() {
        assertNotNull(DischargeWindow.validateDisplayName("Gerald.4711"));
        assertNotNull(DischargeWindow.validateDisplayName("Gerald."));
        assertNotNull(DischargeWindow.validateDisplayName("Anna Maria"));
        assertNotNull(DischargeWindow.validateDisplayName("Bob!"));
        assertNotNull(DischargeWindow.validateDisplayName(""));
        assertNotNull(DischargeWindow.validateDisplayName("A"));
    }
}
