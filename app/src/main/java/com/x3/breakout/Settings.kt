package com.x3.breakout

import android.content.Context

/** Persisted settings (SharedPreferences). */
class Settings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("x3breakout", Context.MODE_PRIVATE)

    /** Swipe sensitivity index 0..3 (multiplier on calibrated stroke). */
    var swipeSens: Int
        get() = prefs.getInt("swipeSens", 1)
        set(v) { prefs.edit().putInt("swipeSens", v.coerceIn(0, 3)).apply() }

    val swipeSensMultiplier: Float  // no array alloc: read on every drag event
        get() = when (swipeSens.coerceIn(0, 3)) { 0 -> 0.6f; 1 -> 1f; 2 -> 1.5f; else -> 2.2f }

    var commentary: Boolean
        get() = prefs.getBoolean("commentary", true)
        set(v) { prefs.edit().putBoolean("commentary", v).apply() }

    /** Story captions / MCP taunt text on the HUD. */
    var captions: Boolean
        get() = prefs.getBoolean("captions", true)
        set(v) { prefs.edit().putBoolean("captions", v).apply() }

    /** 0=SMALL 1=MEDIUM 2=LARGE — scales the whole game window. */
    var windowSize: Int
        get() = prefs.getInt("windowSize", 2)
        set(v) { prefs.edit().putInt("windowSize", v.coerceIn(0, 2)).apply() }

    /** 0..10 */
    var musicVol: Int
        get() = prefs.getInt("musicVol", 8)   // ON by default (0 = hard off)
        set(v) { prefs.edit().putInt("musicVol", v.coerceIn(0, 10)).apply() }

    /** 0..10 */
    var sfxVol: Int
        get() = prefs.getInt("sfxVol", 8)
        set(v) { prefs.edit().putInt("sfxVol", v.coerceIn(0, 10)).apply() }
}
