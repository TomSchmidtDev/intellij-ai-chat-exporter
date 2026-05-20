# Copilot Chat Exporter

> [Deutsche Version](README.de.md)

An IntelliJ plugin that lets you export your **GitHub Copilot** and **Claude Code** chat history to Markdown or styled HTML — directly from your IDE.

![Plugin Panel](screenshots/plugin-panel.png)

## Features

### GitHub Copilot
- **Browse all Copilot chat sessions** — inline chat, agent mode, and edit mode
- **Select individual messages** per session for partial exports
- **Filter by IDE** — show only sessions from the current JetBrains IDE, or toggle "All IDEs"

### Claude Code
- **Browse all Claude Code sessions** from `~/.claude/projects/` — CLI, Claude Desktop, and JetBrains IDE sessions included
- **Filter by project** — narrow the session list to a specific project directory
- **Toggle Tool Calls and Thinking blocks** on/off (hidden by default)

### Export
- **Export to Markdown** (`.md`) — clean, portable, paste-ready
- **Export to HTML** (`.html`) — fully styled with syntax-highlighted code blocks, opens directly in the IDE after export
- **10 built-in color themes** for HTML output: Catppuccin Mocha, GitHub Dark, Dracula, Nord, Tokyo Night, One Dark, Monokai Pro, Material Ocean, Solarized Dark, Light Classic
- **Custom color profile** — configure all HTML colors individually via the settings page
- **Read-only access** — no chat data is ever modified

## Settings

![Plugin Settings](screenshots/plugin-settings.png)

Open **Settings → Tools → Copilot Chat Exporter** to configure the HTML color profile. Choose from 10 presets or customize each color individually.

## Installation

1. Open your JetBrains IDE (IntelliJ IDEA, GoLand, PyCharm, etc.)
2. Go to **Settings → Plugins → Marketplace**
3. Search for **Copilot Chat Exporter** and install

Or install manually via **Settings → Plugins → Install Plugin from Disk** using the `.zip` from the [Releases](https://github.com/TomSchmidtDev/intellij-ai-chat-exporter/releases) page.

## Usage

### Copilot tab
1. Open the **AI Chat Exporter** tool window (right sidebar) and select the **Copilot** tab
2. Select the sessions you want to export using the checkboxes
3. Optionally select individual messages in the preview panel on the right
4. Click **Export MD** or **Export HTML** and choose a save location

### Claude Code tab
1. Select the **Claude Code** tab in the tool window
2. Optionally filter by project using the **Project** dropdown
3. Select sessions and individual messages as above
4. Use the **Tool Calls** and **Thinking** toggles to include or exclude those block types
5. Click **Export MD** or **Export HTML**

## Requirements

- JetBrains IDE 2025.1 or later
- For Copilot export: GitHub Copilot plugin installed with at least one chat session recorded
- For Claude Code export: Claude Code installed with at least one session in `~/.claude/projects/`

## Privacy

This plugin does not collect, transmit, or store any personal data. All operations are performed locally on your machine. No telemetry, analytics, or network requests are made.

## License

Business Source License 1.1 — free for personal, non-commercial, and internal business use. Converts to Apache 2.0 on 2031-04-04. See [LICENSE.md](LICENSE.md) for details.
