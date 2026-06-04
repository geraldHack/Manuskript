package com.manuskript;

/**
 * Sicheres Ersetzen eines Textbereichs im Kapitel-Editor.
 */
final class ChapterRewriteReplaceHelper {

    private ChapterRewriteReplaceHelper() {
    }

    static boolean replaceIfUnchanged(ChapterEditorHost host, int startPos, int endPos,
                                    String originalSnippet, String replacement, String successMessage) {
        if (host == null) {
            return false;
        }
        String current = host.getText();
        if (current == null) {
            host.updateStatusError("Kein Text geladen.");
            return false;
        }
        int safeStart = Math.max(0, Math.min(current.length(), startPos));
        int safeEnd = Math.max(safeStart, Math.min(current.length(), endPos));
        if (safeStart < safeEnd) {
            String atPos = current.substring(safeStart, safeEnd);
            if (!atPos.trim().equals(originalSnippet == null ? "" : originalSnippet.trim())) {
                host.updateStatus("Text hat sich geändert; Ersetzung abgebrochen.");
                return false;
            }
        }
        host.replaceRange(safeStart, safeEnd, replacement == null ? "" : replacement);
        host.updateStatus(successMessage);
        return true;
    }
}
