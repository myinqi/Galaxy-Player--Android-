package dev.betsy.galaxy.logic

import org.json.JSONObject

data class Upgrade(
    val id: String,
    val category: String,
    val name: String,
    val description: String,
    val price: Int,
    val effect: String,
    val color: String,
    val playbackRate: Double,
    val sortOrder: Int,
) {
    fun toJson(): JSONObject {
        val config = JSONObject().put("effect", effect)
        if (category == "engine") config.put("playback_rate", playbackRate) else config.put("color", color)
        return JSONObject()
            .put("id", id)
            .put("category", category)
            .put("name", name)
            .put("description", description)
            .put("price", price)
            .put("enabled", 1)
            .put("sort_order", sortOrder)
            .put("config", config)
            .put("config_json", config.toString())
    }
}

/** Default shipyard from Betsy `UPGRADES`. The app API has no shop, so the device keeps this catalog. */
val SHIPYARD: List<Upgrade> = listOf(
    Upgrade("scout", "hull", "Scout", "Reliable starter exploration ship.", 0, "ship", "#f2efff", 1.0, 0),
    Upgrade("raven", "hull", "Raven", "Animated wings, feathers and a violet aura.", 120, "raven", "#b44dff", 1.0, 10),
    Upgrade("nebula", "hull", "Nebula", "Curved hull with a violet canopy.", 240, "ship", "#a855f7", 1.0, 20),
    Upgrade("music_matrix", "hull", "Music Matrix", "A rotating geometric signal cube.", 300, "cube", "#a4ec6c", 1.0, 30),
    Upgrade("explorer", "hull", "Explorer", "A nimble deep-space survey craft.", 320, "explorer", "#8fd3ff", 1.0, 40),
    Upgrade("destroyer", "hull", "Star Destroyer", "A sharp imperial silhouette.", 400, "destroyer", "#c8cad2", 1.0, 50),
    Upgrade("battlestar", "hull", "Battlestar", "Armored carrier with twin flight decks.", 600, "battlestar", "#9a9ca4", 1.0, 60),
    Upgrade("venator", "hull", "Venator", "A crimson-lined republic cruiser.", 550, "venator", "#d85b63", 1.0, 70),
    Upgrade("gothic", "hull", "Gothic Cruiser", "Cathedral towers among the stars.", 650, "gothic_cruiser", "#a29aa9", 1.0, 80),
    Upgrade("borg", "hull", "Collective Cube", "Resistance remains computationally unlikely.", 700, "borg_cube", "#6cff91", 1.0, 90),
    Upgrade("bird", "hull", "Bird of Prey", "A swept-wing emerald raider.", 450, "bird_of_prey", "#5e8f72", 1.0, 100),
    Upgrade("basestar", "hull", "Basestar", "A mirrored mechanical command ship.", 500, "basestar", "#b7b8c8", 1.0, 110),
    Upgrade("ion_blue", "trail", "Ion Blue", "Classic blue engine trail.", 0, "engine", "#64d8ff", 1.0, 0),
    Upgrade("plasma_pink", "trail", "Plasma Pink", "Bright magenta sparkle trail.", 80, "sparkle", "#f472d0", 1.0, 10),
    Upgrade("solar_gold", "trail", "Solar Gold", "Warm golden engine wake.", 160, "engine", "#fbbf24", 1.0, 20),
    Upgrade("nebula_wake", "trail", "Nebula Wake", "A drifting interstellar cloud.", 220, "nebula", "#a855f7", 1.0, 30),
    Upgrade("pulse", "scanner", "Pulse Scanner", "Standard planetary scan pulse.", 0, "pulse", "#8b7cff", 1.0, 0),
    Upgrade("rune", "scanner", "Rune Scanner", "A rotating runic targeting field.", 100, "rune", "#d946ef", 1.0, 10),
    Upgrade("radar", "scanner", "Deep Radar", "A focused radar sweep.", 180, "radar", "#67e8f9", 1.0, 20),
    Upgrade("slipstream", "engine", "Slipstream Drive", "Unlocks optional 1.2× playback.", 125, "speed", "", 1.2, 0),
    Upgrade("still", "worlds", "Still Worlds", "Classic still planets and a quiet star.", 0, "still", "#f4c56a", 1.0, 0),
    Upgrade("living", "worlds", "Living Worlds", "Drifting clouds, turning rings and a burning sun.", 200, "living", "#ffb347", 1.0, 10),
    Upgrade("observatory", "worlds", "Observatory", "NASA/JPL-Caltech public-domain maps of real worlds and the Sun.", 280, "observatory", "#c4a574", 1.0, 20),
)

fun upgradeById(id: String): Upgrade? = SHIPYARD.find { it.id == id }
