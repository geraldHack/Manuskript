# Plugin-System für den KI-Assistenten

## 📦 Übersicht

Das Plugin-System erweitert den KI-Assistenten um spezialisierte Funktionen. Plugins sind JSON-Dateien mit Prompts und Variablen. **Neu: Dynamische Dialoge für alle Variablen!**

## 🔧 Verwendung

### 1. Plugin auswählen
- Öffne den KI-Assistenten
- Wähle in der Funktionsauswahl unter "📦 Plugins" dein gewünschtes Plugin

### 2. Text vorbereiten (optional)
- **Editor-Selektion**: Markiere Text im Editor für automatische Verwendung
- **Chat-Input**: Oder gib Text in das Chat-Eingabefeld ein

### 3. Plugin starten
- Klicke auf "Generieren"
- **Automatisch**: Ein Dialog öffnet sich mit allen Plugin-Variablen
- **"Selektierter Text"**: Wird automatisch aus Editor-Selektion oder Chat-Input gesetzt
- **Andere Variablen**: Werden mit Standard-Werten vorausgefüllt

### 4. Variablen anpassen
- Bearbeite die Werte im Dialog
- Klicke "OK" um das Plugin zu starten
- Klicke "Abbrechen" um abzubrechen

## 📝 Plugin erstellen

### JSON-Struktur:
```json
{
  "name": "Plugin-Name",
  "description": "Beschreibung des Plugins",
  "category": "Kategorie",
  "prompt": "Prompt mit {Variablen}",
  "temperature": 0.7,
  "maxTokens": 2048,
  "enabled": true
}
```

### Variablen verwenden:
- Verwende `{VariableName}` im Prompt
- **Automatische Variablen**: `{Hier den Text einfügen, den du analysieren möchtest}`, `{selektierter Text}`, `{selected text}`
- **Benutzer-Variablen**: Alle anderen Variablen werden im Dialog abgefragt

### Standard-Werte:
Das System erkennt automatisch Standard-Werte basierend auf Variablen-Namen:
- `{Genre}` → "Fantasy"
- `{Länge}` → "Roman" 
- `{Zielgruppe}` → "Erwachsene"
- `{Charakter-Name}` → "Unbekannter Charakter"
- `{Grundidee}` → Selektierter Text

## 🎯 Verfügbare Plugins

### Charakter-Entwicklung
Entwickelt detaillierte Charakterprofile basierend auf Grundideen.

### Erweiterte Charakter-Entwicklung
Entwickelt Charakterprofile mit vielen Parametern (Name, Alter, Beruf, etc.).

### Plot-Entwicklung
Entwickelt Plot-Ideen und Story-Strukturen.

### Test-Plugin
Einfaches Plugin zum Testen des Systems.

## 🔄 Plugin verwalten

- **Ordner öffnen**: "📁 Ordner öffnen" Button
- **Plugins neu laden**: "🔄 Neu laden" Button
- **Plugin bearbeiten**: JSON-Datei direkt editieren

## 💡 Tipps

1. **Editor-Selektion nutzen**: Markiere Text im Editor für automatische Verwendung
2. **Variablen-Namen**: Verwende aussagekräftige Namen für bessere Standard-Werte
3. **Kategorien**: Gruppiere ähnliche Plugins in Kategorien
4. **Temperature**: Höhere Werte (0.8-1.0) für kreativere, niedrigere (0.3-0.7) für präzisere Antworten

## 🆕 Neue Features

### Dynamische Dialoge
- **Automatische Erkennung**: Alle Variablen werden automatisch erkannt
- **Intelligente Standard-Werte**: Basierend auf Variablen-Namen
- **Editor-Integration**: Automatische Verwendung selektierten Texts
- **Benutzerfreundlich**: Klare Labels und Platzhalter

### Automatische Text-Erkennung
- **Editor-Selektion**: Hat Vorrang
- **Chat-Input**: Fallback wenn keine Selektion
- **Intelligente Zuordnung**: Automatische Erkennung von Text-Variablen
