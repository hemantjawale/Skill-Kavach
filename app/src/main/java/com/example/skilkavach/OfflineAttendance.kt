package com.example.skilkavach

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject

@Composable
fun OfflineAttendance(jobs: List<JSONObject>, busy: Boolean, submit: (JSONObject) -> Unit) {
    var jobId by rememberSaveable { mutableStateOf(jobs.firstOrNull()?.getString("id") ?: "") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    Text("Offline attendance claim", style = MaterialTheme.typography.headlineSmall)
    Text(
        "This records a claim for supervisor review. It does not authorize high-risk work or backdate a safety clearance. Enter local times in YYYY-MM-DDTHH:MM format."
    )
    jobs.forEach { j ->
        FilterChip(
            selected = jobId == j.getString("id"),
            onClick = { jobId = j.getString("id") },
            label = { Text(j.getJSONObject("data").getString("title")) },
        )
    }
    OutlinedTextField(
        value = start,
        onValueChange = { start = it },
        label = { Text("Check-in time") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = end,
        onValueChange = { end = it },
        label = { Text("Check-out time") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = reason,
        onValueChange = { reason = it.take(500) },
        label = { Text("Reason for offline claim") },
        modifier = Modifier.fillMaxWidth(),
    )
    val times = runCatching {
        LocalDateTime.parse(start).atZone(ZoneId.systemDefault()).toInstant() to
            LocalDateTime.parse(end).atZone(ZoneId.systemDefault()).toInstant()
    }
        .getOrNull()
    Button(
        enabled =
            !busy &&
                jobId.isNotEmpty() &&
                reason.isNotBlank() &&
                times != null &&
                times.second > times.first,
        onClick = {
            times?.let {
                submit(
                    JSONObject()
                        .put("jobId", jobId)
                        .put("checkIn", it.first.toString())
                        .put("checkOut", it.second.toString())
                        .put("reason", reason)
                )
            }
        },
    ) {
        Text("Save claim for review")
    }
}
