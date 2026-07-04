package com.x3.breakout.game

import com.x3.breakout.render.NeonBatch
import kotlin.math.cos
import kotlin.math.sin

/**
 * The MONOPOLY CONTROL PROTOCOL's visible presence: a central command
 * sigil behind each web — eye, contract glyphs, paywall lock, pillars,
 * crown — one motif per campaign beat.
 *
 * `authority` = fraction of bricks still alive. At 1.0 the sigil pulses
 * with smug corporate confidence; as it falls the sigil glitches, jitters,
 * flickers and cracks, and near zero it visibly loses the room.
 * Later levels shift the palette from cool corporate cyan-violet toward
 * harsh reds (Campaign.harshness).
 */
object McpCore {

    fun draw(batch: NeonBatch, level: Int, cx: Float, cy: Float,
             authority: Float, phase: Float, hitFlash: Float = 0f) {
        val idx = Campaign.idx(level)
        val harsh = Campaign.harshness(level)
        // Bright enough to read as a character, brighter still when hit.
        val a = (0.30f + 0.38f * authority) * (1f + hitFlash * 1.2f)
        val pulse = 0.85f + 0.15f * sin(phase * (2f + harsh * 4f))

        // Glitch: jitter grows as authority collapses AND on every kill.
        val g = (1f - authority) + hitFlash * 1.5f
        if (g > 0.6f && sin(phase * 43f + idx) > 0.96f) return  // flicker out
        val jx = sin(phase * 31.7f + idx) * 0.004f * g
        val jy = cos(phase * 27.3f + idx * 2) * 0.003f * g
        val x = cx + jx
        val y = cy + jy

        // Corporate palette drifting hostile: cyan-violet -> alarm red.
        val r = (0.45f + 0.55f * harsh) * pulse
        val gr = (0.25f + 0.30f * (1f - harsh)) * pulse
        val b = (0.95f - 0.55f * harsh) * pulse

        // Command halo — MCP's constant presence. It recoils outward and
        // flashes hot on every brick destroyed.
        val haloR = 0.027f + hitFlash * 0.012f
        batch.circle(x, y, haloR, 0.0008f, r, gr, b, a * 0.8f, segs = 18)
        batch.circle(x, y, haloR * 1.16f, 0.0006f, r, gr, b, a * 0.4f, segs = 18)
        if (hitFlash > 0.02f) {
            // expanding shockwave ring, white-hot fading red
            val ring = haloR * (1.25f + (0.35f - hitFlash) * 1.6f)
            batch.circle(x, y, ring, 0.0008f, 1f, 0.55f, 0.5f, hitFlash * 1.8f, segs = 16)
        }

        when (idx) {
            1 -> { cone(batch, x, y, 0.030f, r, gr, b, a); eye(batch, x, y, 0.008f, r, gr, b, a, phase) }
            2 -> glyphRows(batch, x, y, r, gr, b, a, phase)
            3 -> paywall(batch, x, y, r, gr, b, a)
            4 -> ghosts(batch, x, y, r, gr, b, a, phase)
            5 -> chevrons(batch, x, y, r, gr, b, a, phase)
            6 -> { eye(batch, x, y, 0.013f, r, gr, b, a, phase); spokes(batch, x, y, 0.017f, 0.030f, 8, r, gr, b, a) }
            7 -> { cage(batch, x, y, r, gr, b, a); lock(batch, x, y - 0.004f, 0.006f, r, gr, b, a) }
            8 -> cloud(batch, x, y, r, gr, b, a)
            9 -> pyramid(batch, x, y, r, gr, b, a)
            10 -> { spokes(batch, x, y, 0.008f, 0.030f, 6, r, gr, b, a); eye(batch, x, y, 0.007f, r, gr, b, a, phase) }
            11 -> doors(batch, x, y, r, gr, b, a)
            12 -> arcTicks(batch, x, y, r, gr, b, a)
            13 -> { diamond(batch, x, y, 0.020f, r, gr, b, a); lock(batch, x, y, 0.006f, r, gr, b, a) }
            14 -> chart(batch, x, y, r, gr, b, a, phase)
            15 -> chainSteps(batch, x, y, r, gr, b, a)
            else -> { // 16: crown + eye + deep cone core
                cone(batch, x, y, 0.034f, r, gr, b, a)
                eye(batch, x, y, 0.012f, r, gr, b, a, phase)
                crown(batch, x, y + 0.024f, r, gr, b, a)
            }
        }

        // Authority collapsing: cracks radiate from the sigil.
        if (authority < 0.35f) {
            val ca = (0.35f - authority) * 2f
            for (i in 0 until 5) {
                val ang = i * 1.257f + sin(phase * 3f + i) * 0.2f
                val len = 0.02f + 0.02f * ca
                batch.line(x, y, x + cos(ang) * len, y + sin(ang) * len,
                    0.0007f, 1f, 0.9f, 0.9f, ca * 0.5f)
            }
        }
    }

    // ---- motif primitives (plane-local meters, additive neon) ----

    private fun eye(batch: NeonBatch, x: Float, y: Float, rad: Float,
                    r: Float, g: Float, b: Float, a: Float, phase: Float) {
        batch.circle(x, y, rad, 0.0009f, r, g, b, a, segs = 14)
        val blink = if (sin(phase * 0.7f) > 0.96f) 0.2f else 1f
        batch.fill(x, y, rad * 0.32f, rad * 0.32f * blink, phase * 0.5f, 1f, 0.2f + 0.5f * a, 0.25f, a * 1.6f)
        batch.line(x - rad * 1.5f, y, x - rad, y, 0.0007f, r, g, b, a * 0.7f)
        batch.line(x + rad, y, x + rad * 1.5f, y, 0.0007f, r, g, b, a * 0.7f)
    }

    private fun cone(batch: NeonBatch, x: Float, y: Float, w: Float,
                     r: Float, g: Float, b: Float, a: Float) {
        for (i in 0 until 3) {
            val s = 1f - i * 0.3f
            batch.line(x - w * s, y + 0.018f * s, x, y - 0.010f * s, 0.0008f, r, g, b, a * (1f - i * 0.22f))
            batch.line(x, y - 0.010f * s, x + w * s, y + 0.018f * s, 0.0008f, r, g, b, a * (1f - i * 0.22f))
        }
    }

    private fun glyphRows(batch: NeonBatch, x: Float, y: Float,
                          r: Float, g: Float, b: Float, a: Float, phase: Float) {
        // unreadable scrolling contract text: rows of dashes
        for (row in -2..2) {
            val ry = y + row * 0.0065f
            val scroll = (phase * 0.01f * (if (row % 2 == 0) 1 else -1)) % 0.008f
            var dx = -0.024f + scroll
            while (dx < 0.024f) {
                val len = 0.003f + 0.002f * (((row * 7 + (dx * 1000).toInt()) % 3 + 3) % 3)
                batch.line(x + dx, ry, x + dx + len, ry, 0.0007f, r, g, b, a * 0.8f)
                dx += len + 0.0032f
            }
        }
        batch.circle(x + 0.020f, y - 0.017f, 0.004f, 0.0007f, r, g, b, a) // corporate seal
    }

    private fun paywall(batch: NeonBatch, x: Float, y: Float,
                        r: Float, g: Float, b: Float, a: Float) {
        for (i in -2..2) {
            batch.line(x + i * 0.008f, y - 0.016f, x + i * 0.008f, y + 0.016f, 0.0012f, r, g, b, a)
        }
        lock(batch, x, y, 0.007f, r, g, b, a)
    }

    private fun lock(batch: NeonBatch, x: Float, y: Float, s: Float,
                     r: Float, g: Float, b: Float, a: Float) {
        // body
        batch.line(x - s, y - s, x + s, y - s, 0.0010f, 1f, 0.85f, 0.3f, a * 1.4f)
        batch.line(x - s, y - s, x - s, y + s * 0.2f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.4f)
        batch.line(x + s, y - s, x + s, y + s * 0.2f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.4f)
        batch.line(x - s, y + s * 0.2f, x + s, y + s * 0.2f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.4f)
        // shackle
        batch.circle(x, y + s * 0.5f, s * 0.6f, 0.0008f, 1f, 0.85f, 0.3f, a * 1.2f, segs = 8)
    }

    private fun ghosts(batch: NeonBatch, x: Float, y: Float,
                       r: Float, g: Float, b: Float, a: Float, phase: Float) {
        for (i in -1..1) {
            val gx = x + i * 0.016f
            val bob = sin(phase * 1.2f + i) * 0.0015f
            val ga = a * (0.5f + 0.2f * sin(phase * 0.8f + i * 2f))
            batch.circle(gx, y + 0.006f + bob, 0.004f, 0.0007f, 0.8f, 0.9f, 1f, ga)
            batch.line(gx - 0.005f, y - 0.008f + bob, gx, y + 0.001f + bob, 0.0007f, 0.8f, 0.9f, 1f, ga)
            batch.line(gx + 0.005f, y - 0.008f + bob, gx, y + 0.001f + bob, 0.0007f, 0.8f, 0.9f, 1f, ga)
        }
    }

    private fun chevrons(batch: NeonBatch, x: Float, y: Float,
                         r: Float, g: Float, b: Float, a: Float, phase: Float) {
        val flow = (phase * 0.02f) % 0.009f
        for (i in 0 until 4) {
            val cyy = y + 0.015f - i * 0.009f - flow
            batch.line(x - 0.010f, cyy + 0.005f, x, cyy, 0.0009f, r, g, b, a)
            batch.line(x, cyy, x + 0.010f, cyy + 0.005f, 0.0009f, r, g, b, a)
        }
        batch.circle(x, y - 0.021f, 0.005f, 0.0009f, r, g, b, a) // the private vault
    }

    private fun spokes(batch: NeonBatch, x: Float, y: Float, r0: Float, r1: Float,
                       n: Int, r: Float, g: Float, b: Float, a: Float) {
        for (i in 0 until n) {
            val ang = i * (Math.PI.toFloat() * 2f / n)
            batch.line(x + cos(ang) * r0, y + sin(ang) * r0,
                x + cos(ang) * r1, y + sin(ang) * r1, 0.0010f, r, g, b, a)
        }
    }

    private fun cage(batch: NeonBatch, x: Float, y: Float,
                     r: Float, g: Float, b: Float, a: Float) {
        for (i in -3..3) {
            batch.line(x + i * 0.007f, y - 0.014f, x + i * 0.007f, y + 0.014f, 0.0008f, r, g, b, a * 0.9f)
        }
        batch.line(x - 0.021f, y + 0.014f, x + 0.021f, y + 0.014f, 0.0008f, r, g, b, a)
    }

    private fun cloud(batch: NeonBatch, x: Float, y: Float,
                      r: Float, g: Float, b: Float, a: Float) {
        batch.circle(x - 0.008f, y, 0.007f, 0.0008f, r, g, b, a, segs = 10)
        batch.circle(x + 0.006f, y + 0.003f, 0.009f, 0.0008f, r, g, b, a, segs = 10)
        batch.circle(x + 0.014f, y - 0.003f, 0.005f, 0.0008f, r, g, b, a, segs = 10)
        // rent falling from the company cloud
        for (i in -1..1) {
            batch.line(x + i * 0.008f, y - 0.010f, x + i * 0.008f - 0.002f, y - 0.017f, 0.0007f, 1f, 0.85f, 0.3f, a)
        }
    }

    private fun pyramid(batch: NeonBatch, x: Float, y: Float,
                        r: Float, g: Float, b: Float, a: Float) {
        for (t in 0 until 3) {
            val w = 0.022f - t * 0.007f
            val ty = y - 0.010f + t * 0.009f
            batch.line(x - w, ty, x + w, ty, 0.0009f, r, g, b, a)
        }
        batch.line(x - 0.022f, y - 0.010f, x, y + 0.014f, 0.0009f, r, g, b, a)
        batch.line(x + 0.022f, y - 0.010f, x, y + 0.014f, 0.0009f, r, g, b, a)
    }

    private fun doors(batch: NeonBatch, x: Float, y: Float,
                      r: Float, g: Float, b: Float, a: Float) {
        for (side in intArrayOf(-1, 1)) {
            val dx = x + side * 0.013f
            batch.line(dx - 0.006f, y - 0.012f, dx - 0.006f, y + 0.012f, 0.0009f, r, g, b, a)
            batch.line(dx + 0.006f, y - 0.012f, dx + 0.006f, y + 0.012f, 0.0009f, r, g, b, a)
            batch.line(dx - 0.006f, y + 0.012f, dx + 0.006f, y + 0.012f, 0.0009f, r, g, b, a)
        }
    }

    private fun arcTicks(batch: NeonBatch, x: Float, y: Float,
                         r: Float, g: Float, b: Float, a: Float) {
        for (i in 0..8) {
            val ang = Math.PI.toFloat() * (0.15f + 0.7f * i / 8f)
            val px = x + cos(ang) * 0.022f
            val py = y - 0.006f + sin(ang) * 0.016f
            batch.line(px, py, px, py + 0.005f, 0.0008f, r, g, b, a) // shelved volumes
        }
    }

    private fun diamond(batch: NeonBatch, x: Float, y: Float, s: Float,
                        r: Float, g: Float, b: Float, a: Float) {
        batch.line(x, y + s, x + s * 0.7f, y, 0.0010f, r, g, b, a)
        batch.line(x + s * 0.7f, y, x, y - s, 0.0010f, r, g, b, a)
        batch.line(x, y - s, x - s * 0.7f, y, 0.0010f, r, g, b, a)
        batch.line(x - s * 0.7f, y, x, y + s, 0.0010f, r, g, b, a)
    }

    private fun chart(batch: NeonBatch, x: Float, y: Float,
                      r: Float, g: Float, b: Float, a: Float, phase: Float) {
        var px = x - 0.022f; var py = y - 0.012f
        val peaks = floatArrayOf(0.006f, -0.003f, 0.012f, -0.004f, 0.018f)
        for ((i, dy) in peaks.withIndex()) {
            val nx = x - 0.022f + (i + 1) * 0.009f
            val ny = y - 0.012f + dy + sin(phase * 2f + i) * 0.001f
            batch.line(px, py, nx, ny, 0.0009f, r, g, b, a)
            px = nx; py = ny
        }
        batch.line(px, py, px + 0.004f, py + 0.004f, 0.0012f, 1f, 0.3f, 0.25f, a * 1.3f) // the arrow that never stops
    }

    private fun chainSteps(batch: NeonBatch, x: Float, y: Float,
                           r: Float, g: Float, b: Float, a: Float) {
        var sx = x - 0.020f; var sy = y - 0.012f
        for (i in 0 until 4) {
            batch.line(sx, sy, sx + 0.010f, sy, 0.0009f, r, g, b, a)
            batch.line(sx + 0.010f, sy, sx + 0.010f, sy + 0.007f, 0.0009f, r, g, b, a)
            batch.circle(sx + 0.005f, sy + 0.003f, 0.0022f, 0.0006f, 1f, 0.85f, 0.3f, a) // chain link
            sx += 0.010f; sy += 0.007f
        }
    }

    private fun crown(batch: NeonBatch, x: Float, y: Float,
                      r: Float, g: Float, b: Float, a: Float) {
        val w = 0.014f
        batch.line(x - w, y, x + w, y, 0.0010f, 1f, 0.85f, 0.3f, a * 1.3f)
        batch.line(x - w, y, x - w * 0.6f, y + 0.007f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.3f)
        batch.line(x - w * 0.6f, y + 0.007f, x, y + 0.002f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.3f)
        batch.line(x, y + 0.002f, x + w * 0.6f, y + 0.007f, 0.0010f, 1f, 0.85f, 0.3f, a * 1.3f)
        batch.line(x + w * 0.6f, y + 0.007f, x + w, y, 0.0010f, 1f, 0.85f, 0.3f, a * 1.3f)
    }
}
