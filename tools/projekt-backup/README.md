# Projekt-Backup

Sichert das geöffnete Manuskript-Projekt als ZIP. Mehrere Ziele, jeweils mit eigenem Zeitplan.

## Ziele

- Ordner im Dateisystem — USB, Dropbox, iCloud Drive, OneDrive, Google Drive (deren Sync-Ordner)
- SSH/SCP — Host, Benutzer, Remote-Pfad, Schlüssel und/oder Passwort

## Funktionen

- Komprimiertes oder unkomprimiertes ZIP
- Optional AES-256-GCM (`.zip.enc`, Wiederherstellung nur im Plugin)
- Überwachungsmodus ohne Fenster, sobald das Plugin aktiv ist: stündlich / täglich / wöchentlich / monatlich
- Alte Backups begrenzen (lokal und remote)
- Kein OS-Scheduler — läuft nur, solange Manuskript offen ist

## Bauen

```bash
cd tools/projekt-backup
mvn package
```

JAR nach `plugin-catalog/projekt-backup.jar`. Unter **Setup → Plugins** aktivieren.
