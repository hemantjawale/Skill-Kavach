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

// ── Data classes (public API preserved) ─────────────────────────────────

data class Marker(
    val id: String,
    val label: String,
    val x: Float,
    val z: Float,
    val color: FloatArray,
)

data class ScreenMarker(val id: String, val label: String, val x: Float, val y: Float)

// ── Placement preview overlay (upgraded) ────────────────────────────────

class MarkerLabels(context: Context) : View(context) {
    @Volatile var markers: List<ScreenMarker> = emptyList()
    @Volatile var placementVisible = true
    @Volatile var placementReady = false
    @Volatile var highlightId: String? = null

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        // Placement preview — concentric rings with scanning animation
        if (placementVisible) {
            val cx = width / 2f; val cy = height / 2f
            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = 2.5f * density
            if (placementReady) {
                // Green ready state — solid ring + inner dot
                ringPaint.color = 0xFF1B9E5A.toInt()
                canvas.drawCircle(cx, cy, 28 * density, ringPaint)
                ringPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, 4 * density, ringPaint)
                // Outer pulsing ring
                ringPaint.style = Paint.Style.STROKE
                ringPaint.strokeWidth = 1.5f * density
                val pulse = (System.currentTimeMillis() % 1500) / 1500f
                ringPaint.alpha = ((1f - pulse) * 180).toInt()
                canvas.drawCircle(cx, cy, (28 + pulse * 18) * density, ringPaint)
            } else {
                // Red scanning state — dashed ring
                ringPaint.color = 0xFFD63D35.toInt()
                canvas.drawCircle(cx, cy, 28 * density, ringPaint)
                // Corner brackets to indicate scan area
                val half = 36 * density
                ringPaint.strokeWidth = 2f * density
                val bLen = 10 * density
                for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                    canvas.drawLine(cx + sx * half, cy + sy * half, cx + sx * half + sx * -bLen, cy + sy * half, ringPaint)
                    canvas.drawLine(cx + sx * half, cy + sy * half, cx + sx * half, cy + sy * half + sy * -bLen, ringPaint)
                }
            }
        }

        // Marker labels — pill-shaped with object type icon
        markers.forEach { m ->
            val isHighlighted = m.id == highlightId
            val text = m.label
            val tw = labelPaint.measureText(text) + 20 * density
            val th = 22 * density
            val left = m.x - tw / 2; val top = m.y - th
            val right = m.x + tw / 2; val bottom = m.y

            // Background pill
            labelPaint.color = if (isHighlighted) 0xF0FFFFFF.toInt() else 0xCC222222.toInt()
            labelPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(left, top, right, bottom, th / 2, th / 2, labelPaint)

            // Text
            labelPaint.color = if (isHighlighted) 0xFF1A1A1A.toInt() else 0xFFEEEEEE.toInt()
            labelPaint.style = Paint.Style.FILL
            canvas.drawText(text, m.x, m.y - 6 * density, labelPaint)

            // Highlight glow ring
            if (isHighlighted) {
                highlightPaint.color = 0xAA4FC3F7.toInt()
                val pulse = (System.currentTimeMillis() % 1200) / 1200f
                highlightPaint.alpha = ((0.5f + 0.5f * sin(pulse * 2f * PI.toFloat())) * 200).toInt()
                canvas.drawRoundRect(left - 2, top - 2, right + 2, bottom + 2, th / 2 + 2, th / 2 + 2, highlightPaint)
            }
        }
    }
}

// ── Fire suppression state ──────────────────────────────────────────────

enum class FireState {
    ACTIVE,       // Burning — full flames + smoke
    SUPPRESSING,  // Extinguisher active — flames shrinking
    EXTINGUISHED  // Fire out — residual smoke only
}

// ── Scene element with world-space positioning ──────────────────────────

data class SceneElement(
    val id: String,
    val label: String,
    val model: IndustrialMeshes.CompositeModel,
    val x: Float,       // Offset from anchor in meters
    val y: Float,       // Height above ground
    val z: Float,
    val scale: Float = 1f,
    val rotationY: Float = 0f  // Degrees
)

// ── Main renderer ───────────────────────────────────────────────────────

/**
 * Production-grade ARCore renderer for the Fire & Explosion training module.
 *
 * Features:
 * - PBR-approximate lighting (Blinn-Phong + metallic/roughness)
 * - ARCore environment light estimation
 * - Contact shadows on detected planes
 * - Multi-layer fire/smoke particle effects with state transitions
 * - Spatial industrial scene layout
 * - Localized marker labels
 * - Depth-aware rendering where supported
 *
 * Scale: 1 unit = 1 meter
 */
class TrainingRenderer(
    private val session: () -> Session?,
    private val rotation: () -> Int,
    val labels: MarkerLabels,
    private val markers: List<Marker>,
    private val onStatus: (String) -> Unit,
    private val onHit: (String) -> Unit,
    val moduleId: String = "fire",
) : GLSurfaceView.Renderer {

    @Volatile private var resetRequested = false
    @Volatile private var placeRequested = false
    @Volatile private var autoPlaceRequested = false
    @Volatile var fireState: FireState = FireState.ACTIVE
        private set
    @Volatile var alarmActive: Boolean = false
        private set
    @Volatile var isPinPulled: Boolean = false
        private set

    fun reposition() { resetRequested = true }
    fun placeEquipment() { placeRequested = true }
    fun autoPlaceInFront() { autoPlaceRequested = true }
    fun pullPin() {
        if (!isPinPulled) {
            isPinPulled = true
            buildScene()
        }
    }
    fun suppressFire() { if (fireState == FireState.ACTIVE) fireState = FireState.SUPPRESSING }
    fun extinguishFire() { fireState = FireState.EXTINGUISHED }
    fun triggerAlarm() { alarmActive = true }
    fun resetAlarm() { alarmActive = false }

    // GL state
    private var cameraProgram = 0
    private var pbrProgram = 0
    private var shadowProgram = 0
    private var fireProgram = 0
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

    // Lighting — updated from ARCore each frame
    private var ambientIntensity = 0.6f
    private val ambientColor = floatArrayOf(1f, 1f, 1f)
    private val lightDir = floatArrayOf(0.3f, 1f, 0.5f)
    private val lightColor = floatArrayOf(1f, 0.97f, 0.92f)

    // Hazard effects
    private val fireEffect = FireEffectRenderer(maxParticles = 85)
    private val gasEffect = GasEffectRenderer(maxParticles = 75)
    val diffusionSimulator = HazardDiffusionSimulator(width = 10, height = 10)
    private var suppressionProgress = 0f  // 0 = full fire, 1 = fully extinguished

    // Shadow disc mesh
    private lateinit var shadowMesh: IndustrialMeshes.MeshData

    // Scene elements — built from markers
    private lateinit var sceneElements: List<SceneElement>

    // ── Shader sources ──────────────────────────────────────────────

    private val cameraVertSrc = """
        attribute vec2 p; attribute vec2 uv; varying vec2 t;
        void main() { gl_Position = vec4(p, 0., 1.); t = uv; }
    """.trimIndent()

    private val cameraFragSrc = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES camera; varying vec2 t;
        void main() { gl_FragColor = texture2D(camera, t); }
    """.trimIndent()

    /** PBR-approximate vertex shader with view-space position and normal. */
    private val pbrVertSrc = """
        attribute vec3 a_Position;
        attribute vec3 a_Normal;
        uniform mat4 u_MVP;
        uniform mat4 u_MV;
        uniform mat3 u_NormalMat;
        varying vec3 v_Normal;
        varying vec3 v_ViewPos;
        varying float v_Height;
        void main() {
            gl_Position = u_MVP * vec4(a_Position, 1.0);
            v_Normal = normalize(u_NormalMat * a_Normal);
            v_ViewPos = (u_MV * vec4(a_Position, 1.0)).xyz;
            v_Height = a_Position.y;
        }
    """.trimIndent()

    /** PBR-approximate fragment shader: Blinn-Phong + metallic/roughness. */
    private val pbrFragSrc = """
        precision mediump float;
        uniform vec4 u_BaseColor;
        uniform float u_Metallic;
        uniform float u_Roughness;
        uniform float u_Emissive;
        uniform vec3 u_LightDir;
        uniform vec3 u_LightColor;
        uniform float u_AmbientIntensity;
        uniform vec3 u_AmbientColor;
        uniform float u_FireGlow;
        varying vec3 v_Normal;
        varying vec3 v_ViewPos;
        varying float v_Height;

        void main() {
            vec3 N = normalize(v_Normal);
            vec3 L = normalize(u_LightDir);
            vec3 V = normalize(-v_ViewPos);
            vec3 H = normalize(L + V);

            // Fresnel approximation (Schlick)
            float F0 = mix(0.04, 0.9, u_Metallic);
            float VdotH = max(dot(V, H), 0.0);
            float fresnel = F0 + (1.0 - F0) * pow(1.0 - VdotH, 5.0);

            // Diffuse — reduced by metallic
            float NdotL = max(dot(N, L), 0.0);
            vec3 diffuse = u_BaseColor.rgb * (1.0 - u_Metallic) * NdotL * u_LightColor;

            // Specular — Blinn-Phong with roughness-controlled exponent
            float specPower = max(2.0, pow(2.0, (1.0 - u_Roughness) * 10.0));
            float NdotH = max(dot(N, H), 0.0);
            float specular = pow(NdotH, specPower) * fresnel;
            vec3 specColor = mix(vec3(1.0), u_BaseColor.rgb, u_Metallic) * specular * u_LightColor;

            // Ambient with slight directional bias
            float ambBias = 0.5 + 0.5 * max(dot(N, vec3(0.0, 1.0, 0.0)), 0.0);
            vec3 ambient = u_BaseColor.rgb * u_AmbientIntensity * u_AmbientColor * ambBias;

            // Fire glow — warm orange contribution
            vec3 fireContrib = vec3(1.0, 0.5, 0.15) * u_FireGlow * max(0.0, 1.0 - v_Height * 3.0);

            // Emissive self-illumination
            vec3 emissive = u_BaseColor.rgb * u_Emissive;

            vec3 color = ambient + diffuse + specColor + emissive + fireContrib;

            // Tone mapping (simple Reinhard)
            color = color / (color + vec3(1.0));

            gl_FragColor = vec4(color, u_BaseColor.a);
        }
    """.trimIndent()

    /** Contact shadow shader — simple dark disc with soft edge falloff. */
    private val shadowVertSrc = """
        attribute vec3 a_Position;
        uniform mat4 u_MVP;
        varying vec2 v_UV;
        void main() {
            gl_Position = u_MVP * vec4(a_Position, 1.0);
            v_UV = a_Position.xz;
        }
    """.trimIndent()

    private val shadowFragSrc = """
        precision mediump float;
        uniform float u_Radius;
        uniform float u_Opacity;
        varying vec2 v_UV;
        void main() {
            float dist = length(v_UV) / u_Radius;
            float alpha = smoothstep(1.0, 0.3, dist) * u_Opacity;
            gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    // ── Status helper ───────────────────────────────────────────────

    private fun status(s: String) {
        if (s != lastStatus) { lastStatus = s; onStatus(s) }
    }

    fun tap(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) touchDown = event.x to event.y
        if (event.action == MotionEvent.ACTION_UP) {
            val start = touchDown
            swept = start != null && abs(event.x - start.first) > 60 * labels.resources.displayMetrics.density
            tap.set(event.x to event.y)
            touchDown = null
        }
    }

    fun release() {
        anchor?.detach()
        anchor = null
    }

    // ── GL lifecycle ────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Compile shader programs
        cameraProgram = compileProgram(cameraVertSrc, cameraFragSrc)
        pbrProgram = compileProgram(pbrVertSrc, pbrFragSrc)
        shadowProgram = compileProgram(shadowVertSrc, shadowFragSrc)

        // Camera texture
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        texture = ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        // Initialize hazard effects
        if (moduleId == "gas") {
            gasEffect.initialize()
        } else {
            fireEffect.initialize()
        }

        // Build shadow mesh
        shadowMesh = IndustrialMeshes.contactShadow(1f) // Unit radius, scaled per object

        // Build scene elements from markers
        buildScene()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        glViewport(0, 0, w, h)
    }

    // ── Build spatial scene layout ──────────────────────────────────

    private fun buildScene() {
        val elements = mutableListOf<SceneElement>()

        if (moduleId == "gas") {
            /**
             * Believable Industrial Confined Space Training Scenario:
             *
             *                        EXIT MUSTER SIGN (elevated, right upwind)
             *                               |
             *      ALARM BEACON (elevated)  |  STANDBY ATTENDANT (outside boundary)
             *               \               |   /
             *             CONFINED SPACE HATCH & RESCUE TRIPOD (center-floor)
             *               /               |   \
             *     PPE INSPECTION BENCH      |  EXCLUSION BARRICADES
             *               \               |
             *             PERMIT BOARD (left, outside boundary)
             */
            for (marker in markers) {
                val model: IndustrialMeshes.CompositeModel
                val px: Float; val py: Float; val pz: Float
                val scale: Float; val rotY: Float

                when (marker.id) {
                    "hazard" -> {
                        model = IndustrialMeshes.confinedSpaceHatch()
                        px = 0.0f; py = 0.0f; pz = -1.20f; scale = 1.0f; rotY = 0f
                    }
                    "permit" -> {
                        model = IndustrialMeshes.permitClipboard()
                        px = -0.75f; py = 0.0f; pz = -0.55f; scale = 1.0f; rotY = 25f
                    }
                    "detector" -> {
                        model = IndustrialMeshes.gasDetector()
                        px = -0.52f; py = 0.73f; pz = -1.10f; scale = 1.0f; rotY = -25f
                    }
                    "ppe" -> {
                        model = IndustrialMeshes.ppeStation()
                        px = -0.70f; py = 0.0f; pz = -1.15f; scale = 1.0f; rotY = 30f
                    }
                    "buddy" -> {
                        model = IndustrialMeshes.buddyFigure()
                        px = 0.90f; py = 0.0f; pz = -0.65f; scale = 1.0f; rotY = -35f
                    }
                    "alarm" -> {
                        model = IndustrialMeshes.industrialAlarmBeacon()
                        px = -0.90f; py = 1.25f; pz = -1.20f; scale = 1.0f; rotY = 20f
                    }
                    "exit" -> {
                        model = IndustrialMeshes.evacuationMusterSign()
                        px = 1.10f; py = 1.35f; pz = 0.35f; scale = 1.1f; rotY = -140f
                    }
                    else -> {
                        model = IndustrialMeshes.CompositeModel(
                            listOf(IndustrialMeshes.ModelPart(
                                "default", IndustrialMeshes.box(0.06f, 0.08f, 0.04f),
                                IndustrialMeshes.Material(marker.color[0], marker.color[1], marker.color[2])
                            )),
                            height = 0.16f
                        )
                        px = marker.x; py = 0.12f; pz = marker.z; scale = 1f; rotY = 0f
                    }
                }
                elements += SceneElement(
                    id = marker.id, label = marker.label,
                    model = model, x = px, y = py, z = pz,
                    scale = scale, rotationY = rotY
                )
            }

            // Companion ambient industrial objects (non-interactive, empty label)
            elements += SceneElement(
                id = "tripod_companion", label = "",
                model = IndustrialMeshes.rescueTripod(),
                x = 0.0f, y = 0.0f, z = -1.20f,
                scale = 1.0f, rotationY = 0f
            )
            elements += SceneElement(
                id = "barricade_l", label = "",
                model = IndustrialMeshes.safetyBarricade(),
                x = -0.55f, y = 0.0f, z = -0.45f,
                scale = 1.0f, rotationY = 0f
            )
            elements += SceneElement(
                id = "barricade_r", label = "",
                model = IndustrialMeshes.safetyBarricade(),
                x = 0.55f, y = 0.0f, z = -0.45f,
                scale = 1.0f, rotationY = 0f
            )
            elements += SceneElement(
                id = "blower_companion", label = "",
                model = IndustrialMeshes.ventilationBlower(),
                x = 0.55f, y = 0.0f, z = -1.05f,
                scale = 1.0f, rotationY = -45f
            )
        } else {
            /**
             * Spatial arrangement for an industrial fire training scenario:
             *
             *              EXIT SIGN (elevated, far)
             *                   |
             *     ALARM (elevated, left)    SAFE ZONE
             *                   |
             *         FIRE SOURCE (center-right, floor)
             *                   |
             *     EXTINGUISHER (near-left, floor)
             */
            for (marker in markers) {
                val model: IndustrialMeshes.CompositeModel
                val px: Float; val py: Float; val pz: Float
                val scale: Float; val rotY: Float

                when (marker.id) {
                    "extinguisher" -> {
                        model = IndustrialMeshes.fireExtinguisher(isPinPulled)
                        px = -0.35f; py = 0f; pz = 0.25f; scale = 1f; rotY = 25f
                    }
                    "pin" -> {
                        model = IndustrialMeshes.CompositeModel(
                            listOf(IndustrialMeshes.ModelPart(
                                "pin_highlight", IndustrialMeshes.cylinder(0.006f, 0.04f, 12),
                                IndustrialMeshes.Material(0.85f, 0.75f, 0.15f, metallic = 0.7f, roughness = 0.25f)
                            )),
                            height = 0.04f
                        )
                        px = -0.385f; py = 0.455f; pz = 0.25f; scale = 1f; rotY = 0f
                    }
                    "handle" -> {
                        model = IndustrialMeshes.CompositeModel(
                            listOf(IndustrialMeshes.ModelPart(
                                "handle_highlight", IndustrialMeshes.box(0.05f, 0.01f, 0.015f),
                                IndustrialMeshes.Material(0.12f, 0.12f, 0.12f, metallic = 0.8f, roughness = 0.25f)
                            )),
                            height = 0.02f
                        )
                        px = -0.33f; py = 0.47f; pz = 0.25f; scale = 1f; rotY = 0f
                    }
                    "exit" -> {
                        model = IndustrialMeshes.exitSign()
                        px = 0f; py = 1.4f; pz = -0.8f; scale = 1.2f; rotY = 0f
                    }
                    "alarm" -> {
                        model = IndustrialMeshes.fireAlarm()
                        px = -0.55f; py = 1.1f; pz = -0.4f; scale = 1f; rotY = 20f
                    }
                    "base", "hazard" -> {
                        model = IndustrialMeshes.electricalCabinetFire()
                        px = 0.30f; py = 0.0f; pz = -0.35f; scale = 1f; rotY = -25f
                    }
                    "sweep" -> {
                        model = IndustrialMeshes.hazardZone()
                        px = 0.30f; py = 0.005f; pz = -0.35f; scale = 1f; rotY = 0f
                    }
                    else -> {
                        model = IndustrialMeshes.CompositeModel(
                            listOf(IndustrialMeshes.ModelPart(
                                "default", IndustrialMeshes.box(0.06f, 0.08f, 0.04f),
                                IndustrialMeshes.Material(marker.color[0], marker.color[1], marker.color[2])
                            )),
                            height = 0.16f
                        )
                        px = marker.x; py = 0.12f; pz = marker.z; scale = 1f; rotY = 0f
                    }
                }
                elements += SceneElement(
                    id = marker.id, label = marker.label,
                    model = model, x = px, y = py, z = pz,
                    scale = scale, rotationY = rotY
                )
            }
        }
        sceneElements = elements
    }

    // ── Frame rendering ─────────────────────────────────────────────

    override fun onDrawFrame(gl: GL10?) {
        glClearColor(0.08f, 0.09f, 0.11f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val s = session() ?: return

        try {
            s.setCameraTextureName(texture)
            s.setDisplayGeometry(rotation(), width, height)
            val frame = s.update()

            // Draw camera background
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, uv,
            )
            glDisable(GL_DEPTH_TEST)
            glDepthMask(false)
            glUseProgram(cameraProgram)
            setAttribute(cameraProgram, "p", quad, 2)
            setAttribute(cameraProgram, "uv", uv, 2)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            glUniform1i(glGetUniformLocation(cameraProgram, "camera"), 0)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            glDepthMask(true)
            glEnable(GL_DEPTH_TEST)

            // Handle reposition request
            if (resetRequested) {
                anchor?.detach()
                anchor = null
                resetRequested = false
                fireState = FireState.ACTIVE
                suppressionProgress = 0f
                isPinPulled = false
                buildScene()
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

            // ── Environment lighting estimation ───────────────────
            val lightEstimate = frame.lightEstimate
            if (lightEstimate.state == LightEstimate.State.VALID) {
                ambientIntensity = lightEstimate.pixelIntensity.coerceIn(0.15f, 1.5f)
                val correction = FloatArray(4)
                lightEstimate.getColorCorrection(correction, 0)
                ambientColor[0] = correction[0].coerceIn(0.3f, 1.5f)
                ambientColor[1] = correction[1].coerceIn(0.3f, 1.5f)
                ambientColor[2] = correction[2].coerceIn(0.3f, 1.5f)
            }

            // ── Placement logic ───────────────────────────────────
            val touch = if (placeRequested) {
                placeRequested = false; tap.set(null)
                width / 2f to height / 2f
            } else tap.getAndSet(null)

            if (anchor == null) {
                if (autoPlaceRequested) {
                    autoPlaceRequested = false
                    val camPose = camera.pose
                    val forward = camPose.zAxis
                    val autoPose = Pose.makeTranslation(
                        camPose.tx() - forward[0] * 1.5f,
                        camPose.ty() - forward[1] * 1.5f - 0.5f,
                        camPose.tz() - forward[2] * 1.5f
                    )
                    anchor = s.createAnchor(autoPose)
                    labels.placementVisible = false
                    status("Equipment placed. Follow the instructions below.")
                    return
                }
                labels.placementReady = frame.hitTest(width / 2f, height / 2f).any { hit ->
                    val plane = hit.trackable
                    plane is Plane && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(hit.hitPose)
                }
                labels.postInvalidate()
                status(
                    if (labels.placementReady) "Surface detected — tap Place Equipment."
                    else "Point at a textured floor and move slowly."
                )
                if (touch != null) {
                    anchor = frame.hitTest(touch.first, touch.second)
                        .firstOrNull { hit ->
                            val plane = hit.trackable
                            plane is Plane && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(hit.hitPose)
                        }?.createAnchor()
                }
                return
            }

            val a = anchor!!
            if (a.trackingState != TrackingState.TRACKING) {
                labels.markers = emptyList()
                labels.postInvalidate()
                status("Tracking lost. Point camera at the training area.")
                return
            }

            // Distance check
            val distance = sqrt(
                (camera.pose.tx() - a.pose.tx()).pow(2) +
                (camera.pose.tz() - a.pose.tz()).pow(2)
            )
            if (distance < 0.3f || distance > 5f) {
                labels.markers = emptyList()
                labels.postInvalidate()
                status("Move to 0.3–5m from the training scene.")
                return
            }

            status("Follow the training steps below.")

            // Camera matrices
            camera.getProjectionMatrix(projection, 0, 0.05f, 100f)
            camera.getViewMatrix(view, 0)

            val anchorWorld = FloatArray(16)
            a.pose.toMatrix(anchorWorld, 0)

            // ── Update fire state ─────────────────────────────────
            updateFireState()

            // ── Render scene elements ─────────────────────────────
            val projected = mutableListOf<ScreenMarker>()

            for (elem in sceneElements) {
                val model = FloatArray(16)
                Matrix.setIdentityM(model, 0)
                Matrix.multiplyMM(model, 0, anchorWorld, 0, model, 0)
                Matrix.translateM(model, 0, elem.x, elem.y, elem.z)
                if (elem.rotationY != 0f) {
                    Matrix.rotateM(model, 0, elem.rotationY, 0f, 1f, 0f)
                }
                if (elem.scale != 1f) {
                    Matrix.scaleM(model, 0, elem.scale, elem.scale, elem.scale)
                }

                // Compute glow contribution (fire warmth or alarm strobe pulse)
                val glow = when {
                    moduleId == "gas" && alarmActive && elem.id == "alarm" -> {
                        val pulse = (sin(System.currentTimeMillis() * 0.015) * 0.5 + 0.5).toFloat()
                        pulse * 0.9f
                    }
                    moduleId == "fire" && fireState != FireState.EXTINGUISHED -> {
                        val fireX = 0.30f; val fireZ = -0.35f
                        val dx = elem.x - fireX; val dz = elem.z - fireZ
                        val dist = sqrt(dx * dx + dz * dz)
                        val now = System.currentTimeMillis()
                        val flicker = 0.82f + 0.18f * (sin(now * 0.018) * 0.6 + cos(now * 0.031) * 0.4).toFloat()
                        (1f - suppressionProgress) * flicker * (0.35f / (dist + 0.3f)).coerceAtMost(0.45f)
                    }
                    else -> 0f
                }

                // Draw each part with PBR shader
                drawCompositeModel(elem.model, model, glow)

                // Contact shadow for floor-level objects
                if (elem.y < 0.1f && elem.id !in listOf("sweep")) {
                    val shadowRadius = when (elem.id) {
                        "tripod_companion" -> 0.60f
                        "ppe" -> 0.32f
                        "buddy" -> 0.22f
                        "permit" -> 0.20f
                        "base", "hazard" -> 0.42f
                        "extinguisher" -> 0.18f
                        else -> (elem.model.height * elem.scale * 0.55f).coerceIn(0.12f, 0.45f)
                    }
                    drawContactShadow(anchorWorld, elem.x, elem.z, shadowRadius)
                }

                // Project label position to screen (only for interactive elements with labels)
                if (elem.label.isNotEmpty()) {
                    val labelWorldPos = FloatArray(16)
                    Matrix.setIdentityM(labelWorldPos, 0)
                    Matrix.multiplyMM(labelWorldPos, 0, anchorWorld, 0, labelWorldPos, 0)
                    Matrix.translateM(labelWorldPos, 0, elem.x, elem.y + elem.model.height * elem.scale + 0.06f, elem.z)
                    val mv = FloatArray(16)
                    val mvp = FloatArray(16)
                    Matrix.multiplyMM(mv, 0, view, 0, labelWorldPos, 0)
                    Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
                    val clip = FloatArray(4)
                    Matrix.multiplyMV(clip, 0, mvp, 0, floatArrayOf(0f, 0f, 0f, 1f), 0)
                    if (clip[3] > 0 && abs(clip[0] / clip[3]) < 1 && abs(clip[1] / clip[3]) < 1) {
                        projected += ScreenMarker(
                            elem.id, elem.label,
                            (clip[0] / clip[3] + 1) * width / 2,
                            (1 - clip[1] / clip[3]) * height / 2,
                        )
                    }
                }
            }

            // ── Render hazard dispersion effects (Gas or Fire) ───
            if (moduleId == "gas") {
                diffusionSimulator.step()
                val sourceWorldX = a.pose.tx() + 0f
                val sourceWorldY = a.pose.ty() + 0f
                val sourceWorldZ = a.pose.tz() + (-1.20f)

                val liveConcentration = diffusionSimulator.getConcentrationAt(sourceWorldX, sourceWorldY, sourceWorldZ)
                gasEffect.update(sourceWorldX, sourceWorldY, sourceWorldZ, concentration = liveConcentration)
                gasEffect.draw(view, projection, sourceWorldX, sourceWorldY, sourceWorldZ, concentration = liveConcentration)
            } else {
                if (fireState != FireState.EXTINGUISHED || suppressionProgress < 1f) {
                    diffusionSimulator.step()
                    // Faulted conduit / breaker fire origin on the electrical cabinet
                    val fireWorldX = a.pose.tx() + 0.40f
                    val fireWorldY = a.pose.ty() + 0.62f
                    val fireWorldZ = a.pose.tz() + (-0.33f)

                    // Extinguisher nozzle world position
                    val nozzleWorldX = a.pose.tx() - 0.305f
                    val nozzleWorldY = a.pose.ty() + 0.22f
                    val nozzleWorldZ = a.pose.tz() + 0.25f

                    val isDischarging = fireState == FireState.SUPPRESSING

                    fireEffect.update(
                        fireWorldX, fireWorldY, fireWorldZ,
                        fireIntensity = 1f - suppressionProgress,
                        smokeIntensity = if (isDischarging) 1.6f else 0.5f + suppressionProgress * 0.6f,
                        isDischarging = isDischarging,
                        nozzleX = nozzleWorldX,
                        nozzleY = nozzleWorldY,
                        nozzleZ = nozzleWorldZ
                    )
                    fireEffect.draw(view, projection)
                }
            }

            labels.markers = projected
            labels.postInvalidate()

            // ── Hit test on touch ─────────────────────────────────
            if (touch != null) {
                val hit = projected.minByOrNull { hypot(it.x - touch.first, it.y - touch.second) }
                if (hit != null && hypot(hit.x - touch.first, hit.y - touch.second) < 52 * labels.resources.displayMetrics.density) {
                    if (hit.id != "sweep" || swept) onHit(hit.id)
                    else status("Drag sideways across the Sweep marker.")
                }
            }

        } catch (_: com.google.ar.core.exceptions.CameraNotAvailableException) {
            status("Camera unavailable. Close and retry.")
        } catch (_: com.google.ar.core.exceptions.SessionPausedException) {}
    }

    // ── Fire state update ───────────────────────────────────────────

    private fun updateFireState() {
        when (fireState) {
            FireState.ACTIVE -> suppressionProgress = 0f
            FireState.SUPPRESSING -> {
                suppressionProgress = (suppressionProgress + 0.008f).coerceAtMost(1f)
                if (suppressionProgress >= 1f) fireState = FireState.EXTINGUISHED
            }
            FireState.EXTINGUISHED -> suppressionProgress = 1f
        }
    }

    // ── Draw composite model with PBR shader ────────────────────────

    private fun drawCompositeModel(
        model: IndustrialMeshes.CompositeModel,
        worldMatrix: FloatArray,
        fireGlow: Float = 0f
    ) {
        glUseProgram(pbrProgram)

        // Set lighting uniforms
        glUniform3f(glGetUniformLocation(pbrProgram, "u_LightDir"), lightDir[0], lightDir[1], lightDir[2])
        glUniform3f(glGetUniformLocation(pbrProgram, "u_LightColor"), lightColor[0], lightColor[1], lightColor[2])
        glUniform1f(glGetUniformLocation(pbrProgram, "u_AmbientIntensity"), ambientIntensity)
        glUniform3f(glGetUniformLocation(pbrProgram, "u_AmbientColor"), ambientColor[0], ambientColor[1], ambientColor[2])

        for (part in model.parts) {
            val partModel = worldMatrix.copyOf()
            if (part.scaleX != 1f || part.scaleY != 1f || part.scaleZ != 1f) {
                Matrix.scaleM(partModel, 0, part.scaleX, part.scaleY, part.scaleZ)
            }

            // Model-View matrix
            val mv = FloatArray(16)
            Matrix.multiplyMM(mv, 0, view, 0, partModel, 0)

            // Model-View-Projection matrix
            val mvp = FloatArray(16)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

            // Normal matrix (upper-left 3×3 of MV, transposed inverse — approximated for uniform scale)
            val normalMat = floatArrayOf(
                mv[0], mv[1], mv[2],
                mv[4], mv[5], mv[6],
                mv[8], mv[9], mv[10]
            )

            glUniformMatrix4fv(glGetUniformLocation(pbrProgram, "u_MVP"), 1, false, mvp, 0)
            glUniformMatrix4fv(glGetUniformLocation(pbrProgram, "u_MV"), 1, false, mv, 0)
            glUniformMatrix3fv(glGetUniformLocation(pbrProgram, "u_NormalMat"), 1, false, normalMat, 0)

            // Material uniforms
            val mat = part.material
            if (mat.alpha < 1f) {
                glEnable(GL_BLEND)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            }
            glUniform4f(glGetUniformLocation(pbrProgram, "u_BaseColor"), mat.r, mat.g, mat.b, mat.alpha)
            glUniform1f(glGetUniformLocation(pbrProgram, "u_Metallic"), mat.metallic)
            glUniform1f(glGetUniformLocation(pbrProgram, "u_Roughness"), mat.roughness)
            glUniform1f(glGetUniformLocation(pbrProgram, "u_Emissive"), mat.emissive)
            glUniform1f(glGetUniformLocation(pbrProgram, "u_FireGlow"), fireGlow)

            // Vertex data
            part.mesh.positions.position(0)
            part.mesh.normals.position(0)
            val posLoc = glGetAttribLocation(pbrProgram, "a_Position")
            val normLoc = glGetAttribLocation(pbrProgram, "a_Normal")
            glEnableVertexAttribArray(posLoc)
            glEnableVertexAttribArray(normLoc)
            glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 0, part.mesh.positions)
            glVertexAttribPointer(normLoc, 3, GL_FLOAT, false, 0, part.mesh.normals)

            glDrawArrays(GL_TRIANGLES, 0, part.mesh.vertexCount)

            glDisableVertexAttribArray(posLoc)
            glDisableVertexAttribArray(normLoc)
            if (mat.alpha < 1f) glDisable(GL_BLEND)
        }
    }

    // ── Contact shadow ──────────────────────────────────────────────

    private fun drawContactShadow(anchorWorld: FloatArray, x: Float, z: Float, radius: Float) {
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(false)
        glUseProgram(shadowProgram)

        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(model, 0, anchorWorld, 0, model, 0)
        Matrix.translateM(model, 0, x, 0.002f, z) // Slightly above floor to avoid z-fighting
        Matrix.scaleM(model, 0, radius, 1f, radius)

        val mv = FloatArray(16)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

        glUniformMatrix4fv(glGetUniformLocation(shadowProgram, "u_MVP"), 1, false, mvp, 0)
        glUniform1f(glGetUniformLocation(shadowProgram, "u_Radius"), 1f) // Normalized, actual size via scale
        glUniform1f(glGetUniformLocation(shadowProgram, "u_Opacity"), 0.45f * ambientIntensity.coerceAtMost(1f))

        shadowMesh.positions.position(0)
        val posLoc = glGetAttribLocation(shadowProgram, "a_Position")
        glEnableVertexAttribArray(posLoc)
        glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 0, shadowMesh.positions)
        glDrawArrays(GL_TRIANGLES, 0, shadowMesh.vertexCount)
        glDisableVertexAttribArray(posLoc)

        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    // ── Shader utilities ────────────────────────────────────────────

    private fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        fun shader(type: Int, source: String): Int {
            val s = glCreateShader(type)
            glShaderSource(s, source)
            glCompileShader(s)
            val ok = IntArray(1)
            glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(s) }
            return s
        }
        val v = shader(GL_VERTEX_SHADER, vertexSrc)
        val f = shader(GL_FRAGMENT_SHADER, fragmentSrc)
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

    private fun setAttribute(program: Int, name: String, data: FloatBuffer, size: Int) {
        data.position(0)
        val at = glGetAttribLocation(program, name)
        glEnableVertexAttribArray(at)
        glVertexAttribPointer(at, size, GL_FLOAT, false, 0, data)
    }

    companion object {
        fun floats(v: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(v); position(0) }
    }
}
