package com.manuskript.publish;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Baut die Publish-Checkliste aus Modell, Cover und TOC.
 */
public final class ChecklistEngine {

    public record Report(List<ChecklistItem> items) {
        public long passCount() {
            return items.stream().filter(i -> i.status() == ChecklistItem.Status.PASS).count();
        }

        public long failCount() {
            return items.stream().filter(i -> i.status() == ChecklistItem.Status.FAIL).count();
        }

        public long warnCount() {
            return items.stream().filter(i -> i.status() == ChecklistItem.Status.WARN).count();
        }

        public String summaryLine() {
            return passCount() + " OK · " + warnCount() + " Hinweise · " + failCount() + " offen";
        }
    }

    private ChecklistEngine() {
    }

    public static Report evaluate(
            PublishPackageModel model,
            CoverAnalyzer.CoverInfo cover,
            ChapterTocChecker.TocResult toc
    ) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(titleItem(model));
        items.add(authorItem(model));
        items.add(blurbItem(model));
        items.add(keywordsItem(model));
        items.addAll(coverItems(cover));
        items.add(tocItem(toc));
        items.add(rightsItem(model));
        items.add(impressumItem(model));
        items.add(isbnItem(model));
        return new Report(List.copyOf(items));
    }

    private static ChecklistItem titleItem(PublishPackageModel model) {
        if (blank(model.title)) {
            return fail("title", "Titel", "Titel fehlt");
        }
        return pass("title", "Titel", model.title.trim());
    }

    private static ChecklistItem authorItem(PublishPackageModel model) {
        if (blank(model.author)) {
            return fail("author", "Autor", "Autor fehlt");
        }
        return pass("author", "Autor", model.author.trim());
    }

    private static ChecklistItem blurbItem(PublishPackageModel model) {
        int chars = PlatformRules.blurbChars(model.blurb);
        if (chars == 0) {
            return fail("blurb", "Klappentext", "Klappentext fehlt");
        }
        if (!PlatformRules.descriptionOk(model.blurb)) {
            return fail("blurb", "Klappentext",
                    chars + " Zeichen — KDP-Limit " + PlatformRules.KDP_DESCRIPTION_MAX_CHARS);
        }
        if (chars < 120) {
            return warn("blurb", "Klappentext",
                    chars + " Zeichen — eher kurz für Shop-Listing");
        }
        return pass("blurb", "Klappentext", chars + " / " + PlatformRules.KDP_DESCRIPTION_MAX_CHARS + " Zeichen");
    }

    private static ChecklistItem keywordsItem(PublishPackageModel model) {
        model.ensureKeywordSlots();
        int filled = 0;
        int tooLong = 0;
        for (int i = 0; i < PlatformRules.KDP_KEYWORD_SLOTS; i++) {
            String kw = model.keywordAt(i).trim();
            if (!kw.isEmpty()) {
                filled++;
                if (kw.length() > PlatformRules.KDP_KEYWORD_MAX_CHARS) {
                    tooLong++;
                }
            }
        }
        if (tooLong > 0) {
            return fail("keywords", "Keywords",
                    tooLong + " Keyword(s) über " + PlatformRules.KDP_KEYWORD_MAX_CHARS + " Zeichen");
        }
        if (filled == 0) {
            return fail("keywords", "Keywords", "Noch keine Keywords");
        }
        if (filled < PlatformRules.KDP_KEYWORD_SLOTS) {
            return warn("keywords", "Keywords",
                    filled + " von " + PlatformRules.KDP_KEYWORD_SLOTS + " Feldern gefüllt");
        }
        return pass("keywords", "Keywords", "7 / 7 Felder");
    }

    private static List<ChecklistItem> coverItems(CoverAnalyzer.CoverInfo cover) {
        List<ChecklistItem> items = new ArrayList<>();
        if (cover == null || !cover.exists()) {
            items.add(fail("cover", "Cover-Datei", "Cover nicht gefunden"));
            return items;
        }
        if (!cover.readable()) {
            items.add(fail("cover", "Cover-Datei",
                    cover.error() == null ? "Cover unlesbar" : cover.error()));
            return items;
        }

        String size = cover.width() + "×" + cover.height() + " px, "
                + formatBytes(cover.bytes());
        items.add(pass("cover-file", "Cover-Datei", size));

        // KDP
        int shortSide = cover.shortSide();
        if (shortSide < PlatformRules.KDP_COVER_HARD_MIN_SHORT_SIDE) {
            items.add(fail("cover-kdp", "Cover KDP",
                    "Zu klein (kürzeste Seite " + shortSide + " px, Minimum "
                            + PlatformRules.KDP_COVER_HARD_MIN_SHORT_SIDE + ")"));
        } else if (shortSide < PlatformRules.KDP_COVER_MIN_SHORT_SIDE) {
            items.add(warn("cover-kdp", "Cover KDP",
                    "Unter Empfehlung (kürzeste Seite " + shortSide + " px, empfohlen ≥ "
                            + PlatformRules.KDP_COVER_MIN_SHORT_SIDE + ")"));
        } else if (!PlatformRules.ratioNearKdp(cover.width(), cover.height())) {
            items.add(warn("cover-kdp", "Cover KDP",
                    "Seitenverhältnis weicht von ~1,6:1 ab (Höhe/Breite)"));
        } else if (cover.height() < PlatformRules.KDP_COVER_HEIGHT
                || cover.width() < PlatformRules.KDP_COVER_WIDTH) {
            items.add(warn("cover-kdp", "Cover KDP",
                    "OK, aber unter Ideal " + PlatformRules.KDP_COVER_WIDTH + "×"
                            + PlatformRules.KDP_COVER_HEIGHT + " (B×H)"));
        } else {
            items.add(pass("cover-kdp", "Cover KDP", "Maße und Ratio passen"));
        }
        if (cover.bytes() > PlatformRules.KDP_COVER_MAX_BYTES) {
            items.add(fail("cover-kdp-size", "Cover KDP Dateigröße",
                    "Über 50 MB"));
        }
        String fmt = cover.format() == null ? "" : cover.format().toLowerCase(Locale.ROOT);
        if (!fmt.isEmpty() && !(fmt.equals("jpg") || fmt.equals("jpeg") || fmt.equals("tif") || fmt.equals("tiff"))) {
            items.add(warn("cover-kdp-fmt", "Cover KDP Format",
                    "Projekt: ." + fmt + " — Upload bei KDP meist als JPEG/TIFF"));
        }

        // Tolino
        if (!cover.portrait()) {
            items.add(warn("cover-tolino", "Cover Tolino", "Hochformat empfohlen"));
        } else if (cover.width() < PlatformRules.TOLINO_COVER_MIN_WIDTH) {
            items.add(warn("cover-tolino", "Cover Tolino",
                    "Breite " + cover.width() + " px — empfohlen ≥ "
                            + PlatformRules.TOLINO_COVER_MIN_WIDTH));
        } else if (cover.bytes() > PlatformRules.TOLINO_COVER_MAX_BYTES) {
            items.add(warn("cover-tolino", "Cover Tolino",
                    "Datei > 5 MB — ggf. verkleinern"));
        } else {
            items.add(pass("cover-tolino", "Cover Tolino", "Hochformat und Breite OK"));
        }
        return items;
    }

    private static ChecklistItem tocItem(ChapterTocChecker.TocResult toc) {
        if (toc == null || toc.empty()) {
            return fail("toc", "Kapitel / TOC", "Keine Buchauswahl");
        }
        if (toc.missingMd() > 0) {
            return fail("toc", "Kapitel / TOC",
                    toc.missingMd() + " von " + toc.selectedCount() + " ohne Markdown");
        }
        if (toc.withoutHeading() > 0) {
            return warn("toc", "Kapitel / TOC",
                    toc.withoutHeading() + " Kapitel ohne #-Überschrift");
        }
        return pass("toc", "Kapitel / TOC", toc.selectedCount() + " Kapitel mit Überschrift");
    }

    private static ChecklistItem rightsItem(PublishPackageModel model) {
        if (model.rightsConfirmed) {
            return pass("rights", "Rechte", "Bestätigt");
        }
        return warn("rights", "Rechte", "Bitte bestätigen: du besitzt die Veröffentlichungsrechte");
    }

    private static ChecklistItem impressumItem(PublishPackageModel model) {
        if (model.impressumNoted) {
            return pass("impressum", "Impressum / Angaben", "Notiz gesetzt");
        }
        return info("impressum", "Impressum / Angaben",
                "Je nach Vertriebskanal Impressum/Angaben im Buch oder Listing prüfen");
    }

    private static ChecklistItem isbnItem(PublishPackageModel model) {
        if (blank(model.isbn)) {
            return info("isbn", "ISBN", "Optional — KDP kann eine kostenlose ISBN vergeben");
        }
        return pass("isbn", "ISBN", model.isbn.trim());
    }

    private static ChecklistItem pass(String id, String label, String detail) {
        return new ChecklistItem(id, label, ChecklistItem.Status.PASS, detail);
    }

    private static ChecklistItem warn(String id, String label, String detail) {
        return new ChecklistItem(id, label, ChecklistItem.Status.WARN, detail);
    }

    private static ChecklistItem fail(String id, String label, String detail) {
        return new ChecklistItem(id, label, ChecklistItem.Status.FAIL, detail);
    }

    private static ChecklistItem info(String id, String label, String detail) {
        return new ChecklistItem(id, label, ChecklistItem.Status.INFO, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.GERMAN, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.GERMAN, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Für Tests: Cover-Auswertung ohne Datei. */
    public static List<ChecklistItem> evaluateCoverOnly(CoverAnalyzer.CoverInfo cover) {
        return coverItems(cover);
    }
}
