package com.manuskript;

import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Registry aller konfigurierbaren Parameter inkl. Hilfetexten für die Parameter-Verwaltung.
 */
public final class ParameterRegistry {

    private static final List<ParameterDef> ALL = new ArrayList<>();

    static {
        // —— Ollama ——
        add("ollama.temperature", ParameterDef.Type.DOUBLE, "0.3",
                "Temperatur für KI-Antworten (0 = deterministisch, höher = kreativer). Typisch 0.1–0.9.", "Ollama");
        add("ollama.max_tokens", ParameterDef.Type.INT, "2048",
                "Maximale Anzahl Tokens pro Antwort.", "Ollama");
        add("ollama.top_p", ParameterDef.Type.DOUBLE, "0.7",
                "Nukleus-Sampling (Top-P). Begrenzt die Auswahl auf wahrscheinliche Tokens.", "Ollama");
        add("ollama.repeat_penalty", ParameterDef.Type.DOUBLE, "1.3",
                "Strafe für Wiederholungen. Höher = weniger Wiederholungen.", "Ollama");
        add("ollama.http_connect_timeout_secs", ParameterDef.Type.INT, "30",
                "Timeout in Sekunden für die Verbindung zu Ollama.", "Ollama");
        add("ollama.http_request_timeout_secs", ParameterDef.Type.INT, "360",
                "Timeout in Sekunden für die gesamte Ollama-Anfrage.", "Ollama");
        add("session.max_qapairs_per_session", ParameterDef.Type.INT, "20",
                "Maximale Anzahl Frage-Antwort-Paare pro Session vor automatischer Aufteilung.", "Ollama");

        // —— TTS (ComfyUI) ——
        add("comfyui.base_url", ParameterDef.Type.STRING, "http://127.0.0.1:8188",
                "Basis-URL des ComfyUI-Servers (z. B. http://127.0.0.1:8188).", "TTS (ComfyUI)");
        add("comfyui.timeout_seconds", ParameterDef.Type.INT, "600",
                "Wartezeit in Sekunden auf ComfyUI-TTS-Abschluss (z. B. bei langem Text).", "TTS (ComfyUI)");
        add("comfyui.restart_script", ParameterDef.Type.STRING, "",
                "Pfad zum Skript zum Neustarten von ComfyUI (z. B. .bat oder .cmd). Leer = Restart-Button inaktiv.", "TTS (ComfyUI)");

        // —— TTS (ElevenLabs) ——
        add("tts.elevenlabs_api_key", ParameterDef.Type.STRING, "",
                "API-Key für ElevenLabs (kostenpflichtiger TTS-Dienst). Leer = nur ComfyUI-Stimmen verfügbar.", "TTS (ElevenLabs)");

        // —— Online-Lektorat (OpenAI-kompatible API) ——
        add("api.lektorat.api_key", ParameterDef.Type.STRING, "",
                "API-Key für die Online-Lektorat-API (z. B. OpenAI).", "Online-Lektorat");
        add("api.lektorat.base_url", ParameterDef.Type.STRING, "https://api.openai.com/v1",
                "Basis-URL der API (z. B. https://api.openai.com/v1).", "Online-Lektorat");
        add("api.lektorat.model", ParameterDef.Type.STRING, "gpt-4o-mini",
                "Modell für das Lektorat (Dropdown nach „Modelle laden“ oder freie Eingabe).", "Online-Lektorat");
        add("api.lektorat.extra_prompt", ParameterDef.Type.STRING, "",
                "Zusätzlicher Prompt (z. B. Stil-Anweisungen), wird an den Lektorat-Prompt angehängt.", "Online-Lektorat");
        add("api.lektorat.type", ParameterDef.Type.STRING, "allgemein",
                "Art des Lektorats: allgemein, Stil, Grammatik, Plot/Dramaturgie.", "Online-Lektorat");

        // —— Projekt ——
        add("project.root.directory", ParameterDef.Type.STRING, "",
                "Projektwurzel-Verzeichnis (Unterordner = Projekte).", "Projekt");

        // —— UI ——
        add("main_window_theme", ParameterDef.Type.INT, "0",
                "Theme des Hauptfensters (0–5).", "UI");
        add("ui.theme", ParameterDef.Type.STRING, "default",
                "UI-Theme (z. B. default, weiss).", "UI");
        add("ui.editor_theme", ParameterDef.Type.INT, "4",
                "Editor-Theme-Index.", "UI");
        add("ui.selected_session", ParameterDef.Type.STRING, "default",
                "Aktuell ausgewählte Session.", "UI");
        add("help_enabled", ParameterDef.Type.BOOLEAN, "true",
                "Kontexthilfe ein-/ausschalten.", "UI");
        add("paragraph_marking_enabled", ParameterDef.Type.BOOLEAN, "false",
                "Absatzmarkierung im Editor anzeigen.", "UI");

        // —— Editor ——
        add("editor.line-spacing", ParameterDef.Type.DOUBLE, "2.5",
                "Zeilenabstand im Editor.", "Editor");
        add("editor.paragraph-spacing", ParameterDef.Type.DOUBLE, "10",
                "Absatzabstand im Editor.", "Editor");

        // —— DOCX (Auswahl der wichtigsten) ——
        add("docx.defaultFont", ParameterDef.Type.STRING, "Calibri",
                "Standard-Schriftart im DOCX-Export.", "DOCX");
        add("docx.headingFont", ParameterDef.Type.STRING, "Calibri",
                "Schriftart für Überschriften.", "DOCX");
        add("docx.defaultFontSize", ParameterDef.Type.INT, "11",
                "Standard-Schriftgröße (Pt).", "DOCX");
        add("docx.justifyText", ParameterDef.Type.BOOLEAN, "false",
                "Blocksatz im Export.", "DOCX");
        add("docx.lineSpacing", ParameterDef.Type.DOUBLE, "1.15",
                "Zeilenabstand im DOCX.", "DOCX");
        add("docx.topMargin", ParameterDef.Type.DOUBLE, "2.5",
                "Oberer Seitenrand (cm).", "DOCX");
        add("docx.bottomMargin", ParameterDef.Type.DOUBLE, "2.5",
                "Unterer Seitenrand (cm).", "DOCX");
        add("docx.leftMargin", ParameterDef.Type.DOUBLE, "2.5",
                "Linker Seitenrand (cm).", "DOCX");
        add("docx.rightMargin", ParameterDef.Type.DOUBLE, "2.5",
                "Rechter Seitenrand (cm).", "DOCX");
        add("docx.language", ParameterDef.Type.STRING, "de-DE",
                "Sprache des Dokuments (z. B. de-DE, en-US).", "DOCX");
        add("docx.includeTableOfContents", ParameterDef.Type.BOOLEAN, "false",
                "Inhaltsverzeichnis einfügen.", "DOCX");
        add("docx.includePageNumbers", ParameterDef.Type.BOOLEAN, "false",
                "Seitenzahlen einfügen.", "DOCX");
        add("docx.codeFont", ParameterDef.Type.STRING, "Consolas",
                "Schriftart für Code-Blöcke.", "DOCX");
        add("docx.heading1Size", ParameterDef.Type.INT, "18", "Schriftgröße Überschrift 1 (Pt).", "DOCX");
        add("docx.heading2Size", ParameterDef.Type.INT, "16", "Schriftgröße Überschrift 2 (Pt).", "DOCX");
        add("docx.heading3Size", ParameterDef.Type.INT, "14", "Schriftgröße Überschrift 3 (Pt).", "DOCX");
        add("docx.enableHyphenation", ParameterDef.Type.BOOLEAN, "false", "Silbentrennung.", "DOCX");
        add("docx.paragraphSpacing", ParameterDef.Type.DOUBLE, "1.0", "Absatzabstand.", "DOCX");
        add("docx.firstLineIndent", ParameterDef.Type.BOOLEAN, "false", "Erste Zeile einrücken.", "DOCX");
        add("docx.firstLineIndentSize", ParameterDef.Type.DOUBLE, "1.0", "Einrückung erste Zeile (cm).", "DOCX");
        add("docx.centerH1", ParameterDef.Type.BOOLEAN, "false", "Überschrift 1 zentrieren.", "DOCX");
        add("docx.newPageBeforeH1", ParameterDef.Type.BOOLEAN, "false", "Neue Seite vor H1.", "DOCX");
        add("docx.newPageBeforeH2", ParameterDef.Type.BOOLEAN, "false", "Neue Seite vor H2.", "DOCX");
        add("docx.boldHeadings", ParameterDef.Type.BOOLEAN, "true", "Überschriften fett.", "DOCX");
        add("docx.headingColor", ParameterDef.Type.STRING, "2F5496", "Farbe Überschriften (Hex ohne #).", "DOCX");
        add("docx.pageNumberPosition", ParameterDef.Type.STRING, "center", "Seitenzahl-Position: left, center, right.", "DOCX");
        add("docx.tableBorders", ParameterDef.Type.BOOLEAN, "false", "Tabellenrahmen.", "DOCX");
        add("docx.tableHeaderColor", ParameterDef.Type.STRING, "E7E6E6", "Tabellenkopf-Farbe (Hex).", "DOCX");
        add("docx.tableBorderColor", ParameterDef.Type.STRING, "BFBFBF", "Tabellenrahmen-Farbe (Hex).", "DOCX");
        add("docx.codeBackgroundColor", ParameterDef.Type.STRING, "F5F5F5", "Hintergrundfarbe Code (Hex).", "DOCX");
        add("docx.codeBorderColor", ParameterDef.Type.STRING, "D4D4D4", "Rahmenfarbe Code (Hex).", "DOCX");
        add("docx.codeLineNumbers", ParameterDef.Type.BOOLEAN, "false", "Zeilennummern bei Code.", "DOCX");
        add("docx.quoteBorderColor", ParameterDef.Type.STRING, "CCCCCC", "Rahmenfarbe Zitate (Hex).", "DOCX");
        add("docx.quoteBackgroundColor", ParameterDef.Type.STRING, "F9F9F9", "Hintergrund Zitate (Hex).", "DOCX");
        add("docx.quoteIndent", ParameterDef.Type.DOUBLE, "1.0", "Einrückung Zitate (cm).", "DOCX");
        add("docx.bulletStyle", ParameterDef.Type.STRING, "•", "Listenzeichen (•, -, *, …).", "DOCX");
        add("docx.listIndentation", ParameterDef.Type.BOOLEAN, "false", "Listen einrücken.", "DOCX");
        add("docx.listIndentSize", ParameterDef.Type.DOUBLE, "0.5", "Listen-Einrückung (cm).", "DOCX");
        add("docx.linkColor", ParameterDef.Type.STRING, "0563C1", "Link-Farbe (Hex).", "DOCX");
        add("docx.underlineLinks", ParameterDef.Type.BOOLEAN, "false", "Links unterstreichen.", "DOCX");
        add("docx.documentTitle", ParameterDef.Type.STRING, "", "Dokumenttitel (Metadaten).", "DOCX");
        add("docx.documentAuthor", ParameterDef.Type.STRING, "", "Autor (Metadaten).", "DOCX");
        add("docx.documentSubject", ParameterDef.Type.STRING, "", "Betreff (Metadaten).", "DOCX");
        add("docx.documentKeywords", ParameterDef.Type.STRING, "", "Stichwörter (Metadaten).", "DOCX");
        add("docx.documentCategory", ParameterDef.Type.STRING, "", "Kategorie (Metadaten).", "DOCX");
        add("docx.autoNumberHeadings", ParameterDef.Type.BOOLEAN, "false", "Überschriften automatisch nummerieren.", "DOCX");
        add("docx.protectDocument", ParameterDef.Type.BOOLEAN, "false", "Dokument schützen.", "DOCX");
        add("docx.protectionPassword", ParameterDef.Type.STRING, "", "Kennwort für Schutz.", "DOCX");
        add("docx.trackChanges", ParameterDef.Type.BOOLEAN, "false", "Änderungen verfolgen.", "DOCX");
        add("docx.showHiddenText", ParameterDef.Type.BOOLEAN, "false", "Versteckten Text anzeigen.", "DOCX");
        add("docx.includeComments", ParameterDef.Type.BOOLEAN, "false", "Kommentare einbeziehen.", "DOCX");
        add("docx.readingLevel", ParameterDef.Type.STRING, "standard", "Lesbarkeitsstufe: standard, simplified, technical.", "DOCX");

        // —— Textanalyse (config/textanalysis.properties) ——
        add("sprechwörter", ParameterDef.Type.STRING, "",
                "Sprechwörter für Dialog-Analyse (kommagetrennt).", "Textanalyse");
        add("sprechantworten_regex", ParameterDef.Type.STRING, "",
                "Regex für Sprechantworten (z. B. „sagte er“, „fragte sie“).", "Textanalyse");
        add("dialog_regex", ParameterDef.Type.STRING, "\"[^\"]*\"",
                "Regex für Dialog-Zitate.", "Textanalyse");
        add("direkte_rede_regex", ParameterDef.Type.STRING, "[^\"]*",
                "Regex für direkte Rede.", "Textanalyse");
        add("satz_ende_regex", ParameterDef.Type.STRING, "[.!?]+",
                "Regex für Satzende.", "Textanalyse");
        add("paragraph_regex", ParameterDef.Type.STRING, "\\n\\s*\\n",
                "Regex für Absatz-Trennung.", "Textanalyse");
        add("wortwiederholungen_abstand", ParameterDef.Type.INT, "10",
                "Maximaler Zeichenabstand für Wortwiederholungen.", "Textanalyse");
        add("wortwiederholungen_min_laenge", ParameterDef.Type.INT, "4",
                "Mindestlänge (Zeichen) für wiederholte Wörter.", "Textanalyse");
        add("wortwiederholungen_ignoriere_woerter", ParameterDef.Type.STRING, "",
                "Wörter ignorieren bei Wiederholungs-Analyse (kommagetrennt).", "Textanalyse");
        add("fuellwoerter", ParameterDef.Type.STRING, "",
                "Füllwörter für die Analyse (kommagetrennt).", "Textanalyse");
        add("phrasen_begann", ParameterDef.Type.STRING, "",
                "Phrasen mit „begann/begannen“ (kommagetrennt).", "Textanalyse");
        add("phrasen_emotionen", ParameterDef.Type.STRING, "",
                "Emotions-Phrasen (kommagetrennt).", "Textanalyse");
        add("phrasen_dialog", ParameterDef.Type.STRING, "",
                "Dialog-Phrasen wie „sagte er“, „fragte sie“ (kommagetrennt).", "Textanalyse");
        add("phrasen_denken", ParameterDef.Type.STRING, "",
                "Denk-Phrasen (kommagetrennt).", "Textanalyse");
        add("phrasen_gefuehle", ParameterDef.Type.STRING, "",
                "Gefühls-Phrasen, Platzhalter * erlaubt (kommagetrennt).", "Textanalyse");
        add("phrasen_bewegung", ParameterDef.Type.STRING, "",
                "Bewegungs-Phrasen (kommagetrennt).", "Textanalyse");
    }

    private static void add(String key, ParameterDef.Type type, String defaultValue, String helpText, String category) {
        ALL.add(new ParameterDef(key, type, defaultValue, helpText, category));
    }

    public static List<ParameterDef> getAll() {
        return new ArrayList<>(ALL);
    }

    public static List<ParameterDef> getByCategory(String category) {
        List<ParameterDef> out = new ArrayList<>();
        for (ParameterDef p : ALL) {
            if (category.equals(p.getCategory())) out.add(p);
        }
        return out;
    }

    public static List<String> getCategories() {
        List<String> cats = new ArrayList<>();
        for (ParameterDef p : ALL) {
            if (!cats.contains(p.getCategory())) cats.add(p.getCategory());
        }
        return cats;
    }
}
