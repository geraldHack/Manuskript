# Publish-Paket

Checkliste und Metadaten-Hilfe für Amazon KDP eBook und Tolino Media.

Cover-Maße, Klappentext-Zähler, sieben Keywords (je 50 Zeichen), Kapitel/TOC-Check und Hilfslinks.
Daten liegen in `data/publish_package.json`. Kein Upload.

## Bauen

```bash
cd tools/publish-paket
mvn package
```

JAR nach `plugin-catalog/publish-paket.jar`. Unter **Setup → Plugins** aktivieren.
