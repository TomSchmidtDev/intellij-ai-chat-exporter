# Copilot Chat Exporter — Claude Context

## What this project is
An IntelliJ Platform plugin that reads GitHub Copilot chat history from its local Nitrite 4.x database and exports it to Markdown or styled HTML. The plugin is read-only — it never modifies Copilot data.

## Build requirements
- **Java 21** is required (Java 25 breaks the IntelliJ Platform Gradle Plugin 2.x)
- On macOS, if multiple Java versions are installed:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ./gradlew buildPlugin
  ```
- Build output: `build/distributions/*.zip`
- Run in IDE sandbox: `./gradlew runIde`

## Architecture

```
ui/                     Tool window (ExporterPanel, ExporterToolWindowFactory)
services/               CopilotChatReaderService — reads Nitrite DB, cross-platform path resolution
model/                  ChatSession, ChatMessage, Role
export/                 HtmlExporter, MarkdownExporter
settings/               ExporterSettings (persistent), ColorProfile (10 presets + custom), ExporterSettingsConfigurable/Panel
```

## Key technical details

### Copilot DB location
| OS      | Path |
|---------|------|
| Linux   | `~/.config/github-copilot/<ide>/<session-type>/<hash>/*.db` |
| macOS   | `~/Library/Application Support/github-copilot/...` |
| Windows | `%APPDATA%\github-copilot\...` |

Three session types, each with their own DB file:
- `chat-sessions` → `copilot-chat-nitrite.db`
- `chat-agent-sessions` → `copilot-agent-sessions-nitrite.db`
- `chat-edit-sessions` → `copilot-edit-sessions-nitrite.db`

### Nitrite 4.x quirks (important!)
- Copilot registers collections as **Repositories**, so `db.listCollectionNames()` always returns 0.
  Access data via `db.store.openMap(collectionName, NitriteId::class.java, Document::class.java)` instead.
- Fields use **dot notation**: `"name.value"`, `"request.stringContent"`, `"response.contents"`.
- Copilot locks the DB file exclusively → always **copy to a temp file** before opening.
- Requires **Nitrite 4.x** (H2 MVStore Write-Format 3). Nitrite 3.x uses H2 1.4.x and cannot read these files.

### Agent/Edit message format
Agent and edit-mode responses are triple-nested JSON stored in `response.contents`:
- Outer: `{ "<uuid>": { "type": "Value", "value": "<json>" } }`
- Middle: `{ "type": "AgentRound"|"Markdown", "data": "<json>" }`
- Inner (AgentRound): `{ "roundId": N, "reply": "<text>", "toolCalls": [...] }` → take highest roundId
- Inner (Markdown): `{ "text": "<text>" }`

## Versioning strategy
- Patch version: each build
- Minor version: new feature
- Major version: breaking change or new minimum IntelliJ version requirement

## Repository
https://github.com/TomSchmidtDev/intellij-ai-chat-exporter
