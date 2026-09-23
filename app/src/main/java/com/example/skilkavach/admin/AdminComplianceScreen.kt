package com.example.skilkavach.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skilkavach.data.CertificateVault
import com.example.skilkavach.data.CertificateVerificationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminComplianceScreen(
    onClose: () -> Unit
) {
    var qrInputText by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<CertificateVerificationResult?>(null) }
    val vault = remember { CertificateVault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supervisor Compliance Portal", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onClose) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Heatmap Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Site Compliance Heatmap", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeatmapItem("Fire Safety", "92%", Color(0xFF22C55E))
                        HeatmapItem("Gas/Confined", "64%", Color(0xFFEF4444))
                        HeatmapItem("Electrical", "88%", Color(0xFF22C55E))
                        HeatmapItem("Machinery", "76%", Color(0xFFF59E0B))
                    }
                }
            }

            // Predictive Risk Modeling Alert
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Predictive Risk Alert", color = Color(0xFF991B1B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Gas & Confined Space domain shows 36% hesitation rate during Night Shift at Coal Pit #3. Proactive drill recommended.",
                        color = Color(0xFF7F1D1D),
                        fontSize = 14.sp
                    )
                }
            }

            // QR Certificate Verification Tool
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Instant QR Certificate Verifier", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrInputText,
                        onValueChange = { qrInputText = it },
                        label = { Text("Paste/Scan Signed QR JSON Payload") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (qrInputText.isNotBlank()) {
                                verificationResult = vault.verifyCertificateQr(qrInputText)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cryptographically Verify Offline")
                    }

                    verificationResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (res.isValid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = res.statusMessage,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isValid) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                                if (res.workerId != "UNKNOWN") {
                                    Text("Worker ID: ${res.workerId}", color = Color.Black)
                                    Text("Domain: ${res.hazardDomain}", color = Color.Black)
                                    Text("Comprehension Score: ${res.score}%", color = Color.Black)
                                    Text("Ledger Chain Valid: ${res.isChainValid}", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }

            // Pending Self-Registrations Approval Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pending Worker Self-Registrations", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Approve worker accounts to enable OTP login access.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    PendingApprovalItem("Vikas Kumar (EMP002)", "+919876543211", "Coal Pit #2")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PendingApprovalItem("Anita Tudu (EMP003)", "+919876543212", "Mica Unit #1")
                }
            }

            // Overdue Recertification Alerts List
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overdue Recertifications (Living Certificates)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OverdueWorkerItem("Ramesh Oraon (EMP089)", "Mica Plant #2", "Gas Safety", "Confidence 42%")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OverdueWorkerItem("Sunil Hembram (EMP104)", "Coal Shaft #1", "Electrical", "Confidence 49%")
                }
            }
        }
    }
}

@Composable
fun HeatmapItem(label: String, score: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(score, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = Color.LightGray, fontSize = 11.sp)
    }
}

@Composable
fun OverdueWorkerItem(name: String, site: String, domain: String, confidence: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("$site • $domain", color = Color.Gray, fontSize = 12.sp)
        }
        Text(confidence, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun PendingApprovalItem(name: String, phone: String, site: String) {
    var approved by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("$site • $phone", color = Color.Gray, fontSize = 12.sp)
        }
        if (approved) {
            Text("Approved ✓", color = Color(0xFF166534), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else if (rejected) {
            Text("Rejected ✗", color = Color(0xFF991B1B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { approved = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
                ) {
                    Text("Approve", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { rejected = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Reject", fontSize = 12.sp, color = Color(0xFF991B1B))
                }
            }
        }
    }
}
