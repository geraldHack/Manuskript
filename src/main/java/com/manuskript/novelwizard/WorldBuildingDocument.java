package com.manuskript.novelwizard;

/**
 * Struktur fuer {@code worldbuilding.txt} aus dem Roman-Assistenten (Phase WORLD).
 */
public final class WorldBuildingDocument {

    public static final String SECTION_SETTING = "## Setting";
    public static final String SECTION_PLACES = "## Orte";
    public static final String SECTION_LORE = "## Lore";

    private WorldBuildingDocument() {
    }

    public static boolean hasRequiredSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        return markdown.contains(SECTION_SETTING)
                && markdown.contains(SECTION_PLACES)
                && markdown.contains(SECTION_LORE);
    }
}
