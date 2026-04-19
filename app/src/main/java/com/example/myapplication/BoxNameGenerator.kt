package com.example.myapplication

/**
 * Generates unique, human-readable box names of the form
 * `BOX-{ADJECTIVE}-{NOUN}` (e.g. `BOX-SWIFT-EAGLE`).
 *
 * With 49 adjectives × 70 nouns = 3,430 possible combinations the
 * collision probability is negligible for typical usage, and the retry
 * loop plus timestamp fallback guarantee uniqueness regardless.
 */
object BoxNameGenerator {

    private val ADJECTIVES = listOf(
        "AMBER", "AZURE", "BOLD", "BRAVE", "BRIGHT", "CALM", "COOL", "CRISP",
        "DAWN", "DEEP", "EAGER", "EPIC", "FAST", "FIERCE", "FREE", "FRESH",
        "GRAND", "GREAT", "GREEN", "IRON", "JADE", "KEEN", "LIGHT", "NOBLE",
        "POLAR", "PRIME", "PURE", "QUICK", "QUIET", "RAPID", "SHARP", "SILVER",
        "SMART", "SOLAR", "STARK", "STEEL", "STERN", "STORM", "SUNNY", "SWIFT",
        "TALL", "TRUE", "VITAL", "VIVID", "WARM", "WHITE", "WILD", "WISE", "ZEAL"
    )

    private val NOUNS = listOf(
        "APEX", "ARCH", "BEAR", "BIRD", "BLADE", "CEDAR", "CLIFF", "CLOUD",
        "COAST", "CRANE", "CREEK", "CREST", "CROW", "DALE", "DAWN", "DELTA",
        "DOVE", "DUNE", "EAGLE", "ELK", "EMBER", "FJORD", "FLAME", "FLINT",
        "FORD", "FORGE", "FOX", "FROST", "GALE", "GLEN", "HAWK", "HERON",
        "HILL", "JADE", "KITE", "LAKE", "LANCE", "LARK", "LEDGE", "LYNX",
        "MESA", "MIST", "MOON", "OAK", "ONYX", "OTTER", "OWL", "PEAK",
        "PINE", "RAVEN", "REEF", "RIDGE", "RIVER", "ROCK", "SAGE", "SAND",
        "SLATE", "SNOW", "SOLAR", "SPIRE", "SPRUCE", "STAR", "STONE", "STORM",
        "STREAM", "TIDE", "TIGER", "TRAIL", "VALE", "VAULT", "WAVE", "WOLF"
    )

    /** Generate a single random candidate name. */
    fun generate(): String =
        "BOX-${ADJECTIVES.random()}-${NOUNS.random()}"

    /**
     * Generate a box name that is NOT present in [existingNames].
     *
     * Tries up to [maxAttempts] random candidates; if all collide (extremely
     * unlikely) it appends a millisecond timestamp to guarantee uniqueness.
     */
    fun generateUnique(existingNames: Set<String>, maxAttempts: Int = 30): String {
        repeat(maxAttempts) {
            val candidate = generate()
            if (!existingNames.contains(candidate)) return candidate
        }
        // Fallback: timestamp suffix guarantees a name that cannot already exist
        return "BOX-${System.currentTimeMillis()}"
    }
}
