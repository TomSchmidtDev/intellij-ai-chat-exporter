package de.tomschmidtdev.copilotexporter.settings

// =============================================================================
// ColorProfile – Farbschema für den HTML-Export
//
// Jedes Profil definiert die zehn CSS-Farben, die der HtmlExporter verwendet.
// Die fünf vordefinierten Profile decken gängige Editor-Themes ab.
// CUSTOM dient als Sentinel-Wert im Dropdown wenn keine vordefinierten Farben
// aktiv sind oder der Nutzer manuell Farben angepasst hat.
// =============================================================================
data class ColorProfile(
    val name: String,
    val background: String,
    val text: String,
    val userMessageBg: String,
    val userMessageBorder: String,
    val assistantMessageBg: String,
    val assistantMessageBorder: String,
    val sessionHeaderBg: String,
    val sessionTitleColor: String,
    val codeBg: String,
    val borderColor: String,
) {
    // toString() wird vom JComboBox-Renderer verwendet
    override fun toString(): String = name
}

object ColorProfiles {

    val CATPPUCCIN_MOCHA = ColorProfile(
        name = "Dark – Catppuccin Mocha",
        background = "#1e1e2e",
        text = "#cdd6f4",
        userMessageBg = "#1e3a5f",
        userMessageBorder = "#89b4fa",
        assistantMessageBg = "#1b3a2f",
        assistantMessageBorder = "#a6e3a1",
        sessionHeaderBg = "#181825",
        sessionTitleColor = "#cba6f7",
        codeBg = "#11111b",
        borderColor = "#313244",
    )

    val GITHUB_DARK = ColorProfile(
        name = "Dark – GitHub",
        background = "#0d1117",
        text = "#c9d1d9",
        userMessageBg = "#1f3d6e",
        userMessageBorder = "#388bfd",
        assistantMessageBg = "#1a3528",
        assistantMessageBorder = "#3fb950",
        sessionHeaderBg = "#161b22",
        sessionTitleColor = "#d2a8ff",
        codeBg = "#161b22",
        borderColor = "#30363d",
    )

    val DRACULA = ColorProfile(
        name = "Dark – Dracula",
        background = "#282a36",
        text = "#f8f8f2",
        userMessageBg = "#1a2a4a",
        userMessageBorder = "#6272a4",
        assistantMessageBg = "#1e3325",
        assistantMessageBorder = "#50fa7b",
        sessionHeaderBg = "#21222c",
        sessionTitleColor = "#bd93f9",
        codeBg = "#1e1f29",
        borderColor = "#44475a",
    )

    val NORD = ColorProfile(
        name = "Dark – Nord",
        background = "#2e3440",
        text = "#d8dee9",
        userMessageBg = "#2d3f5c",
        userMessageBorder = "#5e81ac",
        assistantMessageBg = "#2a3d30",
        assistantMessageBorder = "#a3be8c",
        sessionHeaderBg = "#272c36",
        sessionTitleColor = "#b48ead",
        codeBg = "#242933",
        borderColor = "#3b4252",
    )

    val LIGHT = ColorProfile(
        name = "Light – Classic",
        background = "#ffffff",
        text = "#24292f",
        userMessageBg = "#dbeafe",
        userMessageBorder = "#3b82f6",
        assistantMessageBg = "#dcfce7",
        assistantMessageBorder = "#22c55e",
        sessionHeaderBg = "#f6f8fa",
        sessionTitleColor = "#7c3aed",
        codeBg = "#f6f8fa",
        borderColor = "#d1d5db",
    )

    val SOLARIZED_DARK = ColorProfile(
        name = "Dark – Solarized",
        background = "#002b36",
        text = "#839496",
        userMessageBg = "#003847",
        userMessageBorder = "#268bd2",
        assistantMessageBg = "#003040",
        assistantMessageBorder = "#859900",
        sessionHeaderBg = "#073642",
        sessionTitleColor = "#cb4b16",
        codeBg = "#073642",
        borderColor = "#586e75",
    )

    val MONOKAI = ColorProfile(
        name = "Dark – Monokai Pro",
        background = "#2d2a2e",
        text = "#fcfcfa",
        userMessageBg = "#1c2c44",
        userMessageBorder = "#78dce8",
        assistantMessageBg = "#1c2e20",
        assistantMessageBorder = "#a9dc76",
        sessionHeaderBg = "#221f22",
        sessionTitleColor = "#ab9df2",
        codeBg = "#19181a",
        borderColor = "#403e41",
    )

    val TOKYO_NIGHT = ColorProfile(
        name = "Dark – Tokyo Night",
        background = "#1a1b26",
        text = "#c0caf5",
        userMessageBg = "#1e2d4d",
        userMessageBorder = "#7aa2f7",
        assistantMessageBg = "#1a2f28",
        assistantMessageBorder = "#9ece6a",
        sessionHeaderBg = "#16161e",
        sessionTitleColor = "#bb9af7",
        codeBg = "#16161e",
        borderColor = "#292e42",
    )

    val MATERIAL_OCEAN = ColorProfile(
        name = "Dark – Material Ocean",
        background = "#0f111a",
        text = "#8f93a2",
        userMessageBg = "#172038",
        userMessageBorder = "#82aaff",
        assistantMessageBg = "#152a20",
        assistantMessageBorder = "#c3e88d",
        sessionHeaderBg = "#090b10",
        sessionTitleColor = "#c792ea",
        codeBg = "#090b10",
        borderColor = "#1f2233",
    )

    val ONE_DARK = ColorProfile(
        name = "Dark – One Dark",
        background = "#282c34",
        text = "#abb2bf",
        userMessageBg = "#1e2d40",
        userMessageBorder = "#61afef",
        assistantMessageBg = "#1e2d26",
        assistantMessageBorder = "#98c379",
        sessionHeaderBg = "#21252b",
        sessionTitleColor = "#c678dd",
        codeBg = "#21252b",
        borderColor = "#3b4048",
    )

    /** Sentinel-Wert: wird gewählt wenn keine vordefinierten Farben aktiv sind */
    val CUSTOM = ColorProfile(
        name = "Custom",
        background = "", text = "",
        userMessageBg = "", userMessageBorder = "",
        assistantMessageBg = "", assistantMessageBorder = "",
        sessionHeaderBg = "", sessionTitleColor = "",
        codeBg = "", borderColor = "",
    )

    val ALL: List<ColorProfile> = listOf(
        CATPPUCCIN_MOCHA, GITHUB_DARK, DRACULA, NORD, LIGHT,
        SOLARIZED_DARK, MONOKAI, TOKYO_NIGHT, MATERIAL_OCEAN, ONE_DARK,
    )
    val ALL_WITH_CUSTOM: List<ColorProfile> = listOf(CUSTOM) + ALL
    val DEFAULT: ColorProfile = CATPPUCCIN_MOCHA
}
