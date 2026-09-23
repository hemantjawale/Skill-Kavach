package com.example.skilkavach.ar

import android.opengl.GLES20.*
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin
import kotlin.math.cos

data class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0.005f,
    var vz: Float = 0f,
    var scale: Float = 0.1f,
    var alpha: Float = 0.8f,
    var life: Float = 1.0f,
    var maxLife: Float = 2.0f,
    var phase: Float = 0f
)

class SmokeParticleRenderer(
    private val maxParticles: Int = 30
) {
    private val particles = Array(maxParticles) { Particle() }
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null

    // Simple quad vertices (-0.5 to 0.5)
    private val quadCoords = floatArrayOf(
        -0.5f,  0.5f, 0.0f,
        -0.5f, -0.5f, 0.0f,
         0.5f,  0.5f, 0.0f,
         0.5f, -0.5f, 0.0f
    )

    fun initialize() {
        val vertexShader = loadShader(GL_VERTEX_SHADER, """
            attribute vec3 a_Position;
            uniform mat4 u_MVP;
            uniform float u_Scale;
            void main() {
                gl_Position = u_MVP * vec4(a_Position * u_Scale, 1.0);
            }
        """.trimIndent())

        val fragmentShader = loadShader(GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """.trimIndent())

        program = glCreateProgram().also {
            glAttachShader(it, vertexShader)
            glAttachShader(it, fragmentShader)
            glLinkProgram(it)
        }

        val bb = ByteBuffer.allocateDirect(quadCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer().apply {
            put(quadCoords)
            position(0)
        }
    }

    fun update(hazardX: Float, hazardY: Float, hazardZ: Float, density: Float = 1.0f) {
        val timeSec = System.currentTimeMillis() / 1000f

        for (i in 0 until maxParticles) {
            val p = particles[i]
            if (p.life <= 0f) {
                // Respawn particle at hazard origin with slight random offsets
                p.x = hazardX + (Math.random().toFloat() - 0.5f) * 0.2f
                p.y = hazardY + 0.05f
                p.z = hazardZ + (Math.random().toFloat() - 0.5f) * 0.2f
                p.vx = (Math.random().toFloat() - 0.5f) * 0.002f
                p.vy = 0.004f + Math.random().toFloat() * 0.004f
                p.vz = (Math.random().toFloat() - 0.5f) * 0.002f
                p.scale = 0.08f + Math.random().toFloat() * 0.04f
                p.maxLife = 1.5f + Math.random().toFloat() * 1.5f
                p.life = p.maxLife
                p.phase = Math.random().toFloat() * 6.28f
            } else {
                p.life -= 0.016f // ~60fps step
                p.y += p.vy
                p.x += p.vx + sin(timeSec * 2.0f + p.phase) * 0.001f
                p.z += p.vz + cos(timeSec * 2.0f + p.phase) * 0.001f
                p.scale += 0.002f // Expand as it rises
                p.alpha = (p.life / p.maxLife) * density * 0.7f
            }
        }
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (program == 0 || vertexBuffer == null) return

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glUseProgram(program)

        val posHandle = glGetAttribLocation(program, "a_Position")
        val mvpHandle = glGetUniformLocation(program, "u_MVP")
        val scaleHandle = glGetUniformLocation(program, "u_Scale")
        val colorHandle = glGetUniformLocation(program, "u_Color")

        glEnableVertexAttribArray(posHandle)
        glVertexAttribPointer(posHandle, 3, GL_FLOAT, false, 0, vertexBuffer)

        val modelMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        for (p in particles) {
            if (p.life <= 0f) continue

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)

            // Billboard rotation: Orient quad to face camera
            modelMatrix[0] = viewMatrix[0]
            modelMatrix[1] = viewMatrix[4]
            modelMatrix[2] = viewMatrix[8]
            modelMatrix[4] = viewMatrix[1]
            modelMatrix[5] = viewMatrix[5]
            modelMatrix[6] = viewMatrix[9]
            modelMatrix[8] = viewMatrix[2]
            modelMatrix[9] = viewMatrix[6]
            modelMatrix[10] = viewMatrix[10]

            Matrix.multiplyMM(mvpMatrix, 0, viewProjection, 0, modelMatrix, 0)

            glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            glUniform1f(scaleHandle, p.scale)

            // Fire / Smoke color gradient (orange-red at base, dark grey at top)
            val isFirePhase = p.y < 0.25f
            val r = if (isFirePhase) 1.0f else 0.4f
            val g = if (isFirePhase) 0.4f else 0.4f
            val b = if (isFirePhase) 0.1f else 0.4f

            glUniform4f(colorHandle, r, g, b, p.alpha.coerceIn(0f, 1f))
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        glDisableVertexAttribArray(posHandle)
        glDisable(GL_BLEND)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return glCreateShader(type).also { shader ->
            glShaderSource(shader, shaderCode)
            glCompileShader(shader)
        }
    }
}
