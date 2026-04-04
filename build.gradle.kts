// =============================================================================
// build.gradle.kts – IntelliJ Platform Gradle Plugin 2.x
//
// LERNHINWEIS: Das IntelliJ Platform Gradle Plugin (Version 2.x) ersetzt das
// alte "gradle-intellij-plugin". Die neue API ist stabiler und besser
// typisiert. Dokumentation: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
// =============================================================================

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    // Das offizielle Plugin zum Bauen von IntelliJ-Plugins
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "de.schmidtdevs"
version = "1.4.0"

// LERNHINWEIS: IntelliJ IDEA 2024.1 läuft auf Java 17 und erwartet Bytecode
// für Java 17. Wir bauen mit dem System-JDK (Java 21), setzen aber
// jvmTarget=17 damit der Bytecode in der IDE funktioniert.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// LERNHINWEIS: intellijPlatform{} konfiguriert das Plugin-Manifest und
// die Ziel-IDE. pluginConfiguration entspricht dem alten intellij{} Block.
intellijPlatform {
    pluginConfiguration {
        id = "de.schmidtdevs.copilot-chat-exporter"
        name = "Copilot Chat Exporter"
        version = project.version.toString()

        ideaVersion {
            // LERNHINWEIS: sinceBuild = minimale IDE-Version (inklusiv).
            // untilBuild = maximale IDE-Version. Leer = keine obere Grenze,
            // d.h. das Plugin ist mit allen zukünftigen Builds kompatibel.
            // Ohne explizites untilBuild = "" setzt das Gradle Plugin automatisch
            // "241.*" als Obergrenze – was neuere IDEs blockiert.
            sinceBuild = "241"   // IntelliJ IDEA 2024.1+
            untilBuild = ""      // Keine Obergrenze → alle neueren Builds erlaubt
        }

        vendor {
            name = "schmidtdevs"
        }
    }
}

repositories {
    // LERNHINWEIS: defaultRepositories() fügt alle von IntelliJ Platform Gradle
    // Plugin 2.x benötigten JetBrains-Repos hinzu (CDN, Marketplace, etc.).
    // mavenCentral() muss zusätzlich explizit angegeben werden für Kotlin,
    // Xodus und andere externe Abhängigkeiten.
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // LERNHINWEIS: intellijPlatform{} im dependencies-Block definiert,
    // gegen welche IDE-Version kompiliert wird.
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
    }

    // Nitrite 4.x – die NoSQL-Datenbank, die GitHub Copilot für Chat-Sessions nutzt.
    // Copilot speichert Daten in *.db-Dateien (H2 MVStore 2.x als Backend, Write-Format 3).
    //
    // WICHTIG: Copilot verwendet H2 MVStore Write-Format 3 (benötigt H2 2.x).
    // Nitrite 3.x bundelt H2 1.4.200 und kann Format 3 NICHT lesen.
    // Nitrite 4.x bundelt H2 2.2.224 und liest Format 3 problemlos.
    //
    // WICHTIG: Copilot registriert Collections intern als Repositories, deshalb
    // gibt db.listCollectionNames() immer 0 zurück. Stattdessen:
    //   db.getStore().openMap(name, NitriteId::class.java, Document::class.java)
    //
    // LERNHINWEIS: In Nitrite 4.x ist das Storage-Backend ein separates Modul:
    //   - nitrite-mvstore-adapter: H2 MVStore-Backend (muss explizit geladen werden)
    //   - nitrite-jackson-mapper: JSON-Serialisierung (für JacksonMapperModule)
    //   - Builder: Nitrite.builder().loadModule(MVStoreModule.withConfig()...).openOrCreate()
    implementation("org.dizitart:nitrite:4.3.0")
    implementation("org.dizitart:nitrite-mvstore-adapter:4.3.0")
    implementation("org.dizitart:nitrite-jackson-mapper:4.3.0")
}
