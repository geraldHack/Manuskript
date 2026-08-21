package com.manuskript;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroStorageTest {

    @Test
    void macrosFilePointsAtBundledConfigNotBareCwd() {
        String path = MacroStorage.macrosFile().getAbsolutePath().replace('\\', '/');
        assertTrue(path.endsWith("/config/makros/macros.txt"), () -> "Unerwarteter Makropfad: " + path);
        assertTrue(MacroStorage.macrosFile().isFile(), "Standard-Makro muss im App-/Repo-Config liegen");
    }

    @Test
    void loadIntoFindsDefaultTextBereinigung() {
        ObservableList<Macro> macros = FXCollections.observableArrayList();
        MacroStorage.loadInto(macros);
        assertFalse(macros.isEmpty(), "Erstinstallation muss das Standard-Makro laden");
        assertTrue(macros.stream().anyMatch(macro -> "Text-Bereinigung".equals(macro.getName())),
                () -> "Geladene Makros: " + macros.stream().map(Macro::getName).toList());
        Macro first = macros.stream()
                .filter(macro -> "Text-Bereinigung".equals(macro.getName()))
                .findFirst()
                .orElseThrow();
        assertFalse(first.getSteps().isEmpty());
    }

    @Test
    void parseIntoReadsMacroBlock() {
        ObservableList<Macro> macros = FXCollections.observableArrayList();
        MacroStorage.parseInto("""
                MACRO:Text-Bereinigung
                DESC:Test
                STEP:1
                SEARCH:foo
                REPLACE:bar
                REGEX:1
                CASE:0
                WORD:0
                ENABLED:1
                STEPDESC:Schritt
                ENDMACRO
                """, macros);
        assertEquals(1, macros.size());
        assertEquals("Text-Bereinigung", macros.getFirst().getName());
        assertEquals("foo", macros.getFirst().getSteps().getFirst().getSearchText());
        assertEquals("bar", macros.getFirst().getSteps().getFirst().getReplaceText());
        assertTrue(macros.getFirst().getSteps().getFirst().isUseRegex());
    }
}
