package com.x3.breakout.game

/**
 * Arkanoid power-up set, T2K flavored. Timed ones (duration > 0) show a
 * Tempest-font countdown and trigger the special power-up music track.
 */
enum class PowerUpType(
    val letter: Char,
    val label: String,
    val duration: Float,     // seconds; 0 = instant
    val weight: Int,
    val commentEvent: String,
    val rgb: FloatArray
) {
    EXPAND('E', "EXPAND", 15f, 20, "powerup_expand", floatArrayOf(0.2f, 1f, 0.35f)),
    LASER('L', "LASER", 10f, 18, "powerup_laser", floatArrayOf(1f, 0.15f, 0.2f)),
    SLOW('S', "SLOW", 12f, 15, "powerup_slow", floatArrayOf(0.1f, 0.95f, 1f)),
    CATCH('C', "CATCH", 15f, 12, "powerup_catch", floatArrayOf(1f, 0.9f, 0.1f)),
    MULTI('M', "MULTI", 0f, 15, "powerup_multi", floatArrayOf(0.9f, 0.2f, 1f)),
    ZAP('Z', "SUPERZAPPER", 0f, 9, "superzapper", floatArrayOf(1f, 1f, 1f)),
    LIFE('1', "1UP", 0f, 4, "extra_life", floatArrayOf(0.25f, 0.35f, 1f));

    val timed get() = duration > 0f

    companion object {
        private val total = entries.sumOf { it.weight }
        fun random(rnd: kotlin.random.Random): PowerUpType {
            var r = rnd.nextInt(total)
            for (t in entries) { r -= t.weight; if (r < 0) return t }
            return EXPAND
        }
    }
}
