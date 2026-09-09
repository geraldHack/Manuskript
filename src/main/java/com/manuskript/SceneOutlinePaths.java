package com.manuskript;

import java.io.File;

/**
 * Hilfsmethoden für Szenen-Outline Sidecar-Dateien in data/.
 */
public final class SceneOutlinePaths {

    private SceneOutlinePaths() {}

    public record Resolution(File canonical, File existing) {
        public File readable() {
            return existing != null ? existing : canonical;
        }
    }

    public static File scenesFileForDocx(File docxFile) {
        return resolve(docxFile).canonical();
    }

    public static File scenesFileForMd(File mdFile) {
        return resolve(mdFile).canonical();
    }

    public static File existingScenesFile(File chapterFile) {
        return resolve(chapterFile).readable();
    }

    public static Resolution resolveBest(File... candidates) {
        Resolution first = null;
        for (File candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Resolution resolution = resolve(candidate);
            if (first == null) {
                first = resolution;
            }
            if (resolution.existing() != null && resolution.existing().isFile()) {
                return resolution;
            }
        }
        return first != null ? first : new Resolution(null, null);
    }

    public static Resolution resolve(File chapterFile) {
        File canonical = canonicalScenesFile(chapterFile);
        if (canonical == null) {
            return new Resolution(null, null);
        }
        if (canonical.isFile()) {
            return new Resolution(canonical, canonical);
        }
        File existing = findExistingIgnoreCase(canonical);
        return new Resolution(canonical, existing);
    }

    static File canonicalScenesFile(File chapterFile) {
        if (chapterFile == null) {
            return null;
        }
        String baseName = stripExtension(chapterFile.getName());
        if (baseName.endsWith("-scenes")) {
            File parent = chapterFile.getParentFile();
            return parent == null ? chapterFile : new File(parent, baseName + ".txt");
        }
        File dataDir = dataDirectoryFor(chapterFile);
        if (dataDir == null) {
            return null;
        }
        return new File(dataDir, baseName + "-scenes.txt");
    }

    static File dataDirectoryFor(File chapterFile) {
        File parent = chapterFile.getParentFile();
        if (parent == null) {
            return null;
        }
        if ("data".equals(parent.getName())) {
            return parent;
        }
        return new File(parent, "data");
    }

    static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static File findExistingIgnoreCase(File canonical) {
        File dataDir = canonical.getParentFile();
        if (dataDir == null || !dataDir.isDirectory()) {
            return null;
        }
        String wanted = canonical.getName();
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith("-scenes.txt"));
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.getName().equalsIgnoreCase(wanted)) {
                return file;
            }
        }
        return null;
    }
}
