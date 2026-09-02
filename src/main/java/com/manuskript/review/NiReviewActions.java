package com.manuskript.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Versand und Rückspielung. Schreibt niemals ZIP-MD nach data/.
 */
public final class NiReviewActions {

    public record ChapterSource(String chapterKey, String docxHint, String markdown) {
    }

    public record SendResult(Path zipFile, int chapterCount, List<String> skipped) {
    }

    public record ImportResult(int merged, List<String> unknownKeys, List<String> remapped) {
    }

    private NiReviewActions() {
    }

    public static SendResult send(Path zipFile, FileBook book, List<ChapterSource> chapters, String author)
            throws IOException {
        if (chapters == null || chapters.isEmpty()) {
            throw new IOException("Keine Kapitel im Buch");
        }
        NiReviewProjectStatus status = NiReviewStore.loadStatus(book.directory());
        String roundId = NiReviewHashes.newRoundId();
        NiReviewManifest manifest = new NiReviewManifest();
        manifest.setRoundId(roundId);
        manifest.setAuthor(author == null || author.isBlank() ? "Autor" : author);
        manifest.setCreated(NiReviewHashes.nowIso());
        manifest.setStatus(NiReviewManifest.STATUS_REQUESTED);
        manifest.setProjectName(book.directory() != null ? book.directory().getName() : "");

        Map<String, String> snapshots = new LinkedHashMap<>();
        Map<String, NiReviewDocument> reviews = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        for (ChapterSource source : chapters) {
            String md = source.markdown() == null ? "" : source.markdown();
            String hash = NiReviewHashes.sha256(md);
            String stem = source.chapterKey().endsWith(".md")
                    ? source.chapterKey().substring(0, source.chapterKey().length() - 3)
                    : source.chapterKey();
            NiReviewManifest.ChapterRef ref = new NiReviewManifest.ChapterRef();
            ref.setChapterKey(source.chapterKey());
            ref.setDocxHint(source.docxHint());
            ref.setMdFile("chapters/" + stem + ".md");
            ref.setReviewFile("chapters/" + stem + ".review.json");
            ref.setBaseHash(hash);
            manifest.getChapters().add(ref);

            NiReviewDocument document = new NiReviewDocument();
            document.setRoundId(roundId);
            document.setChapterKey(source.chapterKey());
            document.setChapterFile(stem + ".md");
            document.setBaseHash(hash);
            document.setReviewer("");

            snapshots.put(source.chapterKey(), md);
            reviews.put(source.chapterKey(), document);
            if (status.getChapters().containsKey(source.chapterKey())) {
                skipped.add(source.chapterKey());
            }
        }
        if (manifest.getChapters().isEmpty()) {
            throw new IOException("Keine Kapitel im Buch");
        }
        NiReviewZip.write(zipFile, manifest, snapshots, reviews,
                NiReviewProject.collectAssets(book.directory(), chapters));
        for (ChapterSource source : chapters) {
            String md = source.markdown() == null ? "" : source.markdown();
            NiReviewStore.markOutForReview(book.directory(), source.chapterKey(),
                    roundId, NiReviewHashes.sha256(md));
        }
        return new SendResult(zipFile, manifest.getChapters().size(), skipped);
    }

    /**
     * Merged Review-JSON auf Live-Kapitel. {@code liveMarkdown} wird nicht aus der ZIP genommen.
     */
    public static ImportResult importReturned(FileBook book, Path zipFile,
                                              Map<String, String> liveMarkdownByKey) throws IOException {
        NiReviewZip.Loaded loaded = NiReviewZip.read(zipFile);
        Set<String> known = liveMarkdownByKey.keySet();
        List<String> unknown = new ArrayList<>();
        List<String> remapped = new ArrayList<>();
        int merged = 0;
        for (NiReviewManifest.ChapterRef ref : loaded.manifest().getChapters()) {
            String key = resolveKnownChapterKey(ref, known);
            if (key == null) {
                unknown.add(ref.getChapterKey());
                continue;
            }
            String live = liveMarkdownByKey.get(key);
            String snapshot = loaded.snapshots().getOrDefault(ref.getChapterKey(),
                    loaded.snapshots().getOrDefault(key, ""));
            NiReviewDocument incoming = loaded.reviews().get(ref.getChapterKey());
            if (incoming == null) {
                incoming = loaded.reviews().get(key);
            }
            if (incoming == null) {
                incoming = new NiReviewDocument();
                incoming.setChapterKey(key);
                incoming.setRoundId(loaded.manifest().getRoundId());
                incoming.setBaseHash(ref.getBaseHash());
            }
            String liveHash = NiReviewHashes.sha256(live == null ? "" : live);
            NiReviewDocument result = NiReviewMerge.ontoLive(incoming, snapshot, live == null ? "" : live);
            if (!liveHash.equals(NiReviewHashes.sha256(snapshot))) {
                remapped.add(ref.getChapterKey());
            }
            NiReviewStore.saveReview(book.directory(), key, result);
            NiReviewStore.markReturned(book.directory(), key);
            merged++;
        }
        return new ImportResult(merged, unknown, remapped);
    }

    public static String chapterKeyForDocxName(String docxName) {
        if (docxName == null || docxName.isBlank()) {
            return null;
        }
        String name = docxName;
        if (name.toLowerCase().endsWith(".docx")) {
            name = name.substring(0, name.length() - 5);
        }
        return name + ".md";
    }

    public static String docxHintForChapterKey(String chapterKey) {
        if (chapterKey == null || chapterKey.isBlank()) {
            return "";
        }
        String name = chapterKey;
        if (name.toLowerCase().endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name + ".docx";
    }

    static String resolveKnownChapterKey(NiReviewManifest.ChapterRef ref, Set<String> known) {
        if (ref == null || known == null) {
            return null;
        }
        String key = ref.getChapterKey();
        if (key != null && known.contains(key)) {
            return key;
        }
        String fromHint = chapterKeyForDocxName(ref.getDocxHint());
        if (fromHint != null && known.contains(fromHint)) {
            return fromHint;
        }
        return null;
    }

    public static String readMarkdown(Path mdFile) throws IOException {
        if (mdFile == null || !Files.isRegularFile(mdFile)) {
            return "";
        }
        return Files.readString(mdFile, StandardCharsets.UTF_8);
    }

    public record FileBook(java.io.File directory) {
    }
}
