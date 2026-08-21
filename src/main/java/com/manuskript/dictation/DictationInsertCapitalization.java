package com.manuskript.dictation;

/**
 * Passt die Großschreibung am Anfang eines Diktat-Einschubs an den umgebenden Satz an.
 */
final class DictationInsertCapitalization {

    private DictationInsertCapitalization() {
    }

    /**
     * Macht den ersten Buchstaben klein, wenn vor der Einfügestelle bereits ein Satz läuft.
     * Satzanfang (Dokumentbeginn, neuer Absatz, nach {@code .?!} oder {@code :}, nach öffnendem Anführungszeichen)
     * bleibt groß.
     */
    static String adjustLeadingCapital(String insert, String textBefore) {
        if (insert == null || insert.isEmpty() || !sentenceAlreadyStarted(textBefore)) {
            return insert;
        }
        return lowercaseFirstLetter(insert);
    }

    static boolean sentenceAlreadyStarted(String textBefore) {
        if (textBefore == null || textBefore.isEmpty()) {
            return false;
        }
        int i = textBefore.length() - 1;
        while (i >= 0 && isHorizontalWhitespace(textBefore.charAt(i))) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        char c = textBefore.charAt(i);
        if (c == '\n' || c == '\r') {
            return false;
        }
        if (c == '.' || c == '!' || c == '?' || c == ':') {
            return false;
        }
        return !isOpeningQuote(c);
    }

    static String lowercaseFirstLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isOpeningQuote(c)) {
                return text;
            }
            if (Character.isLetter(c)) {
                char lower = Character.toLowerCase(c);
                if (lower == c) {
                    return text;
                }
                return text.substring(0, i) + lower + text.substring(i + 1);
            }
            if (!isSkippablePrefix(c)) {
                return text;
            }
        }
        return text;
    }

    private static boolean isHorizontalWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\u00a0';
    }

    private static boolean isSkippablePrefix(char c) {
        return isHorizontalWhitespace(c) || c == '*' || c == '_';
    }

    private static boolean isOpeningQuote(char c) {
        return c == '„' || c == '‚' || c == '»' || c == '«'
                || c == '"' || c == '\'' || c == '\u201c' || c == '\u2018';
    }
}
