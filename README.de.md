# Copilot Chat Exporter

> [English version](README.md)

Ein IntelliJ-Plugin, mit dem du deine **GitHub Copilot**- und **Claude Code**-Chatverläufe direkt aus der IDE heraus als Markdown oder gestyltes HTML exportieren kannst.

![Plugin Panel](screenshots/plugin-panel.png)

## Funktionen

### Suche
- **Alle Sitzungen durchsuchen** — nach Inhalt, Titel oder Nachrichtentext in beiden Tabs
- **Boolesche Abfragesyntax** — `AND`, `OR`, exakte Phrasen mit Anführungszeichen und Klammergruppen
- **Suchbereich-Schalter** — Prompts, Antworten und/oder Sitzungstitel unabhängig durchsuchen
- **Live-Filterung** — Sitzungen werden beim Tippen gefiltert; Treffer-Sitzungen zeigen einen Badge; passende Nachrichten werden im Vorschaufenster hervorgehoben

### GitHub Copilot
- **Alle Copilot-Sitzungen durchsuchen** — Inline-Chat, Agent-Modus und Edit-Modus
- **Einzelne Nachrichten auswählen** pro Sitzung für teilweise Exporte
- **Nach IDE filtern** — nur Sitzungen der aktuellen JetBrains-IDE anzeigen oder „Alle IDEs" aktivieren

### Claude Code
- **Alle Claude Code-Sitzungen durchsuchen** aus `~/.claude/projects/` — CLI-, Claude Desktop- und JetBrains-IDE-Sitzungen inklusive
- **Nach Projekt filtern** — Sitzungsliste auf ein bestimmtes Projektverzeichnis einschränken
- **Tool Calls und Thinking-Blöcke** ein-/ausblenden (standardmäßig ausgeblendet)

### Export
- **Export als Markdown** (`.md`) — sauber, portabel, direkt einfügbar
- **Export als HTML** (`.html`) — vollständig gestylt mit Syntax-Highlighting für Codeblöcke, öffnet sich nach dem Export direkt in der IDE
- **10 eingebaute Farbthemen** für HTML-Ausgabe: Catppuccin Mocha, GitHub Dark, Dracula, Nord, Tokyo Night, One Dark, Monokai Pro, Material Ocean, Solarized Dark, Light Classic
- **Eigenes Farbprofil** — alle HTML-Farben individuell über die Einstellungsseite konfigurierbar
- **Nur-Lesen-Zugriff** — keine Chatdaten werden jemals verändert

## Einstellungen

![Plugin Settings](screenshots/plugin-settings.png)

Öffne **Einstellungen → Tools → Copilot Chat Exporter**, um das HTML-Farbprofil zu konfigurieren. Wähle aus 10 Voreinstellungen oder passe jede Farbe individuell an.

## Installation

1. Öffne deine JetBrains-IDE (IntelliJ IDEA, GoLand, PyCharm usw.)
2. Gehe zu **Einstellungen → Plugins → Marketplace**
3. Suche nach **Copilot Chat Exporter** und installiere das Plugin

Oder installiere es manuell über **Einstellungen → Plugins → Plugin von Datenträger installieren** mit der `.zip`-Datei von der [Releases](https://github.com/TomSchmidtDev/intellij-ai-chat-exporter/releases)-Seite.

## Verwendung

### Sitzungen suchen
1. Text in das **Suche**-Feld unterhalb der Toolbar-Schaltflächen eingeben
2. `AND`, `OR`, `"exakte Phrase"` und `(Klammergruppen)` für boolesche Abfragen nutzen
3. Mit den Schaltern **Prompts / Antworten / Titel** steuern, welche Inhalte durchsucht werden
4. Sitzungen mit Treffern zeigen einen Badge mit der Anzahl; Klick auf eine Sitzung zeigt hervorgehobene Nachrichten

### Copilot-Tab
1. Öffne das **AI Chat Exporter**-Tool-Fenster (rechte Seitenleiste) und wähle den Tab **Copilot**
2. Wähle die gewünschten Sitzungen über die Checkboxen aus
3. Optional einzelne Nachrichten im Vorschaufenster rechts auswählen
4. Klicke auf **Export MD** oder **Export HTML** und wähle einen Speicherort

### Claude Code-Tab
1. Wähle den Tab **Claude Code** im Tool-Fenster
2. Optional nach Projekt filtern über das **Projekt**-Dropdown
3. Sitzungen und einzelne Nachrichten wie oben auswählen
4. Mit den Schaltern **Tool Calls** und **Thinking** diese Block-Typen ein- oder ausblenden
5. Klicke auf **Export MD** oder **Export HTML**

## Voraussetzungen

- JetBrains-IDE 2025.1 oder neuer
- Für Copilot-Export: GitHub Copilot-Plugin installiert und mindestens eine Chat-Sitzung aufgezeichnet
- Für Claude Code-Export: Claude Code installiert und mindestens eine Sitzung in `~/.claude/projects/` vorhanden

## Datenschutz

Dieses Plugin erfasst, überträgt oder speichert keinerlei personenbezogene Daten. Alle Operationen werden lokal auf deinem Gerät ausgeführt. Es werden keine Telemetrie-, Analyse- oder Netzwerkanfragen durchgeführt.

## Lizenz

Business Source License 1.1 — kostenlos für private und nicht-kommerzielle Nutzung sowie den internen Einsatz in Unternehmen. Wird am 2031-04-04 automatisch zur Apache 2.0. Details in [LICENSE.md](LICENSE.md).
