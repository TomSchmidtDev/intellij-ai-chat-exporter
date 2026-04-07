# Changelog

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
