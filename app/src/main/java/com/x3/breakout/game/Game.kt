package com.x3.breakout.game

import com.x3.breakout.Settings
import com.x3.breakout.audio.GameAudio
import com.x3.breakout.render.NeonBatch
import com.x3.breakout.render.VectorFont
import com.x3.breakout.input.SwipeControl
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Arkanoid x Tempest for the X3, presented as a head-locked big screen
 * (NeonTetris3D model): game coordinates stay compact, and the NeonBatch
 * plane basis scales them up to fill the fov-55 view. WINDOW SIZE in the
 * settings menu switches that scale live. The right arm swipes the paddle;
 * the temple pad does everything else.
 */
class Game(
    private val settings: Settings,
    private val audio: GameAudio,
    private val swipe: SwipeControl
) {
    val batch = NeonBatch()

    /** WINDOW SIZE steps: game-units per plane meter (SMALL/MEDIUM/LARGE). */
    private val windowScales = floatArrayOf(70f, 92f, 115f)

    // ---- playfield geometry (plane-local meters) ----
    companion object {
        const val HW = 0.13f            // playfield half width
        const val HH = 0.08f            // playfield half height
        const val AREA_HW = 0.105f      // web brick area half width
        const val AREA_HH = 0.026f      // web brick area half height
        const val AREA_CY = 0.050f      // web band center: lowest brick row
                                        // now sits near +0.024, roughly twice
                                        // the old clearance from the paddle.
        const val PADDLE_Y = -HH + 0.008f
        const val BALL_R = 0.0035f
        const val PADDLE_HALF_BASE = 0.020f
        const val PADDLE_HALF_WIDE = 0.032f
        const val TOKEN_FALL = 0.05f
        const val LASER_SPEED = 0.30f
        const val MAX_BALLS = 3
        const val MAX_LIVES = 5
        const val CAP_WIDTH = 0.245f    // caption region width
        val CAP_SIZES = floatArrayOf(0.0026f, 0.0023f, 0.0020f, 0.0017f, 0.0015f)

        // slightly-isometric presentation
        const val ISO_TILT = 0.38f      // ~22 degree table lean
        const val BRICK_H = 0.0035f     // brick extrusion height
        const val PADDLE_H = 0.0045f    // paddle extrusion height
        const val FLOAT_H = 0.005f      // ball / particle hover height
        const val UI_H = 0.011f         // HUD & caption layer height
        const val MCP_WARP_R = 0.014f
    }

    enum class State { ATTRACT, PLAY, LEVEL_CLEAR, FINALE, GAME_OVER }

    // ---- story captions (readable HUD text; no debug output in play) ----
    // Every caption fits on screen IN FULL — auto-sized, never paged.
    private class CapMsg(val lines: List<String>, val size: Float, val dur: Float, val mcp: Boolean)
    private val capPages = ArrayDeque<CapMsg>()
    private var curCap: CapMsg? = null
    private var capTimer = 0f
    private var levelIntro = 0f      // title-card countdown
    private var tauntFired = false
    private var retortHi = false     // MCP barks at 75%...
    private var retortLo = false     // ...and 25% grid remaining
    private var hitFlash = 0f        // MCP recoil on brick destruction
    private var mcpWarpCooldown = 0f  // prevents repeated laugh spam inside the core
    private var totalBricks = 1
    private var aliveCount = 0

    private var state = State.ATTRACT
    private var settingsOpen = false
    private var stateTimer = 0f
    private var phase = 0f              // global color-cycle clock

    // gameplay
    private var level = 1
    private var score = 0
    private var lives = 3
    private var bricks: List<Levels.Brick> = emptyList()
    private var outline: List<FloatArray> = emptyList()
    private var outlineClosed = true
    private var paddleX = 0f
    private var paddleHalf = PADDLE_HALF_BASE
    private val rnd = Random(System.nanoTime())

    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float,
                       var stuck: Boolean = true, var stuckDx: Float = 0f, var stuckT: Float = 0f)
    private val balls = ArrayList<Ball>()

    private class Token(var x: Float, var y: Float, val type: PowerUpType, var spin: Float = 0f)
    private val tokens = ArrayList<Token>()

    private class Laser(var x: Float, var y: Float)
    private val lasers = ArrayList<Laser>()
    private var laserClock = 0f

    // ---- Neon particle zoo: fixed pool, zero allocation, four kinds.
    private class Particle {
        var active = false
        var kind = 0            // 0 spark, 1 star, 2 ring, 3 streak
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var life0 = 1f
        var r = 1f; var g = 1f; var b = 1f
        var spin = 0f; var spinRate = 0f
        var size = 0.0012f
        var rainbow = false     // hue-cycles every frame, Minter style
    }
    private val pool = Array(288) { Particle() }
    private var poolCursor = 0
    private var trailAcc = 0f

    private fun spawnP(kind: Int, x: Float, y: Float, vx: Float, vy: Float,
                       life: Float, r: Float, g: Float, b: Float,
                       size: Float, rainbow: Boolean = false) {
        val p = pool[poolCursor]                 // recycle oldest — chaos is fine
        poolCursor = (poolCursor + 1) % pool.size
        p.active = true; p.kind = kind
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.life = life; p.life0 = life
        p.r = r; p.g = g; p.b = b
        p.spin = rnd.nextFloat() * 6.28f
        p.spinRate = (rnd.nextFloat() - 0.5f) * 14f
        p.size = size; p.rainbow = rainbow
    }

    private fun sparks(x: Float, y: Float, rgb: FloatArray, n: Int,
                       speed: Float, rainbow: Boolean = false) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.28f
            val v = speed * (0.4f + rnd.nextFloat())
            spawnP(0, x, y, cos(a) * v, sin(a) * v, 0.35f + rnd.nextFloat() * 0.4f,
                rgb[0], rgb[1], rgb[2], 0.0011f + rnd.nextFloat() * 0.0008f, rainbow)
        }
    }

    private fun stars(x: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.28f
            val v = 0.02f + rnd.nextFloat() * 0.05f
            spawnP(1, x, y, cos(a) * v, sin(a) * v, 0.5f + rnd.nextFloat() * 0.4f,
                1f, 1f, 1f, 0.003f + rnd.nextFloat() * 0.002f, rainbow = true)
        }
    }

    private fun ringFx(x: Float, y: Float, rgb: FloatArray, size: Float) {
        spawnP(2, x, y, 0f, 0f, 0.45f, rgb[0], rgb[1], rgb[2], size)
    }

    private val activeTimed = LinkedHashMap<PowerUpType, Float>()
    private val breakTimes = ArrayDeque<Float>()   // streak detection
    private var elapsed = 0f

    // settings menu
    private var menuSel = 0
    private val menuCount = 9
    private var calWasActive = false
    private var calLastLockSerial = 0

    // input events from UI thread: T=tap, D=double, +/- = swipe
    private val events = ConcurrentLinkedQueue<Char>()

    // HUD extras (set from MainActivity's thermal reader)
    @Volatile var tempC = 0
    @Volatile var fps = 0f

    // ------------------------------------------------------------------
    // input (UI thread)

    fun tap() { events.add('T') }
    fun doubleTap() { events.add('D') }
    fun swipe(steps: Int) { events.add(if (steps > 0) '+' else '-') }

    // ------------------------------------------------------------------

    private fun loadLevel(n: Int) {
        bricks = Levels.buildBricks(n, AREA_HW, AREA_HH, AREA_CY)
        val web = Levels.webOutline(n, AREA_HW, AREA_HH, AREA_CY)
        outline = web.first
        outlineClosed = web.second
        totalBricks = bricks.size.coerceAtLeast(1)
        aliveCount = bricks.size
        tauntFired = false
        retortHi = false
        retortLo = false
        hitFlash = 0f
        balls.clear()
        balls.add(Ball(0f, PADDLE_Y + 0.012f, 0f, 0f))
        tokens.clear(); lasers.clear()
        for (p in pool) p.active = false
        clearTimedPowerups(silent = true)
        paddleHalf = PADDLE_HALF_BASE
        paddleX = 0f
    }

    private fun baseSpeed() = (0.11f + 0.008f * (level - 1)).coerceAtMost(0.22f)

    private fun startGame() {
        level = 1; score = 0; lives = 3
        loadLevel(level)
        state = State.PLAY
        audio.playLevelMusic(level)
        beginLevelStory()
    }

    /** Title card + start caption + start voice line for the current level. */
    private fun beginLevelStory() {
        levelIntro = 3.0f
        showCaption(Campaign.beat(level).start, mcp = false)
        audio.say(Campaign.startEvent(level), force = true)
    }

    // ---- caption engine: one whole sentence on screen, auto-sized ----

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (w in text.split(' ')) {
            when {
                cur.isEmpty() -> cur.append(w)
                cur.length + 1 + w.length <= maxChars -> { cur.append(' '); cur.append(w) }
                else -> { lines.add(cur.toString()); cur = StringBuilder(w) }
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    private fun showCaption(text: String, mcp: Boolean) {
        if (!settings.captions) return
        val full = if (mcp) "MCP: $text" else text
        // Largest font whose wrapped form fits the caption region whole:
        // up to 3 lines at big sizes, 4 at the smallest. No paging ever.
        var lines: List<String> = listOf(full)
        var size = CAP_SIZES.last()
        for (s in CAP_SIZES) {
            val maxChars = (CAP_WIDTH / (VectorFont.ADVANCE * s)).toInt().coerceAtLeast(8)
            val wrapped = wrapText(full, maxChars)
            val maxLines = if (s >= 0.0018f) 3 else 4
            lines = wrapped; size = s
            if (wrapped.size <= maxLines) break
        }
        val dur = (2.4f + 0.045f * full.length).coerceAtMost(7.5f)
        capPages.addLast(CapMsg(lines, size, dur, mcp))
    }

    private fun updateCaptions(dt: Float) {
        val c = curCap
        if (c == null) {
            curCap = capPages.removeFirstOrNull()?.also { capTimer = it.dur }
            return
        }
        capTimer -= dt
        if (capTimer <= 0f) {
            curCap = capPages.removeFirstOrNull()?.also { capTimer = it.dur }
        }
    }

    // ------------------------------------------------------------------
    // main tick — called once per frame from the GL thread

    fun update(dt: Float) {
        phase += dt
        elapsed += dt
        stateTimer += dt
        processEvents()
        updateCalibrationFx()
        updateCaptions(dt)

        if (swipe.calibrating) {
            // Physics pauses, but the paddle keeps following the swipe —
            // live motion is the whole point of the calibration screen.
            followSwipePaddle(dt)
            render()
            return
        }

        if (state == State.PLAY && !settingsOpen) simulate(dt)
        if (state == State.LEVEL_CLEAR && stateTimer > 3.2f) {
            if (Campaign.idx(level) == Campaign.beats.size) {
                // MCP core broken: the collapse sequence.
                state = State.FINALE; stateTimer = 0f
                spawnParticles(0f, AREA_CY, Levels.palette[3], 30)
                spawnParticles(0f, AREA_CY, Levels.palette[5], 30)
                showCaption(Campaign.FINALE, mcp = false)
                audio.say("finale_1", force = true)
            } else {
                level++
                loadLevel(level)
                state = State.PLAY; stateTimer = 0f
                audio.playLevelMusic(level)
                beginLevelStory()
            }
        }
        if (state == State.FINALE) {
            updateParticles(dt)
            // MCP breaking into harmless open fragments
            if (stateTimer < 5f && (stateTimer * 10f).toInt() % 4 == 0) {
                spawnParticles((phase * 7f % 1f - 0.5f) * 0.05f,
                    AREA_CY + (phase * 11f % 1f - 0.5f) * 0.04f,
                    Levels.palette[(phase * 6f).toInt().mod(6)], 2)
                if ((stateTimer * 10f).toInt() % 12 == 0) {
                    ringFx(0f, AREA_CY, Levels.palette[(phase * 5f).toInt().mod(6)], 0.010f)
                }
            }
            if (stateTimer > 15f) {
                level++
                loadLevel(level)
                state = State.PLAY; stateTimer = 0f
                audio.playLevelMusic(level)
                beginLevelStory()
            }
        }
        if (state == State.GAME_OVER && stateTimer > 5f) {
            state = State.ATTRACT; stateTimer = 0f
        }

        render()
    }

    private fun processEvents() {
        while (true) {
            val e = events.poll() ?: break
            if (swipe.calibrating) {
                if (e == 'D') {
                    // Cancel cleanly — otherwise the menu opens while the
                    // calibration branch still swallows every input (softlock).
                    swipe.cancelCalibration()
                    settingsOpen = true
                    menuSel = 4
                    audio.sfx("ui")
                }
                continue
            }
            if (settingsOpen) {
                when (e) {
                    'T' -> menuActivate()
                    'D' -> { settingsOpen = false; audio.sfx("ui") }
                    '+' -> { menuSel = (menuSel + 1) % menuCount; audio.sfx("ui") }
                    '-' -> { menuSel = (menuSel - 1 + menuCount) % menuCount; audio.sfx("ui") }
                }
                continue
            }
            when (e) {
                'T' -> when (state) {
                    State.ATTRACT -> startGame()
                    State.PLAY -> launchStuckBalls()
                    State.FINALE -> stateTimer = 16f   // skip the collapse
                    State.GAME_OVER -> { state = State.ATTRACT; stateTimer = 0f }
                    else -> {}
                }
                'D' -> { settingsOpen = true; menuSel = 0; audio.sfx("ui") }
                // '+'/'-' steps are menu-only now; the paddle rides the
                // continuous drag stream through SwipeControl instead.
            }
        }
    }

    private fun menuActivate() {
        audio.sfx("ui")
        when (menuSel) {
            0 -> settingsOpen = false                                        // RESUME
            1 -> { settingsOpen = false; startGame() }                       // RESTART GAME
            2 -> settings.windowSize = (settings.windowSize + 1) % 3         // WINDOW SIZE (live)
            3 -> settings.swipeSens = (settings.swipeSens + 1) % 4           // SWIPE SENS
            4 -> { swipe.beginCalibration(); settingsOpen = false }          // CALIBRATE SWIPE
            5 -> settings.captions = !settings.captions
            6 -> settings.commentary = !settings.commentary
            7 -> { settings.musicVol = stepVol(settings.musicVol); audio.onMusicVolChanged() }
                 // vol 0 fully RELEASES the music players (X3 stall escape hatch)
            8 -> { settings.sfxVol = stepVol(settings.sfxVol); audio.sfx("brick") } // audible test
        }
    }

    /** NeonTetris3D volume steps: 0 -> 0.4 -> 0.8 -> 1.0 -> 0 (stored x10). */
    private fun stepVol(v: Int) = when { v < 2 -> 4; v < 5 -> 8; v < 9 -> 10; else -> 0 }

    private fun volLabel(v: Int) = if (v >= 10) "1.0" else "0.$v"

    private fun launchStuckBalls() {
        for (b in balls) if (b.stuck) launch(b)
    }

    private fun launch(b: Ball) {
        b.stuck = false
        val sp = baseSpeed() * slowFactor()
        val ang = (0.5f + b.stuckDx / paddleHalf * 0.35f) * Math.PI.toFloat() // mostly up
        b.vx = cos(ang) * sp * -1f
        b.vy = sin(ang) * sp
        audio.sfx("bounce")
    }

    private fun slowFactor() = if (activeTimed.containsKey(PowerUpType.SLOW)) 0.55f else 1f

    // ------------------------------------------------------------------
    // simulation

    /**
     * Right-arm swipe -> paddle. During calibration the RAW camera-range x
     * drives it instead of the envelope mapping, so the paddle visibly moves
     * even when the stored envelope is the thing being fixed.
     */
    /** Right-arm temple-pad swipe -> paddle. Frame-rate-independent chase. */
    private fun followSwipePaddle(dt: Float) {
        val range = HW - paddleHalf
        val target = (swipe.pos01 * 2f - 1f) * range
        val k = 1f - exp(-dt * 28f)
        paddleX += (target - paddleX) * k
        paddleX = paddleX.coerceIn(-range, range)
    }

    private fun simulate(dt: Float) {
        followSwipePaddle(dt)

        // paddle width easing (EXPAND)
        val targetHalf = if (activeTimed.containsKey(PowerUpType.EXPAND)) PADDLE_HALF_WIDE else PADDLE_HALF_BASE
        paddleHalf += (targetHalf - paddleHalf) * 0.2f

        // timed power-ups countdown
        if (activeTimed.isNotEmpty()) {
            val iter = activeTimed.entries.iterator()
            var expired = false
            while (iter.hasNext()) {
                val e = iter.next()
                e.setValue(e.value - dt)
                if (e.value <= 0f) { iter.remove(); expired = true }
            }
            if (expired) {
                audio.sfx("powerup_end")
                if (activeTimed.none { it.key.timed }) audio.stopPowerupMusic()
            }
        }

        // balls — index loops everywhere in simulate: per-frame temp lists
        // fed the GC and surfaced as periodic sub-second freezes on device
        val speed = baseSpeed() * slowFactor()
        var anyDied = false
        var bi = balls.size - 1
        while (bi >= 0) {
            val b = balls[bi]
            if (b.stuck) {
                b.x = paddleX + b.stuckDx
                b.y = PADDLE_Y + 0.012f
                b.stuckT += dt
                val autoT = if (activeTimed.containsKey(PowerUpType.CATCH)) 2f else 3f
                if (b.stuckT > autoT) launch(b)
                bi--
                continue
            }
            // keep speed locked (SLOW applies smoothly)
            val v = sqrt(b.vx * b.vx + b.vy * b.vy)
            if (v > 1e-6f) { b.vx *= speed / v; b.vy *= speed / v }
            // substep so fast balls can't tunnel through thin bricks
            val steps = ceil((speed * dt) / (BALL_R * 0.8f)).toInt().coerceIn(1, 8)
            val sdt = dt / steps
            var died = false
            for (s in 0 until steps) {
                b.x += b.vx * sdt
                b.y += b.vy * sdt
                if (collide(b)) { /* one hit per substep is fine */ }
                if (b.y < -HH - 0.02f) { died = true; break }
            }
            if (died) { balls.removeAt(bi); anyDied = true }
            bi--
        }
        if (anyDied && balls.isEmpty()) loseLife()

        // rainbow comet trails behind every live ball
        trailAcc += dt
        if (trailAcc >= 0.045f) {
            trailAcc = 0f
            var tbi = 0
            while (tbi < balls.size) {
                val b = balls[tbi]
                if (!b.stuck) {
                    spawnP(3, b.x, b.y, b.vx, b.vy, 0.3f, 1f, 1f, 1f, 0.001f, rainbow = true)
                }
                tbi++
            }
        }

        // lasers
        if (activeTimed.containsKey(PowerUpType.LASER)) {
            laserClock += dt
            if (laserClock >= 0.5f) {
                laserClock = 0f
                lasers.add(Laser(paddleX - paddleHalf * 0.8f, PADDLE_Y + 0.006f))
                lasers.add(Laser(paddleX + paddleHalf * 0.8f, PADDLE_Y + 0.006f))
                audio.sfx("laser")
            }
        }
        var li = lasers.size - 1
        while (li >= 0) {
            val l = lasers[li]
            l.y += LASER_SPEED * dt
            var dead = l.y > HH
            if (!dead) {
                for (br in bricks) {
                    if (!br.alive) continue
                    if (pointInBrick(l.x, l.y, br)) {
                        hitBrick(br)
                        dead = true
                        break
                    }
                }
            }
            if (dead) lasers.removeAt(li)
            li--
        }

        // tokens
        var ti = tokens.size - 1
        while (ti >= 0) {
            val t = tokens[ti]
            t.y -= TOKEN_FALL * dt
            t.spin += dt * 4f
            var dead = t.y < -HH - 0.02f
            if (!dead && t.y < PADDLE_Y + 0.008f && t.y > PADDLE_Y - 0.008f &&
                abs(t.x - paddleX) < paddleHalf + 0.008f) {
                applyPowerUp(t.type)
                dead = true
            }
            if (dead) tokens.removeAt(ti)
            ti--
        }

        updateParticles(dt)
        if (hitFlash > 0f) hitFlash -= dt
        if (mcpWarpCooldown > 0f) mcpWarpCooldown -= dt

        // MCP reacts as its grid breaks: retort at 75%, taunt at 50%,
        // desperate retort at 25% remaining.
        val gridFrac = aliveCount.toFloat() / totalBricks
        if (!retortHi && gridFrac <= 0.78f) {
            retortHi = true
            audio.say("mcp_retort")?.let { showCaption(it, mcp = true) }
        }
        if (!tauntFired && gridFrac <= 0.5f) {
            tauntFired = true
            showCaption(Campaign.beat(level).taunt, mcp = true)
            audio.say(Campaign.tauntEvent(level), force = true)
        }
        if (!retortLo && gridFrac <= 0.24f) {
            retortLo = true
            audio.say("mcp_retort")?.let { showCaption(it, mcp = true) }
        }

        // level intro title card countdown
        if (levelIntro > 0f) levelIntro -= dt

        // streak commentary: 8 bricks inside 3 s
        while (breakTimes.isNotEmpty() && elapsed - breakTimes.first() > 3f) breakTimes.removeFirst()
        if (breakTimes.size >= 8) {
            breakTimes.clear()
            audio.say("streak")
        }

        // level cleared?
        if (bricks.isNotEmpty() && bricks.none { it.alive }) {
            state = State.LEVEL_CLEAR; stateTimer = 0f
            score += 100 * level
            clearTimedPowerups(silent = false)
            audio.sfx("level_clear")
            // spiral firework out of the dead web core
            for (i in 0 until 30) {
                val ang = i * 0.42f
                val v = 0.03f + i * 0.0022f
                spawnP(0, 0f, AREA_CY, cos(ang) * v, sin(ang) * v,
                    0.5f + i * 0.02f, 1f, 1f, 1f, 0.0014f, rainbow = true)
            }
            ringFx(0f, AREA_CY, Levels.palette[3], 0.014f)
            stars(0f, AREA_CY, 4)
            showCaption(Campaign.beat(level).clear, mcp = false)
            audio.say(Campaign.clearEvent(level), force = true)
        }
    }

    private fun updateParticles(dt: Float) {
        for (p in pool) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.kind == 0 || p.kind == 1) p.vy -= 0.05f * dt
            p.spin += p.spinRate * dt
        }
    }

    /** Draw the whole pool — sparks, spinning stars, expanding rings,
     *  velocity streaks. Rainbow ones hue-cycle per frame. */
    private fun renderParticles() {
        batch.lift = FLOAT_H
        for (p in pool) {
            if (!p.active) continue
            val t = (p.life / p.life0).coerceIn(0f, 1f)
            var cr = p.r; var cg = p.g; var cb = p.b
            if (p.rainbow) {
                val c = Levels.palette[((phase * 11f).toInt() + (p.spin * 3f).toInt()).mod(6)]
                cr = c[0]; cg = c[1]; cb = c[2]
            }
            when (p.kind) {
                0 -> batch.fill(p.x, p.y, p.size, p.size, p.spin, cr, cg, cb, t)
                1 -> { // 4-point star: two crossed spinning lines
                    val s = p.size * (0.6f + 0.4f * t)
                    val ca = cos(p.spin) * s; val sa = sin(p.spin) * s
                    batch.line(p.x - ca, p.y - sa, p.x + ca, p.y + sa, 0.0006f, cr, cg, cb, t)
                    batch.line(p.x + sa, p.y - ca, p.x - sa, p.y + ca, 0.0006f, cr, cg, cb, t)
                }
                2 -> { // expanding ring
                    val rad = p.size * (0.25f + (1f - t) * 2.2f)
                    batch.circle(p.x, p.y, rad, 0.0007f, cr, cg, cb, t, segs = 10)
                }
                else -> { // streak along velocity
                    batch.line(p.x, p.y, p.x - p.vx * 0.06f, p.y - p.vy * 0.06f,
                        0.0008f, cr, cg, cb, t * 0.9f)
                }
            }
        }
        batch.lift = 0f
    }

    /** Minimum vertical velocity fraction: sin(~20°). Below this the ball
     *  is basically horizontal and just ping-pongs between the sidewalls. */
    private val MIN_VY_FRAC = 0.34f

    /**
     * Anti-ping-pong: after a wall or brick bounce, if the trajectory is
     * flatter than ~20° off horizontal, steepen it to that minimum while
     * preserving speed. Keeps the current vertical direction; a dead-flat
     * ball is nudged downward, back toward the paddle.
     */
    private fun steepen(b: Ball) {
        val sp = sqrt(b.vx * b.vx + b.vy * b.vy)
        if (sp < 1e-6f) return
        val minVy = sp * MIN_VY_FRAC
        if (abs(b.vy) >= minVy) return
        val sy = if (b.vy > 0f) 1f else -1f
        b.vy = sy * minVy
        val vx2 = (sp * sp - minVy * minVy).coerceAtLeast(0f)
        b.vx = (if (b.vx >= 0f) 1f else -1f) * sqrt(vx2)
    }

    /** Ball vs walls, paddle, bricks. Returns true if something was hit. */
    private fun collide(b: Ball): Boolean {
        var hit = false
        // walls — every bounce sheds rainbow sparks
        if (b.x < -HW + BALL_R) {
            b.x = -HW + BALL_R; b.vx = abs(b.vx); steepen(b); audio.sfx("wall"); hit = true
            sparks(b.x, b.y, Levels.palette[3], 3, 0.05f, rainbow = true)
        }
        if (b.x > HW - BALL_R) {
            b.x = HW - BALL_R; b.vx = -abs(b.vx); steepen(b); audio.sfx("wall"); hit = true
            sparks(b.x, b.y, Levels.palette[3], 3, 0.05f, rainbow = true)
        }
        if (b.y > HH - BALL_R) {
            b.y = HH - BALL_R; b.vy = -abs(b.vy); audio.sfx("wall"); hit = true
            sparks(b.x, b.y, Levels.palette[3], 3, 0.05f, rainbow = true)
        }
        // MCP core: touching the monopoly intelligence bends the ball's
        // path like a malicious little gravity well, then laughs.
        if (warpFromMcp(b)) hit = true
        // paddle
        if (b.vy < 0f && b.y - BALL_R < PADDLE_Y + 0.004f && b.y > PADDLE_Y - 0.006f &&
            abs(b.x - paddleX) < paddleHalf + BALL_R) {
            if (activeTimed.containsKey(PowerUpType.CATCH)) {
                b.stuck = true; b.stuckDx = (b.x - paddleX).coerceIn(-paddleHalf, paddleHalf)
                b.stuckT = 0f
                audio.sfx("bounce")
                return true
            }
            val rel = ((b.x - paddleX) / paddleHalf).coerceIn(-1f, 1f)
            val sp = sqrt(b.vx * b.vx + b.vy * b.vy)
            val ang = (90f - rel * 55f) * Math.PI.toFloat() / 180f
            b.vx = cos(ang) * sp
            b.vy = sin(ang) * sp
            b.y = PADDLE_Y + 0.004f + BALL_R
            audio.sfx("bounce")
            sparks(b.x, PADDLE_Y + 0.004f, Levels.palette[5], 6, 0.06f, rainbow = true)
            ringFx(b.x, PADDLE_Y + 0.004f, Levels.palette[5], 0.004f)
            hit = true
        }
        // bricks — first contact wins this substep
        for (br in bricks) {
            if (!br.alive) continue
            val n = ballBrickNormal(b, br) ?: continue
            val dot = b.vx * n[0] + b.vy * n[1]
            if (dot < 0f) { b.vx -= 2 * dot * n[0]; b.vy -= 2 * dot * n[1]; steepen(b) }
            hitBrick(br)
            hit = true
            break
        }
        return hit
    }

    private fun warpFromMcp(b: Ball): Boolean {
        val dx = b.x
        val dy = b.y - AREA_CY
        val d2 = dx * dx + dy * dy
        val r = MCP_WARP_R + BALL_R
        if (d2 > r * r) return false
        val d = sqrt(d2).coerceAtLeast(0.0001f)
        val nx = dx / d
        val ny = dy / d
        val sp = sqrt(b.vx * b.vx + b.vy * b.vy).coerceAtLeast(baseSpeed())
        val swirl = if (((level + aliveCount) and 1) == 0) 1f else -1f
        val tx = -ny * swirl
        val ty = nx * swirl
        val blend = 0.72f
        b.vx = (tx * blend + nx * (1f - blend)) * sp
        b.vy = (ty * blend + ny * (1f - blend)) * sp
        b.x = nx * r
        b.y = AREA_CY + ny * r
        hitFlash = 1f
        ringFx(0f, AREA_CY, Levels.palette[5], 0.012f)
        sparks(b.x, b.y, Levels.palette[5], 12, 0.11f, rainbow = true)
        if (mcpWarpCooldown <= 0f) {
            mcpWarpCooldown = 1.7f
            audio.say("mcp_laugh", force = true)
        }
        return true
    }

    private fun pointInBrick(x: Float, y: Float, br: Levels.Brick): Boolean {
        val c = cos(-br.angle); val s = sin(-br.angle)
        val dx = x - br.cx; val dy = y - br.cy
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        return abs(lx) <= br.halfLen && abs(ly) <= br.halfThick
    }

    /** Circle vs oriented box; returns contact normal (plane coords) or null. */
    private fun ballBrickNormal(b: Ball, br: Levels.Brick): FloatArray? {
        val c = cos(-br.angle); val s = sin(-br.angle)
        val dx = b.x - br.cx; val dy = b.y - br.cy
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        val qx = lx.coerceIn(-br.halfLen, br.halfLen)
        val qy = ly.coerceIn(-br.halfThick, br.halfThick)
        val ddx = lx - qx; val ddy = ly - qy
        val d2 = ddx * ddx + ddy * ddy
        if (d2 > BALL_R * BALL_R) return null
        // local normal
        var nx: Float; var ny: Float
        if (d2 > 1e-10f) {
            val d = sqrt(d2); nx = ddx / d; ny = ddy / d
        } else {
            // center inside box: push out along the thin axis
            nx = 0f; ny = if (ly >= 0f) 1f else -1f
        }
        // rotate normal back to plane coords (inverse of the -angle rotation)
        val ca = cos(br.angle); val sa = sin(br.angle)
        return floatArrayOf(nx * ca - ny * sa, nx * sa + ny * ca)
    }

    private fun hitBrick(br: Levels.Brick) {
        br.hp--
        audio.sfx("brick", rate = 1f + (br.maxHp - br.hp) * 0.15f)
        if (br.hp <= 0) {
            br.alive = false
            aliveCount--
            hitFlash = 0.35f   // MCP recoils
            score += 15 * br.maxHp
            breakTimes.addLast(elapsed)
            // Minter-grade demise: colored spray + spinning stars + shockring
            sparks(br.cx, br.cy, br.color, 10, 0.09f)
            stars(br.cx, br.cy, 2)
            ringFx(br.cx, br.cy, br.color, 0.0055f)
            if (rnd.nextFloat() < 0.14f && tokens.size < 3) {
                tokens.add(Token(br.cx, br.cy, PowerUpType.random(rnd)))
            }
        }
    }

    private fun spawnParticles(x: Float, y: Float, rgb: FloatArray, n: Int) =
        sparks(x, y, rgb, n, 0.07f)

    private fun applyPowerUp(type: PowerUpType) {
        audio.sfx("powerup_get")
        score += 50
        // rainbow fountain off the paddle
        sparks(paddleX, PADDLE_Y + 0.008f, type.rgb, 14, 0.09f, rainbow = true)
        ringFx(paddleX, PADDLE_Y + 0.004f, type.rgb, 0.008f)
        when (type) {
            PowerUpType.MULTI -> {
                val toAdd = (MAX_BALLS - balls.size).coerceAtLeast(0)
                for (i in 0 until toAdd) {
                    val o = findMultiBallSource(i) ?: return
                    val a = atan2(o.vy, o.vx) + (if (i % 2 == 0) 0.5f else -0.5f)
                    val sp = baseSpeed() * slowFactor()
                    balls.add(Ball(o.x, o.y, cos(a) * sp, sin(a) * sp, stuck = false))
                }
            }
            PowerUpType.ZAP -> {
                lives = (lives + 1).coerceAtMost(MAX_LIVES)
                audio.sfx("superzap")
            }
            PowerUpType.LIFE -> lives = (lives + 1).coerceAtMost(MAX_LIVES)
            else -> {} // timed: handled below
        }
        if (type.timed) {
            val hadTimed = activeTimed.keys.any { it.timed }
            activeTimed[type] = type.duration
            if (!hadTimed) audio.startPowerupMusic()  // THE power-up track
        }
        audio.say(type.commentEvent, force = type == PowerUpType.ZAP)
    }

    private fun findMultiBallSource(offset: Int): Ball? {
        var seen = 0
        var fallback: Ball? = null
        for (b in balls) {
            if (fallback == null) fallback = b
            if (!b.stuck) {
                if (seen == offset) return b
                seen++
            }
        }
        if (seen > 0) {
            val target = offset % seen
            var i = 0
            for (b in balls) if (!b.stuck) {
                if (i == target) return b
                i++
            }
        }
        return fallback
    }

    private fun zapRandomBricks(limit: Int) {
        var killed = 0
        var guard = 0
        while (killed < limit && aliveCount > 0 && guard < limit * 12) {
            guard++
            val br = bricks[rnd.nextInt(bricks.size)]
            if (!br.alive) continue
            br.hp = 0
            br.alive = false
            aliveCount--
            killed++
            score += 15 * br.maxHp
            sparks(br.cx, br.cy, br.color, 5, 0.08f, rainbow = true)
            ringFx(br.cx, br.cy, br.color, 0.005f)
        }
    }

    private fun clearTimedPowerups(silent: Boolean) {
        val hadTimed = activeTimed.keys.any { it.timed }
        activeTimed.clear()
        lasers.clear()
        if (hadTimed) audio.stopPowerupMusic()
        if (!silent && hadTimed) audio.sfx("powerup_end")
    }

    private fun loseLife() {
        lives--
        clearTimedPowerups(silent = true)
        audio.sfx("lose_life")
        if (lives <= 0) {
            state = State.GAME_OVER; stateTimer = 0f
            settings.highScore = score
            audio.stopLevelMusic()
            audio.say("game_over", force = true)
        } else {
            audio.say("life_lost")
            balls.add(Ball(0f, PADDLE_Y + 0.012f, 0f, 0f))
        }
    }

    // ------------------------------------------------------------------
    // rendering — build all neon geometry for this frame

    private fun cyc(offset: Int): FloatArray =
        Levels.palette[(((phase * 5f).toInt()) + offset).mod(Levels.palette.size)]

    private fun render() {
        // WINDOW SIZE scales the whole presentation (NeonTetris3D-style
        // head-locked screen): plane basis maps game meters -> world units.
        // The plane leans back ~22 degrees — a slightly isometric table —
        // and `normal` points back toward the camera so lifted elements
        // (bricks, paddle, ball, MCP, HUD) get real stereo parallax.
        val s = windowScales[settings.windowSize.coerceIn(0, 2)]
        val ct = cos(ISO_TILT); val st = sin(ISO_TILT)
        batch.setBasis(
            0f, 0f, 0f,
            s, 0f, 0f,
            0f, s * ct, -s * st,
            0f, s * st, s * ct
        )
        batch.lift = 0f
        batch.begin()
        when (state) {
            State.ATTRACT -> renderAttract()
            State.PLAY, State.LEVEL_CLEAR -> renderPlay()
            State.FINALE -> renderFinale()
            State.GAME_OVER -> { renderPlay(); renderGameOver() }
        }
        // text/menu overlays float on the UI layer
        batch.lift = UI_H
        if (!settingsOpen) renderCaptions()
        if (settingsOpen) renderSettings()
        if (swipe.calibrating) renderSwipeCalibration()
        batch.lift = 0f
    }

    private fun renderAttract() {
        // slowly spinning web ring
        val n = 16
        val rot = phase * 0.4f
        for (ring in 0 until 3) {
            val r = 0.07f - ring * 0.018f
            val col = cyc(ring)
            var px = 0f; var py = 0f
            for (i in 0..n) {
                val a = rot + i * 2f * Math.PI.toFloat() / n
                val x = cos(a) * r; val y = 0.01f + sin(a) * r * 0.62f
                if (i > 0) batch.line(px, py, x, y, 0.0012f, col[0], col[1], col[2], 0.5f)
                px = x; py = y
            }
        }
        val t = cyc(0)
        VectorFont.draw(batch, "X3 BREAKOUT", 0f, 0.016f, 0.0042f, t[0], t[1], t[2], 1f, centered = true)
        VectorFont.draw(batch, "THE PADDLE VS THE", 0f, -0.006f, 0.0013f, 0.75f, 0.95f, 1f, 0.85f, centered = true)
        VectorFont.draw(batch, "MONOPOLY CONTROL PROTOCOL", 0f, -0.021f, 0.0013f, 1f, 0.35f, 0.4f, 0.85f, centered = true)
        if ((phase * 2f).toInt() % 2 == 0) {
            VectorFont.draw(batch, "TAP TO START", 0f, -0.044f, 0.0018f, 1f, 1f, 1f, 0.9f, centered = true)
        }
        VectorFont.draw(batch, "DOUBLE TAP: SETTINGS", 0f, -0.060f, 0.0012f, 0.4f, 0.75f, 1f, 0.7f, centered = true)
        // Same gold as the in-game SCORE HUD — "gold means score" everywhere.
        VectorFont.draw(batch, "HIGH SCORE " + settings.highScore.toString().padStart(6, '0'),
            0f, -0.075f, 0.0013f, 1f, 0.9f, 0.1f, 0.85f, centered = true)
    }

    private fun renderPlay() {
        // drifting hue-cycling motes on the table — vector arcade ambience
        for (i in 0 until 24) {
            val mx = (((i * 0.3831f + 0.07f) % 1f) * 2f - 1f) * (HW - 0.006f)
            val myT = (i * 0.5711f + phase * (0.010f + (i % 5) * 0.004f)) % 1f
            val my = (myT * 2f - 1f) * (HH - 0.006f)
            val mc = Levels.palette[(i + (phase * 3f).toInt()).mod(6)]
            batch.fill(mx, my, 0.0007f, 0.0007f, phase * 2f + i, mc[0], mc[1], mc[2], 0.35f)
        }

        // playfield border
        batch.line(-HW, -HH, -HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.65f)
        batch.line(HW, -HH, HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.65f)
        batch.line(-HW, HH, HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.65f)
        batch.line(-HW, -HH, HW, -HH, 0.0008f, 1f, 0.2f, 0.2f, 0.3f) // death line

        // web outline behind bricks, faint pulse
        val pulse = 0.16f + 0.08f * sin(phase * 2.4f)
        val oc = cyc(4)
        val segs = if (outlineClosed) outline.size else outline.size - 1
        for (i in 0 until segs) {
            val a = outline[i]; val b = outline[(i + 1) % outline.size]
            batch.line(a[0], a[1], b[0], b[1], 0.0009f, oc[0], oc[1], oc[2], pulse)
        }

        // the MONOPOLY CONTROL PROTOCOL itself: sigil hovering over the
        // web core, recoiling on every kill, glitching apart as it breaks
        batch.lift = 0.007f
        McpCore.draw(batch, level, 0f, AREA_CY,
            aliveCount.toFloat() / totalBricks, phase, hitFlash)
        batch.lift = 0f

        // bricks — extruded wireframe blocks: dim base on the table,
        // corner struts, bright top face (nested outlines = remaining HP)
        for (br in bricks) {
            if (!br.alive) continue
            val a = 0.6f + 0.4f * (br.hp.toFloat() / br.maxHp)
            batch.lift = 0f
            brickOutline(br, 1f, a * 0.28f)
            brickPosts(br, a * 0.5f)
            batch.lift = BRICK_H
            brickOutline(br, 1f, a)
            if (br.hp >= 2) brickOutline(br, 0.55f, a * 0.85f)
            if (br.hp >= 3) brickOutline(br, 0.26f, a * 0.7f)
        }
        batch.lift = 0f

        // paddle — extruded slab: dim base, end struts, bright top deck
        val pc = cyc(1)
        val ph = 0.0026f
        paddleRect(pc, ph, 0.30f)
        batch.post(paddleX - paddleHalf, PADDLE_Y, 0f, PADDLE_H, 0.0006f, pc[0], pc[1], pc[2], 0.7f)
        batch.post(paddleX + paddleHalf, PADDLE_Y, 0f, PADDLE_H, 0.0006f, pc[0], pc[1], pc[2], 0.7f)
        batch.lift = PADDLE_H
        paddleRect(pc, ph, 1f)
        batch.line(paddleX, PADDLE_Y - ph, paddleX, PADDLE_Y + ph, 0.0006f, 1f, 1f, 1f, 0.8f)
        batch.lift = 0f

        // balls — floating ring with a table shadow beneath
        var bDraw = 0
        while (bDraw < balls.size) {
            val b = balls[bDraw]
            batch.circle(b.x, b.y, BALL_R * 0.8f, 0.0006f, 0.3f, 0.4f, 0.8f, 0.3f, segs = 8)
            batch.lift = FLOAT_H
            batch.circle(b.x, b.y, BALL_R, 0.0008f, 1f, 1f, 1f, 1f, segs = 10)
            batch.fill(b.x, b.y, 0.0008f, 0.0008f, 0f, 1f, 1f, 1f, 0.9f)
            batch.lift = 0f
            bDraw++
        }

        // lasers — hovering bolts
        batch.lift = FLOAT_H
        var lDraw = 0
        while (lDraw < lasers.size) {
            val l = lasers[lDraw]
            batch.line(l.x, l.y, l.x, l.y + 0.012f, 0.0014f, 1f, 0.2f, 0.25f, 1f)
            lDraw++
        }
        batch.lift = 0f

        // power-up tokens: spinning diamonds floating above the table
        var tDraw = 0
        while (tDraw < tokens.size) {
            val t = tokens[tDraw]
            batch.circle(t.x, t.y, 0.005f, 0.0005f, 0.3f, 0.4f, 0.8f, 0.3f, segs = 8) // shadow
            batch.lift = 0.004f
            val w = 0.007f * (0.65f + 0.35f * abs(cos(t.spin)))
            batch.fill(t.x, t.y, w, 0.007f, Math.PI.toFloat() / 4f,
                t.type.rgb[0] * 0.4f, t.type.rgb[1] * 0.4f, t.type.rgb[2] * 0.4f, 0.5f)
            batch.circle(t.x, t.y, 0.008f, 0.0009f, t.type.rgb[0], t.type.rgb[1], t.type.rgb[2], 0.9f, segs = 8)
            VectorFont.draw(batch, t.type.letter.toString(), t.x, t.y - 0.003f, 0.001f,
                1f, 1f, 1f, 1f, centered = true)
            batch.lift = 0f
            tDraw++
        }

        renderParticles()

        // HUD layer floats above everything
        batch.lift = UI_H
        renderPowerupTimers()
        renderHud()

        // level intro title card, big and readable, fades out
        if (!settingsOpen && levelIntro > 0f && state == State.PLAY) {
            val bt = Campaign.beat(level)
            val a = (levelIntro / 0.6f).coerceIn(0f, 1f)
            val t = cyc(0)
            drawFit("WEB ${Campaign.idx(level)}", 0f, 0.014f, 0.0018f, 0.10f, t[0], t[1], t[2], a * 0.9f)
            drawFit(bt.title, 0f, -0.012f, 0.0028f, 0.235f, 1f, 1f, 1f, a)
        }

        if (!settingsOpen && state == State.LEVEL_CLEAR) {
            val t = cyc(0)
            drawFit(Campaign.beat(level).clear, 0f, 0.0f, 0.0022f, 0.24f, t[0], t[1], t[2], 1f)
        }
        batch.lift = 0f
    }

    /** Corner struts connecting a brick's table base to its top face. */
    private fun brickPosts(br: Levels.Brick, alpha: Float) {
        val c = cos(br.angle); val s = sin(br.angle)
        val ax = c * br.halfLen; val ay = s * br.halfLen
        val nx = -s * br.halfThick; val ny = c * br.halfThick
        val cr = br.color[0]; val cg = br.color[1]; val cb = br.color[2]
        batch.post(br.cx - ax - nx, br.cy - ay - ny, 0f, BRICK_H, 0.0005f, cr, cg, cb, alpha)
        batch.post(br.cx + ax - nx, br.cy + ay - ny, 0f, BRICK_H, 0.0005f, cr, cg, cb, alpha)
        batch.post(br.cx + ax + nx, br.cy + ay + ny, 0f, BRICK_H, 0.0005f, cr, cg, cb, alpha)
        batch.post(br.cx - ax + nx, br.cy - ay + ny, 0f, BRICK_H, 0.0005f, cr, cg, cb, alpha)
    }

    /** Paddle outline rectangle at the current lift. */
    private fun paddleRect(pc: FloatArray, ph: Float, alphaMul: Float) {
        val w = 0.0007f
        batch.line(paddleX - paddleHalf, PADDLE_Y - ph, paddleX + paddleHalf, PADDLE_Y - ph, w, pc[0], pc[1], pc[2], alphaMul)
        batch.line(paddleX - paddleHalf, PADDLE_Y + ph, paddleX + paddleHalf, PADDLE_Y + ph, w, pc[0], pc[1], pc[2], alphaMul)
        batch.line(paddleX - paddleHalf, PADDLE_Y - ph, paddleX - paddleHalf, PADDLE_Y + ph, w, pc[0], pc[1], pc[2], alphaMul)
        batch.line(paddleX + paddleHalf, PADDLE_Y - ph, paddleX + paddleHalf, PADDLE_Y + ph, w, pc[0], pc[1], pc[2], alphaMul)
    }

    /** Oriented outline rectangle for a brick (Vectrex style). */
    private fun brickOutline(br: Levels.Brick, scale: Float, alpha: Float) {
        val c = cos(br.angle); val s = sin(br.angle)
        val ax = c * br.halfLen * scale; val ay = s * br.halfLen * scale
        val nx = -s * br.halfThick * scale; val ny = c * br.halfThick * scale
        val x0 = br.cx - ax - nx; val y0 = br.cy - ay - ny
        val x1 = br.cx + ax - nx; val y1 = br.cy + ay - ny
        val x2 = br.cx + ax + nx; val y2 = br.cy + ay + ny
        val x3 = br.cx - ax + nx; val y3 = br.cy - ay + ny
        val w = 0.0006f
        val cr = br.color[0]; val cg = br.color[1]; val cb = br.color[2]
        batch.line(x0, y0, x1, y1, w, cr, cg, cb, alpha)
        batch.line(x1, y1, x2, y2, w, cr, cg, cb, alpha)
        batch.line(x2, y2, x3, y3, w, cr, cg, cb, alpha)
        batch.line(x3, y3, x0, y0, w, cr, cg, cb, alpha)
    }

    /** Draw centered text auto-shrunk to fit a max width — captions must
     *  never spill off the readable region. */
    private fun drawFit(text: String, u: Float, v: Float, baseSize: Float, maxW: Float,
                        r: Float, g: Float, b: Float, a: Float) {
        if (text.isEmpty() || a <= 0.01f) return
        val size = minOf(baseSize, maxW / (text.length * VectorFont.ADVANCE))
        VectorFont.draw(batch, text, u, v, size, r, g, b, a, centered = true)
    }

    /** Caption bar: the WHOLE sentence, large and high-contrast, in the
     *  lower field. Auto-sized by showCaption so it always fits. */
    private fun renderCaptions() {
        val c = curCap ?: return
        val a = minOf(1f, (c.dur - capTimer) * 4f, capTimer * 2.5f).coerceIn(0f, 1f)
        val step = c.size * 6f * 1.45f
        var y = -0.024f
        for (i in c.lines.indices) {
            val line = c.lines[i]
            val jx = if (c.mcp) sin(phase * 37f + i) * 0.0008f else 0f
            if (c.mcp) {
                VectorFont.draw(batch, line, jx, y, c.size, 1f, 0.28f, 0.30f, a, centered = true)
            } else {
                VectorFont.draw(batch, line, 0f, y, c.size, 0.78f, 0.96f, 1f, a, centered = true)
            }
            y -= step
        }
    }

    /** MCP collapse: harmless open fragments, and the Paddle becomes a
     *  public beacon — not a ruler. */
    private fun renderFinale() {
        // field frame
        batch.line(-HW, -HH, -HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.5f)
        batch.line(HW, -HH, HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.5f)
        batch.line(-HW, HH, HW, HH, 0.0012f, 0.25f, 0.35f, 1f, 0.5f)

        // dying MCP core, authority draining to zero, sinking to the table
        val authority = (1f - stateTimer / 5f).coerceIn(0f, 1f) * 0.4f
        if (stateTimer < 6f) {
            batch.lift = 0.007f * (1f - stateTimer / 6f).coerceIn(0f, 1f)
            McpCore.draw(batch, level, 0f, AREA_CY, authority, phase)
            batch.lift = 0f
        }

        // open fragments drifting free
        renderParticles()

        // the Paddle as public beacon: a column of light, pulsing gently
        val pc = cyc(1)
        val beam = 0.35f + 0.20f * sin(phase * 2f)
        batch.lift = PADDLE_H
        batch.line(paddleX, PADDLE_Y, paddleX, HH, 0.004f, pc[0], pc[1], pc[2], beam * 0.5f)
        batch.line(paddleX - paddleHalf, PADDLE_Y, paddleX + paddleHalf, PADDLE_Y,
            0.005f, pc[0], pc[1], pc[2], 1f)

        batch.lift = UI_H
        val t = cyc(0)
        drawFit("THE SIGNAL RETURNS", 0f, 0.052f, 0.0024f, 0.23f, t[0], t[1], t[2], 1f)
        renderHud()
        batch.lift = 0f
    }

    // string caches: no per-frame formatting (GC hygiene)
    private val timerTxt = arrayOfNulls<String>(PowerUpType.entries.size)
    private val timerSec = IntArray(PowerUpType.entries.size) { -1 }

    /** T2K-font countdown timers above the field, one per active power-up. */
    private fun renderPowerupTimers() {
        if (activeTimed.isEmpty()) return
        val slotW = 0.036f
        var x = -(activeTimed.size - 1) * slotW * 0.5f
        for ((type, remaining) in activeTimed) {
            val secs = ceil(remaining).toInt().coerceAtLeast(0)
            val o = type.ordinal
            if (timerSec[o] != secs || timerTxt[o] == null) {
                timerSec[o] = secs
                timerTxt[o] = "${type.letter} ${secs.toString().padStart(2, '0')}"
            }
            // color cycles faster as time runs out — pure Tempest panic
            val col = if (remaining < 3f) cyc((phase * 14f).toInt()) else type.rgb
            VectorFont.draw(batch, timerTxt[o]!!, x, HH + 0.008f, 0.0021f,
                col[0], col[1], col[2], 1f, centered = true)
            x += slotW
        }
    }

    private var hudScoreV = -1; private var hudScoreTxt = ""
    private var hudLevelV = -1; private var hudLevelTxt = ""
    private var hudLivesV = -1; private var hudLivesTxt = ""

    private fun renderHud() {
        if (score != hudScoreV) { hudScoreV = score; hudScoreTxt = "SCORE " + score.toString().padStart(6, '0') }
        if (level != hudLevelV) { hudLevelV = level; hudLevelTxt = "WEB $level" }
        if (lives != hudLivesV) { hudLivesV = lives; hudLivesTxt = "LIVES $lives" }
        val hudY = -HH - 0.017f
        VectorFont.draw(batch, hudScoreTxt, -HW, hudY, 0.0014f, 1f, 0.9f, 0.1f, 0.9f)
        VectorFont.draw(batch, hudLevelTxt, 0.012f, hudY, 0.0014f, 0.1f, 0.95f, 1f, 0.9f)
        VectorFont.draw(batch, hudLivesTxt, 0.075f, hudY, 0.0014f, 1f, 0.15f, 0.2f, 0.9f)
    }

    // No diagnostic text in normal play — FPS/thermals go to logcat only
    // (MainActivity's 2 s heartbeat). The HUD shows polished values only.

    private fun renderGameOver() {
        val t = cyc(0)
        VectorFont.draw(batch, "GAME OVER", 0f, 0.012f, 0.0036f, t[0], t[1], t[2], 1f, centered = true)
        VectorFont.draw(batch, "SCORE ${score.toString().padStart(6, '0')}", 0f, -0.018f, 0.0018f,
            1f, 0.9f, 0.1f, 1f, centered = true)
    }

    private fun renderSettings() {
        // panel (NeonTetris3D-style menu: swipe = cursor, tap = select/cycle)
        val pw = 0.118f; val ph = 0.096f
        batch.fill(0f, 0f, pw, ph, 0f, 0.01f, 0.01f, 0.035f, 0.96f)
        batch.line(-pw, ph, pw, ph, 0.0012f, 0.9f, 0.2f, 1f, 0.9f)
        batch.line(-pw, -ph, pw, -ph, 0.0012f, 0.9f, 0.2f, 1f, 0.9f)
        batch.line(-pw, -ph, -pw, ph, 0.0012f, 0.9f, 0.2f, 1f, 0.9f)
        batch.line(pw, -ph, pw, ph, 0.0012f, 0.9f, 0.2f, 1f, 0.9f)
        val t = cyc(0)
        VectorFont.draw(batch, "SETTINGS", 0f, 0.074f, 0.0022f, t[0], t[1], t[2], 1f, centered = true)

        val items = listOf(
            "RESUME",
            "RESTART GAME",
            "WINDOW SIZE: " + arrayOf("SMALL", "MEDIUM", "LARGE")[settings.windowSize.coerceIn(0, 2)],
            "SWIPE SENS: " + arrayOf("LOW", "MEDIUM", "HIGH", "TURBO")[settings.swipeSens.coerceIn(0, 3)],
            "CALIBRATE SWIPE",
            "CAPTIONS: " + if (settings.captions) "ON" else "OFF",
            "COMMENTARY: " + if (settings.commentary) "ON" else "OFF",
            "MUSIC VOL: " + volLabel(settings.musicVol),
            "SFX VOL: " + volLabel(settings.sfxVol)
        )
        var y = 0.056f
        for ((i, item) in items.withIndex()) {
            val sel = i == menuSel
            if (sel) VectorFont.draw(batch, ">", -0.108f, y, 0.0015f, t[0], t[1], t[2], 1f)
            if (sel) {
                VectorFont.draw(batch, item, -0.095f, y, 0.0015f, 1f, 1f, 1f, 1f)
            } else {
                VectorFont.draw(batch, item, -0.095f, y, 0.0015f, 0.45f, 0.6f, 0.9f, 0.8f)
            }
            y -= 0.0155f
        }
        VectorFont.draw(batch, "SWIPE: MOVE  TAP: SELECT  DBL: CLOSE",
            0f, -0.086f, 0.0009f, 0.45f, 0.55f, 0.7f, 0.7f, centered = true)
    }

    private fun renderSwipeCalibration() {
        batch.fill(0f, -0.002f, 0.125f, 0.056f, 0f, 0.02f, 0.03f, 0.04f, 0.78f)
        batch.line(-0.125f, 0.052f, 0.125f, 0.052f, 0.0014f, 0.2f, 1f, 0.85f, 0.95f)
        batch.line(-0.125f, -0.058f, 0.125f, -0.058f, 0.0014f, 0.2f, 1f, 0.85f, 0.95f)
        VectorFont.draw(batch, "SWIPE CALIBRATION", 0f, 0.032f, 0.0019f, 0.2f, 1f, 0.85f, 1f, centered = true)
        VectorFont.draw(batch, "SWIPE ONE FULL ARM STROKE ON THE PAD",
            0f, 0.012f, 0.00115f, 1f, 1f, 1f, 0.96f, centered = true)

        // live paddle rail — the marker rides the swipe in real time
        val railY = -0.008f
        val railHalf = 0.094f
        batch.line(-railHalf, railY, railHalf, railY, 0.0011f, 0.25f, 0.45f, 1f, 0.65f)
        val mx = -railHalf + 2f * railHalf * swipe.pos01
        batch.line(mx - 0.011f, railY, mx + 0.011f, railY, 0.0036f, 0.2f, 1f, 0.85f, 1f)

        // stroke locks: one diamond per completed full stroke
        for (i in 0 until SwipeControl.STROKES_NEEDED) {
            val x = (i - 1) * 0.024f
            val done = i < swipe.calCount
            if (done) {
                batch.fill(x, -0.030f, 0.0055f, 0.0075f, Math.PI.toFloat() / 4f, 0.2f, 1f, 0.85f, 0.95f)
            } else {
                val pulse = 0.4f + 0.25f * sin(phase * 6f + i)
                batch.circle(x, -0.030f, 0.006f, 0.0009f, 0.45f, 0.6f, 0.9f, pulse, segs = 8)
            }
        }

        VectorFont.draw(batch, "${swipe.calibrationLabel()}  -  DBL TAP: CANCEL",
            0f, -0.049f, 0.00095f, 0.55f, 0.8f, 1f, 0.9f, centered = true)
    }

    private fun updateCalibrationFx() {
        if (!swipe.calibrating) {
            if (calWasActive) audio.sfx("level_clear")
            calWasActive = false
            calLastLockSerial = 0
            return
        }
        calWasActive = true
        if (swipe.calLockSerial != calLastLockSerial) {
            calLastLockSerial = swipe.calLockSerial
            audio.sfx("powerup_get", 0.9f + swipe.calCount * 0.15f)
        }
    }
}
