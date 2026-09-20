package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportPathCheckTest {

    @Test
    void detectsWindowsDriveAndUnc() {
        assertTrue(ExportPathCheck.looksLikeWindowsPath("G:\\exporte"));
        assertTrue(ExportPathCheck.looksLikeWindowsPath("G:/exporte"));
        assertTrue(ExportPathCheck.looksLikeWindowsPath("C:\\Users\\x\\cover.png"));
        assertTrue(ExportPathCheck.looksLikeWindowsPath("\\\\server\\share"));
        assertFalse(ExportPathCheck.looksLikeWindowsPath("/Users/gerald/exporte"));
        assertFalse(ExportPathCheck.looksLikeWindowsPath(""));
    }

    @Test
    void windowsPathIsForeignOnMacAndLinux() {
        assertTrue(ExportPathCheck.isForeignToOs("G:\\exporte", "Mac OS X"));
        assertTrue(ExportPathCheck.isForeignToOs("G:/exporte", "Linux"));
        assertFalse(ExportPathCheck.isForeignToOs("/Users/gerald/exporte", "Mac OS X"));
        assertFalse(ExportPathCheck.isForeignToOs("G:\\exporte", "Windows 11"));
    }

    @Test
    void unixPathIsForeignOnWindowsIfMissing() {
        assertTrue(ExportPathCheck.isForeignToOs("/Users/gerald/exporte", "Windows 10"));
        assertFalse(ExportPathCheck.isForeignToOs("D:\\exporte", "Windows 10"));
    }

    @Test
    void outputDirectoryRejectsWindowsPathOnUnix() {
        String problem = ExportPathCheck.outputDirectoryProblem("G:\\exporte", null, "Mac OS X");
        assertNotNull(problem);
        assertTrue(problem.contains("G:\\exporte"));
        assertTrue(problem.contains("Durchsuchen"));
    }

    @Test
    void outputDirectoryAcceptsExistingDir(@TempDir Path dir) {
        assertNull(ExportPathCheck.outputDirectoryProblem(dir.toString(), null, "Mac OS X"));
    }

    @Test
    void outputDirectoryAllowsNewFolderIfParentExists(@TempDir Path dir) {
        File nested = dir.resolve("exporte").toFile();
        assertFalse(nested.exists());
        assertNull(ExportPathCheck.outputDirectoryProblem(nested.getAbsolutePath(), null, "Mac OS X"));
    }

    @Test
    void coverRejectsMissingAndWindowsPathOnUnix(@TempDir Path dir) throws Exception {
        assertNotNull(ExportPathCheck.existingFileProblem("C:\\covers\\cover.png", "Cover-Bild", "Mac OS X"));
        Path cover = dir.resolve("cover.png");
        Files.writeString(cover, "x");
        assertNull(ExportPathCheck.existingFileProblem(cover.toString(), "Cover-Bild", "Mac OS X"));
        assertNotNull(ExportPathCheck.existingFileProblem(dir.resolve("fehlt.png").toString(), "Cover-Bild", "Mac OS X"));
    }

    @Test
    void nativeOrEmptyDropsWindowsPathOnThisOsIfNotWindows() {
        if (!ExportPathCheck.isWindowsOs()) {
            org.junit.jupiter.api.Assertions.assertEquals("", ExportPathCheck.nativeOrEmpty("G:\\exporte"));
        }
    }
}
