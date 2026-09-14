package com.example.skilkavach.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.View
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class Marker(
    val id: String,
    val label: String,
    val x: Float,
    val z: Float,
    val color: FloatArray,
)

data class ScreenMarker(val id: String, val label: String, val x: Float, val y: Float)

class MarkerLabels(context: Context) : View(context) {
    @Volatile var markers: List<ScreenMarker> = emptyList()
    @Volatile var placementVisible = true
    @Volatile var placementReady = false
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

    override fun onDraw(canvas: Canvas) {
        if (placementVisible) {
            val density = resources.displayMetrics.density
            paint.color = if (placementReady) 0xFF16834A.toInt() else 0xFFD63D35.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4 * density
            canvas.drawCircle(width / 2f, height / 2f, 22 * density, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width / 2f, height / 2f, 4 * density, paint)
        }
        markers.forEach { m ->
            val width = paint.measureText(m.label) + 24
            paint.color = 0xEEFFFFFF.toInt()
            canvas.drawRoundRect(
                m.x - width / 2,
                m.y - 24,
                m.x + width / 2,
                m.y + 12,
                8f,
                8f,
                paint,
            )
            paint.color = 0xFF20242C.toInt()
            canvas.drawText(m.label, m.x, m.y, paint)
        }
    }
}

/** Native ARCore renderer: camera texture, plane hit tests and world-anchored procedural meshes. */
class TrainingRenderer(
    private val session: () -> Session?,
    private val rotation: () -> Int,
    private val labels: MarkerLabels,
    private val markers: List<Marker>,
    private val onStatus: (String) -> Unit,
    private val onHit: (String) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile private var resetRequested = false
    @Volatile private var placeRequested = false
    fun reposition() { resetRequested = true }
    fun placeEquipment() { placeRequested = true }

    private var cameraProgram = 0
    private var objectProgram = 0
    private var texture = 0
    private var width = 1
    private var height = 1
    private var anchor: Anchor? = null
    private val tap = AtomicReference<Pair<Float, Float>?>(null)
    private val quad = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv = floats(FloatArray(8))
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private var lastStatus = ""
    private var touchDown: Pair<Float, Float>? = null
    @Volatile private var swept = false

    private fun status(s: String) {
        if (s != lastStatus) {
            lastStatus = s
            onStatus(s)
        }
    }

    fun tap(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) touchDown = event.x to event.y
        if (event.action == MotionEvent.ACTION_UP) {
            val start = touchDown
            swept =
                start != null &&
                    abs(event.x - start.first) > 60 * labels.resources.displayMetrics.density
            tap.set(event.x to event.y)
            touchDown = null
        }
    }

    fun release() {
        anchor?.detach()
        anchor = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraProgram =
            program(
                "attribute vec2 p; attribute vec2 uv; varying vec2 t; void main(){gl_Position=vec4(p,0.,1.);t=uv;}",
                "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 t; void main(){gl_FragColor=texture2D(camera,t);}",
            )
        objectProgram =
            program(
                "attribute vec3 p; attribute vec3 n; uniform mat4 mvp; varying float light; void main(){gl_Position=mvp*vec4(p,1.);light=.5+.5*max(dot(normalize(n),normalize(vec3(.4,1.,.6))),0.);}",
                "precision mediump float; uniform vec4 color; varying float light; void main(){gl_FragColor=vec4(color.rgb*light,color.a);}",
            )
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        texture = ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        glClearColor(0.12f, 0.14f, 0.17f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val s = session() ?: return
        try {
            s.setCameraTextureName(texture)
            s.setDisplayGeometry(rotation(), width, height)
            val frame = s.update()
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quad,
                Coordinates2d.TEXTURE_NORMALIZED,
                uv,
            )
            glDisable(GL_DEPTH_TEST)
            glDepthMask(false)
            glUseProgram(cameraProgram)
            attribute(cameraProgram, "p", quad, 2)
            attribute(cameraProgram, "uv", uv, 2)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            glUniform1i(glGetUniformLocation(cameraProgram, "camera"), 0)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            glDepthMask(true)
            glEnable(GL_DEPTH_TEST)
            if (resetRequested) {
                anchor?.detach()
                anchor = null
                resetRequested = false
            }
            labels.placementVisible = anchor == null
            labels.placementReady = false
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                labels.markers = emptyList()
                labels.postInvalidate()
                status("Tracking paused. Move slowly in a well-lit area.")
                tap.set(null)
                return
            }
            val touch = if (placeRequested) {
                placeRequested = false
                tap.set(null)
                width / 2f to height / 2f
            } else tap.getAndSet(null)
            if (anchor == null) {
                labels.placementReady = frame.hitTest(width / 2f, height / 2f).any { hit ->
                    val plane = hit.trackable
                    plane is Plane && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(hit.hitPose)
                }
                labels.postInvalidate()
                status(if (labels.placementReady) "Green ring: floor detected. Tap Place equipment." else "Red ring: point at a textured floor and move slowly until it turns green.")
                if (touch != null)
                    anchor =
                        frame
                            .hitTest(touch.first, touch.second)
                            .firstOrNull { hit ->
                                val plane = hit.trackable
                                plane is Plane &&
                                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                    plane.isPoseInPolygon(hit.hitPose)
                            }
                            ?.createAnchor()
                return
            }
            val a = anchor!!
            if (a.trackingState != TrackingState.TRACKING) {
                labels.markers = emptyList()
                labels.postInvalidate()
                status("Equipment tracking lost. Point the camera back at the training area.")
                return
            }
            val distance =
                sqrt(
                    (camera.pose.tx() - a.pose.tx()).pow(2) +
                        (camera.pose.tz() - a.pose.tz()).pow(2)
                )
            if (distance < 0.4f || distance > 4f) {
                labels.markers = emptyList()
                labels.postInvalidate()
                status("Keep the phone 0.4–4 m from the virtual equipment.")
                return
            }
            status("Equipment placed. Follow the instruction and tap the labelled object.")
            camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
            camera.getViewMatrix(view, 0)
            val world = FloatArray(16)
            a.pose.toMatrix(world, 0)
            val projected = mutableListOf<ScreenMarker>()
            markers.forEach { marker ->
                val model = world.copyOf()
                Matrix.translateM(model, 0, marker.x, 0.12f, marker.z)
                val mv = FloatArray(16)
                val mvp = FloatArray(16)
                Matrix.multiplyMM(mv, 0, view, 0, model, 0)
                Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
                drawMesh(marker, mvp)
                val clip = FloatArray(4)
                Matrix.multiplyMV(clip, 0, mvp, 0, floatArrayOf(0f, 0.24f, 0f, 1f), 0)
                if (clip[3] > 0 && abs(clip[0] / clip[3]) < 1 && abs(clip[1] / clip[3]) < 1)
                    projected +=
                        ScreenMarker(
                            marker.id,
                            marker.label,
                            (clip[0] / clip[3] + 1) * width / 2,
                            (1 - clip[1] / clip[3]) * height / 2,
                        )
            }
            labels.markers = projected
            labels.postInvalidate()
            if (touch != null) {
                val hit = projected.minByOrNull { hypot(it.x - touch.first, it.y - touch.second) }
                if (
                    hit != null &&
                        hypot(hit.x - touch.first, hit.y - touch.second) <
                            48 * labels.resources.displayMetrics.density
                ) {
                    if (hit.id != "sweep" || swept) onHit(hit.id)
                    else status("Drag sideways across the Sweep marker to practice the sweep.")
                }
            }
        } catch (_: com.google.ar.core.exceptions.CameraNotAvailableException) {
            status("Camera unavailable. Close training and try again.")
        } catch (_: com.google.ar.core.exceptions.SessionPausedException) {}
    }

    private val cube =
        floats(
            floatArrayOf(
                -.10f,
                0f,
                .10f,
                .10f,
                0f,
                .10f,
                .10f,
                .20f,
                .10f,
                -.10f,
                0f,
                .10f,
                .10f,
                .20f,
                .10f,
                -.10f,
                .20f,
                .10f,
                -.10f,
                0f,
                -.10f,
                -.10f,
                .20f,
                -.10f,
                .10f,
                .20f,
                -.10f,
                -.10f,
                0f,
                -.10f,
                .10f,
                .20f,
                -.10f,
                .10f,
                0f,
                -.10f,
                -.10f,
                .20f,
                -.10f,
                -.10f,
                .20f,
                .10f,
                .10f,
                .20f,
                .10f,
                -.10f,
                .20f,
                -.10f,
                .10f,
                .20f,
                .10f,
                .10f,
                .20f,
                -.10f,
                -.10f,
                0f,
                -.10f,
                -.10f,
                0f,
                .10f,
                -.10f,
                .20f,
                .10f,
                -.10f,
                0f,
                -.10f,
                -.10f,
                .20f,
                .10f,
                -.10f,
                .20f,
                -.10f,
                .10f,
                0f,
                -.10f,
                .10f,
                .20f,
                -.10f,
                .10f,
                .20f,
                .10f,
                .10f,
                0f,
                -.10f,
                .10f,
                .20f,
                .10f,
                .10f,
                0f,
                .10f,
            )
        )
    private val cylinder =
        floats(
            buildList<Float> {
                    for (i in 0 until 24) {
                        val a = i * 2 * PI / 24
                        val b = (i + 1) * 2 * PI / 24
                        val x = cos(a).toFloat() * .08f
                        val z = sin(a).toFloat() * .08f
                        val x2 = cos(b).toFloat() * .08f
                        val z2 = sin(b).toFloat() * .08f
                        addAll(
                            listOf(
                                x,
                                0f,
                                z,
                                x2,
                                0f,
                                z2,
                                x2,
                                .28f,
                                z2,
                                x,
                                0f,
                                z,
                                x2,
                                .28f,
                                z2,
                                x,
                                .28f,
                                z,
                                0f,
                                .28f,
                                0f,
                                x,
                                .28f,
                                z,
                                x2,
                                .28f,
                                z2,
                            )
                        )
                    }
                }
                .toFloatArray()
        )
    private val cubeNormals by lazy { normals(cube) }
    private val cylinderNormals by lazy { normals(cylinder) }

    private fun normals(mesh: FloatBuffer): FloatBuffer {
        val out = FloatArray(mesh.capacity())
        for (i in 0 until mesh.capacity() step 9) {
            val ax = mesh[i + 3] - mesh[i]
            val ay = mesh[i + 4] - mesh[i + 1]
            val az = mesh[i + 5] - mesh[i + 2]
            val bx = mesh[i + 6] - mesh[i]
            val by = mesh[i + 7] - mesh[i + 1]
            val bz = mesh[i + 8] - mesh[i + 2]
            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx
            for (v in 0..2) {
                out[i + v * 3] = nx
                out[i + v * 3 + 1] = ny
                out[i + v * 3 + 2] = nz
            }
        }
        return floats(out)
    }

    private fun drawMesh(m: Marker, matrix: FloatArray) {
        val black = floatArrayOf(.12f, .14f, .17f, 1f)
        val yellow = floatArrayOf(.95f, .7f, .12f, 1f)
        val white = floatArrayOf(.94f, .96f, .98f, 1f)
        fun part(
            mesh: FloatBuffer,
            color: FloatArray,
            x: Float = 0f,
            y: Float = 0f,
            z: Float = 0f,
            sx: Float = 1f,
            sy: Float = 1f,
            sz: Float = 1f,
        ) {
            val transform = matrix.copyOf()
            Matrix.translateM(transform, 0, x, y, z)
            Matrix.scaleM(transform, 0, sx, sy, sz)
            glUseProgram(objectProgram)
            attribute(objectProgram, "p", mesh, 3)
            attribute(objectProgram, "n", if (mesh === cube) cubeNormals else cylinderNormals, 3)
            glUniformMatrix4fv(glGetUniformLocation(objectProgram, "mvp"), 1, false, transform, 0)
            glUniform4fv(glGetUniformLocation(objectProgram, "color"), 1, color, 0)
            glDrawArrays(GL_TRIANGLES, 0, mesh.capacity() / 3)
        }
        when (m.id) {
            "extinguisher" -> {
                part(cylinder, m.color)
                part(cube, white, y = .09f, z = .073f, sx = .55f, sy = .5f, sz = .1f)
                part(cylinder, black, y = .28f, sx = .3f, sy = .2f, sz = .3f)
                part(cube, black, x = .04f, y = .32f, sx = .8f, sy = .12f, sz = .2f)
                part(cube, black, x = .12f, y = .12f, sx = .15f, sy = 1f, sz = .2f)
            }
            "detector" -> {
                part(cube, yellow, sx = .8f, sy = 1.1f, sz = .5f)
                part(cube, black, y = .10f, z = .05f, sx = .6f, sy = .45f, sz = .08f)
                part(cube, white, x = -.03f, y = .03f, z = .05f, sx = .12f, sy = .12f, sz = .08f)
            }
            "exit" -> {
                part(cube, m.color, sx = 1.3f, sy = 1.1f, sz = .15f)
                part(cube, white, y = .08f, z = .02f, sx = .9f, sy = .16f, sz = .06f)
                part(cube, white, x = .055f, y = .04f, z = .02f, sx = .16f, sy = .55f, sz = .06f)
            }
            "permit" -> {
                part(cube, white, sx = 1f, sy = 1.4f, sz = .08f)
                for (i in 0..3) part(
                    cube,
                    black,
                    y = .03f + i * .055f,
                    z = .011f,
                    sx = .75f,
                    sy = .045f,
                    sz = .02f,
                )
            }
            "pin" -> {
                part(cylinder, yellow, sx = .2f, sy = .8f, sz = .2f)
                part(cube, yellow, y = .19f, sx = .55f, sy = .15f, sz = .1f)
            }
            "handle" -> {
                part(cube, black, sx = 1.3f, sy = .18f, sz = .25f)
                part(cube, m.color, y = .07f, sx = 1.3f, sy = .16f, sz = .25f)
            }
            "buddy" -> {
                part(cylinder, m.color, sx = .55f, sy = .55f, sz = .55f)
                part(cylinder, white, y = .17f, sx = .45f, sy = .3f, sz = .45f)
            }
            "base",
            "hazard",
            "sweep" -> {
                part(cylinder, yellow, sx = 1.8f, sy = .07f, sz = 1.8f)
                part(cylinder, m.color, y = .02f, sx = .7f, sy = .6f, sz = .7f)
            }
            else -> part(cube, m.color)
        }
    }

    private fun attribute(program: Int, name: String, data: FloatBuffer, size: Int) {
        data.position(0)
        val at = glGetAttribLocation(program, name)
        glEnableVertexAttribArray(at)
        glVertexAttribPointer(at, size, GL_FLOAT, false, 0, data)
    }

    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val s = glCreateShader(type)
            glShaderSource(s, source)
            glCompileShader(s)
            val ok = IntArray(1)
            glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(s) }
            return s
        }
        val v = shader(GL_VERTEX_SHADER, vertex)
        val f = shader(GL_FRAGMENT_SHADER, fragment)
        val p = glCreateProgram()
        glAttachShader(p, v)
        glAttachShader(p, f)
        glLinkProgram(p)
        val ok = IntArray(1)
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { glGetProgramInfoLog(p) }
        glDeleteShader(v)
        glDeleteShader(f)
        return p
    }

    companion object {
        fun floats(v: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(v)
                    position(0)
                }
    }
}
