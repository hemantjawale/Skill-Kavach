package com.example.skilkavach.ar

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.skilkavach.SafetyApplication
import com.example.skilkavach.data.items
import com.google.ar.core.*
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ArTrainingActivity : ComponentActivity() {
    @Volatile private var session: Session? = null
    private var glView: GLSurfaceView? = null
    private var renderer: TrainingRenderer? = null
    private var status by mutableStateOf("Preparing camera…")
    private var feedback by mutableStateOf("")
    private var step by mutableIntStateOf(0)
    private var practice by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var showDemo by mutableStateOf(true)
    private var fullScreen by mutableStateOf(false)
    private var equipmentPlaced by mutableStateOf(false)
    private var actionAnimating = false
    private var installRequested = false
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private val started = SystemClock.elapsedRealtime()
    private var previousDuration = 0
    private var demoOpenedAt = started
    private var demoMillis = 0L
    private fun closeDemo() {
        demoMillis += SystemClock.elapsedRealtime() - demoOpenedAt
        showDemo = false
    }
    private fun openDemo() {
        demoOpenedAt = SystemClock.elapsedRealtime()
        showDemo = true
    }
    private fun trainingElapsedSeconds(): Int {
        val now = SystemClock.elapsedRealtime()
        val tutorial = demoMillis + if (showDemo) now - demoOpenedAt else 0L
        return previousDuration + ((now - started - tutorial).coerceAtLeast(0L) / 1000).toInt()
    }
    private lateinit var module: JSONObject
    private val repo
        get() = (application as SafetyApplication).repository

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) resumeAr()
            else {
                status = "Camera permission is needed for AR. You can use practice mode."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDemo = savedInstanceState?.getBoolean("showDemo") ?: true
        fullScreen = savedInstanceState?.getBoolean("fullScreen") ?: false
        module =
            repo.modules.firstOrNull { it.getString("id") == intent.getStringExtra("moduleId") }
                ?: run {
                    finish()
                    return
                }
        practice = intent.getBooleanExtra("practice", false)
        lifecycleScope.launch {
            runCatching {
                repo.initialize()
                repo.loadPractice(module.getString("id"))?.let { saved ->
                    if (saved.optString("mode") == if (practice) "PRACTICE" else "AR") {
                        step = saved.optInt("step").coerceIn(0, module.items("steps").size)
                        previousDuration = saved.optInt("duration").coerceIn(0, 7200)
                    }
                }
            }
        }
        speech =
            TextToSpeech(this) { code ->
                speechReady = code == TextToSpeech.SUCCESS
                if (speechReady) speech?.language = Locale.ENGLISH
            }
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(primary = Color(0xFF586DAF), background = Color(0xFFF7F8FA))
            ) {
                if (showDemo) ArInteractionDemo { closeDemo() }
                val steps = module.items("steps")
                LaunchedEffect(fullScreen, practice) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (fullScreen && !practice) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                }
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).systemBarsPadding()) {
                    if (!practice)
                        AndroidView(
                            factory = { ctx ->
                                val labels = MarkerLabels(ctx)
                                val targets = steps.map { it.getString("target") }.distinct()
                                val markers = targets.mapIndexed { i, target ->
                                    Marker(
                                        target,
                                        target.replaceFirstChar { it.uppercase() },
                                        (i % 3 - 1) * .60f,
                                        (i / 3) * -.60f,
                                        if (target == "exit") floatArrayOf(.18f, .55f, .3f, 1f)
                                        else if (target in listOf("hazard", "base", "extinguisher"))
                                            floatArrayOf(.8f, .22f, .15f, 1f)
                                        else floatArrayOf(.35f, .45f, .7f, 1f),
                                    )
                                }
                                val r =
                                    TrainingRenderer(
                                        { session },
                                        { windowManager.defaultDisplay.rotation },
                                        labels,
                                        markers,
                                        { s -> runOnUiThread { status = s } },
                                        { target -> runOnUiThread { select(target) } },
                                        { placed -> runOnUiThread { equipmentPlaced = placed } },
                                    )
                                renderer = r
                                val surface =
                                    GLSurfaceView(ctx).apply {
                                        setEGLContextClientVersion(2)
                                        preserveEGLContextOnPause = true
                                        setRenderer(r)
                                        setOnTouchListener { _, event ->
                                            r.tap(event)
                                            true
                                        }
                                    }
                                glView = surface
                                FrameLayout(ctx).apply {
                                    addView(surface)
                                    addView(labels)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = {
                                renderer?.setActiveTarget(steps.getOrNull(step)?.getString("target"))
                                renderer?.restorePinState(steps.take(step).any { it.getString("target") == "pin" })
                            },
                        )
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = Color.White) {
                            Column(Modifier.fillMaxWidth().padding(if (fullScreen && !practice) 4.dp else 16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { finish() }) { Text("Close training") }
                                    if (!practice) TextButton(onClick = { fullScreen = !fullScreen }) {
                                        Text(if (fullScreen) "Exit fullscreen" else "Fullscreen")
                                    }
                                    OutlinedButton(onClick = { openDemo() }) { Text("Demo") }
                                }
                                if (!fullScreen || practice) {
                                Text(
                                    module.getString("title"),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (practice) "Practice mode • no AR tracking"
                                    else "AR simulation • training area only"
                                )
                                Text("Step ${minOf(step+1,steps.size)} of ${steps.size}")
                                LinearProgressIndicator(
                                    progress = { step.toFloat() / steps.size },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                }
                            }
                        }
                        Surface(color = Color.White) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(max = if (fullScreen && !practice) 180.dp else 340.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!practice) {
                                    if (!equipmentPlaced) {
                                    Text(status, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { renderer?.placeEquipment() }, enabled = session != null, modifier = Modifier.weight(1f)) { Text("Place equipment") }
                                        OutlinedButton(onClick = { renderer?.autoPlaceInFront() }, enabled = session != null, modifier = Modifier.weight(1f)) { Text("Auto-place • 2.5 m") }
                                    }
                                    } else {
                                        if (!status.startsWith("Equipment placed")) Text(status, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (step < steps.size) {
                                    val current = steps[step]
                                    Text(
                                        "Step ${step + 1}: ${current.getString("title")}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(current.getString("instruction"))
                                    if (!practice && equipmentPlaced) Text(
                                        if (current.getString("target") == "sweep") "Now: drag sideways and finish on the green Sweep label."
                                        else "Now: tap the green ${current.getString("target").replaceFirstChar { it.uppercase() }} label above the object.",
                                        color = Color(0xFF08774B),
                                    )
                                    TextButton(
                                        onClick = {
                                            if (speechReady)
                                                speech?.speak(
                                                    current.getString("instruction"),
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "step",
                                                )
                                            else
                                                feedback =
                                                    "Voice is unavailable. Read the instruction above."
                                        }
                                    ) {
                                        Text("Hear instruction")
                                    }
                                    if (practice)
                                        steps
                                            .map { it.getString("target") }
                                            .distinct()
                                            .chunked(3)
                                            .forEach { row ->
                                                Row(
                                                    horizontalArrangement =
                                                        Arrangement.spacedBy(4.dp)
                                                ) {
                                                    row.forEach { target ->
                                                        OutlinedButton(
                                                            onClick = { select(target) },
                                                            modifier = Modifier.weight(1f),
                                                        ) {
                                                            Text(
                                                                target.replaceFirstChar {
                                                                    it.uppercase()
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                    if (!practice && session == null)
                                        OutlinedButton(onClick = { practice = true }) {
                                            Text("Use practice mode")
                                        }
                                } else {
                                    Text(
                                        "Training sequence complete",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        "Assessment and practical review are required before certification."
                                    )
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            val duration =
                                                trainingElapsedSeconds()
                                            if (duration < 30) {
                                                feedback =
                                                    "Take time to review the procedure. Minimum practice duration is 30 seconds."
                                                return@Button
                                            }
                                            if (repo.state.value.snapshot == null) {
                                                finish()
                                                return@Button
                                            }
                                            busy = true
                                            lifecycleScope.launch {
                                                try {
                                                    repo.act(
                                                        "training.complete",
                                                        JSONObject()
                                                            .put("moduleId", module.getString("id"))
                                                            .put("version", 1)
                                                            .put(
                                                                "steps",
                                                                JSONArray(
                                                                    steps.map {
                                                                        it.getString("target")
                                                                    }
                                                                ),
                                                            )
                                                            .put(
                                                                "durationSeconds",
                                                                duration.coerceAtMost(7200),
                                                            )
                                                            .put(
                                                                "mode",
                                                                if (practice) "PRACTICE" else "AR",
                                                            ),
                                                    )
                                                    repo.savePractice(
                                                        module.getString("id"),
                                                        0,
                                                        if (practice) "PRACTICE" else "AR",
                                                        0,
                                                    )
                                                    finish()
                                                } catch (e: Exception) {
                                                    feedback = e.message ?: "Unable to save. Retry."
                                                    busy = false
                                                }
                                            }
                                        },
                                    ) {
                                        Text(if (busy) "Saving…" else "Save training")
                                    }
                                }
                                if (feedback.isNotEmpty()) Text(feedback, color = Color(0xFF586DAF))
                                if (!practice && equipmentPlaced) TextButton(onClick = { renderer?.reposition() }) { Text("Reposition equipment") }
                            }
                        }
                    }
                }
            }
        }
    }

    private val assessmentEngine = com.example.skilkavach.data.AssessmentEngine()

    private fun triggerHaptic(durationMs: Long = 50L) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    private fun select(target: String, animationFinished: Boolean = false) {
        if (!animationFinished && (showDemo || actionAnimating)) return
        val steps = module.items("steps")
        if (step >= steps.size) return
        val currentStep = steps[step]
        val expectedTarget = currentStep.getString("target")

        if (target == expectedTarget) {
            if (target == "pin" && !practice && !animationFinished) {
                actionAnimating = true
                feedback = "Pulling the safety pin — watch the ring slide out of the valve."
                renderer?.animatePinPull()
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1400)
                    actionAnimating = false
                    select(target, animationFinished = true)
                }
                return
            }
            triggerHaptic(if (target == "sweep") 150L else 50L)
            assessmentEngine.logAction(stepIndex = step, target = target, actionType = "TAP", isCorrect = true)
            step++
            feedback = "Correct ✓"
            val completedStep = step
            if (speechReady) {
                speech?.speak(
                    "Correct. " + if (step < steps.size) steps[step].getString("instruction") else "Training sequence complete.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "step_feedback"
                )
            }
            lifecycleScope.launch {
                runCatching {
                    repo.savePractice(
                        module.getString("id"),
                        completedStep,
                        if (practice) "PRACTICE" else "AR",
                        trainingElapsedSeconds(),
                    )
                }
                    .onFailure {
                        feedback = "Progress could not be saved. Keep this screen open and retry."
                    }
            }
        } else {
            assessmentEngine.logAction(stepIndex = step, target = target, actionType = "TAP", isCorrect = false)
            triggerHaptic(200L)
            val currentTitle = currentStep.getString("title")
            feedback = "Incorrect target. Step ${step + 1}: $currentTitle. Please tap the $expectedTarget marker."
        }
    }

    private fun resumeAr() {
        if (practice) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CAMERA)
            return
        }
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            status = "ARCore is not supported on this device hardware. Switched to Practice Mode."
            practice = true
            return
        }
        try {
            if (session == null) {
                if (
                    ArCoreApk.getInstance().requestInstall(this, !installRequested) ==
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {
                    installRequested = true
                    return
                }
                session =
                    Session(this).apply {
                        configure(
                            Config(this).apply {
                                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            }
                        )
                    }
            }
            session?.resume()
            glView?.onResume()
        } catch (_: Exception) {
            status =
                "AR Services unavailable. Switched to Practice Mode."
            practice = true
        }
    }

    override fun onResume() {
        super.onResume()
        resumeAr()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("showDemo", showDemo)
        outState.putBoolean("fullScreen", fullScreen)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        glView?.onPause()
        session?.pause()
        speech?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        renderer?.release()
        session?.close()
        speech?.shutdown()
        super.onDestroy()
    }
}
