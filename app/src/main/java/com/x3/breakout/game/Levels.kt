package com.x3.breakout.game

import kotlin.math.cos
import kotlin.math.sin

/**
 * Tempest-style web shapes. Each level is a web polyline in
 * normalized coords (x,y in -1..1, y up), open or closed. Bricks are laid
 * along the web in concentric rings shrinking toward the web centroid,
 * exactly like flying down the tube. The 16 shapes echo the classic
 * web sequence (circle, square, plus, V, star...) then wrap with faster
 * balls and tougher rings.
 */
object Levels {

    class Web(val points: List<FloatArray>, val closed: Boolean)

    private fun poly(closed: Boolean, vararg xy: Float): Web {
        val pts = ArrayList<FloatArray>()
        var i = 0
        while (i + 1 < xy.size) { pts.add(floatArrayOf(xy[i], xy[i + 1])); i += 2 }
        return Web(pts, closed)
    }

    private fun ngon(n: Int, phase: Float = 0f, rx: Float = 1f, ry: Float = 1f): Web {
        val pts = ArrayList<FloatArray>()
        for (i in 0 until n) {
            val a = phase + i * 2f * Math.PI.toFloat() / n
            pts.add(floatArrayOf(rx * cos(a), ry * sin(a)))
        }
        return Web(pts, true)
    }

    val webs: List<Web> = listOf(
        // 1 circle
        ngon(16),
        // 2 square
        ngon(4, phase = Math.PI.toFloat() / 4f, rx = 1.15f, ry = 1.15f),
        // 3 plus / cross
        poly(true,
            -0.33f, 1f, 0.33f, 1f, 0.33f, 0.33f, 1f, 0.33f, 1f, -0.33f,
            0.33f, -0.33f, 0.33f, -1f, -0.33f, -1f, -0.33f, -0.33f, -1f, -0.33f,
            -1f, 0.33f, -0.33f, 0.33f),
        // 4 flat web (open line)
        poly(false, -1f, 0.1f, -0.5f, 0.1f, 0f, 0.1f, 0.5f, 0.1f, 1f, 0.1f),
        // 5 V
        poly(false, -1f, 0.9f, -0.5f, 0.25f, 0f, -0.4f, 0.5f, 0.25f, 1f, 0.9f),
        // 6 star
        run {
            val pts = ArrayList<FloatArray>()
            for (i in 0 until 10) {
                val a = Math.PI.toFloat() / 2f + i * Math.PI.toFloat() / 5f
                val r = if (i % 2 == 0) 1.1f else 0.5f
                pts.add(floatArrayOf(r * cos(a), r * sin(a)))
            }
            Web(pts, true)
        },
        // 7 U tube
        poly(false, -1f, 0.9f, -0.95f, 0.1f, -0.7f, -0.55f, -0.25f, -0.85f,
            0.25f, -0.85f, 0.7f, -0.55f, 0.95f, 0.1f, 1f, 0.9f),
        // 8 zigzag W
        poly(false, -1f, 0.7f, -0.6f, -0.5f, -0.2f, 0.5f, 0.2f, -0.5f, 0.6f, 0.5f, 1f, -0.7f),
        // 9 triangle
        ngon(3, phase = Math.PI.toFloat() / 2f, rx = 1.15f, ry = 1.15f),
        // 10 hexagon
        ngon(6, rx = 1.1f, ry = 1.1f),
        // 11 bowtie
        poly(true, -1f, 0.8f, 0f, 0.15f, 1f, 0.8f, 1f, -0.8f, 0f, -0.15f, -1f, -0.8f),
        // 12 arc / horseshoe
        run {
            val pts = ArrayList<FloatArray>()
            for (i in 0..10) {
                val a = Math.PI.toFloat() * (0.15f + 0.7f * i / 10f) + Math.PI.toFloat()
                pts.add(floatArrayOf(1.05f * cos(a), -1.05f * sin(a)))
            }
            Web(pts, false)
        },
        // 13 diamond
        ngon(4, rx = 0.9f, ry = 1.25f),
        // 14 triple peaks
        poly(false, -1f, -0.4f, -0.7f, 0.7f, -0.4f, -0.2f, 0f, 0.85f,
            0.4f, -0.2f, 0.7f, 0.7f, 1f, -0.4f),
        // 15 steps
        poly(false, -1f, -0.6f, -0.6f, -0.6f, -0.6f, 0f, -0.2f, 0f,
            -0.2f, 0.6f, 0.2f, 0.6f, 0.2f, 0f, 0.6f, 0f, 0.6f, -0.6f, 1f, -0.6f),
        // 16 octagon (the deep web)
        ngon(8, phase = Math.PI.toFloat() / 8f, rx = 1.12f, ry = 1.12f)
    )

    /** Tempest palette, cycled by ring. */
    val palette = arrayOf(
        floatArrayOf(1f, 0.15f, 0.2f),   // red
        floatArrayOf(1f, 0.9f, 0.1f),    // yellow
        floatArrayOf(0.2f, 1f, 0.35f),   // green
        floatArrayOf(0.1f, 0.95f, 1f),   // cyan
        floatArrayOf(0.25f, 0.35f, 1f),  // blue
        floatArrayOf(0.9f, 0.2f, 1f)     // magenta
    )

    class Brick(
        var cx: Float, var cy: Float,      // center, plane-local meters
        var angle: Float,                  // orientation of the long axis
        var halfLen: Float, var halfThick: Float,
        var hp: Int, var maxHp: Int,
        var color: FloatArray,
        var alive: Boolean = true
    )

    /**
     * Web points rescaled so EVERY shape spans exactly -1..1 on both axes.
     * Raw shapes vary wildly (square corners ±0.81, diamond tip ±1.25), so
     * without this the lowest brick row lands at a different — sometimes
     * paddle-crowding — height on every level. Degenerate axes (the flat
     * web) collapse to 0 = band center.
     */
    private fun normPoints(web: Web): List<FloatArray> {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in web.points) {
            if (p[0] < minX) minX = p[0]
            if (p[0] > maxX) maxX = p[0]
            if (p[1] < minY) minY = p[1]
            if (p[1] > maxY) maxY = p[1]
        }
        val sx = maxX - minX; val sy = maxY - minY
        return web.points.map {
            floatArrayOf(
                if (sx > 1e-4f) (it[0] - minX) / sx * 2f - 1f else 0f,
                if (sy > 1e-4f) (it[1] - minY) / sy * 2f - 1f else 0f
            )
        }
    }

    /**
     * Build the brick field for a level: the web plus 2-3 shrinking rings.
     * Output coords are plane-local meters centered on the web area.
     */
    fun buildBricks(level: Int, areaHalfW: Float, areaHalfH: Float,
                    centerY: Float): List<Brick> {
        val web = webs[(level - 1).mod(webs.size)]
        val rings = if (level <= 4) 2 else 3
        val loop = level > webs.size // wrapped: tougher
        val bricks = ArrayList<Brick>()

        val basePts = normPoints(web)

        // centroid
        var cx = 0f; var cy = 0f
        for (p in basePts) { cx += p[0]; cy += p[1] }
        cx /= basePts.size; cy /= basePts.size

        val brickLen = 0.034f
        for (ring in 0 until rings) {
            val scale = 1f - 0.16f * ring
            val hp = (ring + 1) + if (loop) 1 else 0
            val color = palette[(ring * 2 + level) % palette.size]
            val pts = basePts.map {
                floatArrayOf(
                    (cx + (it[0] - cx) * scale) * areaHalfW,
                    centerY + (cy + (it[1] - cy) * scale) * areaHalfH
                )
            }
            val segCount = if (web.closed) pts.size else pts.size - 1
            for (s in 0 until segCount) {
                val a = pts[s]
                val b = pts[(s + 1) % pts.size]
                val dx = b[0] - a[0]; val dy = b[1] - a[1]
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                if (len < 1e-5f) continue
                val n = kotlin.math.max(1, (len / brickLen).toInt())
                val ang = kotlin.math.atan2(dy, dx)
                for (k in 0 until n) {
                    // wider gaps + slimmer bricks: clear-cut Vectrex outlines
                    val t0 = (k + 0.16f) / n
                    val t1 = (k + 0.84f) / n
                    val mx = a[0] + dx * (t0 + t1) * 0.5f
                    val my = a[1] + dy * (t0 + t1) * 0.5f
                    bricks.add(Brick(
                        mx, my, ang,
                        halfLen = len * (t1 - t0) * 0.5f,
                        halfThick = 0.0038f,
                        hp = hp, maxHp = hp,
                        color = color
                    ))
                }
            }
        }
        return bricks
    }

    /** Web outline for background rendering (plane-local meters). */
    fun webOutline(level: Int, areaHalfW: Float, areaHalfH: Float,
                   centerY: Float): Pair<List<FloatArray>, Boolean> {
        val web = webs[(level - 1).mod(webs.size)]
        return Pair(normPoints(web).map {
            floatArrayOf(it[0] * areaHalfW * 1.06f, centerY + it[1] * areaHalfH * 1.06f)
        }, web.closed)
    }
}
