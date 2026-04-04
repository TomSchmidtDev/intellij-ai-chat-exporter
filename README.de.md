# Copilot Chat Exporter

> [English version](README.md)

Ein IntelliJ-Plugin, mit dem du deine **GitHub Copilot-Chatverläufe** direkt aus der IDE heraus als Markdown oder gestyltes HTML exportieren kannst.

![Plugin Panel](screenshots/plugin-panel.png)

## Funktionen

- **Alle Chat-Sitzungen durchsuchen** in einem dedizierten Tool-Fenster direkt in der IDE
- **Einzelne Nachrichten auswählen** pro Sitzung für teilweise Exporte
- **Export als Markdown** (`.md`) — sauber, portabel, direkt einfügbar
- **Export als HTML** (`.html`) — vollständig gestylt mit Syntax-Highlighting für Codeblöcke, öffnet sich nach dem Export direkt in der IDE
- **10 eingebaute Farbthemen** für HTML-Ausgabe: Catppuccin Mocha, GitHub Dark, Dracula, Nord, Tokyo Night, One Dark, Monokai Pro, Material Ocean, Solarized Dark, Light Classic
- **Eigenes Farbprofil** — alle HTML-Farben individuell über die Einstellungsseite konfigurierbar
- **Nur-Lesen-Zugriff** — die Copilot-Chatdatenbank wird niemals verändert

## Einstellungen

![Plugin Settings](screenshots/plugin-settings.png)

Öffne **Einstellungen → Tools → Copilot Chat Exporter**, um das HTML-Farbprofil zu konfigurieren. Wähle aus 10 Voreinstellungen oder passe jede Farbe individuell an.

## Installation

1. Öffne deine JetBrains-IDE (IntelliJ IDEA, GoLand, PyCharm usw.)
2. Gehe zu **Einstellungen → Plugins → Marketplace**
3. Suche nach **Copilot Chat Exporter** und installiere das Plugin

Oder installiere es manuell über **Einstellungen → Plugins → Plugin von Datenträger installieren** mit der `.zip`-Datei von der [Releases](https://github.com/TomSchmidtDev/intellij-ai-chat-exporter/releases)-Seite.

## Verwendung

1. Öffne das **Copilot Exporter**-Tool-Fenster (rechte Seitenleiste)
2. Wähle die gewünschten Sitzungen über die Checkboxen aus
3. Optional einzelne Nachrichten im Vorschaufenster rechts auswählen
4. Klicke auf **Export MD** oder **Export HTML** und wähle einen Speicherort

## Voraussetzungen

- JetBrains-IDE 2024.1 oder neuer
- GitHub Copilot-Plugin installiert und mindestens eine Chat-Sitzung aufgezeichnet

## Lizenz

Business Source License 1.1 — kostenlos für private und nicht-kommerzielle Nutzung sowie den internen Einsatz in Unternehmen. Wird am 2031-04-04 automatisch zur Apache 2.0. Details in [LICENSE.md](LICENSE.md).
