package com.manuskript.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiReviewMergeTest {

    @Test
    void sameHashKeepsOffsets() {
        String live = "Dann ging schnell die Treppe hinauf.";
        NiReviewDocument incoming = replaceAt(live, "ging schnell", "hastete");
        NiReviewDocument merged = NiReviewMerge.ontoLive(incoming, live, live);
        assertEquals(1, merged.openChanges().size());
        assertEquals(incoming.getChanges().get(0).getStart(), merged.getChanges().get(0).getStart());
        assertEquals(NiReviewChange.STATUS_PENDING, merged.getChanges().get(0).getStatus());
    }

    @Test
    void insertBeforeRemapsByContext() {
        String snapshot = "Dann ging schnell die Treppe hinauf.";
        String live = "Vorwort.\n\n" + snapshot;
        NiReviewDocument incoming = replaceAt(snapshot, "ging schnell", "hastete");
        NiReviewDocument merged = NiReviewMerge.ontoLive(incoming, snapshot, live);
        NiReviewChange change = merged.getChanges().get(0);
        assertEquals(NiReviewChange.STATUS_PENDING, change.getStatus());
        assertEquals(live.indexOf("ging schnell"), change.getStart());
    }

    @Test
    void missingTextBecomesUnresolved() {
        String snapshot = "Dann ging schnell die Treppe hinauf.";
        String live = "Dann rannte er die Treppe hinauf.";
        NiReviewDocument incoming = replaceAt(snapshot, "ging schnell", "hastete");
        NiReviewDocument merged = NiReviewMerge.ontoLive(incoming, snapshot, live);
        assertEquals(NiReviewChange.STATUS_UNRESOLVED, merged.getChanges().get(0).getStatus());
    }

    @Test
    void acceptAppliesOnlyThatChange() {
        String base = "Dann ging schnell die Treppe hinauf.";
        NiReviewDocument document = replaceAt(base, "ging schnell", "hastete");
        NiReviewSession session = new NiReviewSession(base, document, NiReviewSession.PersistMode.PROJECT);
        session.accept(document.getChanges().get(0).getId());
        assertEquals("Dann hastete die Treppe hinauf.", session.baseText());
        assertTrue(session.document().openChanges().isEmpty());
    }

    @Test
    void insertStaysVisibleAndGreen() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.recordDisplayEdit(base, "Hallo liebe Welt.");
        assertEquals(1, session.document().openChanges().size());
        NiReviewChange change = session.document().openChanges().get(0);
        assertEquals(NiReviewChange.KIND_INSERT, change.getKind());
        assertEquals("liebe ", change.getNewText());
        assertTrue(session.display().text().contains("liebe"));
        assertTrue(session.display().spans().stream()
                .anyMatch(span -> span.kind() == NiReviewDisplay.SpanKind.INSERT));
        assertTrue(session.document().openComments().isEmpty());
    }

    @Test
    void insertAfterDeleteAtSamePlaceStaysVisible() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.recordDisplayEdit(base, "Hallo .");
        session.recordDisplayEdit(session.display().text(), "Hallo Erde.");
        assertEquals(1, session.document().openChanges().size());
        NiReviewChange change = session.document().openChanges().get(0);
        assertEquals("Welt", change.getOldText());
        assertEquals("Erde", change.getNewText());
        assertTrue(session.display().text().contains("Welt"));
        assertTrue(session.display().text().contains("Erde"));
        assertTrue(session.display().spans().stream()
                .anyMatch(span -> span.kind() == NiReviewDisplay.SpanKind.INSERT));
    }

    @Test
    void displayKeepsInsertBesideDeleteAtSameOffset() {
        String base = "Hallo Welt.";
        NiReviewDocument document = new NiReviewDocument();
        NiReviewChange del = new NiReviewChange();
        del.setKind(NiReviewChange.KIND_DELETE);
        del.setStart(6);
        del.setEnd(10);
        del.setOldText("Welt");
        document.getChanges().add(del);
        NiReviewChange ins = new NiReviewChange();
        ins.setKind(NiReviewChange.KIND_INSERT);
        ins.setStart(6);
        ins.setEnd(6);
        ins.setNewText("Erde");
        document.getChanges().add(ins);
        NiReviewDisplay.Result result = NiReviewDisplay.build(base, document);
        assertTrue(result.text().contains("Welt"));
        assertTrue(result.text().contains("Erde"));
        assertTrue(result.spans().stream().anyMatch(span -> span.kind() == NiReviewDisplay.SpanKind.INSERT));
    }

    @Test
    void lektorEditCreatesReplaceChange() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.recordDisplayEdit(base, "Hallo Erde.");
        assertEquals(1, session.document().openChanges().size());
        NiReviewChange change = session.document().openChanges().get(0);
        assertEquals("Welt", change.getOldText());
        assertEquals("Erde", change.getNewText());
        assertTrue(session.display().text().contains("Welt"));
        assertTrue(session.display().text().contains("Erde"));
        assertTrue(session.document().openComments().isEmpty());
    }

    @Test
    void typingAfterReplaceStaysOneChange() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.recordDisplayEdit(base, "Hallo E.");
        String shown = session.display().text();
        session.recordDisplayEdit(shown, shown.replace("E", "Er"));
        shown = session.display().text();
        session.recordDisplayEdit(shown, shown.replace("Er", "Erd"));
        shown = session.display().text();
        session.recordDisplayEdit(shown, shown.replace("Erd", "Erde"));
        assertEquals(1, session.document().openChanges().size());
        NiReviewChange change = session.document().openChanges().get(0);
        assertEquals("Welt", change.getOldText());
        assertEquals("Erde", change.getNewText());
        assertTrue(session.display().text().startsWith("Hallo Erde"));
        assertTrue(session.document().openComments().isEmpty());
    }

    @Test
    void deletingLettersStaysOneChange() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.recordDisplayEdit(base, "Hallo Wel.");
        session.recordDisplayEdit(session.display().text(), "Hallo We.");
        session.recordDisplayEdit(session.display().text(), "Hallo W.");
        assertEquals(1, session.document().openChanges().size());
        NiReviewChange change = session.document().openChanges().get(0);
        assertEquals(NiReviewChange.KIND_DELETE, change.getKind());
        assertEquals("elt", change.getOldText());
        assertTrue(session.document().openComments().isEmpty());
    }

    @Test
    void commentCanBeDeleted() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.addComment(0, 5, "Bitte kürzen", false);
        assertEquals(1, session.document().openComments().size());
        session.deleteComment(session.document().openComments().get(0).getId());
        assertTrue(session.document().openComments().isEmpty());
        assertTrue(session.document().getComments().isEmpty());
    }

    @Test
    void undoCheckpointRestoresDeletedComment() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.addComment(0, 5, "Bitte kürzen", false);
        session.pushUndoCheckpoint();
        session.deleteComment(session.document().openComments().get(0).getId());
        assertTrue(session.document().openComments().isEmpty());
        assertTrue(session.undoCheckpoint());
        assertEquals(1, session.document().openComments().size());
        assertEquals("Bitte kürzen", session.document().openComments().get(0).getText());
    }

    @Test
    void undoCheckpointRestoresLastEdit() {
        String base = "Hallo Welt.";
        NiReviewSession session = new NiReviewSession(base, new NiReviewDocument(), NiReviewSession.PersistMode.PACKAGE);
        session.pushUndoCheckpoint();
        session.recordDisplayEdit(base, "Hallo Erde.");
        assertEquals(1, session.document().openChanges().size());
        assertTrue(session.undoCheckpoint());
        assertTrue(session.document().openChanges().isEmpty());
    }

    @Test
    void authorEditGoesIntoLiveTextNotNewChange() {
        String base = "Dann ging schnell die Treppe.";
        NiReviewDocument document = replaceAt(base, "ging schnell", "hastete");
        NiReviewSession session = new NiReviewSession(base, document, NiReviewSession.PersistMode.PROJECT);
        String shown = session.display().text();
        session.applyAuthorDisplayEdit(shown, "Vorwort.\n\n" + shown);
        assertTrue(session.baseText().startsWith("Vorwort."));
        assertTrue(session.baseText().contains("ging schnell"));
        assertEquals(1, session.document().openChanges().size());
        assertEquals(session.baseText().indexOf("ging schnell"),
                session.document().openChanges().get(0).getStart());
        assertEquals(NiReviewChange.STATUS_PENDING, session.document().openChanges().get(0).getStatus());
    }

    @Test
    void displayShowsOldAndNew() {
        String base = "Dann ging schnell die Treppe.";
        NiReviewDocument document = replaceAt(base, "ging schnell", "hastete");
        NiReviewDisplay.Result result = NiReviewDisplay.build(base, document);
        assertTrue(result.text().contains("ging schnell"));
        assertTrue(result.text().contains("hastete"));
        assertEquals(2, result.spans().stream()
                .filter(span -> span.kind() != NiReviewDisplay.SpanKind.COMMENT)
                .count());
    }

    @Test
    void zipRoundtripDoesNotNeedLiveWrite(@TempDir Path temp) throws Exception {
        Path zip = temp.resolve("runde.ni.zip");
        NiReviewActions.FileBook book = new NiReviewActions.FileBook(temp.resolve("book").toFile());
        book.directory().mkdirs();
        NiReviewActions.SendResult sent = NiReviewActions.send(zip, book,
                List.of(new NiReviewActions.ChapterSource("kapitel.md", "kapitel.docx", "Hallo Welt.")),
                "Autor");
        assertEquals(1, sent.chapterCount());
        assertTrue(NiReviewStore.isInLektorat(book.directory(), "kapitel.md"));

        NiReviewZip.Loaded loaded = NiReviewZip.read(zip);
        NiReviewDocument review = loaded.reviews().get("kapitel.md");
        NiReviewChange change = new NiReviewChange();
        change.setKind(NiReviewChange.KIND_REPLACE);
        change.setStart(6);
        change.setEnd(10);
        change.setOldText("Welt");
        change.setNewText("Erde");
        change.setPrefix("Hallo ");
        change.setSuffix(".");
        review.getChanges().add(change);
        loaded.reviews().put("kapitel.md", review);
        Path returned = temp.resolve("zurueck.ni.zip");
        NiReviewZip.writeReturned(returned, loaded);

        Map<String, String> live = new LinkedHashMap<>();
        live.put("kapitel.md", "Hallo Welt.");
        NiReviewActions.ImportResult imported = NiReviewActions.importReturned(book, returned, live);
        assertEquals(1, imported.merged());
        assertTrue(imported.unknownKeys().isEmpty());
        NiReviewDocument stored = NiReviewStore.loadReview(book.directory(), "kapitel.md");
        assertEquals("Welt", stored.getChanges().get(0).getOldText());
        assertFalse(Files.exists(temp.resolve("book/data/kapitel.md")));
    }

    @Test
    void unknownChapterIsNotCreated(@TempDir Path temp) throws Exception {
        Path zip = temp.resolve("runde.ni.zip");
        NiReviewActions.FileBook book = new NiReviewActions.FileBook(temp.resolve("book").toFile());
        book.directory().mkdirs();
        NiReviewActions.send(zip, book,
                List.of(new NiReviewActions.ChapterSource("kapitel.md", "kapitel.docx", "Hallo.")),
                "Autor");
        Map<String, String> live = new LinkedHashMap<>();
        NiReviewActions.ImportResult imported = NiReviewActions.importReturned(book, zip, live);
        assertEquals(0, imported.merged());
        assertEquals(List.of("kapitel.md"), imported.unknownKeys());
        assertTrue(book.directory().toPath().resolve("data").toFile().listFiles() == null
                || book.directory().toPath().resolve("data").toFile().listFiles().length == 0);
    }

    @Test
    void deleteAllRemovesJson(@TempDir Path temp) throws Exception {
        NiReviewActions.FileBook book = new NiReviewActions.FileBook(temp.resolve("book").toFile());
        book.directory().mkdirs();
        Path zip = temp.resolve("a.ni.zip");
        NiReviewActions.send(zip, book,
                List.of(new NiReviewActions.ChapterSource("kapitel.md", "k.docx", "Text")),
                "Autor");
        NiReviewDocument document = new NiReviewDocument();
        document.setChapterKey("kapitel.md");
        NiReviewStore.saveReview(book.directory(), "kapitel.md", document);
        NiReviewStore.deleteAll(book.directory());
        assertTrue(NiReviewStore.loadReview(book.directory(), "kapitel.md") == null);
        assertFalse(NiReviewStore.isInLektorat(book.directory(), "kapitel.md"));
    }

    private static NiReviewDocument replaceAt(String base, String oldText, String newText) {
        int start = base.indexOf(oldText);
        NiReviewChange change = new NiReviewChange();
        change.setKind(NiReviewChange.KIND_REPLACE);
        change.setStart(start);
        change.setEnd(start + oldText.length());
        change.setOldText(oldText);
        change.setNewText(newText);
        change.setPrefix(NiReviewHashes.contextBefore(base, start, 40));
        change.setSuffix(NiReviewHashes.contextAfter(base, start + oldText.length(), 40));
        NiReviewDocument document = new NiReviewDocument();
        document.setBaseHash(NiReviewHashes.sha256(base));
        document.getChanges().add(change);
        return document;
    }
}
