package com.manuskript;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DocxFile {
    private final File file;
    private final String fileName;
    private final long fileSize;
    private final long lastModified;
    private boolean changed = false;
    
    public DocxFile(File file) {
        this.file = file;
        this.fileName = file.getName();
        this.fileSize = file.length();
        this.lastModified = file.lastModified();
    }
    
    public File getFile() {
        return file;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    public String getFormattedLastModified() {
        // Wenn eine MD-Datei existiert, verwende deren Datum, sonst das der DOCX-Datei
        File mdFile = deriveMdFileFor(file);
        long currentLastModified;
        
        if (mdFile != null && mdFile.exists()) {
            // Verwende Datum der MD-Datei
            currentLastModified = mdFile.lastModified();
        } else {
            // Verwende Datum der DOCX-Datei
            currentLastModified = file.exists() ? file.lastModified() : lastModified;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(currentLastModified));
    }
    
    /**
     * Findet die zugehörige MD-Datei für eine DOCX-Datei
     * (entspricht der Logik aus MainController.deriveMdFileFor)
     */
    private File deriveMdFileFor(File docx) {
        if (docx == null) return null;
        String baseName = docx.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) baseName = baseName.substring(0, idx);
        File dataDir = getDataDirectory(docx);
        return new File(dataDir, baseName + ".md");
    }
    
    /**
     * Gibt das data-Verzeichnis für eine DOCX-Datei zurück
     */
    private File getDataDirectory(File docxFile) {
        if (docxFile == null) return null;
        File dataDir = new File(docxFile.getParentFile(), "data");
        if (!dataDir.exists()) {
            return null; // data-Verzeichnis existiert nicht
        }
        return dataDir;
    }
    
    public boolean isChanged() {
        return changed;
    }
    
    public void setChanged(boolean changed) {
        this.changed = changed;
    }
    
    public String getDisplayFileName() {
        // Entferne .docx Erweiterung für die Anzeige
        String displayName = fileName;
        if (fileName.toLowerCase().endsWith(".docx")) {
            displayName = fileName.substring(0, fileName.length() - 5);
        }
        return changed ? "! " + displayName : displayName;
    }
    
    @Override
    public String toString() {
        // Entferne .docx Erweiterung für die Anzeige
        if (fileName.toLowerCase().endsWith(".docx")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DocxFile docxFile = (DocxFile) obj;
        return file.equals(docxFile.file);
    }
    
    @Override
    public int hashCode() {
        return file.hashCode();
    }
} 