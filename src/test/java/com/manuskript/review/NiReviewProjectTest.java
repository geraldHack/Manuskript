package com.manuskript.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiReviewProjectTest {

    private static final byte[] TINY_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06,
            0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A,
            0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
            0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @TempDir
    Path temp;

    @Test
    void sendAgainStillWritesZip() throws Exception {
        Path book = temp.resolve("Roman");
        Files.createDirectories(book.resolve("data"));
        Path zip = temp.resolve("roman-lektorat.ni.zip");
        NiReviewActions.FileBook fileBook = new NiReviewActions.FileBook(book.toFile());
        List<NiReviewActions.ChapterSource> chapters = List.of(
                new NiReviewActions.ChapterSource("k.md", "k.docx", "Eins"));
        assertEquals(1, NiReviewActions.send(zip, fileBook, chapters, "Autor").chapterCount());
        assertEquals(1, NiReviewActions.send(zip, fileBook, chapters, "Autor").chapterCount());
        assertTrue(Files.isRegularFile(zip));
        assertEquals("Roman", NiReviewZip.read(zip).manifest().getProjectName());
    }

    @Test
    void lektorWorkingCopiesStayUnderDocumentsNotBookRoot() throws Exception {
        Path documents = temp.resolve("Documents");
        Files.createDirectories(documents);
        File copies = NiReviewProject.lektorWorkingCopiesDirectory(documents.toFile());
        assertEquals(documents.resolve("Lektorat").toFile(), copies);
        assertTrue(copies.isDirectory());
    }

    @Test
    void uniqueDirectoryGetsRunningNumber() throws Exception {
        Files.createDirectories(temp.resolve("Roman"));
        assertEquals(temp.resolve("Roman 2"), NiReviewProject.uniqueProjectDirectory(temp, "Roman"));
        Files.createDirectories(temp.resolve("Roman 2"));
        assertEquals(temp.resolve("Roman 3"), NiReviewProject.uniqueProjectDirectory(temp, "Roman"));
    }

    @Test
    void authorZipNameUsesBookNotReturnLabel() {
        File book = temp.resolve("Mein Roman").toFile();
        assertEquals("Mein Roman-lektorat.ni.zip", NiReviewProject.authorZipFileName(book));
        assertEquals("lektorat-rueckgabe.ni.zip", NiReviewProject.returnZipFileName());
        assertEquals(new File(temp.toFile(), "Kapitel.ni.zip"),
                NiReviewProject.withNiZipExtension(new File(temp.toFile(), "Kapitel.zip")));
    }

    @Test
    void sendPacksCoverAndChapterImage() throws Exception {
        Path book = temp.resolve("Der Roman");
        Files.createDirectories(book.resolve("data"));
        Files.write(book.resolve("cover_image.png"), TINY_PNG);
        Files.write(book.resolve("data").resolve("szene.png"), TINY_PNG);
        Path zip = temp.resolve("out.ni.zip");
        NiReviewActions.send(zip, new NiReviewActions.FileBook(book.toFile()),
                List.of(new NiReviewActions.ChapterSource(
                        "1. Start.md", "1. Start.docx", "Hallo ![](szene.png)")),
                "Autor");
        NiReviewZip.Loaded loaded = NiReviewZip.read(zip);
        assertEquals("Der Roman", loaded.manifest().getProjectName());
        assertTrue(loaded.assets().containsKey("cover_image.png"));
        assertTrue(loaded.assets().containsKey("data/szene.png"));
    }

    @Test
    void materializeCreatesProjectWithCoverAndNumber() throws Exception {
        Path book = temp.resolve("Insel");
        Files.createDirectories(book.resolve("data"));
        Files.write(book.resolve("cover_image.png"), TINY_PNG);
        Files.writeString(book.resolve("data").resolve("kapitel.md"), "Text", StandardCharsets.UTF_8);
        Path zip = temp.resolve("insel.ni.zip");
        NiReviewActions.send(zip, new NiReviewActions.FileBook(book.toFile()),
                List.of(new NiReviewActions.ChapterSource("kapitel.md", "kapitel.docx", "Text")),
                "Autor");
        Files.createDirectories(temp.resolve("werke").resolve("Insel"));
        Path created = NiReviewProject.materialize(zip, temp.resolve("werke"));
        assertEquals("Insel 2", created.getFileName().toString());
        assertTrue(Files.isRegularFile(created.resolve("cover_image.png")));
        assertTrue(Files.isRegularFile(created.resolve("data").resolve("kapitel.md")));
        assertTrue(Files.isRegularFile(created.resolve("kapitel.docx")));
        assertEquals("Text", Files.readString(created.resolve("data").resolve("kapitel.md")));
        assertTrue(NiReviewProject.hasAuthorSnapshots(created.toFile()));
    }

    @Test
    void materializeKeepsAuthorChapterOrder() throws Exception {
        Path book = temp.resolve("Chronik");
        Files.createDirectories(book.resolve("data"));
        Path zip = temp.resolve("chronik.ni.zip");
        NiReviewActions.send(zip, new NiReviewActions.FileBook(book.toFile()),
                List.of(
                        new NiReviewActions.ChapterSource("Zebra.md", "Zebra.docx", "Z"),
                        new NiReviewActions.ChapterSource("Apfel.md", "Apfel.docx", "A"),
                        new NiReviewActions.ChapterSource("Mitte.md", "Mitte.docx", "M")),
                "Autor");
        Path created = NiReviewProject.materialize(zip, temp.resolve("lektoren"));
        assertEquals(List.of("Zebra.docx", "Apfel.docx", "Mitte.docx"),
                NiReviewProject.readChapterOrder(created.toFile()));
        Path returned = temp.resolve("chronik-zurueck.ni.zip");
        NiReviewProject.writeReturnZip(created.toFile(), returned);
        assertEquals(List.of("Zebra.md", "Apfel.md", "Mitte.md"),
                NiReviewZip.read(returned).manifest().getChapters().stream()
                        .map(NiReviewManifest.ChapterRef::getChapterKey)
                        .toList());
    }

    @Test
    void returnZipContainsLiveEdit() throws Exception {
        Path book = temp.resolve("Buch");
        Files.createDirectories(book.resolve("data"));
        Path zip = temp.resolve("senden.ni.zip");
        NiReviewActions.send(zip, new NiReviewActions.FileBook(book.toFile()),
                List.of(new NiReviewActions.ChapterSource("k.md", "k.docx", "Alt")),
                "Autor");
        Path copy = NiReviewProject.materialize(zip, temp.resolve("kopien"));
        Files.writeString(copy.resolve("data").resolve("k.md"), "Neu", StandardCharsets.UTF_8);
        Path returned = temp.resolve("zurueck.ni.zip");
        NiReviewProject.writeReturnZip(copy.toFile(), returned);
        NiReviewZip.Loaded loaded = NiReviewZip.read(returned);
        assertEquals("Alt", loaded.snapshots().get("k.md"));
        assertEquals("Neu", loaded.reviews().get("k.md").getChanges().get(0).getNewText());
    }

    @Test
    void quoteInChapterNameRoundtripsOnImport() throws Exception {
        String key = "Kapitel \"Einleitung\".md";
        String docx = "Kapitel \"Einleitung\".docx";
        Path book = temp.resolve("Roman");
        Files.createDirectories(book.resolve("data"));
        Files.writeString(book.resolve("data").resolve(key), "Original", StandardCharsets.UTF_8);
        Path zip = temp.resolve("mit-anfuehrungszeichen.ni.zip");
        NiReviewActions.send(zip, new NiReviewActions.FileBook(book.toFile()),
                List.of(new NiReviewActions.ChapterSource(key, docx, "Original")),
                "Autor");
        Path copy = NiReviewProject.materialize(zip, temp.resolve("Lektorat"));
        Path returned = temp.resolve("zurueck.ni.zip");
        NiReviewProject.writeReturnZip(copy.toFile(), returned);
        Map<String, String> live = Map.of(key, "Original");
        NiReviewActions.ImportResult imported = NiReviewActions.importReturned(
                new NiReviewActions.FileBook(book.toFile()), returned, live);
        assertEquals(1, imported.merged());
        assertTrue(imported.unknownKeys().isEmpty());
        assertTrue(NiReviewStore.loadReview(book.toFile(), key) != null);
    }
}
