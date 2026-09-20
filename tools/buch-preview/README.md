# Taschenbuch-Preview

HTML-Vorschau für Taschenbuch- und Hardcover-Formate. Kein PDF.

Quelle: vorhandener **HTML5-Export** (`*_html/*.html`). Klappentext und Cover aus `data/pandoc_metadata.json` (Exportmodul: Abstract / Cover-Bild).

## Bauen

```bash
cd tools/buch-preview
mvn package
```

JAR nach `plugin-catalog/buch-preview.jar`. Unter **Setup → Plugins** aktivieren.
