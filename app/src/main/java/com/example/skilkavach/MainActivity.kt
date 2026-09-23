package com.example.skilkavach

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skilkavach.admin.AdminComplianceScreen
import com.example.skilkavach.ar.ArTrainingActivity
import com.example.skilkavach.data.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Primary = Color(0xFF586DAF)
private val Ink = Color(0xFF20242C)
private val Muted = Color(0xFF606873)
private val Border = Color(0xFFE1E4E8)
private val Danger = Color(0xFFC63D3D)
private val Success = Color(0xFF2E7D4F)
private val Background = Color(0xFFF7F8FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        val repository = (application as SafetyApplication).repository
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = Primary,
                        background = Background,
                        surface = Color.White,
                        onSurface = Ink,
                        onBackground = Ink,
                        error = Danger,
                    )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Background, contentColor = Ink) {
                    SafetyApp(repository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetyApp(repo: SafetyRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("language", 0) }
    var language by rememberSaveable { mutableStateOf(prefs.getString("value", "") ?: "") }
    var screen by rememberSaveable { mutableStateOf("Home") }
    var selected by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sos by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    val run: (suspend () -> Unit) -> Unit = { action ->
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    action()
                    error = ""
                } catch (e: Exception) {
                    error =
                        if (e is ApiFailure) e.message ?: "Please retry."
                        else "Unable to connect. Check your connection and retry."
                } finally {
                    busy = false
                }
            }
        }
    }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) run { repo.enableNotifications() }
            else error = "Notifications are disabled. Updates remain available in the app inbox."
        }
    val navigate: (String) -> Unit = {
        screen = it
        error = ""
    }
    val act: (String, JSONObject, Boolean) -> Unit = { type, payload, online ->
        run {
            repo.act(type, payload, online)
            if (!online) repo.sync()
        }
    }
    LaunchedEffect(Unit) {
        if (!state.initialized) repo.initialize()
        if (repo.state.value.snapshot != null) {
            repo.scheduleSync()
            repo.sync()
        }
    }
    BackHandler(enabled = screen != "Home") { screen = "Home" }
    fun launchTraining(moduleId: String, practice: Boolean = false) {
        context.startActivity(
            Intent(context, ArTrainingActivity::class.java)
                .putExtra("moduleId", moduleId)
                .putExtra("practice", practice)
        )
    }
    if (!state.initialized) {
        Box(Modifier.fillMaxSize().padding(32.dp)) { CircularProgressIndicator() }
        return
    }
    if (language.isEmpty()) {
        Page(systemBars = true) {
            Title(
                "Choose your language",
                "Select the language for navigation. Training content shows its available language.",
            )
            listOf(
                "en" to "English",
                "hi" to "हिन्दी",
                "sat" to "ᱥᱟᱱᱛᱟᱲᱤ (Santali - Ol Chiki)"
            ).forEach { (code, label) ->
                Action(label) {
                    language = code
                    prefs.edit().putString("value", code).apply()
                }
            }
            Info(
                "Language Support",
                "Full English, Hindi, and Santali (Ol Chiki script) localizations are active."
            )
        }
        return
    }
    if (state.storageProblem) {
        Page {
            Title("Saved data needs attention", state.message)
            Action("Retry opening saved data", !busy) { run { repo.retryInitialization() } }
        }
        return
    }
    if (state.snapshot == null || screen == "Sign in again") {
        Login(
            busy,
            error,
            { org, emp, email, done -> run { done(repo.requestCode(org, emp, email)) } },
            { org, emp, name, email, site, done -> run { done(repo.selfRegisterWorker(org, emp, name, email, site)) } },
            { challenge, code ->
                run {
                    repo.signIn(challenge, code)
                    screen = "Home"
                }
            },
            { launchTraining(it, true) },
            { language = "" },
            repo.modules,
        )
        return
    }
    val snapshot = state.snapshot!!
    val user = snapshot.getJSONObject("user")
    val records = snapshot.items("records")
    val ownRecords = records.filter { it.optString("owner_id") == user.getString("id") }
    val jobs = ownRecords.filter { it.optString("kind") == "job" }
    val certs = ownRecords.filter { it.optString("kind") == "certificate" }
    val modules = repo.modules
    val labels =
        if (language == "hi") listOf("होम", "प्रशिक्षण", "कार्य", "गतिविधि", "प्रोफ़ाइल")
        else listOf("Home", "Training", "Jobs", "Activity", "Profile")
    val tabs = listOf("Home", "Training", "Jobs", "Activity", "Profile")
    val icons =
        listOf(
            Icons.Outlined.Home,
            Icons.Outlined.School,
            Icons.Outlined.WorkOutline,
            Icons.Outlined.Assignment,
            Icons.Outlined.PersonOutline,
        )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (screen in tabs) "SurakshaSetu" else screen,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (screen !in tabs)
                        IconButton(onClick = { screen = "Home" }) {
                            Icon(Icons.Outlined.ArrowBack, "Back")
                        }
                },
                actions = {
                    IconButton(onClick = { screen = "Admin" }) {
                        Icon(Icons.Outlined.AdminPanelSettings, "Admin Portal")
                    }
                    IconButton(onClick = { screen = "Notifications" }) {
                        Icon(Icons.Outlined.NotificationsNone, "Notifications")
                    }
                    IconButton(onClick = { run { repo.sync() } }, enabled = !busy) {
                        Icon(Icons.Outlined.Sync, "Sync saved work")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = screen == tab,
                        onClick = { navigate(tab) },
                        icon = { Icon(icons[i], null) },
                        label = { Text(labels[i]) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { sos = true },
                containerColor = Danger,
                contentColor = Color.White,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Sos, null)
                    Text("SOS")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.connected || state.pending.isNotEmpty())
                Text(
                    "${state.message} • ${state.pending.size} pending",
                    Modifier.fillMaxWidth().background(Color(0xFFFFF6E5)).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            if (state.needsSignIn)
                TextButton(onClick = { screen = "Sign in again" }) { Text("Sign in again to sync") }
            if (error.isNotEmpty())
                Text(
                    error,
                    Modifier.fillMaxWidth().background(Color(0xFFFCECEC)).padding(12.dp),
                    color = Danger,
                )
            Page {
                when (screen) {
                    "Admin" -> AdminComplianceScreen(onClose = { screen = "Home" })
                    "Home" -> {
                        Title(
                            if (language == "hi") "नमस्ते, ${user.getString("name")}"
                            else "Hello, ${user.getString("name")}",
                            "${user.getString("site")} • ${user.getString("employeeId")}",
                        )
                        val valid = certs.count {
                            certificateStatus(it.getJSONObject("data")) == "VALID"
                        }
                        Info(
                            "Safety status",
                            "$valid active certifications",
                            if (valid == 0) Color(0xFFFFF6E5) else Color(0xFFEAF5EE),
                        )
                        Text(
                            "Training and current certification are required for high-risk jobs.",
                            color = Muted,
                        )
                        Section("Today's work")
                        val today = jobs.firstOrNull {
                            it.getJSONObject("data").getString("end") > Instant.now().toString()
                        }
                        if (today == null)
                            Empty("No current job", "Your assigned jobs will appear here.")
                        else
                            JobCard(today, certs) {
                                selected = today.getString("id")
                                screen = "Job details"
                            }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { screen = "Training" },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Training")
                            }
                            OutlinedButton(
                                onClick = { screen = "Certificates" },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Certificates")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { screen = "Attendance" },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Attendance")
                            }
                            OutlinedButton(
                                onClick = { screen = "Jobs" },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("My jobs")
                            }
                        }
                        Section("Required training")
                        modules
                            .filter { m ->
                                certs.none {
                                    it.getJSONObject("data").optString("moduleId") ==
                                        m.getString("id") &&
                                        certificateStatus(it.getJSONObject("data")) == "VALID"
                                }
                            }
                            .forEach { m ->
                                TrainingCard(m, ownRecords) {
                                    selected = m.getString("id")
                                    screen = "Training details"
                                }
                            }
                    }
                    "Training" -> {
                        Title(
                            "Safety training",
                            "Complete your required modules before working in high-risk zones.",
                        )
                        var filter by rememberSaveable { mutableStateOf("All") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Required", "Completed").forEach { f ->
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text(f) },
                                )
                            }
                        }
                        modules
                            .filter { m ->
                                val complete = ownRecords.any {
                                    it.optString("kind") == "progress" &&
                                        it.getJSONObject("data").optString("moduleId") ==
                                            m.getString("id")
                                }
                                filter == "All" ||
                                    (filter == "Completed" && complete) ||
                                    (filter == "Required" && !complete)
                            }
                            .forEach { m ->
                                TrainingCard(m, ownRecords) {
                                    selected = m.getString("id")
                                    screen = "Training details"
                                }
                            }
                    }
                    "Training details" -> {
                        val m = modules.firstOrNull { it.getString("id") == selected }
                        if (m != null) {
                            Title(
                                m.getString("title"),
                                "${m.getInt("minutes")} min • English training content",
                            )
                            Text(m.getString("description"))
                            Info(
                                "Available offline",
                                "Instructions, assessment questions and procedural 3D equipment are included with this app.",
                            )
                            Section("What you will practice")
                            m.items("steps").forEachIndexed { i, s ->
                                Text("${i+1}. ${s.getString("title")}")
                            }
                            Info("Training equipment", m.getString("equipment"))
                            Action("Start AR training") { launchTraining(selected) }
                            Secondary("Practice without camera") { launchTraining(selected, true) }
                            Secondary("Take assessment") { screen = "Assessment" }
                            Text(
                                "A trainer must review practical competence before a certificate is issued.",
                                color = Muted,
                            )
                        }
                    }
                    "Assessment" -> {
                        modules
                            .firstOrNull { it.getString("id") == selected }
                            ?.let { m ->
                                key(selected) {
                                    Assessment(m, busy) { answers ->
                                        run {
                                            repo.act(
                                                "assessment.submit",
                                                JSONObject()
                                                    .put("moduleId", selected)
                                                    .put("answers", JSONArray(answers)),
                                            )
                                            repo.sync()
                                            screen = "Training history"
                                        }
                                    }
                                }
                            }
                    }
                    "Training history" -> {
                        Title("Training history", "Assessment scores are calculated by the server.")
                        val attempts = ownRecords.filter { it.optString("kind") == "attempt" }
                        if (attempts.isEmpty())
                            Empty(
                                "No results yet",
                                "Complete training, submit an assessment and sync to view the result.",
                            )
                        attempts.forEach { a ->
                            val d = a.getJSONObject("data")
                            Info(
                                "${moduleTitle(modules,d.optString("moduleId"))} • ${d.optInt("score")}%",
                                "${d.optString("status").replace('_',' ')}\nAttempt ${d.optInt("attemptNumber")} • ${formatTime(d.optString("completedAt"))}\nTopics to review: ${d.optJSONArray("weakTopics")?.join(", ")?.replace("\"","")?.ifEmpty{"None"}}",
                            )
                        }
                        Secondary("View certificates") { screen = "Certificates" }
                    }
                    "Certificates" -> {
                        Title(
                            "Certificates",
                            "Only server-issued, reviewed qualifications appear here.",
                        )
                        if (certs.isEmpty())
                            Empty(
                                "No certificates yet",
                                "Pass your assessment and ask your trainer to review your practical competence.",
                            )
                        certs.forEach { c ->
                            val d = c.getJSONObject("data")
                            CardBox {
                                Text(
                                    moduleTitle(modules, d.getString("moduleId")),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Status(certificateStatus(d))
                                Text(
                                    "Issued ${formatTime(d.getString("issuedAt"))}\nExpires ${formatTime(d.getString("expiresAt"))}"
                                )
                                Text(c.getString("id"), style = MaterialTheme.typography.bodySmall)
                                Secondary("Show QR code") {
                                    selected = c.getString("id")
                                    screen = "Certificate QR"
                                }
                            }
                        }
                        Text(
                            "Saved status can be stale offline. Verify online before authorizing work.",
                            color = Muted,
                        )
                    }
                    "Certificate QR" -> {
                        val c = certs.firstOrNull { it.getString("id") == selected }
                        if (c != null) {
                            Title("Verify certificate", user.getString("name"))
                            Qr(snapshot.optString("verificationBase") + selected)
                            Text(selected)
                            Secondary("Open verification page") {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            snapshot.optString("verificationBase") + selected
                                        ),
                                    )
                                )
                            }
                        }
                    }
                    "Jobs" -> {
                        Title(
                            "My jobs",
                            "Safety requirements are checked again when your shift starts.",
                        )
                        if (jobs.isEmpty())
                            Empty(
                                "No assigned jobs",
                                "Your supervisor will assign a job after checking eligibility.",
                            )
                        jobs.forEach { j ->
                            JobCard(j, certs) {
                                selected = j.getString("id")
                                screen = "Job details"
                            }
                        }
                    }
                    "Job details" -> {
                        jobs
                            .firstOrNull { it.getString("id") == selected }
                            ?.let { j ->
                                val d = j.getJSONObject("data")
                                Title(
                                    d.getString("title"),
                                    "${d.getString("site")} • ${d.getString("supervisor")}",
                                )
                                Info(
                                    "Shift",
                                    "${formatTime(d.getString("start"))} – ${formatTime(d.getString("end"))}",
                                )
                                Info("Required PPE", d.getString("ppe"))
                                Section("Required safety")
                                val required = d.getJSONArray("requirements")
                                for (i in 0 until required.length()) {
                                    val mid = required.getString(i)
                                    val valid = certs.any {
                                        it.getJSONObject("data").optString("moduleId") == mid &&
                                            CredentialPolicy.coversShift(
                                                it.getJSONObject("data").optString("status"),
                                                it.getJSONObject("data").optString("expiresAt"),
                                                d.getString("end"),
                                            )
                                    }
                                    Text(
                                        "${if(valid)"✓" else "Required:"} ${moduleTitle(modules,mid)}",
                                        color = if (valid) Success else Danger,
                                    )
                                }
                                Secondary("Complete required training") { screen = "Training" }
                                Section("Today's tasks")
                                val tasks = d.getJSONArray("tasks")
                                val done = d.getJSONArray("completedTasks")
                                var note by rememberSaveable { mutableStateOf("") }
                                Field("Inspection / completion note", note, { note = it })
                                for (i in 0 until tasks.length()) {
                                    val completed =
                                        (0 until done.length()).any { done.getInt(it) == i }
                                    CardBox {
                                        Text(tasks.getString(i))
                                        Status(if (completed) "SUBMITTED" else "PENDING")
                                        if (!completed)
                                            Secondary(
                                                "Submit task",
                                                enabled = note.isNotBlank() && !busy,
                                            ) {
                                                act(
                                                    "task.complete",
                                                    JSONObject()
                                                        .put("jobId", selected)
                                                        .put("index", i)
                                                        .put("note", note),
                                                    false,
                                                )
                                            }
                                    }
                                }
                                Action("Start shift", enabled = !busy) {
                                    act("attendance.in", JSONObject().put("jobId", selected), true)
                                }
                                Text(
                                    "Shift authorization requires an online eligibility check.",
                                    color = Muted,
                                )
                            }
                    }
                    "Activity" -> {
                        Title("My activity", "Work records and personal information")
                        listOf(
                                "Attendance",
                                "Leave",
                                "Salary",
                                "Tasks",
                                "Training history",
                                "Certificates",
                                "Sync status",
                            )
                            .forEach { destination ->
                                Secondary(destination) { screen = destination }
                            }
                    }
                    "Tasks" -> {
                        Title("Daily tasks", "Open your assigned job to submit completion notes.")
                        jobs.forEach { j ->
                            JobCard(j, certs) {
                                selected = j.getString("id")
                                screen = "Job details"
                            }
                        }
                    }
                    "Attendance" -> {
                        Title(
                            "Attendance",
                            "Check-in and check-out times are recorded by the server.",
                        )
                        val entries = ownRecords.filter { it.optString("kind") == "attendance" }
                        if (entries.isEmpty())
                            Empty(
                                "No attendance records",
                                "Open your assigned job to start your shift.",
                            )
                        entries.forEach { a ->
                            val d = a.getJSONObject("data")
                            CardBox {
                                Text("Check-in: ${formatTime(d.getString("checkIn"))}")
                                Text(
                                    "Check-out: ${if(d.isNull("checkOut"))"Still checked in" else formatTime(d.getString("checkOut"))}"
                                )
                                if (d.has("minutes"))
                                    Text("${d.getInt("minutes")} minutes recorded")
                                if (d.isNull("checkOut"))
                                    Action("Check out", enabled = !busy) {
                                        act(
                                            "attendance.out",
                                            JSONObject().put("attendanceId", a.getString("id")),
                                            true,
                                        )
                                    }
                            }
                        }
                        Secondary("View jobs") { screen = "Jobs" }
                        Secondary("Record offline attendance") { screen = "Offline attendance" }
                        ownRecords
                            .filter { it.optString("kind") == "attendanceClaim" }
                            .forEach { c ->
                                val d = c.getJSONObject("data")
                                Info(
                                    "Offline attendance claim",
                                    "${formatTime(d.getString("checkIn"))} – ${formatTime(d.getString("checkOut"))}\n${d.getString("status")}",
                                )
                            }
                    }
                    "Offline attendance" -> {
                        OfflineAttendance(jobs, busy) { payload ->
                            run {
                                repo.act("attendance.claim", payload)
                                repo.sync()
                                screen = "Attendance"
                            }
                        }
                    }
                    "Leave" -> {
                        Title(
                            "Leave",
                            "12 casual and 12 sick days per calendar year. Pending requests reserve balance.",
                        )
                        val leaves = ownRecords.filter { it.optString("kind") == "leave" }
                        listOf("CASUAL", "SICK").forEach { type ->
                            val used =
                                leaves
                                    .filter {
                                        val d = it.getJSONObject("data")
                                        d.optString("type") == type &&
                                            d.optString("status") != "REJECTED" &&
                                            d.optString("start")
                                                .startsWith(
                                                    java.time.LocalDate.now().year.toString()
                                                )
                                    }
                                    .sumOf { it.getJSONObject("data").optInt("days") }
                            Text("$type: ${12-used} days available")
                        }
                        Secondary("Apply for leave") { screen = "Apply leave" }
                        leaves.forEach { l ->
                            val d = l.getJSONObject("data")
                            Info(
                                d.getString("type"),
                                "${d.getString("start")} → ${d.getString("end")}\n${d.getString("status")}\n${d.optString("decisionReason",d.getString("reason"))}",
                            )
                        }
                    }
                    "Apply leave" -> {
                        LeaveForm(busy) { payload ->
                            run {
                                repo.act("leave.apply", payload)
                                repo.sync()
                                screen = "Leave"
                            }
                        }
                    }
                    "Salary" -> {
                        Title(
                            "Salary & payslips",
                            "Amounts are published by your payroll administrator.",
                        )
                        val payroll = ownRecords.filter { it.optString("kind") == "payroll" }
                        if (payroll.isEmpty())
                            Empty(
                                "No payslips available",
                                "Published payroll records will appear here.",
                            )
                        payroll.forEach { p ->
                            val d = p.getJSONObject("data")
                            CardBox {
                                Text(
                                    d.getString("month"),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "Net pay ${money(d.getLong("net"))}",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                listOf(
                                        "base" to "Base salary",
                                        "overtime" to "Overtime",
                                        "incentives" to "Incentives",
                                        "deductions" to "Deductions",
                                    )
                                    .forEach { (key, label) ->
                                        Text("$label: ${money(d.getLong(key))}")
                                    }
                                Status(d.getString("status"))
                                Secondary("View payslip") {
                                    selected = p.getString("id")
                                    screen = "Payslip"
                                }
                            }
                        }
                    }
                    "Payslip" -> {
                        ownRecords
                            .firstOrNull { it.getString("id") == selected }
                            ?.let { p ->
                                val d = p.getJSONObject("data")
                                Title(
                                    "Payslip • ${d.getString("month")}",
                                    "${user.getString("name")} • ${user.getString("employeeId")}",
                                )
                                Text("${user.getString("site")}\nRecord ${p.getString("id")}")
                                listOf("base", "overtime", "incentives", "deductions", "net")
                                    .forEach { key ->
                                        Info(
                                            key.replaceFirstChar { it.uppercase() },
                                            money(d.getLong(key)),
                                        )
                                    }
                                Status(d.getString("status"))
                            }
                    }
                    "Profile" -> {
                        Title(
                            user.getString("name"),
                            "${user.getString("employeeId")} • ${user.getString("role")}",
                        )
                        Info("Worksite", user.getString("site"))
                        Secondary("Certificates & skills") { screen = "Certificates" }
                        Secondary("Training history") { screen = "Training history" }
                        Secondary("Language") { language = "" }
                        Secondary("Downloads") { screen = "Downloads" }
                        Secondary("Sync status") { screen = "Sync status" }
                        Secondary("Emergency instructions") { screen = "Emergency guidance" }
                        Secondary("Emergency alerts") { screen = "Emergency alerts" }
                        Secondary("Enable push notifications") {
                            if (Build.VERSION.SDK_INT >= 33)
                                notificationPermission.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            else run { repo.enableNotifications() }
                        }
                        Secondary("Sign in again") { screen = "Sign in again" }
                        Secondary("Sign out") { logout = true }
                    }
                    "Downloads" -> {
                        Title(
                            "Offline content",
                            "Both training packs are bundled and work without an internet connection.",
                        )
                        modules.forEach { m ->
                            Info(
                                m.getString("title"),
                                "Version ${m.getInt("version")} • English • Available offline",
                            )
                        }
                        Text(
                            "AR tracking requires a supported device and Google Play Services for AR. Install AR services while online before visiting a remote site."
                        )
                    }
                    "Sync status" -> {
                        Title("Sync status", state.message)
                        Action("Retry synchronization", enabled = !busy) { run { repo.sync() } }
                        if (state.pending.isEmpty())
                            Empty("No pending changes", "Your saved work has been synchronized.")
                        state.pending.forEach { p ->
                            CardBox {
                                Text(
                                    "Saved ${formatTime(Instant.ofEpochMilli(p.createdAt).toString())}"
                                )
                                Text(
                                    if (p.error.isEmpty()) "Waiting to sync" else p.error,
                                    color = if (p.error.isEmpty()) Muted else Danger,
                                )
                                if (p.error.isNotEmpty())
                                    Secondary("Discard rejected action") {
                                        run { repo.discard(p.id) }
                                    }
                            }
                        }
                    }
                    "Emergency guidance" -> {
                        Title("Emergency guidance", "Follow the site's approved emergency plan.")
                        Info(
                            "Fire",
                            "Raise the alarm, move to the designated assembly area and keep escape routes clear.",
                        )
                        Info(
                            "Gas / confined space",
                            "Withdraw from the hazard area, raise the alarm and do not attempt an unplanned rescue.",
                        )
                        Info(
                            "Injury",
                            "Call the site emergency team. Do not expose yourself to the same hazard.",
                        )
                        Secondary("Open phone dialer") {
                            context.startActivity(Intent(Intent.ACTION_DIAL))
                        }
                    }
                    "Emergency alerts" -> {
                        Title(
                            "Emergency alerts",
                            "Received means the server stored the alert. Acknowledged means a responder accepted it.",
                        )
                        val alerts = ownRecords.filter { it.optString("kind") == "emergency" }
                        if (alerts.isEmpty())
                            Empty(
                                "No received alerts",
                                "Check Sync status for alerts waiting on this phone.",
                            )
                        alerts.forEach { e ->
                            val d = e.getJSONObject("data")
                            Info(
                                d.getString("type"),
                                "${d.getString("status")}\n${formatTime(d.getString("createdAt"))}\n${d.optString("message")}",
                            )
                        }
                    }
                    "Notifications" -> {
                        Title("Notifications", "Updates are downloaded when the app synchronizes.")
                        val notifications = ownRecords.filter {
                            it.optString("kind") == "notification"
                        }
                        if (notifications.isEmpty())
                            Empty(
                                "No updates",
                                "Training, job and workforce updates will appear here.",
                            )
                        notifications.forEach { n ->
                            val d = n.getJSONObject("data")
                            CardBox {
                                Section(d.getString("title"))
                                Text(d.getString("message"))
                                Text(formatTime(d.getString("createdAt")))
                                if (!d.optBoolean("read"))
                                    Secondary("Mark as read") {
                                        act(
                                            "notification.read",
                                            JSONObject().put("notificationId", n.getString("id")),
                                            false,
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
    if (sos)
        EmergencyDialog(
            busy,
            { sos = false },
            { type, message ->
                run {
                    repo.act(
                        "emergency.raise",
                        JSONObject().put("type", type).put("message", message),
                    )
                    repo.sync()
                    sos = false
                    screen = if (repo.state.value.connected) "Emergency alerts" else "Sync status"
                }
            },
            { context.startActivity(Intent(Intent.ACTION_DIAL)) },
        )
    if (logout)
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "${state.pending.size} pending actions will be removed from this device. Sync your work before signing out. A connection is required to revoke your session."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        run {
                            repo.logout()
                            logout = false
                            screen = "Home"
                        }
                    }
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("Keep account") } },
        )
}

@Composable
private fun Login(
    busy: Boolean,
    error: String,
    request: (String, String, String, (JSONObject) -> Unit) -> Unit,
    register: (String, String, String, String, String, (JSONObject) -> Unit) -> Unit,
    verify: (String, String) -> Unit,
    practice: (String) -> Unit,
    language: () -> Unit,
    modules: List<JSONObject>,
) {
    var org by rememberSaveable { mutableStateOf("") }
    var employee by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var workerName by rememberSaveable { mutableStateOf("") }
    var siteName by rememberSaveable { mutableStateOf("") }
    var isRegistering by rememberSaveable { mutableStateOf(false) }
    var regMessage by rememberSaveable { mutableStateOf("") }
    val loginContext = androidx.compose.ui.platform.LocalContext.current
    var challenge by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Page(systemBars = true) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Outlined.HealthAndSafety, null, tint = Primary, modifier = Modifier.size(48.dp))
        Title("SurakshaSetu", if (isRegistering) "Worker Self-Registration" else "Safety training. Safer work.")
        
        if (regMessage.isNotEmpty()) {
            Text(
                regMessage,
                Modifier.fillMaxWidth().background(Color(0xFFEAF5EE)).padding(12.dp),
                color = Success,
                fontWeight = FontWeight.Bold
            )
        }

        if (isRegistering) {
            Text("Register your worker details. Your registration will be sent to your site administrator for approval.")
            Field("Organization ID", org, { org = it })
            Field("Employee ID (e.g. EMP001)", employee, { employee = it })
            Field("Full Name", workerName, { workerName = it })
            Field("Registered email address", email, { email = it.trim().take(254) })
            Field("Site / Plant Name", siteName, { siteName = it })
            Action(
                "Submit Registration for Approval",
                !busy && org.isNotBlank() && employee.isNotBlank() && workerName.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            ) {
                register(org, employee, workerName, email, siteName) { res ->
                    regMessage = res.optString("message", "Registration submitted. Pending admin approval.")
                    isRegistering = false
                }
            }
            Secondary("Back to Sign In") {
                isRegistering = false
            }
        } else {
            Text("Sign in with the employee account provided by your organization.")
            if (challenge.isEmpty()) {
                Field("Organization ID", org, { org = it })
                Field("Employee ID", employee, { employee = it })
                Field("Registered email address", email, { email = it.trim().take(254) })
                Text("Use the email address registered by your manager.", color = Muted)
                Action("Send verification code", !busy && org.isNotBlank() && employee.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    request(org, employee, email) { res ->
                        challenge = res.optString("challengeId")
                    }
                }
                Secondary("New worker? Register account") {
                    isRegistering = true
                    regMessage = ""
                }
            } else {
                Text("If your account and email match, a code was sent to that email. Check your inbox and spam folder.")
                Field("6-digit verification code", code, { code = it.filter(Char::isDigit).take(6) })
                Action("Verify and sign in", !busy && code.length == 6) { verify(challenge, code) }
                Secondary("Request another code") {
                    challenge = ""
                    code = ""
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error, color = Danger)
        Secondary("Manager login — workforce console") {
            loginContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BuildConfig.API_BASE_URL + "manager")))
        }
        Secondary("Change language", onClick = language)
        Section("Explore offline practice")
        Text("Practice does not create qualifications or authorize work.", color = Muted)
        modules.forEach { m -> Secondary(m.getString("title")) { practice(m.getString("id")) } }
    }
}

@Composable
private fun Assessment(module: JSONObject, busy: Boolean, submit: (List<Int>) -> Unit) {
    var answers by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val list = if (answers.isEmpty()) emptyList() else answers.split(',').map { it.toInt() }
    val questions = module.items("questions")
    if (list.size >= questions.size) {
        Title(
            "Assessment ready",
            "Submit your answers for server scoring. Offline submissions wait for synchronization.",
        )
        Action("Submit assessment", !busy) { submit(list) }
        return
    }
    val q = questions[list.size]
    Title("Question ${list.size+1} of ${questions.size}", module.getString("title"))
    LinearProgressIndicator(
        progress = { list.size.toFloat() / questions.size },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(q.getString("text"), style = MaterialTheme.typography.titleLarge)
    val options = q.getJSONArray("options")
    for (i in 0 until options.length()) {
        OutlinedButton(
            onClick = { selected = i },
            modifier = Modifier.fillMaxWidth(),
            border =
                BorderStroke(
                    if (selected == i) 2.dp else 1.dp,
                    if (selected == i) Primary else Border,
                ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                RadioButton(selected == i, onClick = null)
                Text(options.getString(i), Modifier.padding(12.dp))
            }
        }
    }
    Action("Continue", selected >= 0) {
        answers = (list + selected).joinToString(",")
        selected = -1
    }
}

@Composable
private fun LeaveForm(busy: Boolean, submit: (JSONObject) -> Unit) {
    var type by rememberSaveable { mutableStateOf("CASUAL") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    Title("Apply for leave", "Your supervisor will review your request.")
    Row {
        listOf("CASUAL", "SICK").forEach { t ->
            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
        }
    }
    Field("Start date (YYYY-MM-DD)", start, { start = it })
    Field("End date (YYYY-MM-DD)", end, { end = it })
    Field("Reason", reason, { reason = it })
    val valid = runCatching {
        val s = java.time.LocalDate.parse(start)
        val e = java.time.LocalDate.parse(end)
        !e.isBefore(s) && !s.isBefore(java.time.LocalDate.now())
    }
        .getOrDefault(false)
    if (start.isNotEmpty() && end.isNotEmpty() && !valid)
        Text(
            "Enter valid current or future dates, with the end on or after the start.",
            color = Danger,
        )
    Action("Apply for leave", !busy && valid && reason.isNotBlank()) {
        submit(
            JSONObject().put("type", type).put("start", start).put("end", end).put("reason", reason)
        )
    }
}

@Composable
private fun EmergencyDialog(
    busy: Boolean,
    close: () -> Unit,
    send: (String, String) -> Unit,
    dial: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf("FIRE") }
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Emergency assistance", color = Danger) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "An alert is not a confirmed emergency response. If offline, it remains on this phone until delivered. Call the site emergency team immediately."
                )
                listOf("FIRE", "GAS", "INJURY", "MACHINERY", "OTHER").forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
                Field("Location / message", message, { message = it })
                TextButton(onClick = dial) { Text("Open phone dialer") }
            }
        },
        confirmButton = {
            Button(
                onClick = { send(type, message) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) {
                Text("Send emergency alert")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
private fun TrainingCard(m: JSONObject, records: List<JSONObject>, onClick: () -> Unit) {
    val complete = records.any {
        it.optString("kind") == "progress" &&
            it.getJSONObject("data").optString("moduleId") == m.getString("id")
    }
    CardBox {
        Icon(Icons.Outlined.ViewInAr, null, tint = Primary)
        Text(m.getString("title"), style = MaterialTheme.typography.titleMedium)
        Text("${m.getInt("minutes")} min • Available offline", color = Muted)
        Status(if (complete) "TRAINING COMPLETED" else "READY TO START")
        Secondary(if (complete) "Review training" else "View training", onClick = onClick)
    }
}

@Composable
private fun JobCard(j: JSONObject, certs: List<JSONObject>, onClick: () -> Unit) {
    val d = j.getJSONObject("data")
    CardBox {
        Text(d.getString("title"), style = MaterialTheme.typography.titleMedium)
        Text("${d.getString("site")} • ${d.getString("supervisor")}", color = Muted)
        Text(formatTime(d.getString("start")))
        val req = d.getJSONArray("requirements")
        val missing =
            (0 until req.length())
                .map { req.getString(it) }
                .filter { mid ->
                    certs.none {
                        it.getJSONObject("data").optString("moduleId") == mid &&
                            CredentialPolicy.coversShift(
                                it.getJSONObject("data").optString("status"),
                                it.getJSONObject("data").optString("expiresAt"),
                                d.getString("end"),
                            )
                    }
                }
        Status(
            if (missing.isEmpty()) d.getString("status") else "BLOCKED: ${missing.joinToString()}"
        )
        Secondary("View job", onClick = onClick)
    }
}

@Composable
private fun Page(systemBars: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .then(if (systemBars) Modifier.systemBarsPadding() else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Border),
        color = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(subtitle, color = Muted)
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Info(title: String, text: String, color: Color = Color.White) {
    Surface(color = color, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(title)
            Text(text)
        }
    }
}

@Composable
private fun Empty(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, color = Muted)
    }
}

@Composable
private fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun Secondary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
    ) {
        Text(text)
    }
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { change(it.take(500)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                cursorColor = Primary,
                focusedLabelColor = Primary,
                unfocusedLabelColor = Muted,
            ),
    )
}

@Composable
private fun Status(text: String) {
    Text(
        text.replace('_', ' '),
        color =
            when {
                text in listOf("VALID", "APPROVED", "PAID", "TRAINING COMPLETED") -> Success
                text.startsWith("BLOCKED") || text in listOf("REVOKED", "EXPIRED", "FAILED") ->
                    Danger
                else -> Primary
            },
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun Qr(value: String) {
    val bitmap =
        remember(value) {
            val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
            Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
                val pixels =
                    IntArray(640 * 640) { i ->
                        if (matrix[i % 640, i / 640]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    }
                setPixels(pixels, 0, 640, 0, 0, 640, 640)
            }
        }
    Image(
        bitmap.asImageBitmap(),
        "Certificate verification QR",
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    )
}

private fun certificateStatus(d: JSONObject): String =
    CredentialPolicy.status(d.optString("status"), d.optString("expiresAt"))

private fun moduleTitle(modules: List<JSONObject>, id: String) =
    modules.firstOrNull { it.getString("id") == id }?.getString("title") ?: id

private fun formatTime(value: String) = runCatching {
    DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}
    .getOrDefault(value)

private fun money(paise: Long) =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(paise / 100.0)
