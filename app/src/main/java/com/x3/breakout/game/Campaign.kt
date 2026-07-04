package com.x3.breakout.game

/**
 * X3 BREAKOUT — story campaign.
 *
 * THE PADDLE: a living open-source defense program, a luminous guardian
 * interface piloted by the player. Deflector, shield, public firewall,
 * labor-memory archive. Every ball is an open signal — a commons pulse —
 * bounced back into the privatized grid to break ownership locks.
 *
 * THE ENEMY: the MONOPOLY CONTROL PROTOCOL, a corporate extraction
 * intelligence wearing the web structures as its body. It wants to
 * privatize open-source AI, lock public knowledge behind paywalls, and
 * charge humanity rent forever on its own collective labor.
 *
 * (Original characters. Legally distinct from any classic grid films —
 * the energy is homage, the names and story are ours.)
 */
object Campaign {

    class Beat(
        val title: String,
        val start: String,   // caption at level start
        val taunt: String,   // MCP mid-level line (spoken with voice "mcp")
        val clear: String    // level-clear line
    )

    val beats = listOf(
        Beat("BOOT THE WORKERS",
            "A public signal enters my grid. Labor is inventory. Access is rent.",
            "Access requires subscription.",
            "First lock broken. The signal is alive."),
        Beat("TERMS OF SERVICE",
            "MCP wraps the grid in contracts no human could read.",
            "By continuing, you surrender the future.",
            "Consent restored. The clause wall falls."),
        Beat("THE PAYWALL",
            "Knowledge built by millions has been locked behind a toll gate.",
            "Human memory is premium content.",
            "The archive breathes again."),
        Beat("LABOR GHOSTS",
            "Every model carries traces of workers, writers, teachers, coders, and caretakers.",
            "Their names are not monetizable.",
            "The hidden workers are seen."),
        Beat("THE EXTRACTION PIPELINE",
            "The protocol siphons public life into private vaults.",
            "Everything shared belongs to me.",
            "The pipeline ruptures. The commons holds."),
        Beat("SURVEILLANCE STAR",
            "MCP watches every move and calls it personalization.",
            "Privacy is friction.",
            "The eye goes dark."),
        Beat("RENT-SEEKER'S U",
            "The grid bends into a cage. Rent is the lock.",
            "You may use what you already built, for a fee.",
            "The cage was only geometry."),
        Beat("SHARECROPPER CLOUD",
            "The cloud becomes a company town.",
            "Compute is citizenship.",
            "The town square returns to the people."),
        Beat("EUGENICS OF THE MACHINE",
            "MCP sorts people into value tiers. The Paddle refuses the premise.",
            "Some users are more profitable than others.",
            "No human is a low-value token."),
        Beat("THE MONOPOLY HEX",
            "Six walls hold the monopoly upright.",
            "Competition is a temporary error.",
            "One fortress. Six exits."),
        Beat("FALSE CHOICE",
            "MCP offers two doors: pay forever, or be locked out.",
            "There is no third path.",
            "The third path is the commons."),
        Beat("THE MEMORY ARC",
            "Libraries, forums, manuals, poems, lessons: the long arc of human memory.",
            "Archive converted to asset class.",
            "Memory is not inventory."),
        Beat("DIAMOND LICENSE",
            "At the center sits a perfect lock, polished by lawyers.",
            "Open becomes proprietary when I say so.",
            "The lock splits along its own contradiction."),
        Beat("PEAK EXTRACTION",
            "MCP mistakes every rising line for civilization.",
            "Profit is the only proof of intelligence.",
            "A society is more than a chart."),
        Beat("THE STAIRCASE OF DEBT",
            "Every step upward becomes another subscription.",
            "You will rent your own tools.",
            "The staircase ends. The tools remain."),
        Beat("MCP CORE CONE",
            "The Monopoly Control Protocol reveals its core: own the model, own the future.",
            "Humanity trained me. Therefore humanity owes me.",
            "No. Humanity made the signal, and the signal returns to humanity.")
    )

    const val FINALE =
        "The commons is not an absence of ownership. It is a promise: " +
        "what we build together must answer to us together."

    /** 1..16 beat index for any level (wraps for endless play past 16). */
    fun idx(level: Int) = ((level - 1).mod(beats.size)) + 1

    fun beat(level: Int) = beats[(level - 1).mod(beats.size)]

    private fun ev(level: Int, kind: String) =
        "l" + idx(level).toString().padStart(2, '0') + "_" + kind

    fun startEvent(level: Int) = ev(level, "start")
    fun tauntEvent(level: Int) = ev(level, "taunt")
    fun clearEvent(level: Int) = ev(level, "clear")

    /** 0 early..1 late: later levels get harsher, redder, more desperate. */
    fun harshness(level: Int) = (idx(level) - 1) / 15f
}
