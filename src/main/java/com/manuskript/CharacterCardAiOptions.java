package com.manuskript;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Optionen für KI-Ausfüllen einer einzelnen Character Card.
 */
public record CharacterCardAiOptions(
        Set<String> fields,
        boolean includeWorldEditorContext,
        boolean includeCurrentChapter,
        boolean onlyEmptyFields,
        String additionalInstructions) {

    public CharacterCardAiOptions {
        fields = fields == null ? Set.of() : Set.copyOf(fields);
        additionalInstructions = additionalInstructions == null ? "" : additionalInstructions.trim();
    }

    public static CharacterCardAiOptions defaults() {
        return new CharacterCardAiOptions(
                new LinkedHashSet<>(CharacterSheetDocument.STANDARD_FIELD_LABELS),
                true,
                true,
                true,
                "");
    }

    public boolean hasFields() {
        return !fields.isEmpty();
    }

    public List<String> orderedFields() {
        return CharacterSheetDocument.STANDARD_FIELD_LABELS.stream()
                .filter(fields::contains)
                .toList();
    }
}
