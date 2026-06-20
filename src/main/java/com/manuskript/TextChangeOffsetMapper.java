package com.manuskript;

/**
 * Mappt Textoffsets nach einer Ersetzung (Caret/Leseanker beim Agenten-Ersetzen).
 */
public final class TextChangeOffsetMapper {

    private TextChangeOffsetMapper() {
    }

    public static int mapOffsetThroughTextChange(String before, String after, int offset) {
        if (before == null || after == null) {
            return Math.max(0, offset);
        }
        if (before.equals(after)) {
            return Math.max(0, Math.min(offset, after.length()));
        }
        int beforeLength = before.length();
        int afterLength = after.length();
        int safeOffset = Math.max(0, Math.min(offset, beforeLength));
        if (safeOffset == 0) {
            return 0;
        }
        if (safeOffset >= beforeLength) {
            return afterLength;
        }

        int prefix = 0;
        int prefixLimit = Math.min(Math.min(safeOffset, beforeLength), afterLength);
        while (prefix < prefixLimit && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        if (safeOffset <= prefix) {
            return safeOffset;
        }

        int suffix = 0;
        while (suffix < beforeLength - prefix
                && suffix < afterLength - prefix
                && before.charAt(beforeLength - 1 - suffix) == after.charAt(afterLength - 1 - suffix)) {
            suffix++;
        }

        int beforeMiddleLength = beforeLength - prefix - suffix;
        int afterMiddleLength = afterLength - prefix - suffix;
        int offsetInMiddle = safeOffset - prefix;
        if (offsetInMiddle >= beforeMiddleLength) {
            return afterLength - (beforeLength - safeOffset);
        }
        if (beforeMiddleLength <= 0) {
            return prefix;
        }
        int mappedMiddle = (int) Math.round(offsetInMiddle * (afterMiddleLength / (double) beforeMiddleLength));
        return prefix + Math.max(0, Math.min(afterMiddleLength, mappedMiddle));
    }
}
