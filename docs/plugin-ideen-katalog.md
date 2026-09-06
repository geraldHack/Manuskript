# Plugin-Ideen für Manuskript

Übergeordneter Ideen-Katalog: Lücken zu Sudowrite/NovelCrafter, JAR-Plugins, externe Anbindungen.

> **Stand:** Publish-Paket ist umgesetzt (`tools/publish-paket/`). Dieses Dokument liegt im Repo, damit der Katalog nicht nur in Cursor-Plans verschwindet.

---

## Ausgangslage

Manuskript hat bereits starkes Fundament:

- **In-App:** Agenten, Online-Lektorat, LanguageTool, Textanalyse, Makros, Szenen-Outline, Roman-Assistent, Welt-Editor, Diktat, Hörbuch
- **JAR-Plugins:** Statistik, OpenRouter-/Mammouth-Monitor, Projekt-Backup, Publish-Paket
- **PluginHost:** Projektpfad, Kapitel-Markdown, themed Stage, Browser, `completeChat`

**Regel:** Nebenfenster/Workflows → **JAR-Plugin**; Prose-Helfer à la Sudowrite → **Agent** oder **FeaturePack** im Canvas-Editor.

---

## Sudowrite / NovelCrafter vs. Manuskript

| Konkurrent-Feature | Manuskript heute | Lücke |
|---|---|---|
| Story Bible / Codex | Welt-Editor + Roman-Assistent | Kein Auto-Link Text → Codex, kein Series-Codex |
| Write / Continue | Agent „Szene Schreiben“ | Kein Cursor-Continue mit Varianten inline |
| Describe / Expand / Rewrite | Überarbeiten, Lektorat | Keine dedizierten Describe/Expand-Buttons |
| Feedback / Beta-Reader | Plothole/Dialog, Online-Lektorat | Kein virtueller Leser über ganzes Manuskript |
| Scene Beats → Prose | Szenen-Outline | Kein Beat→Text-Pipeline |
| Character extraction | Welt „Aus Kapiteln“ | Könnte robuster sein |
| Visualize | Bild-Prompt-Agent | Keine Bild-API |
| BYOK Multi-Model | Ollama + OpenAI-kompatibel | Schon stark |

**Strategie:** NovelCrafter-Nähe (BYOK + Organisation) + deutsche Autoren-Workflows, nicht Sudowrite 1:1.

---

## JAR-Plugin-Ideen

### Tier A — hoher Alltagswert

1. **Recherche-Panel** — Wikipedia, Duden, DWDS; Snippets nach `recherche/`
2. **Konsistenz-Checker** — Welt-Dateien vs. Kapitel (Figuren, Orte, Widersprüche)
3. **Szenen-Zusammenfassung / Beats** — pro Kapitel, für Agenten-Kontext
4. **Virtueller Beta-Leser** — Manuskript-Feedback, Export als Review-MD
5. **Publish-Paket-Helfer** — **fertig** (`tools/publish-paket/`)
6. **DeepL-Bridge** — Übersetzung/Glossar für fremdsprachige Passagen
7. **Mindmap** — **fertig** (`tools/mindmap/`) — WebView editierbar, KI Erstlesen/Aktualisieren

### Tier B — mehr Integration

8. **Codex-Linker** — Namen im Editor → Welt-Eintrag (Host/Editor-Hook)
8. **Describe/Expand-Agenten** — Sudowrite-ähnliche Presets
9. **Continue-Schreiben** — 2–3 Varianten inline (Kern-App, kein JAR)
10. **Bild-Generierung** — Leonardo/ComfyUI nach Bild-Prompt
11. **Obsidian-Sync** — Welt/Projekt als Vault
12. **Timeline** — Chronologie aus Welt + Kapiteln

### Tier C — leicht / Launcher

13. Launcher-Presets (`config/launchers.json`)
14. Blurb-Preview (Zeichenlimits)
15. Name-Generator
16. Pomodoro / Tagesziel (Statistik-Erweiterung)
17. Zitat-Finder (Wikiquote)
18. ISBN/Metadaten für Pandoc-YAML

---

## Nicht zuerst

- Sudowrite-API (gibt es praktisch nicht)
- Grammarly/ProWritingAid In-App
- Collaborative Editing wie NovelCrafter Specialist
- Plugin-Marketplace vor wenigen guten Plugins

---

## Architektur

| Idee | Wo |
|---|---|
| Checklisten, Recherche, Stats | JAR `tools/…` |
| Describe, Expand, Continue, Beta | Agent / FeaturePack |
| Welt↔Text | `ChapterEditorHost` |
| Externe Programme | `launchers.json` |

---

## Priorität (nächste Schritte)

1. Konsistenz-Checker  
2. Recherche-Panel  
3. Describe/Expand-Agenten  
4. Virtueller Beta-Leser  
5. ~~Publish-Paket~~ ✓
