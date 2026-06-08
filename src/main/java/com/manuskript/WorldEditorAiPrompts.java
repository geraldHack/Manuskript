package com.manuskript;

/**
 * Prompts fuer KI-Aktionen im Welt-Editor (Generierung vs. Extraktion aus dem Manuskript).
 */
public final class WorldEditorAiPrompts {

    private WorldEditorAiPrompts() {
    }

    public static String generatePrompt(String filename) {
        return switch (filename) {
            case "chapter.txt" ->
                    "Erstelle Zusammenfassungen aller Kapitel des Buches. Nutze Markdown mit ## Ueberschrift pro Kapitel.";
            case "characters.txt" -> characterSheetPrompt(false);
            case "context.txt" ->
                    "Erstelle eine strukturierte Uebersicht wichtiger Projekt-Grunddaten (Genre, Praemisse, Themen, Konflikt). "
                            + "Nutze Markdown mit Ueberschriften und Stichpunkten.";
            case "outline.txt" ->
                    "Erstelle ein Outline mit Szenen fuer alle Kapitel. Markdown mit Kapitel-Nummern und Szenen-Beschreibungen.";
            case "akte.txt" ->
                    "Erstelle eine Akt- und Dramaturgiestruktur (Akte, Wendepunkte, Eskalation). Markdown mit Ueberschriften pro Akt.";
            case "style.txt" ->
                    "Erstelle eine Beschreibung des Schreibstils (Perspektive, Tempo, Ton, Satzbau). Markdown.";
            case "synopsis.txt" -> """
                    Erstelle eine Synopsis (Handlung, Konflikt, Wendepunkte, Ende).
                    Hauptfiguren nur kurz (Name + ein Satz Rolle). Markdown.
                    """;
            case "worldbuilding.txt" ->
                    "Erstelle Worldbuilding (Setting, Regeln, Gesellschaft, Orte). Markdown mit Kategorien.";
            default -> "Erhalte Informationen ueber das Projekt.";
        };
    }

    public static String extractFromChaptersPrompt(String filename, boolean append) {
        return extractFromChaptersPrompt(filename, append, null);
    }

    public static String extractFromChaptersPrompt(String filename, boolean append, WorldEditorExtractScope scope) {
        String merge = append
                ? "Bestehenden Tab-Inhalt wird programmatisch zusammengefuehrt. "
                        + "Gib NUR neue oder aktualisierte Markdown-Abschnitte zurueck (## …), "
                        + "nicht den gesamten Tab und nicht den bisherigen Inhalt wiederholen. "
                        + "Neue Figuren/Fakten als Abschnitte; vorhandene Abschnitte nur wenn sich etwas aendert."
                : "Ersetze den bisherigen Inhalt vollstaendig durch eine neue Fassung aus dem Manuskript.";
        String scopeHint = buildScopeHint(scope, filename, append);
        return switch (filename) {
            case "characters.txt" -> characterSheetPrompt(true, scope) + "\n" + merge + scopeHint
                    + "\nNur Figuren, die in den genannten Kapiteln vorkommen oder klar impliziert sind.";
            case "context.txt" -> """
                    Extrahiere aus dem Manuskript die Projekt-Grunddaten auf Deutsch.
                    Markdown mit ### Grunddaten (Kurz) und Feldern:
                    - **Genre:**, **Subgenre:**, **Praemisse:**, **Umfang:** (falls erkennbar),
                      **Stil:**, **Ton:**, **Themen:**
                    Nur belegbare Aussagen aus dem Text, keine Erfindungen.
                    """ + merge + scopeHint;
            case "worldbuilding.txt" -> """
                    Extrahiere aus dem Manuskript alle Worldbuilding-Fakten (Setting, Regeln, Orte, Gesellschaft, Magie/Technik).
                    Markdown mit sinnvollen ##-Abschnitten. Nur belegbare Details aus dem Manuskript.
                    """ + merge + scopeHint;
            case "style.txt" -> """
                    Leite aus den Kapitelproben den tatsaechlichen Schreibstil ab (Perspektive, Zeitform, Satzlaenge, Ton, Dialog).
                    Markdown. Belege mit kurzen Paraphrasen, keine woertlichen Zitate noetig.
                    """ + merge + scopeHint;
            case "outline.txt" -> """
                    Rekonstruiere aus dem Manuskript die Handlung als Outline (Kapitel / Szenen in erzaehlter Reihenfolge).
                    Markdown mit ## Kapitel … und Stichpunkten zu Szenen. Nur was im Text vorkommt.
                    """ + merge + scopeHint;
            case "akte.txt" -> """
                    Leite aus dem Manuskript eine Akt-Struktur ab (Akte, Wendepunkte, Eskalation).
                    Markdown mit ## Akte … – auch wenn der Entwurf noch unfertig ist, markiere Luecken ehrlich.
                    """ + merge + scopeHint;
            case "synopsis.txt" -> """
                    Schreibe eine Synopsis aus dem bisherigen Manuskript (Spoiler erlaubt).
                    Hauptfiguren nur kurz. Markdown, zusammenhaengender Fliesstext oder Abschnitte.
                    """ + merge + scopeHint;
            default -> generatePrompt(filename);
        };
    }

    public static int maxTokensForFile(String filename, boolean extract) {
        if (extract) {
            return switch (filename) {
                case "synopsis.txt", "outline.txt", "characters.txt" -> 4096;
                default -> 3000;
            };
        }
        return "chapter.txt".equals(filename) ? 2000 : 2500;
    }

    public static boolean supportsExtractFromChapters(String filename) {
        return switch (filename) {
            case "characters.txt", "context.txt", "worldbuilding.txt", "style.txt",
                 "outline.txt", "akte.txt", "synopsis.txt" -> true;
            default -> false;
        };
    }

    private static String characterSheetPrompt(boolean fromManuscript) {
        return characterSheetPrompt(fromManuscript, null);
    }

    private static String characterSheetPrompt(boolean fromManuscript, WorldEditorExtractScope scope) {
        String source = fromManuscript
                ? "Analysiere die gewaehlten Kapitel und erstelle strukturierte Character Sheets."
                : "Erstelle strukturierte Character Sheets fuer alle wichtigen Figuren.";
        if (scope != null && scope.hasCharacterFocus()) {
            source += " Konzentriere dich auf die genannten Figuren; lege fehlende Eintraege an "
                    + "und aktualisiere vorhandene. Andere Figuren nur, wenn ausdruecklich noetig.";
        } else if (fromManuscript) {
            source += " Erfasse jede namentlich genannte Person (Vor- und/oder Nachname), auch Nebenfiguren.";
        }
        return source + """
                
                Kein Interview, keine Fragen/Antworten.
                Pro Figur: ## Vorname Nachname
                **Rolle:**, **Alter / Aussehen:**, **Persoenlichkeit:**, **Hintergrund:**,
                **Ziele:**, **Schwaechen / innere Konflikte:**, **Beziehungen:**, **Character Arc:**
                Felder nur mit belegbaren Informationen fuellen; fehlende Angaben weglassen oder „unbekannt“.
                """;
    }

    private static String buildScopeHint(WorldEditorExtractScope scope, String filename, boolean append) {
        if (scope == null || !scope.hasChapterSelection()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\nAnalysiere NUR diese Kapitel (Volltext unten):\n");
        for (String md : scope.selectedMdFileNames()) {
            sb.append("- ").append(md.replace(".md", "").trim()).append("\n");
        }
        if (scope.hasCharacterFocus()) {
            sb.append("\nFiguren-Fokus (Prioritaet): ").append(scope.characterFocus().trim()).append("\n");
            if ("characters.txt".equals(filename) && append) {
                sb.append("Liefere nur Character-Sheet-Abschnitte (## Name) fuer die genannten Figuren.\n");
            }
        }
        sb.append("Ignoriere Kapitel-Zusammenfassungen (chapter.txt); nutze nur den mitgelieferten Volltext.\n");
        return sb.toString();
    }
}
