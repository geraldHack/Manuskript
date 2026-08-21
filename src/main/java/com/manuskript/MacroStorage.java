package com.manuskript;

import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/**
 * Lädt Makros aus {@code config/makros/macros.txt} im App-Bundle (nicht relativ zum CWD).
 * Fehlt die Datei, wird das mitgelieferte Standard-Makro aus dem Classpath verwendet.
 */
public final class MacroStorage {

    private static final Logger logger = LoggerFactory.getLogger(MacroStorage.class);
    static final String MACROS_RELATIVE = "config/makros/macros.txt";
    private static final String CLASSPATH_DEFAULT = "/makros/macros.txt";

    private MacroStorage() {
    }

    /**
     * Nutz- und Installationsdatei: App-Home {@code config/makros/macros.txt}, sonst Arbeitsverzeichnis.
     */
    public static File macrosFile() {
        return ApplicationPaths.resolveConfigPath(MACROS_RELATIVE);
    }

    public static void loadInto(ObservableList<Macro> target) {
        target.clear();
        String savedMacros = readMacrosContent();
        if (savedMacros == null || savedMacros.contains("|||") || savedMacros.contains("<<<MACRO>>>")) {
            savedMacros = "";
        }
        if (savedMacros.isBlank()) {
            savedMacros = readClasspathDefault();
        }
        if (savedMacros == null || savedMacros.isBlank()) {
            logger.warn("Keine Makros gefunden (weder {} noch Classpath {})",
                    macrosFile().getAbsolutePath(), CLASSPATH_DEFAULT);
            return;
        }
        parseInto(savedMacros, target);
        logger.info("Makros geladen: {} aus {}", target.size(), macrosFile().getAbsolutePath());
    }

    public static void saveFrom(ObservableList<Macro> macros) {
        try {
            String macroData = serialize(macros);
            File file = macrosFile();
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(file.toPath(), macroData, StandardCharsets.UTF_8);
            Preferences.userNodeForPackage(EditorWindow.class).put("savedMacros", macroData);
            Preferences.userNodeForPackage(EditorWindow.class).flush();
        } catch (Exception e) {
            logger.error("Makros speichern fehlgeschlagen", e);
            throw new RuntimeException("Makros speichern fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public static String serialize(ObservableList<Macro> macros) {
        StringBuilder sb = new StringBuilder();
        for (Macro macro : macros) {
            sb.append("MACRO:").append(macro.getName()).append("\n");
            sb.append("DESC:").append(macro.getDescription() != null ? macro.getDescription() : "").append("\n");
            for (MacroStep step : macro.getSteps()) {
                String replaceText = step.getReplaceText() != null ? step.getReplaceText() : "";
                sb.append("STEP:").append(step.getStepNumber()).append("\n");
                sb.append("SEARCH:").append(step.getSearchText() != null ? step.getSearchText() : "").append("\n");
                sb.append("REPLACE:").append(replaceText).append("\n");
                sb.append("REGEX:").append(step.isUseRegex() ? "1" : "0").append("\n");
                sb.append("CASE:").append(step.isCaseSensitive() ? "1" : "0").append("\n");
                sb.append("WORD:").append(step.isWholeWord() ? "1" : "0").append("\n");
                sb.append("ENABLED:").append(step.isEnabled() ? "1" : "0").append("\n");
                sb.append("STEPDESC:").append(step.getDescription() != null ? step.getDescription() : "").append("\n");
                sb.append("REPLACECOUNT:").append(step.getReplacementCount()).append("\n");
            }
            sb.append("ENDMACRO\n");
        }
        return sb.toString();
    }

    private static String readMacrosContent() {
        File file = macrosFile();
        try {
            if (file.isFile()) {
                String fromFile = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (fromFile != null && !fromFile.isBlank()) {
                    return fromFile;
                }
            }
        } catch (Exception e) {
            logger.error("Makros laden fehlgeschlagen: {}", file.getAbsolutePath(), e);
        }
        try {
            String legacy = Preferences.userNodeForPackage(EditorWindow.class).get("savedMacros", "");
            if (legacy != null && !legacy.isBlank()) {
                return legacy;
            }
        } catch (Exception e) {
            logger.warn("Legacy-Makro-Preferences nicht lesbar", e);
        }
        return "";
    }

    private static String readClasspathDefault() {
        try (InputStream in = MacroStorage.class.getResourceAsStream(CLASSPATH_DEFAULT)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Classpath-Standard-Makro nicht lesbar", e);
            return "";
        }
    }

    static void parseInto(String macroContent, ObservableList<Macro> macros) {
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
