package com.manuskript;

import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/**
 * Lädt Makros aus {@code config/makros/macros.txt} (gemeinsam mit dem Legacy-Editor).
 */
public final class MacroStorage {

    private static final Logger logger = LoggerFactory.getLogger(MacroStorage.class);

    private MacroStorage() {
    }

    public static File macrosFile() {
        File dir = new File(ResourceManager.getConfigDirectory(), "makros");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "macros.txt");
    }

    public static void loadInto(ObservableList<Macro> target) {
        target.clear();
        String savedMacros = "";
        File file = macrosFile();
        try {
            if (file.exists()) {
                savedMacros = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } else {
                String legacy = Preferences.userNodeForPackage(EditorWindow.class).get("savedMacros", "");
                if (legacy != null && !legacy.isEmpty()) {
                    savedMacros = legacy;
                }
            }
        } catch (Exception e) {
            logger.error("Makros laden fehlgeschlagen", e);
        }
        if (savedMacros == null || savedMacros.contains("|||") || savedMacros.contains("<<<MACRO>>>")) {
            savedMacros = "";
        }
        if (savedMacros.isBlank()) {
            return;
        }
        parseInto(savedMacros, target);
    }

    private static void parseInto(String macroContent, ObservableList<Macro> macros) {
        Macro currentMacro = null;
        MacroStep currentStep = null;
        for (String rawLine : macroContent.split("\n")) {
            String originalLine = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            String line = originalLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("MACRO:")) {
                currentMacro = new Macro(line.substring(6));
                macros.add(currentMacro);
                currentStep = null;
            } else if (line.startsWith("DESC:")) {
                if (currentMacro != null) {
                    currentMacro.setDescription(line.substring(5));
                }
            } else if (line.startsWith("STEP:")) {
                if (currentMacro != null) {
                    currentStep = new MacroStep();
                    currentMacro.addStep(currentStep);
                }
            } else if (line.startsWith("SEARCH:")) {
                if (currentStep != null) {
                    currentStep.setSearchText(line.substring(7));
                }
            } else if (originalLine.startsWith("REPLACE:")) {
                if (currentStep != null) {
                    currentStep.setReplaceText(originalLine.substring(8));
                }
            } else if (line.startsWith("REGEX:")) {
                if (currentStep != null) {
                    currentStep.setUseRegex("1".equals(line.substring(6)));
                }
            } else if (line.startsWith("CASE:")) {
                if (currentStep != null) {
                    currentStep.setCaseSensitive("1".equals(line.substring(5)));
                }
            } else if (line.startsWith("WORD:")) {
                if (currentStep != null) {
                    currentStep.setWholeWord("1".equals(line.substring(5)));
                }
            } else if (line.startsWith("ENABLED:")) {
                if (currentStep != null) {
                    currentStep.setEnabled("1".equals(line.substring(8)));
                }
            } else if (line.startsWith("STEPDESC:")) {
                if (currentStep != null) {
                    currentStep.setDescription(line.substring(9));
                }
            }
        }
    }
}
