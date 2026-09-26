package com.example.skilkavach.ar

import android.opengl.GLES20.*
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Multi-layer fire and smoke particle renderer for realistic AR fire simulation.
 *
 * Visual layers:
 * 1. Inner flame core — bright yellow/white, small, fast flicker
 * 2. Middle flame — orange, moderate size, turbulent
 * 3. Outer flame — red/dark, large, slow drift
 * 4. Smoke — dark grey, rising, expanding, fading
 * 5. Embers — tiny bright orange sparks
 *
 * Supports state transitions: ACTIVE → SUPPRESSING → EXTINGUISHED
 * All rendering uses billboard-oriented textured quads with alpha blending.
 */
class FireEffectRenderer(private val maxParticles: Int = 60) {

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
        var layer: Int = 0  // 0=core, 1=mid, 2=outer, 3=smoke, 4=ember
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

    // ── Shader source: soft circle with radial gradient ─────────

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

    /** Fragment shader creates a soft radial circle with color gradient.
     *  Avoids the flat-rectangle look of the original implementation. */
    private val fragmentShaderSrc = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform float u_Flicker;
        uniform int u_Layer;
        varying vec2 v_UV;
        void main() {
            vec2 center = v_UV - 0.5;
            float dist = length(center);

            // Soft circle falloff
            float alpha;
            if (u_Layer == 4) {
                // Embers: tiny sharp dot
                alpha = smoothstep(0.5, 0.15, dist);
            } else if (u_Layer == 3) {
                // Smoke: very soft blob
                alpha = smoothstep(0.5, 0.0, dist) * 0.6;
            } else {
                // Fire: medium soft with turbulent edge
                float turbulence = sin(dist * 20.0 + u_Flicker * 6.28) * 0.05;
                alpha = smoothstep(0.5 + turbulence, 0.1, dist);
            }

            // Apply particle alpha and flicker
            float flickerMod = 1.0;
            if (u_Layer < 3) {
                flickerMod = 0.7 + 0.3 * sin(u_Flicker * 12.56 + dist * 8.0);
            }

            gl_FragColor = vec4(u_Color.rgb * flickerMod, u_Color.a * alpha);
        }
    """.trimIndent()

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
                i < maxParticles * 0.15 -> 0  // Core (15%)
                i < maxParticles * 0.35 -> 1  // Mid (20%)
                i < maxParticles * 0.55 -> 2  // Outer (20%)
                i < maxParticles * 0.85 -> 3  // Smoke (30%)
                else -> 4                      // Embers (15%)
            }
            particles[i].life = 0f // Will respawn on first update
        }
    }

    /**
     * Update all particles based on fire and smoke intensity.
     * @param fireIntensity 1.0 = full fire, 0.0 = extinguished
     * @param smokeIntensity Higher during suppression
     */
    fun update(
        hazardX: Float, hazardY: Float, hazardZ: Float,
        fireIntensity: Float = 1f,
        smokeIntensity: Float = 0.5f
    ) {
        timeSeconds = (System.currentTimeMillis() % 100000) / 1000f
        val dt = 0.016f // ~60fps

        for (p in particles) {
            if (p.life <= 0f) {
                // Respawn logic based on layer and intensity
                val shouldSpawn = when (p.layer) {
                    0, 1, 2 -> fireIntensity > 0.05f && Math.random() < fireIntensity
                    3 -> smokeIntensity > 0.05f
                    4 -> fireIntensity > 0.2f && Math.random() < fireIntensity * 0.6
                    else -> false
                }
                if (!shouldSpawn) continue

                val spreadRadius = when (p.layer) {
                    0 -> 0.04f   // Core: tight cluster
                    1 -> 0.08f   // Mid: moderate spread
                    2 -> 0.12f   // Outer: wider
                    3 -> 0.15f   // Smoke: wide
                    4 -> 0.06f   // Embers: moderate
                    else -> 0.1f
                }

                p.x = hazardX + (Math.random().toFloat() - 0.5f) * spreadRadius
                p.y = hazardY + 0.01f + Math.random().toFloat() * 0.03f
                p.z = hazardZ + (Math.random().toFloat() - 0.5f) * spreadRadius
                p.phase = Math.random().toFloat() * 6.28f

                when (p.layer) {
                    0 -> { // Core flame
                        p.vy = 0.008f + Math.random().toFloat() * 0.006f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.scale = 0.04f + Math.random().toFloat() * 0.02f
                        p.maxLife = 0.4f + Math.random().toFloat() * 0.3f
                        p.alpha = 0.9f * fireIntensity
                    }
                    1 -> { // Mid flame
                        p.vy = 0.006f + Math.random().toFloat() * 0.005f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.003f
                        p.scale = 0.06f + Math.random().toFloat() * 0.04f
                        p.maxLife = 0.5f + Math.random().toFloat() * 0.4f
                        p.alpha = 0.8f * fireIntensity
                    }
                    2 -> { // Outer flame
                        p.vy = 0.004f + Math.random().toFloat() * 0.004f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.004f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.004f
                        p.scale = 0.08f + Math.random().toFloat() * 0.06f
                        p.maxLife = 0.6f + Math.random().toFloat() * 0.5f
                        p.alpha = 0.6f * fireIntensity
                    }
                    3 -> { // Smoke
                        p.vy = 0.003f + Math.random().toFloat() * 0.004f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.002f
                        p.scale = 0.10f + Math.random().toFloat() * 0.06f
                        p.maxLife = 2.0f + Math.random().toFloat() * 1.5f
                        p.alpha = 0.35f * smokeIntensity.coerceAtMost(1f)
                    }
                    4 -> { // Embers
                        p.vy = 0.010f + Math.random().toFloat() * 0.010f
                        p.vx = (Math.random().toFloat() - 0.5f) * 0.008f
                        p.vz = (Math.random().toFloat() - 0.5f) * 0.008f
                        p.scale = 0.01f + Math.random().toFloat() * 0.008f
                        p.maxLife = 1.0f + Math.random().toFloat() * 0.8f
                        p.alpha = 1.0f * fireIntensity
                    }
                }
                p.life = p.maxLife

            } else {
                // Animate existing particle
                p.life -= dt
                p.y += p.vy
                p.x += p.vx + sin(timeSeconds * 3f + p.phase) * 0.0008f
                p.z += p.vz + cos(timeSeconds * 2.5f + p.phase) * 0.0008f

                // Layer-specific animation
                when (p.layer) {
                    0, 1, 2 -> {
                        // Flames shrink slightly and fade as they rise
                        val lifeRatio = p.life / p.maxLife
                        p.scale *= 1f + 0.003f * (1f - lifeRatio)
                        p.vy += 0.0001f // Slight acceleration upward
                    }
                    3 -> {
                        // Smoke expands and drifts
                        p.scale += 0.003f
                        p.vy *= 0.999f // Slow down gradually
                        // Wind drift
                        p.vx += sin(timeSeconds * 0.5f) * 0.00003f
                    }
                    4 -> {
                        // Embers decelerate (gravity-like)
                        p.vy -= 0.0002f
                        p.scale *= 0.998f
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

        // Sort particles back-to-front for correct blending (smoke first, then fire)
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
                    // Core: white-yellow → yellow
                    r = 1f; g = 0.9f - (1f - lifeRatio) * 0.3f; b = 0.4f * lifeRatio
                    a = p.alpha * lifeRatio
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive for inner glow
                }
                1 -> {
                    // Mid: bright orange → dark orange
                    r = 1f; g = 0.55f + lifeRatio * 0.2f; b = 0.05f + lifeRatio * 0.1f
                    a = p.alpha * lifeRatio.coerceAtMost(0.9f)
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive
                }
                2 -> {
                    // Outer: red-orange → dark red
                    r = 0.85f + lifeRatio * 0.15f; g = 0.15f + lifeRatio * 0.2f; b = 0.02f
                    a = p.alpha * lifeRatio * 0.8f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
                3 -> {
                    // Smoke: dark grey, fading
                    r = 0.25f; g = 0.25f; b = 0.28f
                    a = p.alpha * lifeRatio * 0.6f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                }
                4 -> {
                    // Embers: bright orange-white
                    r = 1f; g = 0.7f; b = 0.2f
                    a = p.alpha * lifeRatio
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE) // Additive for glow
                }
                else -> { r = 1f; g = 1f; b = 1f; a = 0.5f
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA) }
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
