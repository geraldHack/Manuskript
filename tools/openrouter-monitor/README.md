# OpenRouter Monitor

Eigenständiges Hilfsprogramm für Manuskript-Nutzer mit OpenRouter: zeigt **Credits** und **API-Logs** in einem Fenster mit zwei Tabs.

Manuskript selbst wird nicht verändert; das Tool liest die bestehende Konfiguration.

## Voraussetzungen

- Java 21
- Maven (zum Bauen)
- OpenRouter in Manuskript konfiguriert:
  - `agent.openai.api_url` = `https://openrouter.ai/api/v1`
  - `agent.openai.api_key` = Ihr OpenRouter API-Key

Konfiguration wird aus Java Preferences (`/com/manuskript`, wie Manuskript ResourceManager) oder `config/parameters.properties` gelesen.

- `agent.openai.api_key` + `agent.openai.api_url`
- `api.lektorat.api_key` + `api.lektorat.base_url` (häufig bei OpenRouter-Nutzung)

## Bauen

```bash
cd tools/openrouter-monitor
mvn package
```

Ergebnis: `target/openrouter-monitor.jar`

## Starten

Aus dem **Repo-Root** (Konfiguration wird per `--config-dir` gesetzt):

```bash
./run-openrouter-monitor.sh
```

Windows:

```bat
run-openrouter-monitor.bat
```

Das Startskript nutzt `mvn javafx:run` mit **JavaFX 21** (JDK-21-kompatibel). Das im Repo mitgelieferte `javafx-sdk-26` wird nicht verwendet (erfordert JDK 24+).

Entwicklermodus:

```bash
cd tools/openrouter-monitor
mvn javafx:run -Djavafx.args="--config-dir=../.."
```

## API-Hinweise

| Tab | Endpoint | Key-Typ |
|-----|----------|---------|
| Credits (Konto) | `GET /api/v1/credits` | Management API Key |
| Credits (Key) | `GET /api/v1/key` | Inference Key |
| Logs | `POST /api/v1/analytics/query` | **Management API Key** (im Logs-Tab eingeben) |
| Kontoguthaben | `GET /api/v1/credits` | **Management API Key** |

Der **Inference-Key** aus Manuskript reicht für Key-Statistiken (`GET /api/v1/key`), nicht für Logs/Kontoguthaben.

### Management API Key

1. Bei [openrouter.ai/settings/keys](https://openrouter.ai/settings/keys) einen **Management Key** erstellen
2. Im Tab **Logs** des Monitors einfügen und **Key speichern** klicken
3. Alternativ: `config/openrouter-monitor.properties` mit `management_api_key=sk-or-…` oder Umgebungsvariable `OPENROUTER_MANAGEMENT_API_KEY`
