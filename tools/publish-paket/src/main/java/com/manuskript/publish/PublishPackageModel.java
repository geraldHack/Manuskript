package com.manuskript.publish;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistierte Publish-Metadaten ({@code data/publish_package.json}).
 */
public final class PublishPackageModel {

    public String title = "";
    public String subtitle = "";
    public String author = "";
    public String isbn = "";
    public String language = "de";
    public String ageRating = "";
    public String blurb = "";
    public String coverPath = "";
    public String authorHints = "";
    /** Zusätzliche Vorgaben nur für KI-Klappentext (Genre, Stil, Spoiler-Grenze). */
    public String blurbHints = "";
    /** Klappentext in data/pandoc_metadata.json (Feld abstract) für Buch-Export übernehmen. */
    public Boolean blurbToExport = Boolean.TRUE;
    public List<String> keywords = new ArrayList<>();
    public boolean rightsConfirmed;
    public boolean impressumNoted;

    public PublishPackageModel() {
        ensureKeywordSlots();
    }

    public void ensureKeywordSlots() {
        if (keywords == null) {
            keywords = new ArrayList<>();
        }
        while (keywords.size() < PlatformRules.KDP_KEYWORD_SLOTS) {
            keywords.add("");
        }
        if (keywords.size() > PlatformRules.KDP_KEYWORD_SLOTS) {
            keywords = new ArrayList<>(keywords.subList(0, PlatformRules.KDP_KEYWORD_SLOTS));
        }
    }

    public String keywordAt(int index) {
        ensureKeywordSlots();
        if (index < 0 || index >= keywords.size()) {
            return "";
        }
        String value = keywords.get(index);
        return value == null ? "" : value;
    }

    public void setKeyword(int index, String value) {
        ensureKeywordSlots();
        if (index < 0 || index >= keywords.size()) {
            return;
        }
        keywords.set(index, value == null ? "" : value);
    }

    /** {@code null} oder fehlend in JSON = Export aktiv (Default). */
    public boolean exportBlurb() {
        return blurbToExport == null || blurbToExport;
    }
}
