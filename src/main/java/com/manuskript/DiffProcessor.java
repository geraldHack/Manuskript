package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verarbeitet Diff-Operationen zwischen DOCX-Dateien und Sidecar-Dateien
 */
public class DiffProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DiffProcessor.class);
    
    /**
     * Berechnet CRC32-Hash einer Datei
     */
    public static long calculateFileHash(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }
        
        try (CheckedInputStream cis = new CheckedInputStream(new FileInputStream(file), new CRC32())) {
            byte[] buffer = new byte[8192];
            while (cis.read(buffer) != -1) {
                // Lese die gesamte Datei
            }
            return cis.getChecksum().getValue();
        } catch (IOException e) {
            logger.error("Fehler beim Berechnen des Datei-Hashs: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Prüft ob eine DOCX-Datei seit der letzten Verarbeitung geändert wurde
     */
    public static boolean hasDocxChanged(File docxFile, File sidecarFile) {
        System.out.println("=== DEBUG: hasDocxChanged() aufgerufen ===");
        System.out.println("=== DEBUG: docxFile = " + (docxFile != null ? docxFile.getAbsolutePath() : "null") + " ===");
        System.out.println("=== DEBUG: sidecarFile = " + (sidecarFile != null ? sidecarFile.getAbsolutePath() : "null") + " ===");
        
        if (docxFile == null || !docxFile.exists()) {
            System.out.println("=== DEBUG: docxFile ist null oder existiert nicht ===");
            return false;
        }
        
        // Wenn keine Sidecar-Datei existiert, ist die DOCX "neu" - aber nur wenn auch .meta fehlt
        if (sidecarFile == null || !sidecarFile.exists()) {
            // Prüfe ob .meta Datei existiert
            File metaFile = new File(docxFile.getParent(), docxFile.getName() + ".meta");
            if (!metaFile.exists()) {
                return true; // Keine .meta Datei = DOCX wurde noch nie verarbeitet
            }
            return false; // .meta existiert, aber keine MD - das ist normal
        }
        
        // Lade gespeicherten Hash aus Sidecar-Metadaten
        long savedHash = loadSavedHash(sidecarFile);
        long currentHash = calculateFileHash(docxFile);
        
        System.out.println("=== DEBUG: savedHash = " + savedHash + " ===");
        System.out.println("=== DEBUG: currentHash = " + currentHash + " ===");
        
        // Wenn kein gespeicherter Hash existiert (savedHash = -1), betrachte DOCX als geändert
        boolean result = (savedHash == -1) || (savedHash != -1 && currentHash != -1 && savedHash != currentHash);
        System.out.println("=== DEBUG: hasDocxChanged() gibt " + result + " zurück ===");
        
        return result;
    }
    
    /**
     * Lädt den gespeicherten Hash aus Sidecar-Metadaten
     */
    private static long loadSavedHash(File sidecarFile) {
        System.out.println("=== DEBUG: loadSavedHash() aufgerufen ===");
        System.out.println("=== DEBUG: sidecarFile = " + sidecarFile.getAbsolutePath() + " ===");
        
        try {
            // Suche nach DOCX-basierter .meta Datei, nicht MD-basierter
            String docxFileName = sidecarFile.getName().replace(".md", ".docx");
            Path metadataPath = sidecarFile.toPath().resolveSibling(docxFileName + ".meta");
            System.out.println("=== DEBUG: metadataPath = " + metadataPath + " ===");
            System.out.println("=== DEBUG: metadataPath.exists() = " + Files.exists(metadataPath) + " ===");
            
            if (Files.exists(metadataPath)) {
                String content = Files.readString(metadataPath);
                System.out.println("=== DEBUG: .meta Datei Inhalt = " + content + " ===");
                
                // Suche nach Hash-Zeile
                Pattern hashPattern = Pattern.compile("docx_hash=([0-9]+)");
                Matcher matcher = hashPattern.matcher(content);
                if (matcher.find()) {
                    long hash = Long.parseLong(matcher.group(1));
                    System.out.println("=== DEBUG: Hash gefunden = " + hash + " ===");
                    return hash;
                } else {
                    System.out.println("=== DEBUG: Kein Hash-Pattern gefunden ===");
                    
                    // Suche nach reinem Hash-Wert (Format: 123456789 oder e275e583)
                    Pattern pureHashPattern = Pattern.compile("^([0-9a-fA-F]+)$");
                    matcher = pureHashPattern.matcher(content.trim());
                    if (matcher.find()) {
                        try {
                            long hash = Long.parseLong(matcher.group(1), 16); // Hexadezimal
                            System.out.println("=== DEBUG: Hex-Hash gefunden = " + hash + " ===");
                            return hash;
                        } catch (NumberFormatException e) {
                            try {
                                long hash = Long.parseLong(matcher.group(1)); // Dezimal
                                System.out.println("=== DEBUG: Dezimal-Hash gefunden = " + hash + " ===");
                                return hash;
                            } catch (NumberFormatException e2) {
                                System.out.println("=== DEBUG: Hash-Wert kann nicht geparst werden: " + matcher.group(1) + " ===");
                            }
                        }
                    } else {
                        System.out.println("=== DEBUG: Auch kein reiner Hash-Wert gefunden ===");
                    }
                }
            } else {
                System.out.println("=== DEBUG: .meta Datei existiert nicht ===");
            }
        } catch (Exception e) {
            System.out.println("=== DEBUG: Exception in loadSavedHash: " + e.getMessage() + " ===");
            logger.warn("Fehler beim Laden des gespeicherten Hashs: {}", e.getMessage());
        }
        System.out.println("=== DEBUG: loadSavedHash() gibt -1 zurück ===");
        return -1;
    }
    
    /**
     * Speichert den Hash einer DOCX-Datei in Sidecar-Metadaten
     */
    public static void saveDocxHash(File docxFile, File sidecarFile) {
        if (docxFile == null || sidecarFile == null) {
            return;
        }
        
        try {
            long hash = calculateFileHash(docxFile);
            Path metadataPath = sidecarFile.toPath().resolveSibling(sidecarFile.getName() + ".meta");
            
            String metadata = String.format("docx_hash=%d\ndocx_path=%s\nlast_updated=%d\n", 
                hash, docxFile.getAbsolutePath(), System.currentTimeMillis());
            
            Files.writeString(metadataPath, metadata);
            logger.info("DOCX-Hash gespeichert: {} -> {}", docxFile.getName(), hash);
            
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des DOCX-Hashs: {}", e.getMessage());
        }
    }
    
    /**
     * Erstellt ein Diff zwischen zwei Texten
     */
    public static DiffResult createDiff(String originalText, String newText) {
        DiffResult result = new DiffResult();
        
        if (originalText == null) originalText = "";
        if (newText == null) newText = "";
        
        // Echter Myers-Diff-Algorithmus für intelligente Block-Erkennung
        String[] originalLines = originalText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);
        
        List<DiffLine> diffLines = new ArrayList<>();
        
        // Myers-Diff-Algorithmus implementieren
        int[][] lcs = computeLCS(originalLines, newLines);
        List<DiffOperation> operations = backtrackLCS(originalLines, newLines, lcs);
        
        int originalIndex = 0;
        int newIndex = 0;
        int lineNumber = 1;
        
        for (DiffOperation op : operations) {
            switch (op.getType()) {
                case KEEP:
                    // Beide Zeilen sind gleich
                    diffLines.add(new DiffLine(lineNumber, DiffType.UNCHANGED, 
                        originalLines[originalIndex], newLines[newIndex]));
                    originalIndex++;
                    newIndex++;
                    lineNumber++;
                    break;
                    
                case DELETE:
                    // Zeile wurde gelöscht
                    diffLines.add(new DiffLine(lineNumber, DiffType.DELETED, 
                        originalLines[originalIndex], ""));
                    originalIndex++;
                    lineNumber++;
                    break;
                    
                case INSERT:
                    // Zeile wurde hinzugefügt
                    diffLines.add(new DiffLine(lineNumber, DiffType.ADDED, 
                        "", newLines[newIndex]));
                    newIndex++;
                    lineNumber++;
                    break;
            }
        }
        
        result.setDiffLines(diffLines);
        result.setHasChanges(!originalText.equals(newText));
        
        return result;
    }
    

    
    /**
     * Berechnet die Longest Common Subsequence (LCS) Matrix
     */
    private static int[][] computeLCS(String[] original, String[] modified) {
        int m = original.length;
        int n = modified.length;
        int[][] lcs = new int[m + 1][n + 1];
        
        // LCS-Matrix aufbauen
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (original[i - 1].equals(modified[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }

            }
        }
        
        return lcs;
    }
    
    /**
     * Backtracking um die Diff-Operationen zu finden
     */
    private static List<DiffOperation> backtrackLCS(String[] original, String[] modified, int[][] lcs) {
        List<DiffOperation> operations = new ArrayList<>();
        int i = original.length;
        int j = modified.length;
        
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && original[i - 1].equals(modified[j - 1])) {
                // Gleiche Zeile - KEEP
                operations.add(0, new DiffOperation(DiffOpType.KEEP));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                // Zeile wurde hinzugefügt - INSERT
                operations.add(0, new DiffOperation(DiffOpType.INSERT));
                j--;
            } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                // Zeile wurde gelöscht - DELETE
                operations.add(0, new DiffOperation(DiffOpType.DELETE));
                i--;
            }
        }
        
        return operations;
    }
    
    /**
     * Diff-Operation
     */
    private static class DiffOperation {
        private final DiffOpType type;
        
        public DiffOperation(DiffOpType type) {
            this.type = type;
        }
        
        public DiffOpType getType() {
            return type;
        }
    }
    
    /**
     * Diff-Operation-Typen
     */
    private enum DiffOpType {
        KEEP, DELETE, INSERT
    }
    
    /**
     * Erstellt HTML-Diff-Output
     */
    public static String createHtmlDiff(DiffResult diffResult) {
        if (diffResult == null || diffResult.getDiffLines().isEmpty()) {
            return "<p>Keine Änderungen gefunden.</p>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class='diff-container'>\n");
        html.append("<style>\n");
        html.append(".diff-line { font-family: monospace; white-space: pre; margin: 0; padding: 2px 0; }\n");
        html.append(".diff-added { background-color: #e6ffe6; color: #006600; }\n");
        html.append(".diff-deleted { background-color: #ffe6e6; color: #cc0000; }\n");
        html.append(".diff-unchanged { background-color: #f8f8f8; }\n");
        html.append(".line-number { color: #888; margin-right: 10px; }\n");
        html.append("</style>\n");
        
        for (DiffLine line : diffResult.getDiffLines()) {
            String cssClass = "";
            switch (line.getType()) {
                case ADDED:
                    cssClass = "diff-added";
                    break;
                case DELETED:
                    cssClass = "diff-deleted";
                    break;
                case UNCHANGED:
                    cssClass = "diff-unchanged";
                    break;
            }
            
            html.append("<div class='diff-line ").append(cssClass).append("'>");
            html.append("<span class='line-number'>").append(line.getLineNumber()).append("</span>");
            
            if (line.getType() == DiffType.DELETED) {
                html.append("- ").append(escapeHtml(line.getOriginalText()));
            } else if (line.getType() == DiffType.ADDED) {
                html.append("+ ").append(escapeHtml(line.getNewText()));
            } else {
                html.append("  ").append(escapeHtml(line.getOriginalText()));
            }
            
            html.append("</div>\n");
        }
        
        html.append("</div>");
        return html.toString();
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    /**
     * Diff-Linie
     */
    public static class DiffLine {
        private final int lineNumber;
        private final DiffType type;
        private final String originalText;
        private final String newText;
        
        public DiffLine(int lineNumber, DiffType type, String originalText, String newText) {
            this.lineNumber = lineNumber;
            this.type = type;
            this.originalText = originalText;
            this.newText = newText;
        }
        
        public int getLineNumber() { return lineNumber; }
        public DiffType getType() { return type; }
        public String getOriginalText() { return originalText; }
        public String getNewText() { return newText; }
    }
    
    /**
     * Diff-Typ
     */
    public enum DiffType {
        ADDED, DELETED, UNCHANGED
    }
    
    /**
     * Diff-Ergebnis
     */
    public static class DiffResult {
        private List<DiffLine> diffLines = new ArrayList<>();
        private boolean hasChanges = false;
        
        public List<DiffLine> getDiffLines() { return diffLines; }
        public void setDiffLines(List<DiffLine> diffLines) { this.diffLines = diffLines; }
        public boolean hasChanges() { return hasChanges; }
        public void setHasChanges(boolean hasChanges) { this.hasChanges = hasChanges; }
    }
}

