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
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(lastModified));
    }
    
    public boolean isChanged() {
        return changed;
    }
    
    public void setChanged(boolean changed) {
        this.changed = changed;
    }
    
    public String getDisplayFileName() {
        return changed ? "! " + fileName : fileName;
    }
    
    @Override
    public String toString() {
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