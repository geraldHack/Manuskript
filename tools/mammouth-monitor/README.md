# Mammouth Monitor

Plugin für Manuskript: zeigt **Credits** (`GET /key/info`) und **Modelle** (`GET /public/models`) im Manuskript-Look.

Der Key kommt aus der Manuskript-Konfiguration (aktueller OpenAI-kompatibler Key, wenn die URL `mammouth.ai` enthält, sonst das Provider-Profil „Mammouth“). Alternativ im Monitor selbst eintragen — nicht in der Parameter-Verwaltung extra.

## Bauen

```bash
cd tools/mammouth-monitor
mvn package
```

JAR landet in `target/mammouth-monitor.jar` und wird nach `plugin-catalog/` kopiert. Unter **Setup → Plugins** aktivieren — erst dann erscheint der Toolbar-Button.

## Starten ohne Haupt-App

Aus dem Repo-Root:

```bash
./run-mammouth-monitor.sh
```

## API

| Tab | Endpoint | Auth |
|-----|----------|------|
| Credits | `GET https://api.mammouth.ai/key/info` (Fallback: `/v1/key/info`) | Mammouth API-Key |
| Modelle | `GET https://api.mammouth.ai/public/models` | optional |

Dashboard: [mammouth.ai/app/account/settings/api](https://mammouth.ai/app/account/settings/api)
