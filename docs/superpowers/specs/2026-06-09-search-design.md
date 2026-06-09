# Search Feature Design

**Date:** 2026-06-09  
**Status:** Approved

## Problem

The plugin accumulates many chat sessions over time and there is no way to find a specific one without scrolling through the full list. Users need a way to search both session titles and message content.

## Scope

Both tabs: **Copilot** (`ExporterPanel`) and **Claude Code** (`ClaudeCodePanel`).

## Approach: Hybrid Inline Filter (Option C)

A search bar is placed below the existing toolbar buttons in both panels. The session list filters live as the user types: sessions with no matches are greyed out (not hidden, so the user keeps the full picture). Each matching session shows a yellow badge with the match count. When the user clicks a matching session, the message panel on the right highlights the matching messages and scrolls to the first match.

No separate search result mode is introduced; the existing session+message split layout is reused.

## UI Components

### Search Bar

Placed below the existing toolbar buttons, above the `JBSplitter`. Contains:

- A `JTextField` with a leading search icon (magnifier)
- A `Clear` button (✕) that appears when the field is non-empty and resets the search
- Three `JCheckBox` controls: **Prompts**, **Responses**, **Title** — all checked by default
- A small hint label below the field: `Syntax: word · AND · OR · (…) · "phrase"`

Search is triggered with a **300 ms debounce** after the last keystroke to avoid re-filtering on every character.

### Session List

Existing `CheckBoxList<Session>` is reused. When a search query is active:

- Sessions with at least one matching message: rendered normally with an amber badge showing the match count (e.g. `5`)
- Sessions with no matches: text set to a dimmed/greyed colour; still clickable and checkable, but visually de-emphasised

The badge is embedded in the HTML display string already used for each session item.

### Message Panel

Existing `CheckBoxList<Message>` is reused. When a search query is active and a session is selected:

- Messages that contain at least one query term: rendered with a highlighted background + the matched terms wrapped in `<mark>` HTML (rendered as yellow text on dark background)
- Messages without matches: rendered normally but slightly dimmed
- The list auto-scrolls to the first matching message after the panel is populated

Highlighting is applied inside the existing HTML `displayText` string constructed in `rebuildMessageList`.

## Query Syntax

Case-insensitive. Operators are uppercase literals.

| Input | Meaning |
|---|---|
| `react hooks` | Both words must appear (implicit AND) |
| `react AND hooks` | Explicit AND |
| `react OR vue` | At least one must appear |
| `"react hooks"` | Exact phrase |
| `react AND (hooks OR context)` | Grouping with parentheses |

**Parsing strategy:** A hand-written recursive-descent parser (no third-party library). The grammar is small:

```
expr   = or_expr
or_expr  = and_expr ( OR and_expr )*
and_expr = atom ( AND? atom )*   // AND is optional between atoms
atom   = NOT? ( PHRASE | WORD | LPAREN expr RPAREN )
```

`NOT` is out of scope for v1 (omitted from syntax table, parser raises an error if encountered).

The parser produces a sealed `SearchNode` tree (`And`, `Or`, `Term`, `Phrase`). A separate `SearchMatcher` evaluates a node against a string, returning both a boolean result and the list of matched term positions (used for highlighting).

## Search Scope

The three checkboxes control which text is fed to `SearchMatcher`:

| Checkbox | Copilot (`ChatMessage`) | Claude Code (`ClaudeCodeMessage`) |
|---|---|---|
| **Prompts** | `content` of `Role.USER` messages | `textBlocks` of `Role.USER` messages |
| **Responses** | `content` of `Role.ASSISTANT` messages | `textBlocks` of `Role.CLAUDE` messages |
| **Title** | `ChatSession.title` | `ClaudeCodeSession.title` |

`thinkingBlocks` and `toolCallBlocks` are not searched (they are structural noise).

A session matches if: its title matches (when Title is checked) **OR** at least one of its messages matches under the active scope.

## Architecture

### New classes

| Class | Location | Responsibility |
|---|---|---|
| `SearchNode` (sealed) | `search/SearchNode.kt` | AST nodes: `And`, `Or`, `Term`, `Phrase` |
| `QueryParser` | `search/QueryParser.kt` | Parses a query string into a `SearchNode` |
| `SearchMatcher` | `search/SearchMatcher.kt` | Evaluates a `SearchNode` against text; returns match positions |

### Changes to existing classes

- **`ExporterPanel`**: add search bar below toolbar; filter `sessions` on query change; pass match positions into `rebuildMessageList`
- **`ClaudeCodePanel`**: same changes
- No changes to model, export, or service classes

### Shared search components

`QueryParser` and `SearchMatcher` are shared between both panels. The UI glue (debounce, scope checkboxes, badge rendering, highlight injection) is duplicated in the two panels — extraction into a shared helper is possible but not required for v1.

## Highlighting

Matched terms are wrapped in HTML inside the existing display strings:

```kotlin
val highlighted = rawText.highlightMatches(matchPositions, "#ffd700")
// produces: "…Wie nutze ich <b style='background:#6b5900;color:#ffd700'>React hooks</b>…"
```

The preview text is already truncated to 55 characters before HTML escaping, so highlighting is applied after truncation to avoid offset drift.

## Error Handling

- Malformed query (e.g. unbalanced parentheses): the search field gets a red border; no filtering is applied; a tooltip on the field shows the parse error
- Empty query: no filtering; all sessions shown normally

## Out of Scope (v1)

- NOT operator
- Regex search
- Searching `thinkingBlocks` or `toolCallBlocks`
- Search result export (exporting only matching messages based on search)
- Persisting the last search query across IDE restarts
