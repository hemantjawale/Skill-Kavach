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
    private var installRequested = false
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private val started = SystemClock.elapsedRealtime()
    private var previousDuration = 0
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
                val steps = module.items("steps")
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
                                        (i % 3 - 1) * .48f,
                                        (i / 3) * -.48f,
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
                        )
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = Color.White) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                TextButton(onClick = { finish() }) { Text("Close training") }
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
                        Surface(color = Color.White) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!practice) {
                                    Text(status)
                                    Text("Red ring = keep scanning. Green ring = ready to place. Virtual red equipment and green exit appear after placement.", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { renderer?.placeEquipment() }, enabled = session != null) { Text("Place equipment") }
                                        OutlinedButton(onClick = { renderer?.reposition() }, enabled = session != null) { Text("Reposition") }
                                    }
                                }
                                if (step < steps.size) {
                                    val current = steps[step]
                                    Text(
                                        current.getString("title"),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(current.getString("instruction"))
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
                                                previousDuration +
                                                    ((SystemClock.elapsedRealtime() - started) /
                                                            1000)
                                                        .toInt()
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
                            }
                        }
                    }
                }
            }
        }
    }

    private fun select(target: String) {
        val steps = module.items("steps")
        if (step >= steps.size) return
        if (target == steps[step].getString("target")) {
            step++
            feedback = "Correct"
            val completedStep = step
            lifecycleScope.launch {
                runCatching {
                    repo.savePractice(
                        module.getString("id"),
                        completedStep,
                        if (practice) "PRACTICE" else "AR",
                        previousDuration +
                            ((SystemClock.elapsedRealtime() - started) / 1000).toInt(),
                    )
                }
                    .onFailure {
                        feedback = "Progress could not be saved. Keep this screen open and retry."
                    }
            }
        } else feedback = "Try again. Follow the current instruction before choosing equipment."
    }

    private fun resumeAr() {
        if (practice) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CAMERA)
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
                "AR is unavailable on this device. Check Google Play Services for AR or use practice mode."
        }
    }

    override fun onResume() {
        super.onResume()
        resumeAr()
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
