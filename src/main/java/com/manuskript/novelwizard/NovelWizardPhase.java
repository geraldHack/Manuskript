package com.manuskript.novelwizard;

import java.util.List;

public enum NovelWizardPhase {
    BOOTSTRAP("Bootstrap", "Projekt einrichten", List.of()),
    BRAINSTORM("Brainstorm", "Genre, Stil, Umfang, Prämisse", List.of("context.txt", "style.txt")),
    WORLD("Welt", "Setting, Regeln, Lore", List.of("worldbuilding.txt")),
    CHARACTERS("Figuren", "Namen, Beschreibungen, Rollen, Beziehungen", List.of("characters.txt")),
    PLOT("Handlung", "Ausgangslage und Hauptkonflikt", List.of("outline.txt")),
    SYNOPSIS("Synopsis", "Gesamt-Synopsis", List.of("synopsis.txt")),
    STRUCTURE("Akte", "Akte und Handlungsabschnitte", List.of("akte.txt")),
    CHAPTERS("Kapitel", "Kapitel-Zusammenfassungen und DOCX", List.of("chapter.txt"));

    private final String title;
    private final String description;
    private final List<String> targetFiles;

    NovelWizardPhase(String title, String description, List<String> targetFiles) {
        this.title = title;
        this.description = description;
        this.targetFiles = targetFiles;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTargetFiles() {
        return targetFiles;
    }

    public NovelWizardPhase next() {
        NovelWizardPhase[] values = values();
        int idx = ordinal() + 1;
        return idx < values.length ? values[idx] : this;
    }
}
