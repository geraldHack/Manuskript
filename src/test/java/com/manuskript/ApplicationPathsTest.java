package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationPathsTest {

    @Test
    void defaultUserProjectsDirectoryPrefersDocuments(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("Documents"));
        File dir = ApplicationPaths.defaultUserProjectsDirectory(home.toString());
        assertEquals(home.resolve("Documents").resolve("Manuskript").toFile(), dir);
    }

    @Test
    void defaultUserProjectsDirectoryUsesDokumenteWhenDocumentsMissing(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("Dokumente"));
        File dir = ApplicationPaths.defaultUserProjectsDirectory(home.toString());
        assertEquals(home.resolve("Dokumente").resolve("Manuskript").toFile(), dir);
    }

    @Test
    void userDocumentsDirectoryPrefersDocuments(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("Documents"));
        assertEquals(home.resolve("Documents").toFile(),
                ApplicationPaths.userDocumentsDirectory(home.toString()));
    }

    @Test
    void seedCopiesGottDemoOnlyWhenMissing(@TempDir Path temp) throws Exception {
        Path bundled = temp.resolve("bundled");
        Path demo = bundled.resolve(ApplicationPaths.DEMO_PROJECT_NAME);
        Files.createDirectories(demo);
        Files.writeString(demo.resolve("1. Kapitel.docx"), "demo");

        Path userDir = temp.resolve("user");
        assertTrue(ApplicationPaths.seedGottDemoOnce(bundled.toFile(), userDir.toFile(), false));
        assertTrue(Files.isRegularFile(userDir.resolve(ApplicationPaths.DEMO_PROJECT_NAME).resolve("1. Kapitel.docx")));

        Files.writeString(userDir.resolve(ApplicationPaths.DEMO_PROJECT_NAME).resolve("1. Kapitel.docx"), "user-edit");
        assertFalse(ApplicationPaths.seedGottDemoOnce(bundled.toFile(), userDir.toFile(), false));
        assertEquals("user-edit", Files.readString(
                userDir.resolve(ApplicationPaths.DEMO_PROJECT_NAME).resolve("1. Kapitel.docx")));
    }

    @Test
    void seedDoesNothingWhenAlreadyMarked(@TempDir Path temp) throws Exception {
        Path bundled = temp.resolve("bundled");
        Files.createDirectories(bundled.resolve(ApplicationPaths.DEMO_PROJECT_NAME));
        Path userDir = temp.resolve("user");

        assertFalse(ApplicationPaths.seedGottDemoOnce(bundled.toFile(), userDir.toFile(), true));
        assertFalse(Files.exists(userDir.resolve(ApplicationPaths.DEMO_PROJECT_NAME)));
    }

    @Test
    void findGottDemoMatchesFolderName(@TempDir Path temp) throws Exception {
        Path bundled = temp.resolve("bundled");
        Path demo = bundled.resolve("Mein Gott von Demirantha");
        Files.createDirectories(demo);
        assertEquals(demo.toFile(), ApplicationPaths.findGottDemo(bundled.toFile()));
    }

    @Test
    void isInsideApplicationHomeDetectsAppSubfolder() {
        File appHome = ApplicationPaths.getApplicationHomeDirectory();
        File nested = new File(appHome, "Manuskripte" + File.separator + "Projekt");
        assertTrue(ApplicationPaths.isInsideApplicationHome(nested));
        assertFalse(ApplicationPaths.isInsideApplicationHome(new File(System.getProperty("user.home"), "Documents")));
    }

    @Test
    void defaultDirectoryIsNotDirectlyInHome() {
        File dir = ApplicationPaths.defaultUserProjectsDirectory();
        assertEquals("Manuskript", dir.getName());
        assertFalse(ApplicationPaths.isLegacyHomeProjectsDirectory(dir));
        assertTrue(ApplicationPaths.looksLikeDocumentsFolder(dir.getParentFile())
                || !dir.getParentFile().exists());
    }

    @Test
    void legacyHomeManuskriptFolderIsDetected(@TempDir Path home) {
        File homeDir = home.toFile();
        File legacy = new File(homeDir, "Manuskript");
        File documents = new File(new File(homeDir, "Documents"), "Manuskript");
        assertTrue(ApplicationPaths.isLegacyHomeProjectsDirectory(legacy, homeDir));
        assertFalse(ApplicationPaths.isLegacyHomeProjectsDirectory(documents, homeDir));
        assertTrue(ApplicationPaths.shouldRelocateProjectRoot(new File(System.getProperty("user.home"), "Manuskript")));
    }

    @Test
    void copyMissingProjectFoldersSkipsExisting(@TempDir Path temp) throws Exception {
        Path from = temp.resolve("from");
        Path to = temp.resolve("to");
        Files.createDirectories(from.resolve("Der Gott von Demirantha"));
        Files.writeString(from.resolve("Der Gott von Demirantha").resolve("a.docx"), "neu");
        Files.createDirectories(to.resolve("Der Gott von Demirantha"));
        Files.writeString(to.resolve("Der Gott von Demirantha").resolve("a.docx"), "alt");
        Files.createDirectories(from.resolve("Neues Buch"));
        Files.writeString(from.resolve("Neues Buch").resolve("b.docx"), "buch");

        ApplicationPaths.copyMissingProjectFolders(from.toFile(), to.toFile());

        assertEquals("alt", Files.readString(to.resolve("Der Gott von Demirantha").resolve("a.docx")));
        assertEquals("buch", Files.readString(to.resolve("Neues Buch").resolve("b.docx")));
    }
}
