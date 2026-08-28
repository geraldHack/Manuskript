# JAR-Plugins für Manuskript

Eigene Werkzeuge im **gleichen Look** wie Manuskript (`CustomStage` + Theme).
Das Plugin läuft **in derselben JVM** wie die Haupt-App.

Das ist **nicht** dasselbe wie:

- `config/plugins/*.json` — altes KI-Assistenten-Format, wird nicht mehr geladen
- `config/launchers.json` — startet **fremde Programme als Extra-Prozess** (sicherer für Software, die du nicht selbst gebaut hast)

JAR-Plugins sind nur für Code, den **du selbst** schreibst. Ein Absturz im Plugin kann Manuskript mitreißen.

## Braucht man ein eigenes Git?

Nein. Zwei übliche Wege:

| Weg | Wann | Git |
|-----|------|-----|
| Ordner unter `tools/` in diesem Repo | Plugin gehört fest zu Manuskript (wie der OpenRouter-Monitor) | dasselbe Git |
| Eigenes Maven-Projekt irgendwo | Plugin ist unabhängig, du willst es getrennt versionieren | optional eigenes Git |

In beiden Fällen landet die gebaute JAR zuerst im **Katalog** `plugin-catalog/`. Offizielle Plugins holt Setup → Plugins von spoteroxe.de dorthin. Aktivieren kopiert nach `plugins/` (Toolbar-Button).

- Katalog (inaktiv, mitgeliefert): `plugin-catalog/`
- Aktiv (wird geladen): `plugins/` — Entwicklung `user.dir/plugins`, installiert `app/plugins/`

## 1. API-JAR erzeugen

Einmal im Manuskript-Repo:

```bash
mvn package -DskipTests
```

Danach liegt die schlanke API hier:

`target/manuskript-1.0.0-plugin-api.jar`

Darin sind nur `ManuskriptPlugin` und `PluginHost`. Dagegen kompiliert das Plugin. Die Interfaces **nicht** in die Plugin-JAR packen (Manuskript bringt sie schon mit).

## 2. Maven-Projekt

Java 21, JavaFX 21. API als `system`/`provided`:

```xml
<dependency>
    <groupId>com.manuskript</groupId>
    <artifactId>manuskript-plugin-api</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>/PFAD/ZU/manuskript/target/manuskript-1.0.0-plugin-api.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>21.0.6</version>
    <scope>provided</scope>
</dependency>
```

JavaFX nicht in die Plugin-JAR shaden — die App hat es schon. Eigene Bibliotheken (z. B. Gson) per Shade **mit**packen.

Liegt das Plugin unter `tools/mein-tool/`, kannst du statt der API-JAR auch die Quellen einbinden, wie der Monitor:

`${project.basedir}/../../plugin-api/src/main/java`

## 3. Plugin-Klasse

```java
package com.example.meinplugin;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class MeinPlugin implements ManuskriptPlugin {

    private Stage stage;

    @Override
    public String id() {
        return "mein-plugin";
    }

    @Override
    public String label() {
        return "Mein Plugin";
    }

    @Override
    public void start(PluginHost host) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage = host.createThemedStage("Mein Plugin");
        Label label = new Label(host.projectRoot()
                .map(p -> "Projekt: " + p)
                .orElse("Kein Projekt gewählt"));
        host.attachScene(stage, new Scene(new StackPane(label), 480, 280));
        stage.show();
    }
}
```

`host` liefert Projektpfad, App-Home/`config`, optionales Kapitel-Markdown, Theme-Index und `openInBrowser`.

Fenster immer so öffnen: `createThemedStage` → `attachScene` → `show`. Nicht `Application.launch` und nicht `Platform.exit()` — das würde Manuskript beenden.

## 4. Service-Datei (ohne die wird nichts geladen)

Datei im Plugin-Projekt:

`src/main/resources/META-INF/services/com.manuskript.plugin.ManuskriptPlugin`

Inhalt, eine Zeile, voller Klassenname:

```
com.example.meinplugin.MeinPlugin
```

Nur JARs mit dieser Datei gelten als Plugin. Andere Dateien in `plugins/` bleiben höchstens Starter-Kandidaten und werden **nicht** in die JVM geladen.

## 5. Bauen und einlegen

```bash
mvn package
cp target/mein-plugin.jar /PFAD/ZU/manuskript/plugin-catalog/
```

Im Setup unter **Plugins** aktivieren. Manuskript lädt nur JARs aus `plugins/`.

## Referenz

Vollständige Beispiele:

- [`tools/openrouter-monitor`](../tools/openrouter-monitor/)
- [`tools/mammouth-monitor`](../tools/mammouth-monitor/)

Standalone ohne laufende Haupt-App bleibt möglich (`./run-openrouter-monitor.sh`), ist für ein reines Plugin aber nicht nötig.
