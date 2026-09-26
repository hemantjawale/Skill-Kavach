package com.example.skilkavach.ar

import android.opengl.GLES20.*
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Realistic Educational Gas Dispersion & Hazard Boundary AR Renderer.
 *
 * Designed to visualize invisible gas behavior for confined space safety training:
 * 1. Volumetric vapor dispersion:
 *    - Soft, low-opacity particles (alpha ~0.06 to 0.20)
 *    - Fluid-like aerodynamic expansion and buoyant upward/horizontal drift
 *    - Micro-turbulence using Perlin-like sinusoids
 *    - Natural density dissipation over distance and time
 *    - NEVER looks like a solid cartoon blob — mimics real atmospheric vapor dispersion
 *
 * 2. Multi-Zone Hazard Floor Rings (Training Overlay):
 *    - Zone A (Critical / IDLH): ~0.45m radius — immediate danger
 *    - Zone B (High Hazard): ~0.85m radius — exceeds permissible exposure limit
 *    - Zone C (Moderate Warning): ~1.40m radius — action level
 *    - Zone D (Safe Perimeter): ~1.90m radius — safe attendant watch line
 *
 * Scale: 1 unit = 1 meter
 */
class GasEffectRenderer(private val maxParticles: Int = 80) {

    private data class GasParticle(
        var x: Float = 0f,
        var y: Float = 0f,
        var z: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var vz: Float = 0f,
        var scale: Float = 0.08f,
        var alpha: Float = 0.15f,
        var life: Float = 0f,
        var maxLife: Float = 2.5f,
        var densityTier: Int = 0 // 0=dense near source, 1=diffusing, 2=ambient drift
    )

    private val particles = Array(maxParticles) { GasParticle() }
    private var particleProgram = 0
    private var zoneProgram = 0
    private var quadBuffer: FloatBuffer? = null
    private var timeSeconds = 0f

    // Unit quad for billboard particles
    private val quadVerts = floatArrayOf(
        -0.5f,  0.5f, 0f,
        -0.5f, -0.5f, 0f,
         0.5f,  0.5f, 0f,
         0.5f, -0.5f, 0f,
    )

    // ── Particle Shaders ─────────────────────────────────────────────

    private val particleVertSrc = """
        attribute vec3 a_Position;
        uniform mat4 u_MVP;
        uniform float u_Scale;
        varying vec2 v_UV;
        void main() {
            gl_Position = u_MVP * vec4(a_Position * u_Scale, 1.0);
            v_UV = a_Position.xy + 0.5;
        }
    """.trimIndent()

    private val particleFragSrc = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform float u_Turbulence;
        varying vec2 v_UV;
        void main() {
            vec2 center = v_UV - 0.5;
            float dist = length(center);

            // Subtle organic edge turbulence
            float turb = sin(dist * 18.0 + u_Turbulence * 4.0) * 0.04;
            float softEdge = smoothstep(0.5 + turb, 0.05, dist);

            // Realistic low-opacity vapor gradient
            float alpha = softEdge * u_Color.a;
            gl_FragColor = vec4(u_Color.rgb, alpha);
        }
    """.trimIndent()

    // ── Hazard Zone Floor Ring Shaders ───────────────────────────────

    private val zoneVertSrc = """
        attribute vec3 a_Position;
        uniform mat4 u_MVP;
        varying vec2 v_UV;
        void main() {
            gl_Position = u_MVP * vec4(a_Position, 1.0);
            v_UV = a_Position.xz;
        }
    """.trimIndent()

    private val zoneFragSrc = """
        precision mediump float;
        uniform vec4 u_ZoneColor;
        uniform float u_InnerRadius;
        uniform float u_OuterRadius;
        uniform float u_Pulse;
        varying vec2 v_UV;
        void main() {
            float dist = length(v_UV);
            if (dist < u_InnerRadius || dist > u_OuterRadius) {
                discard;
            }

            // Soft ring band with dashed edge pattern
            float ringMid = (u_InnerRadius + u_OuterRadius) * 0.5;
            float ringWidth = (u_OuterRadius - u_InnerRadius) * 0.5;
            float edgeDist = abs(dist - ringMid) / ringWidth;
            float alpha = smoothstep(1.0, 0.2, edgeDist) * u_ZoneColor.a * (0.8 + 0.2 * u_Pulse);

            gl_FragColor = vec4(u_ZoneColor.rgb, alpha);
        }
    """.trimIndent()

    // Zone disc meshes
    private lateinit var zoneAMesh: IndustrialMeshes.MeshData
    private lateinit var zoneBMesh: IndustrialMeshes.MeshData
    private lateinit var zoneCMesh: IndustrialMeshes.MeshData
    private lateinit var zoneDMesh: IndustrialMeshes.MeshData

    fun initialize() {
        val pv = compileShader(GL_VERTEX_SHADER, particleVertSrc)
        val pf = compileShader(GL_FRAGMENT_SHADER, particleFragSrc)
        particleProgram = glCreateProgram().also {
            glAttachShader(it, pv); glAttachShader(it, pf); glLinkProgram(it)
        }

        val zv = compileShader(GL_VERTEX_SHADER, zoneVertSrc)
        val zf = compileShader(GL_FRAGMENT_SHADER, zoneFragSrc)
        zoneProgram = glCreateProgram().also {
            glAttachShader(it, zv); glAttachShader(it, zf); glLinkProgram(it)
        }

        quadBuffer = ByteBuffer.allocateDirect(quadVerts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(quadVerts); position(0) }

        // Build flat concentric discs for hazard zones
        zoneAMesh = IndustrialMeshes.disc(0.48f, 0.40f, 48) // Zone A rim
        zoneBMesh = IndustrialMeshes.disc(0.88f, 0.82f, 48) // Zone B rim
        zoneCMesh = IndustrialMeshes.disc(1.42f, 1.36f, 48) // Zone C rim
        zoneDMesh = IndustrialMeshes.disc(1.92f, 1.86f, 48) // Zone D perimeter

        // Initialize particles with staggered lifespans
        for (i in 0 until maxParticles) {
            particles[i].life = (i.toFloat() / maxParticles) * 2.5f
            particles[i].maxLife = 2.0f + (i % 5) * 0.3f
            particles[i].densityTier = when {
                i < maxParticles * 0.35 -> 0 // Near hatch collar
                i < maxParticles * 0.70 -> 1 // Mid dispersion
                else -> 2                    // Ambient plume
            }
        }
    }

    /**
     * Updates gas particles emanating from the confined space hatch.
     */
    fun update(
        sourceX: Float, sourceY: Float, sourceZ: Float,
        concentration: Float = 0.8f,
        windX: Float = 0.10f, windZ: Float = 0.05f
    ) {
        timeSeconds = (System.currentTimeMillis() % 100000) / 1000f
        val dt = 0.016f

        for (p in particles) {
            p.life -= dt
            if (p.life <= 0f) {
                // Respawn at confined space opening with subtle random jitter
                val angle = Math.random().toFloat() * 2f * PI.toFloat()
                val radius = (Math.random().toFloat() * 0.28f)

                p.x = sourceX + cos(angle) * radius
                p.y = sourceY + (Math.random().toFloat() * 0.08f) // Near mouth of hatch
                p.z = sourceZ + sin(angle) * radius

                // Velocity: buoyant gentle rise + radial spreading
                val spreadSpeed = 0.06f + Math.random().toFloat() * 0.08f
                p.vx = cos(angle) * spreadSpeed + windX * 0.3f
                p.vy = 0.04f + Math.random().toFloat() * 0.08f // Soft rise
                p.vz = sin(angle) * spreadSpeed + windZ * 0.3f

                p.maxLife = 2.2f + Math.random().toFloat() * 1.2f
                p.life = p.maxLife
                p.scale = 0.10f + Math.random().toFloat() * 0.06f
                p.alpha = (0.12f + Math.random().toFloat() * 0.08f) * concentration.coerceIn(0.2f, 1f)
            } else {
                // Physics step: expansion, drag, turbulence
                val ageFrac = 1f - (p.life / p.maxLife)

                // Aerodynamic turbulence
                val turbX = sin(timeSeconds * 2.5f + p.y * 8f) * 0.015f
                val turbZ = cos(timeSeconds * 2.5f + p.x * 8f) * 0.015f

                p.x += (p.vx + turbX) * dt
                p.y += p.vy * dt
                p.z += (p.vz + turbZ) * dt

                // Particles expand as they diffuse into the air
                p.scale += 0.08f * dt

                // Alpha fades smoothly as particle dissipates
                val fade = sin(ageFrac * PI.toFloat()).coerceIn(0f, 1f)
                p.alpha = (0.16f * fade * concentration).coerceIn(0f, 0.25f)
            }
        }
    }

    /**
     * Renders gas dispersion particles and hazard zone floor rings.
     */
    fun draw(
        view: FloatArray, projection: FloatArray,
        sourceX: Float, sourceY: Float, sourceZ: Float,
        concentration: Float = 0.8f
    ) {
        val quad = quadBuffer ?: return
        val pulse = (sin(timeSeconds * 3.0f) * 0.5f + 0.5f)

        // ── 1. Draw Multi-Zone Hazard Floor Rings (Training Overlay) ──
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(false)
        glUseProgram(zoneProgram)

        fun drawFloorRing(mesh: IndustrialMeshes.MeshData, color: FloatArray, innerR: Float, outerR: Float) {
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, sourceX, sourceY + 0.005f, sourceZ)

            val mv = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

            glUniformMatrix4fv(glGetUniformLocation(zoneProgram, "u_MVP"), 1, false, mvp, 0)
            glUniform4f(glGetUniformLocation(zoneProgram, "u_ZoneColor"), color[0], color[1], color[2], color[3])
            glUniform1f(glGetUniformLocation(zoneProgram, "u_InnerRadius"), innerR)
            glUniform1f(glGetUniformLocation(zoneProgram, "u_OuterRadius"), outerR)
            glUniform1f(glGetUniformLocation(zoneProgram, "u_Pulse"), pulse)

            mesh.positions.position(0)
            val posLoc = glGetAttribLocation(zoneProgram, "a_Position")
            glEnableVertexAttribArray(posLoc)
            glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 0, mesh.positions)
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount)
            glDisableVertexAttribArray(posLoc)
        }

        // Zone A: Critical IDLH — Industrial Red (0.85, 0.15, 0.10)
        drawFloorRing(zoneAMesh, floatArrayOf(0.88f, 0.16f, 0.12f, 0.65f), 0.40f, 0.48f)

        // Zone B: High Hazard — Warning Amber (0.92, 0.55, 0.08)
        drawFloorRing(zoneBMesh, floatArrayOf(0.92f, 0.55f, 0.08f, 0.55f), 0.82f, 0.88f)

        // Zone C: Moderate — Caution Yellow (0.85, 0.78, 0.12)
        drawFloorRing(zoneCMesh, floatArrayOf(0.85f, 0.78f, 0.12f, 0.45f), 1.36f, 1.42f)

        // Zone D: Safe Perimeter — Safety Green/Cyan (0.20, 0.65, 0.45)
        drawFloorRing(zoneDMesh, floatArrayOf(0.20f, 0.65f, 0.45f, 0.40f), 1.86f, 1.92f)

        // ── 2. Draw Realistic Volumetric Gas Particles ─────────────────
        glUseProgram(particleProgram)
        quad.position(0)
        val posLoc = glGetAttribLocation(particleProgram, "a_Position")
        glEnableVertexAttribArray(posLoc)
        glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 0, quad)

        // Sort back-to-front from camera
        val camX = -view[12]; val camY = -view[13]; val camZ = -view[14]
        val sorted = particles.filter { it.alpha > 0.005f }
            .sortedByDescending { (it.x - camX).pow(2) + (it.y - camY).pow(2) + (it.z - camZ).pow(2) }

        for (p in sorted) {
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, p.x, p.y, p.z)

            // Billboard: cancel out camera rotation
            model[0] = view[0]; model[1] = view[4]; model[2] = view[8]
            model[4] = view[1]; model[5] = view[5]; model[6] = view[9]
            model[8] = view[2]; model[9] = view[6]; model[10] = view[10]

            val mv = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

            glUniformMatrix4fv(glGetUniformLocation(particleProgram, "u_MVP"), 1, false, mvp, 0)
            glUniform1f(glGetUniformLocation(particleProgram, "u_Scale"), p.scale)
            glUniform1f(glGetUniformLocation(particleProgram, "u_Turbulence"), timeSeconds)

            // Subtle industrial gas tint: faint vapor mist with slight amber/ash tone
            val r = 0.88f; val g = 0.86f; val b = 0.80f
            glUniform4f(glGetUniformLocation(particleProgram, "u_Color"), r, g, b, p.alpha)

            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        glDisableVertexAttribArray(posLoc)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    private fun compileShader(type: Int, source: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, source)
        glCompileShader(s)
        val ok = IntArray(1)
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { glGetShaderInfoLog(s) }
        return s
    }
}
