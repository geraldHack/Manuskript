package com.manuskript.dictation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class DictationHotkeysTest {

    @Test
    void plainSpaceDoesNotStartDictation() {
        assertFalse(DictationHotkeys.isPushToTalkPress(KeyCode.SPACE, false, false, false, true));
    }

    @Test
    void optionSpaceStartsDictationOnMac() {
        assertTrue(DictationHotkeys.isPushToTalkPress(KeyCode.SPACE, true, false, false, true));
    }

    @Test
    void optionSpaceIgnoredOnWindows() {
        assertFalse(DictationHotkeys.isPushToTalkPress(KeyCode.SPACE, true, false, false, false));
    }

    @Test
    void commandSpaceDoesNotStartDictation() {
        assertFalse(DictationHotkeys.isPushToTalkPress(KeyCode.SPACE, true, true, false, true));
    }

    @Test
    void f9StartsDictation() {
        assertTrue(DictationHotkeys.isPushToTalkPress(KeyCode.F9, false, false, false, true));
    }
}
