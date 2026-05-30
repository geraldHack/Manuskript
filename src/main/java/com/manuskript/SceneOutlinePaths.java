package com.manuskript;

import java.io.File;

/**
 * Hilfsmethoden für Szenen-Outline Sidecar-Dateien in data/.
 */
public final class SceneOutlinePaths {

    private SceneOutlinePaths() {}

    /**
     * data/{KapitelBasename}-scenes.txt neben der DOCX-Datei.
     */
    public static File scenesFileForDocx(File docxFile) {
        if (docxFile == null) {
            return null;
        }
        String baseName = docxFile.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) {
            baseName = baseName.substring(0, idx);
        }
        File parent = docxFile.getParentFile();
        if (parent == null) {
            return null;
        }
        File dataDir = new File(parent, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, baseName + "-scenes.txt");
    }

    public static File scenesFileForMd(File mdFile) {
        if (mdFile == null) {
            return null;
        }
        String baseName = mdFile.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) {
            baseName = baseName.substring(0, idx);
        }
        if (baseName.endsWith("-scenes")) {
            return mdFile;
        }
        File parent = mdFile.getParentFile();
        if (parent == null) {
            return null;
        }
        if ("data".equals(parent.getName())) {
            File projectDir = parent.getParentFile();
            if (projectDir != null) {
                return new File(parent, baseName + "-scenes.txt");
            }
        }
        File dataDir = new File(parent, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, baseName + "-scenes.txt");
    }
}
