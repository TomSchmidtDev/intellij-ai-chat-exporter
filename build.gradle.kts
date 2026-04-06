plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "de.tomschmidtdev"
version = "1.4.6"

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
