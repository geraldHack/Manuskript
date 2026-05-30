package com.manuskript.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst Szene-Nummern aus Anweisungen und extrahiert einzelne Szenen aus nummerierten Outlines.
 */
public final class SceneOutlineParser {

    private static final Pattern SCENE_NUMBER = Pattern.compile(
        "(?i)(?:szene|scene)\\s*(?:nr\\.?|nummer|#)?\\s*(\\d+)|(?:schreib(?:e|en)?\\s+)(\\d+)\\.\\s*(?:szene|scene)");

    private SceneOutlineParser() {}

    public static Integer parseSceneNumberFromInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        Matcher m = SCENE_NUMBER.matcher(instruction.trim());
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            String num = g1 != null ? g1 : g2;
            if (num != null) {
                return Integer.parseInt(num);
            }
        }
        return null;
    }

    /**
     * Extrahiert eine Zeile der Form „7. Beschreibung …“ aus dem Outline-Text.
     */
    public static String extractScene(String outlineText, int sceneNumber) {
        if (outlineText == null || outlineText.isBlank() || sceneNumber < 1) {
            return "";
        }
        Pattern linePattern = Pattern.compile("^\\s*" + sceneNumber + "\\.\\s*(.+?)\\s*$");
        for (String line : outlineText.split("\\R")) {
            Matcher m = linePattern.matcher(line);
            if (m.matches()) {
                return sceneNumber + ". " + m.group(1).trim();
            }
        }
        return "";
    }
}
