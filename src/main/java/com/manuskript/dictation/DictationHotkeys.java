package com.manuskript.dictation;

import javafx.scene.input.KeyCode;

/**
 * Push-to-talk nur bei F9/F10 oder wirklich gedrückter Option+Leertaste.
 * Ein „sticky Alt“ nach Option+Umlaut darf normales Leerzeichen nicht schlucken.
 */
final class DictationHotkeys {

    private DictationHotkeys() {
    }

    static boolean isPushToTalkPress(KeyCode code, boolean altDown, boolean metaDown, boolean controlDown,
                                     boolean mac) {
        if (code == KeyCode.F9 || code == KeyCode.F10) {
            return true;
        }
        return isOptionSpace(code, altDown, metaDown, controlDown, mac);
    }

    static boolean isOptionSpace(KeyCode code, boolean altDown, boolean metaDown, boolean controlDown, boolean mac) {
        if (!mac || code != KeyCode.SPACE) {
            return false;
        }
        return altDown && !metaDown && !controlDown;
    }

    static boolean isPushToTalkRelease(KeyCode code, boolean spacePushToTalkActive) {
        if (code == KeyCode.F9 || code == KeyCode.F10) {
            return true;
        }
        if (!spacePushToTalkActive) {
            return false;
        }
        return code == KeyCode.SPACE || code == KeyCode.ALT;
    }
}
