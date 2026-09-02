# Plugin-Katalog

Mitgelieferte In-Process-Plugins liegen **hier**, nicht in `plugins/`.

Beim Kunden ist dieser Ordner gefüllt, `plugins/` ist leer. Erst unter **Setup → Plugins** wird eine JAR nach `plugins/` kopiert (aktivieren) oder von dort gelöscht (deaktivieren). Nur `plugins/` lädt Manuskript in die JVM.

Entwicklung: `mvn package` der Plugin-Projekte kopiert JAR und gleichnamige `.txt` hierher.
