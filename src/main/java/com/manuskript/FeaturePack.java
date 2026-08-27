package com.manuskript;

/**
 * Einschaltbare Funktionsgruppen. Speicher in Preferences, Steuerung im Setup.
 */
public enum FeaturePack {
    AI("feature.pack.ai",
            "KI-Funktionen insgesamt",
            "Hauptschalter für alles, was eine Sprach-KI braucht.",
            "Wenn dieser Schalter aus ist, verschwinden Agenten, Roman-Assistent, Online-Lektorat "
                    + "und die KI-Buttons im Welt-Editor. Gespeicherte API-Keys, Modelle und Texte bleiben "
                    + "erhalten – sie werden nur nicht mehr angeboten.\n\n"
                    + "Diktat und Hörbuch/Sprachsynthese bleiben unabhängig: Diktat kann lokal mit Whisper "
                    + "laufen, TTS oft mit ComfyUI oder ElevenLabs, ohne denselben KI-Chat.\n\n"
                    + "Brauchst du später wieder KI: Setup öffnen und diesen Schalter einschalten. "
                    + "Die Unterpakete merken sich ihren eigenen Zustand.",
            false,
            new String[0]),

    AGENTS("feature.pack.agents",
            "Agenten",
            "Analyse, Chat und Überarbeiten direkt im Kapitel-Editor.",
            "Im Kapitel-Editor erscheint ein Agenten-Panel (Plotlöcher, Dialog, Textstruktur, Chatbot, "
                    + "Szenen schreiben, Überarbeiten, Sprachentflechtung). Die Agenten lesen den Kapiteltext "
                    + "und optional Welt-Kontext und schlagen Änderungen vor – sie ersetzen nicht automatisch "
                    + "dein Manuskript, bis du übernimmst.\n\n"
                    + "Voraussetzung: Ollama lokal oder ein OpenAI-kompatibler Dienst (z. B. OpenRouter) "
                    + "mit API-Key in den Parametern. Ohne dieses Paket gibt es keinen Agenten-Button und "
                    + "kein Agenten-Panel. Der Parameter-Tab „Agenten“ wird ebenfalls ausgeblendet.\n\n"
                    + "Gehört zu „KI-Funktionen insgesamt“.",
            true,
            new String[]{"Agenten"}),

    NOVEL_WIZARD("feature.pack.novel_wizard",
            "Roman-Assistent",
            "Geführtes Anlegen von Welt, Figuren und Kapitelstruktur mit der KI.",
            "Der Roman-Assistent stellt Fragen, schreibt Antworten in die Welt-Dateien und kann am Ende "
                    + "Kapitel-DOCX anlegen. Er ist für den Start eines neuen Romans oder zum Nachziehen "
                    + "fehlender Worldbuilding-Teile gedacht – nicht als Kapitel-Lektor.\n\n"
                    + "Voraussetzung: dieselbe KI-Anbindung wie die Agenten (Ollama oder Online-API). "
                    + "Ohne das Paket fehlen der Toolbar-Button und der Einstieg in der Projektauswahl.\n\n"
                    + "Gehört zu „KI-Funktionen insgesamt“.",
            true,
            new String[0]),

    ONLINE_LEKTORAT("feature.pack.online_lektorat",
            "Online-Lektorat",
            "Cloud-Korrektur mit Anmerkungen und Vorschlägen im Kapitel-Editor.",
            "Im Kapitel-Editor startest du „Lektorat“: Der Text geht in Abschnitten an eine OpenAI-kompatible "
                    + "API. Du bekommst markierte Stellen, Erklärungen und alternative Formulierungen, die du "
                    + "einzeln annehmen oder verwerfen kannst. Es ist kein lokales LanguageTool "
                    + "(Rechtschreibung bleibt unabhängig).\n\n"
                    + "Voraussetzung: API-Key und Modell im Parameter-Tab „Online-Lektorat“. Ohne das Paket "
                    + "fehlt der Lektorat-Button, der Tab in den Parametern wird ausgeblendet.\n\n"
                    + "Gehört zu „KI-Funktionen insgesamt“. Kosten entstehen beim Anbieter pro Anfrage.",
            true,
            new String[]{"Online-Lektorat"}),

    DICTATION("feature.pack.dictation",
            "Spracherkennung",
            "Diktieren in den Kapitel-Editor, lokal oder über Whisper-API.",
            "Im Kapitel-Editor erscheinen Diktat und Glossar. Lokal arbeitet whisper.cpp offline "
                    + "(Setup kann das Modell einrichten). Alternativ OpenAI-Whisper in den Parametern.\n\n"
                    + "Ohne das Paket fehlen Diktat- und Glossar-Button. Der Parameter-Tab „Diktat“ wird "
                    + "ausgeblendet. Bereits eingerichtetes Whisper bleibt installiert.\n\n"
                    + "Unabhängig vom KI-Hauptschalter – du kannst diktieren, ohne Agenten zu nutzen.",
            false,
            new String[]{"Diktat"}),

    AUDIOBOOK("feature.pack.audiobook",
            "Hörbuch / Sprachsynthese",
            "Kapitel vorlesen lassen und zu einer Hörbuch-MP3 zusammenfügen.",
            "„Sprachsynthese“ öffnet den TTS-Editor: Text in Segmente, Stimmen (ComfyUI und/oder ElevenLabs). "
                    + "„Hörbuch“ fügt vorhandene TTS-Segmente mit FFmpeg zu einer MP3 zusammen.\n\n"
                    + "Voraussetzung: FFmpeg (Setup kann es entpacken). ComfyUI oder ElevenLabs-Key nur, "
                    + "wenn du dort erzeugen willst. Ohne das Paket fehlen beide Toolbar-Buttons und die "
                    + "TTS-Parameter-Tabs.\n\n"
                    + "Unabhängig vom KI-Hauptschalter.",
            false,
            new String[]{"TTS (ComfyUI)", "TTS (ElevenLabs)"});

    private final String key;
    private final String title;
    private final String summary;
    private final String details;
    private final boolean requiresAi;
    private final String[] parameterCategories;

    FeaturePack(String key, String title, String summary, String details,
                boolean requiresAi, String[] parameterCategories) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.details = details;
        this.requiresAi = requiresAi;
        this.parameterCategories = parameterCategories;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String details() {
        return details;
    }

    public boolean requiresAi() {
        return requiresAi;
    }

    public String[] parameterCategories() {
        return parameterCategories;
    }
}
