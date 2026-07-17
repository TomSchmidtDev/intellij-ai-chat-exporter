# Changelog

## [1.7.5] - 2026-07-17

### Security
- Upgraded `jackson-databind`, `jackson-core`, and `jackson-annotations` from 2.18.6 to 2.18.9, resolving four Dependabot alerts against `jackson-databind`:
  - [GHSA-j3rv-43j4-c7qm](https://github.com/advisories/GHSA-j3rv-43j4-c7qm) (high, CVE-2026-54512) — PolymorphicTypeValidator bypass via generic type parameters
  - [GHSA-rmj7-2vxq-3g9f](https://github.com/advisories/GHSA-rmj7-2vxq-3g9f) (high, CVE-2026-54513) — PolymorphicTypeValidator array-subtype allowlist bypass
  - [GHSA-hgj6-7826-r7m5](https://github.com/advisories/GHSA-hgj6-7826-r7m5) (moderate, CVE-2026-54514) — SSRF via eager DNS resolution in `InetSocketAddress` deserialization
  - [GHSA-5jmj-h7xm-6q6v](https://github.com/advisories/GHSA-5jmj-h7xm-6q6v) (moderate, CVE-2026-54515) — case-insensitive deserialization bypasses per-property `@JsonIgnoreProperties`
  - This plugin does not enable polymorphic typing or deserialize untrusted JSON via jackson-databind, so none of these were directly exploitable here; the upgrade removes the vulnerable code from the shipped jar regardless.

## [1.7.4] - 2026-07-17

### Fixed
- Newest Copilot chat/agent sessions no longer silently disappear from the export list. GitHub Copilot occasionally changes its internal Nitrite schema between plugin versions (e.g. dropping the legacy `NtChatSession`/`NtTurn` entities in favor of a unified agent schema); sessions are now also matched against turns embedded directly in the session document as a fallback, and turns whose content can't be parsed are exported as a placeholder instead of causing the whole session to be dropped (thanks to Cal Briden for reporting this!)

## [1.7.3] - 2026-06-10

### Fixed
- Search match-count badge now visible on both light and dark IDE themes (Swing BasicHTML CSS color leakage fix)

## [1.7.2] - 2026-06-09

### Fixed
- Claude Code session titles now read from Claude Desktop metadata: sessions with an auto-generated title in Claude Desktop are displayed with that name instead of the UUID
- Both `ai-title` (older CLI format) and `custom-title` (newer CLI format) JSONL entries are now recognized

## [1.7.1] - 2026-06-09

### Fixed
- Claude Code session titles now displayed correctly (were shown as GUIDs due to wrong JSONL field name)

## [1.7.0] - 2026-06-09

### Added
- Search bar in both Copilot and Claude Code tabs: filter sessions live as you type
- Boolean query syntax: `AND`, `OR`, phrase matching with quotes, and grouping with parentheses
- Scope toggles to search prompts, responses, and/or session titles
- Match-count badge on sessions with results; matching messages highlighted in the preview panel

## [1.6.4] - 2026-05-30
### Fixed
- Plugin version is now embedded at build time via a generated `BuildConfig` constant — eliminates all internal and deprecated `PluginManager` API usages, resolving JetBrains Marketplace compatibility warnings for IntelliJ 2026.2+

## [1.6.3] - 2026-05-30
### Fixed
- Replaced internal IntelliJ Platform API `PluginManager.getPluginByClass` with public API — resolves JetBrains Marketplace compatibility warning for IntelliJ 2026.2+

## [1.6.2] - 2026-05-20
### Changed
- Prompts / Assistant / Tool Calls / Thinking buttons in Claude Code tab now toggle message checkbox selection instead of hiding messages — consistent with Copilot tab behavior
- "Copilot" button in Copilot tab renamed to "Assistant"
- "User" button in Claude Code tab renamed to "Prompts"
- Export always includes full message content; checkbox selection is the filter

## [1.6.1] - 2026-05-20
### Fixed
- Claude Code message preview no longer blank for messages that contain only tool calls or tool results

### Added
- Claude Code messages show a tooltip on hover with full content (text, thinking, tool calls) — up to 10 lines, same as Copilot tab
- Last selected tab (Copilot / Claude Code) is remembered across IDE restarts

## [1.6.0] - 2026-05-20
### Added
- Claude Code tab: browse and export Claude Code chat sessions stored in `~/.claude/projects/`
- Project filter dropdown to show sessions from a specific project only
- Toggle buttons for Tool Calls and Thinking blocks (hidden by default; User and Assistant shown by default)
- Sessions from all entrypoints (CLI, Claude Desktop, JetBrains IDE plugin) are included

## [1.5.6] - 2026-05-20
### Fixed
- IDE filter now correctly identifies the current IDE using the product code (e.g. `iu` for IntelliJ IDEA Ultimate) instead of the script name — previously no sessions were shown when "All IDEs" was disabled

### Added
- Settings page now shows the detected IDE directory name below the "All IDEs" checkbox

## [1.5.5] - 2026-05-20
### Added
- Sessions are now filtered to the current IDE by default
- "All IDEs" checkbox in the toolbar and Settings page to show sessions from all JetBrains IDEs

## [1.5.4] - 2026-05-20
### Improved
- Plugin can now be installed and uninstalled without restarting the IDE (`require-restart="false"`)

## [1.5.3] - 2026-05-20
### Fixed
- Replaced internal API `PluginManagerCore.getPlugin()` with public `PluginManager.getPluginByClass()` for compatibility with IntelliJ 2026.2+

## [1.5.2] - 2026-05-11
### Changed
- Session list now shows and sorts by last-modified date (timestamp of the most recent turn) instead of the session creation date

### Added
- Hovering over a message in the preview panel shows a tooltip with the message timestamp and up to 10 lines of content

## [1.5.1] - 2026-05-11
### Fixed
- Diagnostic no longer truncates session and turn lists (previously capped at 3 sessions / 5 turns per database file)
- Sessions with turns that produce no extractable text now emit a debug log entry instead of silently disappearing

### Improved
- Diagnostic groups turns by session and shows USER / ASSISTANT content availability per turn
- Timestamps in diagnostic are now formatted as human-readable dates (e.g. `2026-01-15 14:37:43`) in addition to raw milliseconds
- Sessions without turns are marked `→ FILTERED` in the diagnostic output, making it clear why they do not appear in the plugin UI
- Deleted turns are counted separately and excluded from the active-turn list in the diagnostic

## [1.5.0] - 2026-04-07
### Added
- Plugin logo (40×40 SVG) displayed in the JetBrains Marketplace and IDE plugin list
- Preview panel header now shows the title of the currently selected session
- Two toggle buttons below the preview title to select/deselect all user prompts or all Copilot responses in one click

## [1.4.4] - 2026-04-04
### Fixed
- Upgraded jackson-core/databind/annotations to 2.18.6 to resolve a moderate DoS vulnerability (Number Length Constraint Bypass in async parser)

### Changed
- Renamed internal package from `de.schmidtdevs` to `de.tomschmidtdev`

## [1.4.3] - 2026-04-04
### Fixed
- Session titles and message previews with `<`, `>`, or `&` characters now display correctly in the UI (HTML-escaped before rendering in JList)
- Settings dialog no longer crashes when a color value in the settings file is corrupted/invalid (`Color.decode` wrapped in safe error handling)

### Improved
- Reduced memory usage when reading large Copilot databases: turn documents are now filtered lazily instead of loading the entire database into heap first
- `diagnose()` no longer materializes the full DB just to display 3–5 sample entries

## [1.4.2] - 2026-04-04
### Added
- JetBrains Marketplace publishing pipeline (signing, plugin verifier, GitHub Actions)
- Automated release workflow: push a `v*.*.*` tag → GitHub Release + Marketplace upload

### Changed
- Improved Marketplace description and vendor metadata

## [1.4.1] - 2026-04-04
### Fixed
- Agent-mode chat sessions (Subgraph-wrapped responses) now export correctly instead of showing raw JSON
- Tool window renamed to "Copilot Chat Exporter" for consistency

### Changed
- Upgraded Kotlin compiler to 2.1.20 (fixes build failure on systems with JDK 25 as default)

## [1.4.0] - 2026-04-04
### Added
- Initial public release
- Export Copilot inline chat, agent, and edit sessions to Markdown and HTML
- 10 built-in color profiles for HTML export
- Cross-platform support: Linux, macOS, Windows
