# Copilot Chat Exporter — Claude Context

## What this project is
An IntelliJ Platform plugin that reads GitHub Copilot chat history from its local Nitrite 4.x database and exports it to Markdown or styled HTML. The plugin is read-only — it never modifies Copilot data.

## Build requirements
- **Java 21** is required (matches JetBrains Runtime 21 bundled in IntelliJ 2025.1+)
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

## Dependency license policy

Whenever a dependency is added or updated in `build.gradle.kts`, do the following **before committing**:

1. Run `./gradlew dependencies --configuration runtimeClasspath` to list all transitive dependencies.
2. Verify each **bundled** dependency's license via Maven Central (`https://central.sonatype.com/artifact/<group>/<artifact>/<version>`).
3. Skip IDE-provided dependencies — `kotlin-stdlib` and `org.jetbrains:annotations` are provided by the IntelliJ Platform and do not need NOTICE entries.
4. Update both `NOTICE` and `src/main/resources/NOTICE` (keep them in sync) using this format:

```
--------------------------------------------------------------------------------
<Library Name> (<URL>)
Copyright <year> <author>.
Licensed under <License Name> (<SPDX-ID>).

  <group>:<artifact>:<version>

A copy of the <License Name> is available at:
  <URL>
--------------------------------------------------------------------------------
```

5. Flag non-permissive licenses before proceeding:
   - **Copyleft** (GPL, LGPL, AGPL): may require source disclosure — escalate to the developer
   - **Weak copyleft** (MPL, EPL): allowed if the library is unmodified — note dual-license options
   - **Permissive** (Apache-2.0, MIT, BSD): allowed, attribution required in NOTICE

## Versioning strategy
- Patch version: each build / bug fix / refactor
- Minor version: new user-visible feature
- Major version: breaking change or new minimum IntelliJ version requirement

When bumping a version, always update **all three** in the same commit:
1. `build.gradle.kts` → `version = "X.Y.Z"`
2. `CHANGELOG.md` → new `## [X.Y.Z] - YYYY-MM-DD` section
3. `build.gradle.kts` → `changeNotes` block (shown on JetBrains Marketplace "What's New" tab)

## Plugin icon
- Tool window icon (13×13): `src/main/resources/icons/pluginIcon.svg` — speech-bubble style
- Marketplace / IDE plugin list icon (40×40): `src/main/resources/META-INF/pluginIcon.svg` — document + brain badge

## UI conventions (ExporterPanel)
- All UI is built with standard Swing + JetBrains `com.intellij.ui.*` components.
- Long-running work (DB reads, exports) runs via `Task.Backgroundable`; never block the EDT.
- `CheckBoxList<T>` — use `addItem(item, text, selected)` to populate; HTML strings are supported as display text.
- **Avoid calling `setItemSelected` in a loop** — it does an O(n) model scan per call (→ O(n²) total). Instead, read all checked states into a `BooleanArray`, modify it, then rebuild the list with `clear()` + `addItem` in one pass.

## Repository
https://github.com/TomSchmidtDev/intellij-ai-chat-exporter
