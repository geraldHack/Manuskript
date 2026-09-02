package com.manuskript.review;

import com.manuskript.ResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiReviewRoleTest {

    private String previous;

    @BeforeEach
    void remember() {
        previous = ResourceManager.getParameter(NiReviewRole.PARAMETER, "autor");
    }

    @AfterEach
    void restore() {
        ResourceManager.saveParameter(NiReviewRole.PARAMETER, previous);
    }

    @Test
    void persistsAuthorAndLektor() {
        NiReviewRole.set(NiReviewRole.LEKTOR);
        assertEquals(NiReviewRole.LEKTOR, NiReviewRole.current());
        NiReviewRole.set(NiReviewRole.AUTHOR);
        assertEquals(NiReviewRole.AUTHOR, NiReviewRole.current());
    }

    @Test
    void lektorProjectKeepsLektorRoleEvenIfGlobalIsAuthor(@TempDir Path temp) throws Exception {
        NiReviewRole.set(NiReviewRole.AUTHOR);
        Path book = temp.resolve("lektor-kopie");
        Files.createDirectories(book.resolve("lektorat").resolve("snapshots"));
        assertEquals(NiReviewRole.AUTHOR, NiReviewRole.current());
        assertEquals(NiReviewRole.LEKTOR, NiReviewRole.forBook(book.toFile()));
    }

    @Test
    void realAuthorBookIsNeverLektorJustBecauseRoleIsSet(@TempDir Path temp) throws Exception {
        NiReviewRole.set(NiReviewRole.LEKTOR);
        Path book = temp.resolve("MeinRoman");
        Files.createDirectories(book.resolve("data"));
        assertEquals(NiReviewRole.LEKTOR, NiReviewRole.current());
        assertEquals(NiReviewRole.AUTHOR, NiReviewRole.forBook(book.toFile()));
    }

    @Test
    void authorModeNeverUsesLektorUiEvenWithSnapshots(@TempDir Path temp) throws Exception {
        NiReviewRole.set(NiReviewRole.AUTHOR);
        Path book = temp.resolve("MeinRoman");
        Files.createDirectories(book.resolve("lektorat").resolve("snapshots"));
        assertFalse(NiReviewRole.isLektorEditing(book.toFile(), false));
        assertFalse(NiReviewRole.isLektorEditing(book.toFile(), true));
    }

    @Test
    void lektorModeOnWorkingCopyUsesLektorUi(@TempDir Path temp) throws Exception {
        NiReviewRole.set(NiReviewRole.LEKTOR);
        Path book = temp.resolve("lektor-kopie");
        Files.createDirectories(book.resolve("lektorat").resolve("snapshots"));
        assertTrue(NiReviewRole.isLektorEditing(book.toFile(), false));
        assertFalse(NiReviewRole.isLektorEditing(book.toFile(), true));
    }
}
