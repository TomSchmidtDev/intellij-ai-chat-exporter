plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "de.tomschmidtdev"
version = "1.7.2"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        id = "de.tomschmidtdev.copilot-chat-exporter"
        name = "Copilot Chat Exporter"
        version = project.version.toString()

        description = providers.fileContents(layout.projectDirectory.file("DESCRIPTION.html")).asText

        // Shown on the Marketplace "What's New" tab. Update with each release.
        changeNotes = """
            <b>1.7.2</b>
            <ul>
                <li>Fixed: Claude Code session titles now read from Claude Desktop metadata — sessions that have an auto-generated title in Claude Desktop are now displayed with that name instead of the UUID</li>
                <li>Fixed: both ai-title (older) and custom-title (newer) JSONL title formats are now supported</li>
            </ul>
            <b>1.7.1</b>
            <ul>
                <li>Fixed: Claude Code session titles now shown correctly (were displayed as GUIDs)</li>
            </ul>
            <b>1.7.0</b>
            <ul>
                <li>New: search bar in both Copilot and Claude Code tabs — filter sessions live as you type</li>
                <li>New: boolean query syntax: AND, OR, phrase matching with quotes, and grouping with parentheses</li>
                <li>New: scope toggles to search prompts, responses, and/or session titles</li>
                <li>New: match-count badge on sessions with results; matching messages highlighted in the preview panel</li>
            </ul>
            <b>1.6.4</b>
            <ul>
                <li>Fixed: plugin version is now embedded at build time — eliminates all internal and deprecated PluginManager API usages (Marketplace compatibility)</li>
            </ul>
            <b>1.6.3</b>
            <ul>
                <li>Fixed: replaced internal IntelliJ Platform API usage (resolves JetBrains Marketplace compatibility warning for IntelliJ 2026.2+)</li>
            </ul>
            <b>1.6.2</b>
            <ul>
                <li>Changed: Prompts / Assistant / Tool Calls / Thinking buttons now toggle message checkbox selection (like Copilot tab) instead of hiding messages</li>
                <li>Changed: "Copilot" button in Copilot tab renamed to "Assistant" for consistency</li>
                <li>Changed: "User" button in Claude Code tab renamed to "Prompts" for consistency</li>
                <li>Changed: export always includes full message content; selection controls what is exported</li>
            </ul>
            <b>1.6.1</b>
            <ul>
                <li>Fixed: Claude Code message preview no longer blank for tool-only messages</li>
                <li>New: Claude Code messages show a tooltip with full content on hover (same as Copilot tab)</li>
                <li>New: last selected tab (Copilot / Claude Code) is remembered across IDE restarts</li>
            </ul>
            <b>1.6.0</b>
            <ul>
                <li>New: Claude Code tab — browse and export Claude Code chat sessions from ~/.claude/projects/</li>
                <li>New: filter Claude Code sessions by project via dropdown</li>
                <li>New: toggle tool calls and thinking blocks on/off (hidden by default; User and Assistant shown by default)</li>
            </ul>
            <b>1.5.6</b>
            <ul>
                <li>Fixed: IDE filter now correctly identifies the current IDE via product code (e.g. "iu" for IntelliJ IDEA Ultimate) — previously no sessions were shown when "All IDEs" was unchecked</li>
                <li>New: Settings page now shows the detected IDE directory name below the "All IDEs" checkbox</li>
            </ul>
            <b>1.5.5</b>
            <ul>
                <li>New: sessions are now filtered to the current IDE by default; enable "All IDEs" in the toolbar or Settings to see sessions from all JetBrains IDEs</li>
            </ul>
            <b>1.5.4</b>
            <ul>
                <li>Improved: plugin can now be installed and uninstalled without restarting the IDE (dynamic plugin)</li>
            </ul>
            <b>1.5.3</b>
            <ul>
                <li>Fixed: replaced internal API PluginManagerCore.getPlugin() with public PluginManager.getPluginByClass() for compatibility with IntelliJ 2026.2+</li>
            </ul>
            <b>1.5.2</b>
            <ul>
                <li>Changed: session list now shows and sorts by last-modified date (timestamp of the most recent turn) instead of creation date</li>
                <li>Added: hovering over a message in the preview shows a tooltip with its timestamp and up to 10 lines of content</li>
            </ul>
            <b>1.5.1</b>
            <ul>
                <li>Fixed: diagnostic no longer truncates session and turn lists (previously capped at 3 sessions / 5 turns per database)</li>
                <li>Improved: diagnostic groups turns by session with USER / ASSISTANT content indicators per turn</li>
                <li>Improved: timestamps in diagnostic shown as formatted dates (e.g. 2026-01-15 14:37:43) plus raw milliseconds</li>
                <li>Improved: sessions without turns are marked FILTERED in diagnostic output so it is clear why they do not appear in the plugin</li>
            </ul>
            <b>1.5.0</b>
            <ul>
                <li>Added: plugin logo displayed in the JetBrains Marketplace and IDE plugin list</li>
                <li>Added: preview panel header now shows the title of the currently selected session</li>
                <li>Added: toggle buttons to select/deselect all user prompts or all Copilot responses in one click</li>
            </ul>
            <b>1.4.4</b>
            <ul>
                <li>Fixed: upgraded jackson-core to 2.18.6 to resolve moderate DoS vulnerability (Number Length Constraint Bypass)</li>
                <li>Changed: renamed internal package from de.schmidtdevs to de.tomschmidtdev</li>
            </ul>
            <b>1.4.3</b>
            <ul>
                <li>Fixed: session titles and message previews with &lt;, &gt;, or &amp; now display correctly in the UI</li>
                <li>Fixed: settings dialog no longer crashes on corrupted color values</li>
                <li>Improved: reduced memory usage when reading large Copilot databases (lazy turn filtering)</li>
            </ul>
            <b>1.4.2</b>
            <ul>
                <li>Prepared for JetBrains Marketplace: improved description, signing &amp; publishing pipeline</li>
                <li>Added GitHub Actions CI/CD workflow for automated releases</li>
            </ul>
            <b>1.4.1</b>
            <ul>
                <li>Fixed parsing of Subgraph-wrapped agent session responses (agent-mode chats now export correctly)</li>
                <li>Renamed tool window to "Copilot Chat Exporter"</li>
                <li>Upgraded Kotlin compiler to 2.1.20 (fixes build failure on systems with JDK 25)</li>
            </ul>
            <b>1.4.0</b>
            <ul>
                <li>Initial public release</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "251"   // IntelliJ IDEA 2025.1+
            // untilBuild not set → no upper limit, compatible with all future builds
        }

        vendor {
            name = "TomSchmidtDev"
            url = "https://github.com/TomSchmidtDev"
        }
    }

    // Plugin signing — required for JetBrains Marketplace.
    // Supply via environment variables or GitHub Secrets:
    //   CERTIFICATE_CHAIN  – PEM chain (cert + intermediates)
    //   PRIVATE_KEY        – PEM private key
    //   PRIVATE_KEY_PASSWORD – passphrase (empty string if none)
    // Generate a self-signed certificate with:
    //   ./gradlew generateCertificate   (or use marketplace-zip-signer CLI)
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Publishing to JetBrains Marketplace.
    // Requires a Personal Access Token from https://plugins.jetbrains.com/author/me/tokens
    // Supply via environment variable or GitHub Secret: PUBLISH_TOKEN
    // First upload must be done manually via the Marketplace UI.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Uncomment to publish to a non-default channel (e.g. beta):
        // channels = listOf("beta")
    }

    // Plugin Verifier — checks binary compatibility across IDE versions.
    // Run with: ./gradlew verifyPlugin
    pluginVerification {
        ides {
            recommended()
        }
    }
}

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val pluginVersion = project.version.toString()
    outputs.dir(outputDir)
    inputs.property("version", pluginVersion)
    doLast {
        val dir = outputDir.get().asFile.resolve("de/tomschmidtdev/copilotexporter")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """package de.tomschmidtdev.copilotexporter

object BuildConfig {
    const val VERSION = "$pluginVersion"
}
"""
        )
    }
}

tasks.compileKotlin {
    dependsOn(generateBuildConfig)
    source(layout.buildDirectory.dir("generated/buildconfig"))
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        pluginVerifier()
        zipSigner()
    }

    // Nitrite 4.x — reads GitHub Copilot's local chat database (H2 MVStore, Write-Format 3).
    // Nitrite 3.x cannot read these files; Nitrite 4.x with H2 2.x is required.
    implementation("org.dizitart:nitrite:4.3.0")
    implementation("org.dizitart:nitrite-mvstore-adapter:4.3.0")
    implementation("org.dizitart:nitrite-jackson-mapper:4.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Force jackson >= 2.18.6 to fix CVE: Number Length Constraint Bypass (moderate DoS).
    // jackson 2.17.1 (pulled in transitively by nitrite-jackson-mapper) is vulnerable.
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-core:2.18.6") {
            because("jackson-core < 2.18.6 is vulnerable to a DoS via async parser (GitHub advisory)")
        }
        implementation("com.fasterxml.jackson.core:jackson-databind:2.18.6") {
            because("align jackson-databind with forced jackson-core version")
        }
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.6") {
            because("align jackson-annotations with forced jackson-core version")
        }
    }
}
