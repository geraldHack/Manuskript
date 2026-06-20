package com.manuskript.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kurzbeschreibungen für Agenten-Reiter (Tab-Tooltips).
 */
public final class AgentTabTooltipSupport {

    private static final Map<String, String> BY_AGENT_ID = new LinkedHashMap<>();

    static {
        BY_AGENT_ID.put("8f863d56-5b14-46b6-9fae-8587f99958ae",
                "Findet Plotlöcher, logische Widersprüche und Inkonsistenzen im Kapitel.");
        BY_AGENT_ID.put("5a1546dd-c6d7-4615-8d53-2671b9910efa",
                "Prüft Dialoge auf klare Sprecherzuordnung und natürlichen Sprachrhythmus.");
        BY_AGENT_ID.put("d8b753f0-2684-414a-88a3-6cd4bef68650",
                "Findet Wortwiederholungen, monotone Satzanfänge und strukturelle Monotonie.");
        BY_AGENT_ID.put("a7b8c9d0-e1f2-4a5b-8c9d-0e1f2a3b4c5d",
                "Erkennt Tell-Stellen, bei denen Emotionen erzählt statt gezeigt werden.");
        BY_AGENT_ID.put(SelectionRevisionSupport.DEFAULT_AGENT_ID,
                "Überarbeitet die markierte Textstelle — per ▶, Kontextmenü oder optionaler Anweisung.");
        BY_AGENT_ID.put("40dd16a2-1683-4cd6-b443-5d5b6350061a",
                "Schreibt eine Szene als Fließprosa passend zu Outline, Stil und Worldbuilding.");
        BY_AGENT_ID.put("b2c3d4e5-f6a7-4890-b1c2-d3e4f5a6b7c8",
                "Schreib-Assistent mit Sessions und wählbarem Kapitelkontext.");
        BY_AGENT_ID.put(IdiomReviewSupport.DEFAULT_AGENT_ID,
                "Findet unnatürliche oder KI-typische Formulierungen in der Markierung (max. "
                        + IdiomReviewSupport.maxSelectionChars() + " Zeichen).");
    }

    private AgentTabTooltipSupport() {
    }

    public static String tooltipFor(AgentConfig config) {
        if (config == null) {
            return "";
        }
        String byId = BY_AGENT_ID.get(config.getId());
        if (byId != null && !byId.isBlank()) {
            return byId;
        }
        if (config.isSceneWritingAgent()) {
            return "Schreibt eine Szene als Fließprosa passend zu Outline, Stil und Worldbuilding.";
        }
        if (config.isChatbotAgent()) {
            return "Schreib-Assistent mit Sessions und wählbarem Kapitelkontext.";
        }
        if (config.isSelectionRevisionAgent()) {
            return "Überarbeitet die markierte Textstelle — per ▶, Kontextmenü oder optionaler Anweisung.";
        }
        if (config.isIdiomReviewAgent()) {
            return "Findet unnatürliche oder KI-typische Formulierungen in der Markierung (max. "
                    + IdiomReviewSupport.maxSelectionChars() + " Zeichen).";
        }
        if (config.isUserDefined()) {
            return "Eigener Analyse-Agent — System-Prompt unter ⚙ Einstellungen anpassen.";
        }
        return "Analysiert das Kapitel und schlägt konkrete Textänderungen vor (▶ oder ⚡ Echtzeit).";
    }

    public static String addTabTooltip() {
        return "Neuen Analyse-Agenten anlegen (eigener System-Prompt).";
    }
}
