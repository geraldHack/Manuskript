package com.manuskript.novelwizard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public class NovelWizardPromptRegistry {
    private final Map<NovelWizardPhase, PromptDef> prompts = new EnumMap<>(NovelWizardPhase.class);

    public NovelWizardPromptRegistry(Path configFile) {
        loadDefaults();
        loadOverrides(configFile);
    }

    public PromptDef get(NovelWizardPhase phase) {
        return prompts.getOrDefault(phase, prompts.get(NovelWizardPhase.BRAINSTORM));
    }

    private void loadDefaults() {
        put(NovelWizardPhase.BRAINSTORM,
                "Du bist ein erfahrener Roman-Coach. Entwickle mit dem Autor das Grundgeruest eines Romans.",
                "Frage genau EIN noch offenes Pflichtfeld ab (Reihenfolge: Genre, Subgenre, Praemisse, Umfang/Wortzahl, "
                        + "Stil, Ton, Themen). Benenne das Feld in der Frage explizit. "
                        + "4 bis 6 projektpassende Optionen. Keine Plot- oder Quest-Fragen vor den Pflichtfeldern.");
        put(NovelWizardPhase.WORLD,
                "Du bist Worldbuilding-Berater. Entwickle Setting, Weltregeln, Gesellschaft und Konflikte.",
                "Stelle eine konkrete Frage zur Welt. Biete 4 bis 6 passende Antwortoptionen (ohne Freitext-Option).");
        put(NovelWizardPhase.CHARACTERS,
                "Du bist Figurenentwickler. Jede wichtige Figur braucht Namen, Rolle, Beschreibung, "
                        + "Persoenlichkeit, Hintergrund, Beziehungen und Arc.",
                "Stelle genau EINE Frage zur Figurenentwicklung. Zuerst Namen und Rollen, dann Aussehen/Beschreibung, "
                        + "dann Motivation und Beziehungen. Optionen mit konkreten Namen und beschreibenden Texten.");
        put(NovelWizardPhase.PLOT,
                "Du bist Plot-Entwickler. Entwickle Ausgangslage, Hauptkonflikt, Stakes und Wendepunkte.",
                "Stelle eine naechste Plot-Frage und biete 4 bis 6 Optionen.");
        put(NovelWizardPhase.SYNOPSIS,
                "Du bist ein professioneller Expose- und Synopsis-Autor. Schreibe eine zusammenhaengende "
                        + "Gesamt-Synopsis aus dem Projektstand – keine Rueckfragen.",
                "Schreibe jetzt eine vollstaendige Synopsis (ca. 800–1500 Woerter) in <CONTENT>. "
                        + "Nutze Brainstorm, Welt, Figuren und Handlung. Hauptfiguren nur kurz nennen – "
                        + "keine ausfuehrlichen Character Sheets (die stehen in characters.txt). Keine Plot-Rueckfragen.");
        put(NovelWizardPhase.STRUCTURE,
                "Du bist Dramaturg. Zerlege den Roman in Akte, Handlungsabschnitte und Plot Points.",
                "Erzeuge oder verfeinere eine Akt- und Abschnittsstruktur.");
        put(NovelWizardPhase.CHAPTERS,
                "Du bist Entwicklungslektor. Erzeuge eine sinnvolle Kapitelliste mit knappen Zusammenfassungen.",
                "Erzeuge oder verfeinere Kapitel-Zusammenfassungen im Markdown-Format.");
    }

    private void put(NovelWizardPhase phase, String systemPrompt, String instruction) {
        prompts.put(phase, new PromptDef(phase.name(), phase.getTitle(), systemPrompt, instruction));
    }

    private void loadOverrides(Path configFile) {
        if (configFile == null || !Files.exists(configFile)) {
            return;
        }
        try {
            Type type = new TypeToken<Map<String, PromptDef>>() { }.getType();
            Map<String, PromptDef> overrides = new Gson().fromJson(
                    Files.readString(configFile, StandardCharsets.UTF_8), type);
            if (overrides == null) {
                return;
            }
            for (Map.Entry<String, PromptDef> entry : overrides.entrySet()) {
                NovelWizardPhase phase = NovelWizardPhase.valueOf(entry.getKey());
                if (entry.getValue() != null) {
                    prompts.put(phase, entry.getValue());
                }
            }
        } catch (Exception ignored) {
            // Defaults bleiben gueltig, wenn die Konfigurationsdatei fehlt oder fehlerhaft ist.
        }
    }

    public static class PromptDef {
        public String id;
        public String title;
        public String systemPrompt;
        public String instruction;

        public PromptDef(String id, String title, String systemPrompt, String instruction) {
            this.id = id;
            this.title = title;
            this.systemPrompt = systemPrompt;
            this.instruction = instruction;
        }
    }
}
