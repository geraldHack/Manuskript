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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verarbeitet Diff-Operationen zwischen DOCX-Dateien und Sidecar-Dateien
 */
public class DiffProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DiffProcessor.class);
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);
    
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
     * Berechnet CRC32-Hash einer Datei (asynchron)
     */
    public static CompletableFuture<Long> calculateFileHashAsync(File file) {
        return CompletableFuture.supplyAsync(() -> {
            if (file == null || !file.exists()) {
                return -1L;
            }
            
            try (CheckedInputStream cis = new CheckedInputStream(new FileInputStream(file), new CRC32())) {
                byte[] buffer = new byte[8192];
                while (cis.read(buffer) != -1) {
                    // Lese die gesamte Datei
                }
                return cis.getChecksum().getValue();
            } catch (IOException e) {
                logger.error("Fehler beim Berechnen des Datei-Hashs: {}", e.getMessage());
                return -1L;
            }
        }, backgroundExecutor);
    }
    
    
    /**
     * Prüft ob eine DOCX-Datei seit der letzten Verarbeitung geändert wurde (asynchron)
     */
    public static CompletableFuture<Boolean> hasDocxChangedAsync(File docxFile, File sidecarFile) {
        return CompletableFuture.supplyAsync(() -> {
            if (docxFile == null || !docxFile.exists()) {
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
            
            // Wenn kein gespeicherter Hash existiert (savedHash = -1), betrachte DOCX als geändert
            boolean result = (savedHash == -1) || (savedHash != -1 && currentHash != -1 && savedHash != currentHash);
            
            return result;
        }, backgroundExecutor);
    }
    
    /**
     * Prüft ob eine DOCX-Datei seit der letzten Verarbeitung geändert wurde (synchron)
     */
    public static boolean hasDocxChanged(File docxFile, File sidecarFile) {
        if (docxFile == null || !docxFile.exists()) {
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
        
        // Wenn kein gespeicherter Hash existiert (savedHash = -1), betrachte DOCX als geändert
        boolean result = (savedHash == -1) || (savedHash != -1 && currentHash != -1 && savedHash != currentHash);
        
        return result;
    }
    
    /**
     * Lädt den gespeicherten Hash aus Sidecar-Metadaten
     */
    private static long loadSavedHash(File sidecarFile) {
        try {
            // Suche nach DOCX-basierter .meta Datei, nicht MD-basierter
            String docxFileName = sidecarFile.getName().replace(".md", ".docx");
            Path metadataPath = sidecarFile.toPath().resolveSibling(docxFileName + ".meta");
            
            if (Files.exists(metadataPath)) {
                String content = Files.readString(metadataPath);
                
                // Suche nach Hash-Zeile
                Pattern hashPattern = Pattern.compile("docx_hash=([0-9]+)");
                Matcher matcher = hashPattern.matcher(content);
                if (matcher.find()) {
                    long hash = Long.parseLong(matcher.group(1));
                    return hash;
                } else {
                    // Suche nach reinem Hash-Wert (Format: 123456789 oder e275e583)
                    Pattern pureHashPattern = Pattern.compile("^([0-9a-fA-F]+)$");
                    matcher = pureHashPattern.matcher(content.trim());
                    if (matcher.find()) {
                        try {
                            long hash = Long.parseLong(matcher.group(1), 16); // Hexadezimal
                            return hash;
                        } catch (NumberFormatException e) {
                            try {
                                long hash = Long.parseLong(matcher.group(1)); // Dezimal
                                return hash;
                            } catch (NumberFormatException e2) {
                                // Hash-Wert kann nicht geparst werden
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Laden des gespeicherten Hashs: {}", e.getMessage());
        }
        return -1;
    }
    
    /**
     * Speichert den Hash einer DOCX-Datei in Sidecar-Metadaten (asynchron)
     */
    public static CompletableFuture<Void> saveDocxHashAsync(File docxFile, File sidecarFile) {
        return CompletableFuture.runAsync(() -> {
            if (docxFile == null || sidecarFile == null) {
                return;
            }
            
            try {
                long hash = calculateFileHash(docxFile);
                Path metadataPath = sidecarFile.toPath().resolveSibling(sidecarFile.getName() + ".meta");
                
                String metadata = String.format("docx_hash=%d\ndocx_path=%s\nlast_updated=%d\n", 
                    hash, docxFile.getAbsolutePath(), System.currentTimeMillis());
                
                Files.writeString(metadataPath, metadata);
                
            } catch (Exception e) {
                logger.error("Fehler beim Speichern des DOCX-Hashs: {}", e.getMessage());
            }
        }, backgroundExecutor);
    }
    
    /**
     * Speichert den Hash einer DOCX-Datei in Sidecar-Metadaten (synchron)
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
            
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des DOCX-Hashs: {}", e.getMessage());
        }
    }
    
    /**
     * Erstellt ein Diff zwischen zwei Texten mit echtem Myers-Diff-Algorithmus (asynchron)
     */
    public static CompletableFuture<DiffResult> createDiffAsync(String originalText, String newText) {
        return CompletableFuture.supplyAsync(() -> {
            return createDiff(originalText, newText);
        }, backgroundExecutor);
    }
    
    /**
     * Erstellt ein Diff zwischen zwei Texten mit echtem Myers-Diff-Algorithmus
     */
    public static DiffResult createDiff(String originalText, String newText) {
        DiffResult result = new DiffResult();
        
        if (originalText == null) originalText = "";
        if (newText == null) newText = "";
        
        // Normalisiere Leerzeilen - entferne leere Zeilen am Ende und normalisiere Leerzeilen
        String normalizedOriginal = originalText.replaceAll("\n+$", "").replaceAll("\n\n+", "\n");
        String normalizedNew = newText.replaceAll("\n+$", "").replaceAll("\n\n+", "\n");
        
        // Normalisiere Whitespace am Ende von Zeilen - entferne nur Leerzeichen/Tabs am Zeilenende
        normalizedOriginal = normalizedOriginal.replaceAll("(?m)\\s+$", "");
        normalizedNew = normalizedNew.replaceAll("(?m)\\s+$", "");
        
        String[] originalLines = normalizedOriginal.split("\n", -1);
        String[] newLines = normalizedNew.split("\n", -1);
        
        List<DiffLine> diffLines = new ArrayList<>();
        
        // LCS-basierter Diff-Algorithmus für intelligente Block-Erkennung
        int[][] lcs = computeLCS(originalLines, newLines);
        List<DiffOperation> operations = backtrackLCS(originalLines, newLines, lcs);
        
        int originalIndex = 0;
        int newIndex = 0;
        int leftLineNumber = 1;
        int rightLineNumber = 1;
        
        for (DiffOperation op : operations) {
            switch (op.getType()) {
                case KEEP:
                    // Beide Zeilen sind gleich
                    diffLines.add(new DiffLine(leftLineNumber, rightLineNumber, DiffType.UNCHANGED, 
                        originalLines[originalIndex], newLines[newIndex]));
                    originalIndex++;
                    newIndex++;
                    leftLineNumber++;
                    rightLineNumber++;
                    break;
                    
                case DELETE:
                    // Zeile wurde gelöscht
                    diffLines.add(new DiffLine(leftLineNumber, rightLineNumber, DiffType.DELETED, 
                        originalLines[originalIndex], ""));
                    originalIndex++;
                    leftLineNumber++;
                    rightLineNumber++;
                    break;
                    
                case INSERT:
                    // Zeile wurde hinzugefügt
                    diffLines.add(new DiffLine(leftLineNumber, rightLineNumber, DiffType.ADDED, 
                        "", newLines[newIndex]));
                    newIndex++;
                    leftLineNumber++; // Linke Seite auch erhöhen für Synchronisation
                    rightLineNumber++; // Rechte Seite auch erhöhen
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
        // Null-Checks für Arrays
        if (original == null) original = new String[0];
        if (modified == null) modified = new String[0];
        
        int m = original.length;
        int n = modified.length;
        int[][] lcs = new int[m + 1][n + 1];
        
        // LCS-Matrix aufbauen
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                String origLine = original[i - 1] != null ? original[i - 1] : "";
                String modLine = modified[j - 1] != null ? modified[j - 1] : "";
                if (origLine.equals(modLine)) {
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
        
        // Null-Checks für Arrays
        if (original == null) original = new String[0];
        if (modified == null) modified = new String[0];
        
        int i = original.length;
        int j = modified.length;
        
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                String origLine = original[i - 1] != null ? original[i - 1] : "";
                String modLine = modified[j - 1] != null ? modified[j - 1] : "";
                if (origLine.equals(modLine)) {
                    operations.add(0, new DiffOperation(DiffOpType.KEEP));
                    i--;
                    j--;
                } else if (lcs[i - 1][j] > lcs[i][j - 1]) {
                    operations.add(0, new DiffOperation(DiffOpType.DELETE));
                    i--;
                } else {
                    operations.add(0, new DiffOperation(DiffOpType.INSERT));
                    j--;
                }
            } else if (i > 0) {
                operations.add(0, new DiffOperation(DiffOpType.DELETE));
                i--;
            } else {
                operations.add(0, new DiffOperation(DiffOpType.INSERT));
                j--;
            }
        }
        
        return operations;
    }
    
    /**
     * Einfacher line-by-line Diff als Fallback
     */
    private static List<DiffOperation> simpleLineByLineDiff(String[] original, String[] modified) {
        List<DiffOperation> operations = new ArrayList<>();
        
        // Null-Checks für Arrays
        if (original == null) original = new String[0];
        if (modified == null) modified = new String[0];
        
        int i = 0, j = 0;
        
        while (i < original.length || j < modified.length) {
            if (i >= original.length) {
                // Nur noch neue Zeilen
                operations.add(new DiffOperation(DiffOpType.INSERT));
                j++;
            } else if (j >= modified.length) {
                // Nur noch alte Zeilen
                operations.add(new DiffOperation(DiffOpType.DELETE));
                i++;
            } else {
                // Null-Checks für einzelne Zeilen
                String origLine = original[i] != null ? original[i] : "";
                String modLine = modified[j] != null ? modified[j] : "";
                
                if (origLine.equals(modLine)) {
                    // Gleiche Zeile
                    operations.add(new DiffOperation(DiffOpType.KEEP));
                    i++;
                    j++;
                } else {
                    // Verschiedene Zeilen - versuche zu finden, wo die nächste Übereinstimmung ist
                    boolean found = false;
                    
                    // Suche in den nächsten Zeilen nach einer Übereinstimmung
                    for (int k = 1; k <= 3 && i + k < original.length; k++) {
                        String nextOrigLine = original[i + k] != null ? original[i + k] : "";
                        if (nextOrigLine.equals(modLine)) {
                            // Füge k DELETE-Operationen hinzu
                            for (int l = 0; l < k; l++) {
                                operations.add(new DiffOperation(DiffOpType.DELETE));
                            }
                            i += k;
                            found = true;
                            break;
                        }
                    }
                    
                    if (!found) {
                        for (int k = 1; k <= 3 && j + k < modified.length; k++) {
                            String nextModLine = modified[j + k] != null ? modified[j + k] : "";
                            if (origLine.equals(nextModLine)) {
                                // Füge k INSERT-Operationen hinzu
                                for (int l = 0; l < k; l++) {
                                    operations.add(new DiffOperation(DiffOpType.INSERT));
                                }
                                j += k;
                                found = true;
                                break;
                            }
                        }
                    }
                    
                    if (!found) {
                        // Keine Übereinstimmung gefunden - behandle als DELETE + INSERT
                        operations.add(new DiffOperation(DiffOpType.DELETE));
                        operations.add(new DiffOperation(DiffOpType.INSERT));
                        i++;
                        j++;
                    }
                }
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
     * Erstellt HTML-Diff-Output (asynchron)
     */
    public static CompletableFuture<String> createHtmlDiffAsync(DiffResult diffResult) {
        return CompletableFuture.supplyAsync(() -> {
            return createHtmlDiff(diffResult);
        }, backgroundExecutor);
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
            html.append("<span class='line-number'>").append(line.getLeftLineNumber()).append("</span>");
            
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
        private final int leftLineNumber;
        private final int rightLineNumber;
        private final DiffType type;
        private final String originalText;
        private final String newText;
        
        public DiffLine(int leftLineNumber, int rightLineNumber, DiffType type, String originalText, String newText) {
            this.leftLineNumber = leftLineNumber;
            this.rightLineNumber = rightLineNumber;
            this.type = type;
            this.originalText = originalText;
            this.newText = newText;
        }
        
        public int getLeftLineNumber() { return leftLineNumber; }
        public int getRightLineNumber() { return rightLineNumber; }
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
    
    /**
     * Schließt den Background-Executor (beim Beenden der Anwendung aufrufen)
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
    }
}

