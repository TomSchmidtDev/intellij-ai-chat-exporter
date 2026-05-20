# Claude Code Session Export — Design Spec

**Date:** 2026-05-20  
**Version target:** 1.6.0  
**Approach:** Option A — parallel structure, fully isolated from Copilot code

---

## Overview

Add a second "Claude Code" tab to the plugin that reads and exports Claude Code chat sessions stored as local JSONL files. All sessions are exported regardless of entrypoint (CLI, Claude Desktop, JetBrains IDE plugin). Sessions can be filtered by project. Tool calls and thinking blocks are hidden by default but can be toggled on.

---

## Data Layer

### Storage Location

Claude Code stores one JSONL file per session:

| OS | Path |
|---|---|
| macOS / Linux | `~/.claude/projects/<slug>/<uuid>.jsonl` |
| Windows | `%USERPROFILE%\.claude\projects\<slug>\<uuid>.jsonl` |

The `<slug>` is the absolute project path with path separators replaced by `-`.  
Example: `/Users/schmidt/projects/my-app` → `-Users-schmidt-projects-my-app`

### JSONL Entry Types

Each line in a session file is a JSON object. Relevant types:

| Type | Purpose |
|---|---|
| `ai-title` | Session title (`aiTitle` field). Falls back to session UUID if absent. |
| `last-prompt` | Points to the leaf UUID of the active conversation branch (`leafUuid`). |
| `user` | User message. `message.content` is a string or list of content blocks. |
| `assistant` | Assistant response. `message.content` is a list of typed blocks. |
| `attachment`, `system`, `permission-mode`, `file-history-snapshot` | Ignored. |

### Content Block Types

Assistant messages contain typed content blocks:

| Block type | Meaning | Default visibility |
|---|---|---|
| `text` | Plain assistant text | Always shown |
| `thinking` | Extended thinking trace | Hidden (toggle off) |
| `tool_use` | Tool call (name + input) | Hidden (toggle off) |
| `tool_result` | Tool response | Hidden (toggle off) |

### Conversation Tree

Messages are linked via `parentUuid`. The reader reconstructs the active branch by:
1. Reading `last-prompt.leafUuid`
2. Walking parent links from the leaf to the root
3. Reversing to get chronological order

### New Classes

```
model/
  ClaudeCodeSession.kt       sessionId, aiTitle, projectPath, lastModified, messages: List<ChatMessage>

services/
  ClaudeCodeReaderService.kt  discovers all projects + sessions under ~/.claude/projects/
  ClaudeJsonParser.kt         parses a single .jsonl file into a ClaudeCodeSession

ui/
  ClaudeCodePanel.kt          the new tab panel
```

`ClaudeCodeSession` and `ClaudeJsonParser` use the existing `ChatMessage` and `Role` model directly. No new model classes for messages.

---

## UI

### Tab Integration

`ExporterToolWindowFactory` wraps both panels in a `JTabbedPane`:
- Tab 1: "Copilot" — existing `ExporterPanel` (unchanged)
- Tab 2: "Claude Code" — new `ClaudeCodePanel`

### ClaudeCodePanel Layout

```
Toolbar:  [Refresh]  [Project ▾]  [Export Markdown]  [Export HTML]

Left panel (session list):
  Flat list, sorted by last modified (newest first)
  Each row: aiTitle + relative date

Right panel (preview):
  Header: selected session title
  Toggle buttons: [User] [Assistant] [Tool Calls] [Thinking]
    - User: ON by default
    - Assistant: ON by default
    - Tool Calls: OFF by default
    - Thinking: OFF by default
  Message checkbox list (same pattern as ExporterPanel)
```

### Project Filter

The Project dropdown lists all distinct project slugs decoded as readable paths, plus "All Projects" at the top. Selecting a project filters the session list to that project only.

---

## Export

### Exporter Reuse

`HtmlExporter` and `MarkdownExporter` are used unchanged — they accept `List<ChatMessage>`. `ClaudeCodePanel` assembles the message list from the selected session by:

1. Including only messages whose role toggle is active (User / Assistant)
2. For assistant messages: filtering content blocks based on the Tool Calls / Thinking toggles
3. Concatenating remaining blocks into `ChatMessage.content`

### Version Bump

- `build.gradle.kts`: `version = "1.6.0"` + updated `changeNotes`
- `CHANGELOG.md`: new `## [1.6.0]` section

---

## Existing Code — Changes

| File | Change |
|---|---|
| `ExporterToolWindowFactory.kt` | Wrap in `JTabbedPane`, add `ClaudeCodePanel` as second tab |
| `plugin.xml` | Register `ClaudeCodeReaderService` as application service |

All other existing files remain untouched.

---

## Out of Scope

- Syncing or modifying Claude Code session data
- Importing sessions from remote / cloud
- Per-session `entrypoint` filtering (CLI vs. Desktop vs. IDE)
- Settings persistence for Claude Code tab state