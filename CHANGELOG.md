# Changelog

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
