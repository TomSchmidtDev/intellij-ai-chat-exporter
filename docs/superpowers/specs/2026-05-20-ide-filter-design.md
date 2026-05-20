# IDE Filter for Copilot Sessions

**Date:** 2026-05-20
**Status:** Approved

## Problem

The plugin reads Copilot chat sessions from all JetBrains IDEs simultaneously (all subdirectories under `github-copilot/`). When running in Rider, users see IntelliJ sessions mixed with Rider sessions. Users expect to see only sessions from the IDE they are currently working in.

## Goal

- Default: show only sessions from the current IDE
- Toggle: "All IDEs" available in both the tool window toolbar and the Settings page
- Fallback: when no sessions exist for the current IDE, show an empty state with a hint pointing to the toggle

## IDE Detection

```kotlin
ApplicationNamesInfo.getInstance().scriptName.lowercase()
// IntelliJ IDEA → "idea"
// Rider         → "rider"
// WebStorm      → "webstorm"
```

GitHub Copilot uses this exact name as the subdirectory under `github-copilot/`. No hardcoded mapping needed.

## Components

### 1. ExporterSettings

Add one new persistent field:

```kotlin
var showAllIdes: Boolean = false
```

Default `false` → filter to current IDE.

### 2. CopilotChatReaderService

Modify `findAllDatabaseFiles()`:

- Accept or read `showAllIdes: Boolean` from `ExporterSettings`
- When `false`: skip any `ideDir` whose name does not match `scriptName.lowercase()`
- When `true`: existing behavior — iterate all subdirectories

`readSessions()` and `diagnose()` pass the setting through automatically since they call `findAllDatabaseFiles()`.

No change to parsing logic; only the directory scan is affected.

### 3. ExporterPanel (toolbar)

Add a `JCheckBox("All IDEs")` to `leftButtons` in the toolbar, as the first element — to the left of the "Refresh" button.

- Initialized from `ExporterSettings.showAllIdes`
- On state change: persist to `ExporterSettings.showAllIdes`, then reload the session list
- The checkbox state is kept in sync if the setting is changed via the Settings page

**Empty state hint** (shown when filtered list is empty):

> "No Copilot sessions found for this IDE. Enable \"All IDEs\" to see sessions from other IDEs."

This replaces or extends the existing empty-state message.

### 4. ExporterSettingsPanel

Add a `JCheckBox` below the existing options:

> "Show sessions from all IDEs (not just the current one)"

Bound to `ExporterSettings.showAllIdes`. No restart required (plugin is dynamic).

## Behavior Summary

| showAllIdes | Sessions shown |
|-------------|----------------|
| false (default) | Only current IDE's sessions |
| true | All IDE sessions (previous behavior) |
| false + no data for current IDE | Empty list + hint message |

## Version

1.5.4 → 1.5.5 (new user-visible feature → minor patch bump)

## Out of Scope

- Per-IDE grouping or labels in the session list
- Automatic fallback to "all IDEs" when current IDE has no data
- Any changes to parsing, export, or HTML/Markdown output
