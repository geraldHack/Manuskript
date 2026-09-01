package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainControllerProjectRootTest {

    @Test
    void childAndRootAreInsideRoot(@TempDir Path tmp) {
        File root = tmp.toFile();
        File child = new File(root, "buch");
        assertTrue(child.mkdir());
        assertTrue(MainController.isUnderProjectRoot(root, root));
        assertTrue(MainController.isUnderProjectRoot(child, root));
    }

    @Test
    void siblingWithSharedNamePrefixIsOutside(@TempDir Path tmp) {
        File root = new File(tmp.toFile(), "Manuskript");
        File other = new File(tmp.toFile(), "Manuskript-alt");
        assertTrue(root.mkdir());
        assertTrue(other.mkdir());
        assertFalse(MainController.isUnderProjectRoot(other, root));
        assertFalse(MainController.isUnderProjectRoot(root, other));
    }

    @Test
    void bookFolderResolvesToParentCollection(@TempDir Path tmp) throws Exception {
        File root = new File(tmp.toFile(), "manuskripte");
        File book = new File(root, "Traumwelt");
        assertTrue(root.mkdirs());
        assertTrue(book.mkdir());
        assertTrue(new File(book, "kapitel.docx").createNewFile());
        File other = new File(root, "Exil");
        assertTrue(other.mkdir());
        assertTrue(new File(other, "kapitel.docx").createNewFile());

        assertEquals(root.getCanonicalFile(), MainController.resolveAsProjectRoot(book).getCanonicalFile());
        assertEquals(root.getCanonicalFile(), MainController.resolveAsProjectRoot(root).getCanonicalFile());
    }

    @Test
    void isolatedBookIsNotLiftedToHome(@TempDir Path tmp) throws Exception {
        File book = new File(tmp.toFile(), "NurEinBuch");
        assertTrue(book.mkdir());
        assertTrue(new File(book, "kapitel.docx").createNewFile());
        assertEquals(book.getCanonicalFile(), MainController.resolveAsProjectRoot(book).getCanonicalFile());
    }
}
