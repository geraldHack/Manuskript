# Quill Editor - Lokale Installation

Dieses Verzeichnis enthält die Quill-Editor-Bibliothek für die lokale Verwendung.

## Dateien herunterladen

Bitte laden Sie die folgenden Dateien von der Quill-Website herunter und speichern Sie sie in diesem Verzeichnis:

### 1. Quill JavaScript (quill.min.js)
- **URL**: https://cdn.quilljs.com/1.3.6/quill.min.js
- **Speichern als**: `quill.min.js`
- **Alternativ**: https://unpkg.com/quill@1.3.6/dist/quill.min.js

### 2. Quill Snow Theme CSS (quill.snow.css)
- **URL**: https://cdn.quilljs.com/1.3.6/quill.snow.css
- **Speichern als**: `quill.snow.css`
- **Alternativ**: https://unpkg.com/quill@1.3.6/dist/quill.snow.css

## Automatischer Download (PowerShell)

Sie können diese PowerShell-Befehle verwenden, um die Dateien automatisch herunterzuladen:

```powershell
# Wechsle ins Quill-Verzeichnis
cd src\main\resources\quill

# Lade quill.min.js herunter
Invoke-WebRequest -Uri "https://cdn.quilljs.com/1.3.6/quill.min.js" -OutFile "quill.min.js"

# Lade quill.snow.css herunter
Invoke-WebRequest -Uri "https://cdn.quilljs.com/1.3.6/quill.snow.css" -OutFile "quill.snow.css"
```

## Manueller Download

1. Öffnen Sie die folgenden URLs in Ihrem Browser:
   - https://cdn.quilljs.com/1.3.6/quill.min.js
   - https://cdn.quilljs.com/1.3.6/quill.snow.css

2. Speichern Sie die Dateien mit "Speichern unter..." in diesem Verzeichnis (`src/main/resources/quill/`)

3. Stellen Sie sicher, dass die Dateien genau diese Namen haben:
   - `quill.min.js`
   - `quill.snow.css`

## Verifizierung

Nach dem Download sollten folgende Dateien vorhanden sein:
- `quill.min.js` (ca. 200-300 KB)
- `quill.snow.css` (ca. 10-20 KB)
- `README.md` (diese Datei)

## Funktionsweise

Die `quill-editor.html` versucht zuerst, die Quill-Dateien lokal zu laden. Falls die lokalen Dateien nicht gefunden werden, wird automatisch auf das CDN zurückgegriffen. Dies ermöglicht:

- **Offline-Verwendung**: Funktioniert auch ohne Internetverbindung
- **Firewall-unabhängig**: Keine Probleme mit Firewall-Einstellungen
- **Schnelleres Laden**: Lokale Dateien laden schneller als CDN
- **Fallback**: Falls lokale Dateien fehlen, wird das CDN verwendet

## Version

Verwendete Quill-Version: **1.3.6**

Für andere Versionen passen Sie die URLs entsprechend an.


