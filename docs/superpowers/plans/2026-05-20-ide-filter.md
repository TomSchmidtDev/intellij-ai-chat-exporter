# IDE Filter for Copilot Sessions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Filter Copilot sessions to the current IDE by default, with an "All IDEs" toggle in both the toolbar and the Settings page.

**Architecture:** Add `showAllIdes: Boolean` to `ExporterSettings.State` (persistent). `CopilotChatReaderService.findAllDatabaseFiles()` reads this flag and filters IDE subdirectories via `ApplicationNamesInfo.getInstance().scriptName`. The `ExporterPanel` toolbar gets a `JCheckBox` (first item, left of Refresh); the `ExporterSettingsPanel` gets a matching checkbox in a new General section.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`ApplicationNamesInfo`, `PersistentStateComponent`), Swing

---

## Files Modified

- `src/main/kotlin/.../settings/ExporterSettings.kt` — add `showAllIdes` field to `State`
- `src/main/kotlin/.../services/CopilotChatReaderService.kt` — filter `findAllDatabaseFiles()` by current IDE
- `src/main/kotlin/.../ui/ExporterPanel.kt` — add `allIdesCheckBox` to toolbar, update empty-state hint, sync on load
- `src/main/kotlin/.../settings/ExporterSettingsPanel.kt` — add checkbox, wire `isModified`/`apply`/`reset`
- `build.gradle.kts` — version 1.5.4 → 1.5.5
- `CHANGELOG.md` — new entry for 1.5.5

---

## Task 1: Add `showAllIdes` to `ExporterSettings`

**Files:**
- Modify: `src/main/kotlin/de/tomschmidtdev/copilotexporter/settings/ExporterSettings.kt`

- [ ] **Add the field to `State`**

  In `ExporterSettings.kt`, extend the `State` data class with one new field at the end:

  ```kotlin
  data class State(
      var background: String = "#1e1e2e",
      var text: String = "#cdd6f4",
      var userMessageBg: String = "#1e3a5f",
      var userMessageBorder: String = "#89b4fa",
      var assistantMessageBg: String = "#1b3a2f",
      var assistantMessageBorder: String = "#a6e3a1",
      var sessionHeaderBg: String = "#181825",
      var sessionTitleColor: String = "#cba6f7",
      var codeBg: String = "#11111b",
      var borderColor: String = "#313244",
      var showAllIdes: Boolean = false,
  )
  ```

- [ ] **Compile check**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

  ```bash
  git add src/main/kotlin/de/tomschmidtdev/copilotexporter/settings/ExporterSettings.kt
  git commit -m "feat: add showAllIdes field to ExporterSettings"
  ```

---

## Task 2: Filter sessions by current IDE in `CopilotChatReaderService`

**Files:**
- Modify: `src/main/kotlin/de/tomschmidtdev/copilotexporter/services/CopilotChatReaderService.kt`

- [ ] **Add import for `ApplicationNamesInfo` and `ExporterSettings`**

  At the top of `CopilotChatReaderService.kt`, add:

  ```kotlin
  import com.intellij.openapi.application.ApplicationNamesInfo
  import de.tomschmidtdev.copilotexporter.settings.ExporterSettings
  ```

- [ ] **Update `findAllDatabaseFiles()` to accept and apply the filter**

  Replace the existing `findAllDatabaseFiles()` signature and body:

  ```kotlin
  private fun findAllDatabaseFiles(showAllIdes: Boolean): List<Pair<File, SessionTypeConfig>> {
      val result = mutableListOf<Pair<File, SessionTypeConfig>>()
      val seen = mutableSetOf<String>()
      val currentIde = ApplicationNamesInfo.getInstance().scriptName.lowercase()

      copilotBaseDirs().forEach { base ->
          base.listFiles()?.filter { it.isDirectory }
              ?.filter { showAllIdes || it.name.lowercase() == currentIde }
              ?.forEach { ideDir ->
                  sessionTypes.forEach { (sessionTypeName, config) ->
                      File(ideDir, sessionTypeName).takeIf { it.exists() }
                          ?.listFiles()?.filter { it.isDirectory }
                          ?.forEach { hashDir ->
                              val dbFile = File(hashDir, config.dbFileName)
                              if (dbFile.exists() && seen.add(dbFile.canonicalPath)) {
                                  result.add(dbFile to config)
                              }
                          }
                  }
              }
      }
      return result
  }
  ```

- [ ] **Update `readSessions()` to pass the setting**

  Replace the call to `findAllDatabaseFiles()` in `readSessions()`:

  ```kotlin
  fun readSessions(): List<ChatSession> {
      val showAllIdes = ExporterSettings.getInstance().state.showAllIdes
      val dbFiles = findAllDatabaseFiles(showAllIdes)
      if (dbFiles.isEmpty()) {
          log.info("No Copilot *.db files found under ${copilotBaseDirs().map { it.absolutePath }}")
          return emptyList()
      }
      return dbFiles.flatMap { (dbFile, config) ->
          readNitriteDb(dbFile, config)
      }.sortedByDescending { if (it.lastModifiedAt > 0L) it.lastModifiedAt else it.createdAt }
  }
  ```

- [ ] **Update `diagnose()` to pass the setting**

  Replace the call to `findAllDatabaseFiles()` in `diagnose()`:

  ```kotlin
  fun diagnose(): DiagnosticReport {
      val showAllIdes = ExporterSettings.getInstance().state.showAllIdes
      val baseDirs = copilotBaseDirs()
      val dbFiles = findAllDatabaseFiles(showAllIdes)
      // ... rest of the method unchanged
  ```

- [ ] **Compile check**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

  ```bash
  git add src/main/kotlin/de/tomschmidtdev/copilotexporter/services/CopilotChatReaderService.kt
  git commit -m "feat: filter Copilot DB scan to current IDE by default"
  ```

---

## Task 3: Add "All IDEs" checkbox to `ExporterPanel`

**Files:**
- Modify: `src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ExporterPanel.kt`

- [ ] **Add import for `ExporterSettings` and `JCheckBox`**

  Add to imports in `ExporterPanel.kt`:

  ```kotlin
  import de.tomschmidtdev.copilotexporter.settings.ExporterSettings
  import javax.swing.JCheckBox
  ```

- [ ] **Declare `allIdesCheckBox` as a field**

  Add this field to the UI-Komponenten section of `ExporterPanel`, after `statusLabel`:

  ```kotlin
  private val allIdesCheckBox = JCheckBox("All IDEs").apply {
      toolTipText = "Show sessions from all JetBrains IDEs, not just this one"
      isSelected = ExporterSettings.getInstance().state.showAllIdes
      addActionListener {
          ExporterSettings.getInstance().state.showAllIdes = isSelected
          loadSessionsInBackground()
      }
  }
  ```

- [ ] **Insert checkbox as first item in `leftButtons`**

  In `buildLayout()`, modify the `leftButtons` block to add `allIdesCheckBox` first:

  ```kotlin
  val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
      add(allIdesCheckBox)
      add(JButton("Refresh", AllIcons.Actions.Refresh).apply {
          toolTipText = "Reload chat sessions from Copilot database"
          addActionListener { loadSessionsInBackground() }
      })
      add(JButton("Export MD", AllIcons.FileTypes.Text).apply {
          toolTipText = "Export selected sessions to Markdown"
          addActionListener { doExport(ExportFormat.MARKDOWN) }
      })
      add(JButton("Export HTML", AllIcons.FileTypes.Html).apply {
          toolTipText = "Export selected sessions to HTML"
          addActionListener { doExport(ExportFormat.HTML) }
      })
  }
  ```

- [ ] **Update `loadSessionsInBackground()` to sync checkbox from settings**

  At the start of `loadSessionsInBackground()`, sync the checkbox with the persisted setting (handles the case where the user changed the setting via the Settings page):

  ```kotlin
  private fun loadSessionsInBackground() {
      allIdesCheckBox.isSelected = ExporterSettings.getInstance().state.showAllIdes
      setStatus("Loading…")
      // ... rest unchanged
  ```

- [ ] **Update empty-state message in `populateSessionList()`**

  Replace the empty-state block:

  ```kotlin
  if (sessions.isEmpty()) {
      val hint = if (ExporterSettings.getInstance().state.showAllIdes) ""
                 else " Enable \"All IDEs\" to see sessions from other IDEs."
      setStatus("No Copilot sessions found for this IDE.$hint")
      return
  }
  ```

- [ ] **Compile check**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

  ```bash
  git add src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ExporterPanel.kt
  git commit -m "feat: add All IDEs checkbox to ExporterPanel toolbar"
  ```

---

## Task 4: Add "All IDEs" setting to `ExporterSettingsPanel`

**Files:**
- Modify: `src/main/kotlin/de/tomschmidtdev/copilotexporter/settings/ExporterSettingsPanel.kt`

- [ ] **Add import for `JCheckBox`**

  `javax.swing.JCheckBox` is already needed; add to imports if not present:

  ```kotlin
  import javax.swing.JCheckBox
  ```

- [ ] **Declare `showAllIdesCheckBox` as a field**

  Add this field after `profileCombo` in `ExporterSettingsPanel`:

  ```kotlin
  private val showAllIdesCheckBox = JCheckBox("Show sessions from all IDEs (not just the current one)")
  ```

- [ ] **Add a General section to `buildFormPanel()`**

  In `buildFormPanel()`, add a "General" section above the existing color form. Replace the `return JPanel(java.awt.BorderLayout()).apply { ... }` block:

  ```kotlin
  val generalSection = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      border = JBUI.Borders.emptyTop(4)
      add(showAllIdesCheckBox)
  }

  val generalWrapper = JPanel(java.awt.BorderLayout()).apply {
      add(TitledSeparator("General"), java.awt.BorderLayout.NORTH)
      add(generalSection, java.awt.BorderLayout.CENTER)
  }

  return JPanel(java.awt.BorderLayout()).apply {
      border = JBUI.Borders.empty(0)
      add(generalWrapper, java.awt.BorderLayout.NORTH)
      add(JPanel(java.awt.BorderLayout()).apply {
          add(TitledSeparator("HTML Export Colors"), java.awt.BorderLayout.NORTH)
          add(colorForm, java.awt.BorderLayout.CENTER)
          add(debugSection, java.awt.BorderLayout.SOUTH)
      }, java.awt.BorderLayout.CENTER)
  }
  ```

- [ ] **Update `isModified()` to include the new checkbox**

  ```kotlin
  fun isModified(): Boolean {
      val state = ExporterSettings.getInstance().state
      return showAllIdesCheckBox.isSelected != state.showAllIdes ||
          entries.any { entry ->
              entry.panel.selectedColor?.toHex() != entry.getter(state)
          }
  }
  ```

- [ ] **Update `apply()` to persist the checkbox**

  ```kotlin
  fun apply() {
      val state = ExporterSettings.getInstance().state
      state.showAllIdes = showAllIdesCheckBox.isSelected
      entries.forEach { entry ->
          entry.panel.selectedColor?.toHex()?.let { hex -> entry.setter(state, hex) }
      }
  }
  ```

- [ ] **Update `reset()` to restore the checkbox**

  Add one line at the start of the existing `reset()` body:

  ```kotlin
  fun reset() {
      val state = ExporterSettings.getInstance().state
      showAllIdesCheckBox.isSelected = state.showAllIdes
      entries.forEach { entry ->
          runCatching { Color.decode(entry.getter(state)) }.getOrNull()
              ?.let { entry.panel.selectedColor = it }
      }
      profileCombo.selectedItem = detectProfile(state)
  }
  ```

- [ ] **Compile check**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

  ```bash
  git add src/main/kotlin/de/tomschmidtdev/copilotexporter/settings/ExporterSettingsPanel.kt
  git commit -m "feat: add All IDEs checkbox to ExporterSettingsPanel"
  ```

---

## Task 5: Version bump and changelog

**Files:**
- Modify: `build.gradle.kts`
- Modify: `CHANGELOG.md`

- [ ] **Update version in `build.gradle.kts`**

  ```kotlin
  version = "1.5.5"
  ```

- [ ] **Add new `changeNotes` entry in `build.gradle.kts`** (prepend before the `1.5.4` block):

  ```
  <b>1.5.5</b>
  <ul>
      <li>New: sessions are now filtered to the current IDE by default; enable "All IDEs" in the toolbar or Settings to see sessions from all JetBrains IDEs</li>
  </ul>
  ```

- [ ] **Add entry to `CHANGELOG.md`** (prepend after the `# Changelog` heading):

  ```markdown
  ## [1.5.5] - 2026-05-20
  ### Added
  - Sessions are now filtered to the current IDE by default
  - "All IDEs" checkbox in the toolbar and Settings page to show sessions from all JetBrains IDEs
  ```

- [ ] **Compile check**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

  ```bash
  git add build.gradle.kts CHANGELOG.md
  git commit -m "Bump version to 1.5.5: IDE filter with All IDEs toggle"
  ```

---

## Task 6: Push and release

- [ ] **Push commits to origin/main**

  ```bash
  git push origin main
  ```

- [ ] **Tag and push release**

  ```bash
  git tag v1.5.5 && git push origin v1.5.5
  ```

  This triggers the GitHub Actions release workflow (build → sign → publish to Marketplace → GitHub Release).
