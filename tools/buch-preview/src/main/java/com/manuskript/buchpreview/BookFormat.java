package com.manuskript.buchpreview;

import java.util.List;

/**
 * Trim-Formate für die HTML-Vorschau (mm). Kein Druck-PDF.
 */
public final class BookFormat {

    public final String id;
    public final String label;
    public final double widthMm;
    public final double heightMm;
    public final double marginInnerMm;
    public final double marginOuterMm;
    public final double marginTopMm;
    public final double marginBottomMm;
    public final boolean hardcover;
    public final double paperMm;
    public final double coverExtraMm;

    public BookFormat(String id, String label, double widthMm, double heightMm,
                      double marginInnerMm, double marginOuterMm,
                      double marginTopMm, double marginBottomMm,
                      boolean hardcover, double paperMm, double coverExtraMm) {
        this.id = id;
        this.label = label;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.marginInnerMm = marginInnerMm;
        this.marginOuterMm = marginOuterMm;
        this.marginTopMm = marginTopMm;
        this.marginBottomMm = marginBottomMm;
        this.hardcover = hardcover;
        this.paperMm = paperMm;
        this.coverExtraMm = coverExtraMm;
    }

    public static List<BookFormat> presets() {
        return List.of(
                new BookFormat("pocket", "Pocket 12,5 × 19 cm", 125, 190, 16, 12, 14, 16, false, 0.10, 0),
                new BookFormat("klappen", "Klappen-TB 13,5 × 21,5 cm", 135, 215, 18, 14, 16, 18, false, 0.10, 0),
                new BookFormat("a5", "A5 14,8 × 21,0 cm", 148, 210, 18, 14, 16, 18, false, 0.10, 0),
                new BookFormat("trade", "Trade 15,2 × 22,9 cm", 152, 229, 20, 15, 18, 20, false, 0.10, 0),
                new BookFormat("hardcover", "Hardcover 16,0 × 24,0 cm", 160, 240, 22, 16, 20, 22, true, 0.10, 3.0)
        );
    }

    public static BookFormat byId(String id) {
        if (id == null) {
            return presets().get(0);
        }
        for (BookFormat format : presets()) {
            if (format.id.equals(id)) {
                return format;
            }
        }
        return presets().get(0);
    }

    /**
     * Rücken in mm: (Seiten / 2) × Papierdicke, Hardcover plus Deckelzuschlag.
     */
    public static double spineMm(int pages, double paperMm, boolean hardcover, double coverExtraMm) {
        int safePages = Math.max(pages, 1);
        double spine = (safePages / 2.0) * (paperMm > 0 ? paperMm : 0.10);
        if (hardcover) {
            spine += coverExtraMm > 0 ? coverExtraMm : 3.0;
        }
        return spine;
    }

    public double spineMm(int pages) {
        return spineMm(pages, paperMm, hardcover, coverExtraMm);
    }

    @Override
    public String toString() {
        return label;
    }
}
