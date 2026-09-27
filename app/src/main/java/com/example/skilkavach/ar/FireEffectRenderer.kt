package com.example.skilkavach.ar

import android.opengl.GLES20.*
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Multi-layer fire, smoke, and extinguisher discharge particle renderer for realistic AR fire simulation.
 *
 * Visual layers:
 * 1. Inner flame core — bright yellow/white, high velocity, fast flicker
 * 2. Middle flame — vibrant orange, turbulent aerodynamic expansion
 * 3. Outer flame — deep red/amber, slow buoyant drift
 * 4. Smoke / Steam — transitions from dark carbon soot to thick white-gray vapor during suppression
 * 5. Embers — bright orange/gold sparks rising with micro-turbulence
 * 6. Extinguisher Discharge Agent — pressurized conical spray plume of dry chemical MAP / CO2 cloud
 *
 * Supports state transitions: ACTIVE → SUPPRESSING → EXTINGUISHED
 * All rendering uses billboard-oriented quads with soft circular falloff and layer-specific alpha blending.
 */
class FireEffectRenderer(private val maxParticles: Int = 85) {

    private data class FireParticle(
        var x: Float = 0f,
        var y: Float = 0f,
        var z: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var vz: Float = 0f,
        var scale: Float = 0.05f,
        var alpha: Float = 1f,
        var life: Float = 0f,
        var maxLife: Float = 1f,
        var phase: Float = 0f,
        var layer: Int = 0  // 0=core, 1=mid, 2=outer, 3=smoke, 4=ember, 5=discharge agent
    )

    private val particles = Array(maxParticles) { FireParticle() }
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var timeSeconds = 0f

    // Quad vertices for billboard
    private val quadVerts = floatArrayOf(
        -0.5f,  0.5f, 0f,
        -0.5f, -0.5f, 0f,
         0.5f,  0.5f, 0f,
         0.5f, -0.5f, 0f,
    )

    // ── Shader source: soft radial gradient with procedural turbulence ─────────

    private val vertexShaderSrc = """
        attribute vec3 a_Position;
        uniform mat4 u_MVP;
        uniform float u_Scale;
        varying vec2 v_UV;
        void main() {
            gl_Position = u_MVP * vec4(a_Position * u_Scale, 1.0);
            v_UV = a_Position.xy + 0.5;
        }
    """.trimIndent()

    private val fragmentShaderSrc = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform float u_Flicker;
        uniform int u_Layer;
        varying vec2 v_UV;
        void main() {
            vec2 center = v_UV - 0.5;
            float dist = length(center);

            // Soft radial falloff per layer
            float alpha;
            if (u_Layer == 4) {
                // Embers: sharp bright pinpoint
                alpha = smoothstep(0.5, 0.12, dist);
            } else if (u_Layer == 3) {
                // Smoke / Steam: very soft expanding puff
                alpha = smoothstep(0.5, 0.0, dist) * 0.65;
            } else if (u_Layer == 5) {
                // Extinguisher agent cloud: soft dense powder droplet
                alpha = smoothstep(0.5, 0.05, dist) * 0.75;
            } else if (u_Layer == 6) {
                // Heat Shimmer: subtle refractive distortion ring around hottest region
                alpha = smoothstep(0.48, 0.10, dist) * 0.25;
            } else {
                // Fire flames: medium soft with dynamic edge turbulence
                float turbulence = sin(dist * 22.0 + u_Flicker * 6.28) * 0.06;
                alpha = smoothstep(0.5 + turbulence, 0.08, dist);
            }

            // Apply particle alpha and flicker
            float flickerMod = 1.0;
            if (u_Layer < 3) {
                flickerMod = 0.75 + 0.25 * sin(u_Flicker * 14.0 + dist * 8.0);
            }

            gl_FragColor = vec4(u_Color.rgb * flickerMod, u_Color.a * alpha);
        }
    """.trimIndent()

    /** Returns dynamic flickering light intensity for environment ambient lighting. */
    fun getDynamicFireLightIntensity(fireIntensity: Float): Float {
        if (fireIntensity <= 0.01f) return 0f
        val flicker = sin(timeSeconds * 12f) * cos(timeSeconds * 7.5f)
        return (fireIntensity * (0.85f + 0.15f * flicker)).coerceIn(0f, 1.2f)
    }

    fun initialize() {
        val vShader = compileShader(GL_VERTEX_SHADER, vertexShaderSrc)
        val fShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSrc)
        program = glCreateProgram().also {
            glAttachShader(it, vShader)
            glAttachShader(it, fShader)
            glLinkProgram(it)
        }

        vertexBuffer = ByteBuffer.allocateDirect(quadVerts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(quadVerts); position(0) }

        // Initialize particles across layers
        for (i in 0 until maxParticles) {
            particles[i].layer = when {
                i < maxParticles * 0.14 -> 0  // Core (14%)
                i < maxParticles * 0.32 -> 1  // Mid (18%)
                i < maxParticles * 0.50 -> 2  // Outer (18%)
                i < maxParticles * 0.72 -> 3  // Smoke / Steam (22%)
                i < maxParticles * 0.84 -> 4  // Embers (12%)
                else -> 5                      // Extinguisher Agent Plume (16%)
            }
            particles[i].life = 0f
        }
    }

    /**
     * Update all particles based on fire intensity and extinguisher discharge.
     * @param hazardX, hazardY, hazardZ Coordinates of the fire source
     * @param fireIntensity 1.0 = full roaring fire, 0.0 = completely suppressed
     * @param smokeIntensity Smoke density
     * @param isDischarging When true, emits dry chemical spray from nozzle to fire base
     * @param nozzleX, nozzleY, nozzleZ World position of the extinguisher nozzle
     */
    fun update(
        hazardX: Float, hazardY: Float, hazardZ: Float,
        fireIntensity: Float = 1f,
        smokeIntensity: Float = 0.5f,
        isDischarging: Boolean = false,
        nozzleX: Float = hazardX - 0.55f,
        nozzleY: Float = hazardY + 0.25f,
        nozzleZ: Float = hazardZ + 0.50f
    ) {
        timeSeconds = (System.currentTimeMillis() % 100000) / 1000f
        val dt = 0.016f // ~60fps

        for (p in particles) {
            if (p.life <= 0f) {
                // Respawn logic based on layer and intensity
                val shouldSpawn = when (p.layer) {
                    0, 1, 2 -> fireIntensity > 0.05f && Math.random() < fireIntensity
                    3 -> smokeIntensity > 0.05f
                    4 -> fireIntensity > 0.2f && Math.random() < fireIntensity * 0.65
                    5 -> isDischarging // Agent plume only emits when discharging
                    else -> false
                }
                if (!shouldSpawn) continue

                if (p.layer == 5) {
                    // Spawn at extinguisher nozzle pointing toward fire base
                    p.x = nozzleX + (Math.random().toFloat() - 0.5f) * 0.02f
                    p.y = nozzleY + (Math.random().toFloat() - 0.5f) * 0.02f
                    p.z = nozzleZ + (Math.random().toFloat() - 0.5f) * 0.02f

                    // Velocity vector from nozzle to fire base
                    val targetX = hazardX + (Math.random().toFloat() - 0.5f) * 0.12f
                    val targetY = hazardY + 0.05f + Math.random().toFloat() * 0.08f
                    val targetZ = hazardZ + (Math.random().toFloat() - 0.5f) * 0.12f

                    val dx = targetX - nozzleX
                    val dy = targetY - nozzleY
                    val dz = targetZ - nozzleZ
                    val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)

                    val speed = 1.35f + Math.random().toFloat() * 0.45f
                    p.vx = (dx / dist) * speed * dt
                    p.vy = (dy / dist) * speed * dt
                    p.vz = (dz / dist) * speed * dt

                    p.scale = 0.035f + Math.random().toFloat() * 0.025f
                    p.maxLife = 0.45f + Math.random().toFloat() * 0.25f
                    p.alpha = 0.85f
                    p.life = p.maxLife
                    continue
                }

                // Fire & Smoke cluster spread around fire source
                val spreadRadius = when (p.layer) {
                    0 -> 0.05f   // Core: tight cluster
                    1 -> 0.10f   // Mid: moderate spread
                    2 -> 0.15f   // Outer: wider
                    3 -> 0.18f   // Smoke: broad plume
                    4 -> 0.08f   // Embers: moderate
                    else -> 0.1f
                }

                p.x = hazardX + (Math.random().toFloat() - 0.5f) * spreadRadius
                p.y = hazardY + 0.02f + Math.random().toFloat() * 0.04f
                p.z = hazardZ + (Math.random().toFloat() - 0.5f) * spreadRadius
                p.phase = Math.random().toFloat() * 6.28f

                when (p.layer) {
                    0 -> { // Core flame
                        p.vy = 0.010f + Math.random().toFloat() * 0.008f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.scale = (0.05f + Math.random().toFloat() * 0.03f) * (0.4f + 0.6f * fireIntensity)
                        p.maxLife = 0.38f + Math.random().toFloat() * 0.28f
                        p.alpha = 0.95f * fireIntensity
                    }
                    1 -> { // Mid flame
                        p.vy = 0.008f + Math.random().toFloat() * 0.006f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.scale = (0.08f + Math.random().toFloat() * 0.05f) * (0.4f + 0.6f * fireIntensity)
                        p.maxLife = 0.48f + Math.random().toFloat() * 0.35f
                        p.alpha = 0.85f * fireIntensity
                    }
                    2 -> { // Outer flame
                        p.vy = 0.006f + Math.random().toFloat() * 0.005f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.004f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.004f
                        p.scale = (0.11f + Math.random().toFloat() * 0.07f) * (0.4f + 0.6f * fireIntensity)
                        p.maxLife = 0.58f + Math.random().toFloat() * 0.42f
                        p.alpha = 0.70f * fireIntensity
                    }
                    3 -> { // Smoke / Steam
                        p.vy = 0.005f + Math.random().toFloat() * 0.005f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.scale = 0.12f + Math.random().toFloat() * 0.08f
                        p.maxLife = 2.2f + Math.random().toFloat() * 1.6f
                        p.alpha = 0.40f * smokeIntensity.coerceAtMost(1f)
                    }
                    4 -> { // Embers
                        p.vy = 0.012f + Math.random().toFloat() * 0.012f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.010f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.010f
                        p.scale = 0.012f + Math.random().toFloat() * 0.009f
                        p.maxLife = 1.1f + Math.random().toFloat() * 0.8f
                        p.alpha = 1.0f * fireIntensity
                    }
                }
                p.life = p.maxLife

            } else {
                // Animate existing particle
                p.life -= dt
                p.x += p.vx
                p.y += p.vy
                p.z += p.vz

                when (p.layer) {
                    0, 1, 2 -> {
                        // Flames shrink slightly and fade as they rise
                        val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
                        p.scale *= 1f + 0.002f * (1f - lifeRatio)
                        p.vy += 0.0001f // Buoyancy acceleration
                        p.x += sin(timeSeconds * 4f + p.phase) * 0.0007f
                        p.z += cos(timeSeconds * 3.5f + p.phase) * 0.0007f
                    }
                    3 -> {
                        // Smoke expands and drifts
                        p.scale += 0.0035f
                        p.vy *= 0.998f
                        p.vx += sin(timeSeconds * 0.6f) * 0.00004f
                    }
                    4 -> {
                        // Embers drift with gravity deceleration
                        p.vy -= 0.00015f
                        p.scale *= 0.998f
                        p.x += sin(timeSeconds * 5f + p.phase) * 0.001f
                    }
                    5 -> {
                        // Extinguisher agent plume expands rapidly as it hits base
                        p.scale += 0.006f
                        p.vx *= 0.96f
                        p.vy *= 0.96f
                        p.vz *= 0.96f
                    }
                }
            }
        }
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (program == 0 || vertexBuffer == null) return

        glEnable(GL_BLEND)
        glDepthMask(false) // Don't write to depth buffer

        val posHandle = glGetAttribLocation(program, "a_Position")
        val mvpHandle = glGetUniformLocation(program, "u_MVP")
        val scaleHandle = glGetUniformLocation(program, "u_Scale")
        val colorHandle = glGetUniformLocation(program, "u_Color")
        val flickerHandle = glGetUniformLocation(program, "u_Flicker")
        val layerHandle = glGetUniformLocation(program, "u_Layer")

        glUseProgram(program)
        glEnableVertexAttribArray(posHandle)
        glVertexAttribPointer(posHandle, 3, GL_FLOAT, false, 0, vertexBuffer)

        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
        val modelMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)

        // Sort particles back-to-front (smoke and discharge first, then outer, mid, core flames)
        val sorted = particles.filter { it.life > 0f }.sortedByDescending { it.layer }

        for (p in sorted) {
            val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)

            // Billboard orientation: extract camera-facing rotation from view matrix
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
            modelMatrix[0] = viewMatrix[0]; modelMatrix[1] = viewMatrix[4]; modelMatrix[2] = viewMatrix[8]
            modelMatrix[4] = viewMatrix[1]; modelMatrix[5] = viewMatrix[5]; modelMatrix[6] = viewMatrix[9]
            modelMatrix[8] = viewMatrix[2]; modelMatrix[9] = viewMatrix[6]; modelMatrix[10] = viewMatrix[10]

            Matrix.multiplyMM(mvpMatrix, 0, viewProjection, 0, modelMatrix, 0)
            glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            glUniform1f(scaleHandle, p.scale)
            glUniform1i(layerHandle, p.layer)
            glUniform1f(flickerHandle, timeSeconds + p.phase)

            // Color based on layer and life
            val r: Float; val g: Float; val b: Float; val a: Float
            when (p.layer) {
                0 -> {
                    // Core: bright white-yellow → yellow
                    r = 1.0f; g = 0.95f - (1f - lifeRatio) * 0.25f; b = 0.45f * lifeRatio
                    a = p.alpha * lifeRatio
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive for brilliant inner core
                }
                1 -> {
                    // Mid: bright orange → dark orange
                    r = 1.0f; g = 0.58f + lifeRatio * 0.20f; b = 0.06f + lifeRatio * 0.10f
                    a = p.alpha * lifeRatio.coerceAtMost(0.9f)
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive
                }
                2 -> {
                    // Outer: red-orange → deep red
                    r = 0.88f + lifeRatio * 0.12f; g = 0.18f + lifeRatio * 0.20f; b = 0.03f
                    a = p.alpha * lifeRatio * 0.85f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
                3 -> {
                    // Smoke: dark soot or chemical steam
                    r = 0.32f; g = 0.32f; b = 0.35f
                    a = p.alpha * lifeRatio * 0.65f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
                4 -> {
                    // Embers: bright orange-white spark
                    r = 1.0f; g = 0.75f; b = 0.25f
                    a = p.alpha * lifeRatio
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive for spark glow
                }
                5 -> {
                    // Extinguisher agent discharge: white/gray dry chemical powder cloud
                    r = 0.92f; g = 0.94f; b = 0.96f
                    a = p.alpha * lifeRatio * 0.75f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
                else -> {
                    r = 1f; g = 1f; b = 1f; a = 0.5f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
            }

            glUniform4f(colorHandle, r, g, b, a.coerceIn(0f, 1f))
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        glDisableVertexAttribArray(posHandle)
        glDepthMask(true)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_BLEND)
    }

    private fun compileShader(type: Int, source: String): Int {
        return glCreateShader(type).also {
            glShaderSource(it, source)
            glCompileShader(it)
        }
    }
}
