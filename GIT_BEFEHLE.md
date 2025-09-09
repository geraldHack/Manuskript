# Git-Befehle für Anfänger 🚀

**Eine einfache Anleitung zu den wichtigsten Git-Befehlen mit Erklärungen für Dummies**

---

## 📁 **Grundlegende Git-Befehle**

### **1. Repository initialisieren**
```bash
git init
```
**Was macht es:** Erstellt ein neues Git-Repository im aktuellen Ordner. Das ist wie ein "Projektordner" für Git.

### **2. Status prüfen**
```bash
git status
```
**Was macht es:** Zeigt dir, welche Dateien geändert wurden, welche neu sind und welche bereit zum Speichern sind.

### **3. Dateien zum Speichern vorbereiten**
```bash
git add dateiname.txt
git add .                    # Alle Dateien hinzufügen
```
**Was macht es:** Markiert Dateien als "bereit zum Speichern". Wie ein "In den Einkaufswagen legen".

### **4. Änderungen speichern (Commit)**
```bash
git commit -m "Meine Änderungen beschreiben"
```
**Was macht es:** Speichert deine Änderungen mit einer Nachricht. Wie ein "Foto" deines Codes zu diesem Zeitpunkt.

### **5. Änderungen anzeigen**
```bash
git log                     # Alle Commits anzeigen
git log --oneline          # Kurze Übersicht
```
**Was macht es:** Zeigt die Geschichte deiner Speicherungen an.

---

## 📥 **Mit anderen arbeiten**

### **6. Repository von GitHub herunterladen**
```bash
git clone https://github.com/username/projektname.git
```
**Was macht es:** Lädt ein Projekt von GitHub auf deinen Computer herunter.

### **7. Änderungen von anderen holen**
```bash
git pull
```
**Was macht es:** Holt neue Änderungen von GitHub und fügt sie zu deinem lokalen Projekt hinzu.

### **8. Änderungen hochladen**
```bash
git push
```
**Was macht es:** Lädt deine lokalen Änderungen zu GitHub hoch.

---

## 🌿 **Branches (Zweige) - für Fortgeschrittene**

### **9. Neuen Branch erstellen**
```bash
git branch neuer-zweig
git checkout neuer-zweig
# Oder kürzer:
git checkout -b neuer-zweig
```
**Was macht es:** Erstellt eine "Kopie" deines Projekts, wo du experimentieren kannst.

### **10. Zwischen Branches wechseln**
```bash
git checkout main          # Zurück zum Hauptzweig
git branch                 # Alle Branches anzeigen
```

---

## 🔧 **Hilfreiche Befehle**

### **11. Änderungen rückgängig machen**
```bash
git checkout -- dateiname.txt    # Eine Datei zurücksetzen
git reset --hard HEAD            # ALLES zurücksetzen (Vorsicht!)
```

### **12. Unterschiede anzeigen**
```bash
git diff                        # Änderungen in Dateien anzeigen
git diff dateiname.txt          # Nur eine Datei
```

### **13. Remote-Repository hinzufügen**
```bash
git remote add origin https://github.com/username/projektname.git
```
**Was macht es:** Verbindet dein lokales Projekt mit GitHub.

---

## 📋 **Typischer Arbeitsablauf**

**Für jeden Tag:**
```bash
git pull                      # Neue Änderungen holen
# ... arbeiten ...
git add .                     # Alle Änderungen vorbereiten
git commit -m "Beschreibung"  # Speichern
git push                      # Hochladen
```

---

## ⚠️ **Wichtige Tipps**

1. **Immer `git status` vor dem Commit** - schaue, was du speicherst!
2. **Aussagekräftige Commit-Nachrichten** - beschreibe, was du gemacht hast
3. **Regelmäßig `git pull`** - hole dir neue Änderungen von anderen
4. **Backup machen** - bevor du `git reset --hard` verwendest!

---

## 🆘 **Häufige Probleme und Lösungen**

### **"Permission denied"**
```bash
# SSH-Key einrichten oder HTTPS verwenden
git remote set-url origin https://github.com/username/projektname.git
```

### **"Merge conflict"**
```bash
# Konflikte manuell lösen, dann:
git add .
git commit -m "Merge conflict resolved"
```

### **Falsche Datei committed**
```bash
git reset --soft HEAD~1      # Letzten Commit rückgängig, Dateien bleiben
git reset --hard HEAD~1      # Letzten Commit komplett löschen
```

---

## 📚 **Weitere nützliche Befehle**

```bash
git config --global user.name "Dein Name"           # Name setzen
git config --global user.email "deine@email.com"    # Email setzen
git remote -v                                        # Remote-Repositories anzeigen
git branch -d branch-name                            # Branch löschen
git stash                                            # Änderungen temporär speichern
git stash pop                                        # Gespeicherte Änderungen wiederherstellen
```

---

**Das sind die wichtigsten Befehle für den Start. Git wird mit der Zeit einfacher!** 😊

**Tipp:** Kopiere diese Datei in dein Projekt und verwende sie als schnelle Referenz!

