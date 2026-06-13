# Plugin-System (Legacy)

## Hinweis

Das **Plugin-System** gehörte zum früheren **KI-Assistenten** (Ollama-Fenster mit Chat und Plugin-Auswahl). Dieser Assistent ist nicht mehr Teil der Benutzeroberfläche.

Für KI-gestützte Arbeit nutzen Sie stattdessen:

- **Agenten** im Kapitel-Editor (Analyse, Szene schreiben, Chat, Überarbeiten) – konfiguriert in `config/agents.json`
- **Online-Lektorat** – Parameter unter `api.lektorat.*`
- **Welt-Editor** und **Roman-Assistent** – KI-Generierung und Extraktion aus Kapiteln

Hilfe dazu: ?-Buttons im Editor bzw. in den jeweiligen Fenstern.

## Alte Plugin-Dateien

JSON-Dateien in `config/plugins/` können noch im Repository liegen. Sie werden von der aktuellen Anwendung **nicht mehr geladen**.

Falls Sie Prompts aus einem Plugin übernehmen möchten, kopieren Sie den `prompt`-Text in einen neuen Agenten (`config/agents.json`) oder in den System-Prompt eines Agent-Tabs im Editor.

### Beispiel-Struktur (nur Referenz)

```json
{
  "name": "Plugin-Name",
  "description": "Beschreibung",
  "category": "Kategorie",
  "prompt": "Prompt mit {Variablen}",
  "temperature": 0.7,
  "maxTokens": 2048,
  "enabled": true
}
```

Neue Agenten werden über den **+**-Tab im Agenten-Panel oder direkt in `config/agents.json` angelegt – siehe Hilfe „Agenten“ im Kapitel-Editor.
