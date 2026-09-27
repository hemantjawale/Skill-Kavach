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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skilkavach.data.CertificateStatus
import com.example.skilkavach.data.CertificateVault
import com.example.skilkavach.data.CertificateVerificationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminComplianceScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lang = remember { context.getSharedPreferences("language", 0).getString("value", "en") ?: "en" }

    var qrInputText by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<CertificateVerificationResult?>(null) }
    val vault = remember { CertificateVault() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val portalTitle = when (lang) {
        "hi" -> "पर्यवेक्षक अनुपालन पोर्टल"
        "sat" -> "ᱥᱩᱯᱚᱨᱵᱷᱟᱭᱤᱡᱚᱨ ᱪᱮᱠ ᱯᱚᱨᱴᱟᱞ"
        else -> "Supervisor Compliance Portal"
    }
    val closeLabel = when (lang) {
        "hi" -> "बंद करें"
        "sat" -> "ᱵᱚᱸᱫ"
        else -> "Close"
    }
    val heatmapTitle = when (lang) {
        "hi" -> "साइट अनुपालन हीटमैप"
        "sat" -> "ᱥᱟᱭᱤᱴ ᱪᱮᱠ ᱦᱤᱴᱢᱮᱯ"
        else -> "Site Compliance Heatmap"
    }
    val riskTitle = when (lang) {
        "hi" -> "पूर्वानुमानित जोखिम चेतावनी"
        "sat" -> "ᱵᱚᱛᱚᱨ ᱦᱩᱥᱤᱭᱟᱹᱨ ᱠᱷᱚᱵᱚᱨ"
        else -> "Predictive Risk Alert"
    }
    val riskMsg = when (lang) {
        "hi" -> "कोयला खदान #3 में नाइट शिफ्ट के दौरान गैस और सीमित स्थान क्षेत्र में 36% झिझक दर दिखाई देती है। सक्रिय ड्रिल की सिफारिश की जाती है।"
        "sat" -> "ᱠᱳᱭᱞᱟ ᱠᱷᱟᱫᱟᱱ #3 ᱨᱮ ᱧᱤᱸᱫᱟᱹ ᱠᱟᱹᱢᱤ ᱡᱚᱠᱷᱮᱡ ᱜᱮᱥ ᱟᱨ ᱥᱩᱢᱩᱝ ᱡᱟᱭᱜᱟ ᱨᱮ 36% ᱵᱚᱛᱚᱨ ᱧᱮᱞᱚᱜ ᱠᱟᱱᱟ᱾ ᱴᱨᱮᱱᱤᱝ ᱞᱟᱹᱠᱛᱤᱭᱟ᱾"
        else -> "Gas & Confined Space domain shows 36% hesitation rate during Night Shift at Coal Pit #3. Proactive drill recommended."
    }
    val qrTitle = when (lang) {
        "hi" -> "त्वरित क्यूआर प्रमाणपत्र सत्यापनकर्ता"
        "sat" -> "ᱞᱚᱜᱚᱱ QR ᱥᱟᱴᱤᱯᱷᱤᱠᱮᱴ ᱪᱮᱠ"
        else -> "Instant QR Certificate Verifier"
    }
    val qrHint = when (lang) {
        "hi" -> "हस्ताक्षरित क्यूआर जेएसओएन पेलोड चिपकाएं/स्कैन करें"
        "sat" -> "QR ᱠᱳᱰ ᱱᱚᱸᱰᱮ ᱞᱟᱴᱷᱟ/ᱥᱠᱮᱱ ᱢᱮ"
        else -> "Paste/Scan Signed QR JSON Payload"
    }
    val verifyBtn = when (lang) {
        "hi" -> "ऑफलाइन क्रिप्टोग्राफिक रूप से सत्यापित करें"
        "sat" -> "ᱚᱯᱷᱞᱟᱭᱤᱱ ᱥᱟᱴᱤᱯᱷᱤᱠᱮᱴ ᱪᱮᱠ"
        else -> "Cryptographically Verify Offline"
    }
    val pendingTitle = when (lang) {
        "hi" -> "लंबित कार्यकर्ता स्व-पंजीकरण"
        "sat" -> "ᱛᱟᱺᱜᱤ ᱨᱮ ᱢᱮᱱᱟᱜ ᱠᱟᱹᱢᱤᱭᱟᱹ ᱧᱩᱛᱩᱢ ᱚᱞ"
        else -> "Pending Worker Self-Registrations"
    }
    val pendingDesc = when (lang) {
        "hi" -> "ओटीपी लॉगिन एक्सेस सक्षम करने के लिए कार्यकर्ता खातों को स्वीकृत करें।"
        "sat" -> "OTP ᱞᱚᱜᱤᱱ ᱞᱟᱹᱜᱤᱫ ᱠᱟᱹᱢᱤᱭᱟᱹ ᱧᱩᱛᱩᱢ ᱥᱟᱹᱠᱷᱤ ᱢᱮ᱾"
        else -> "Approve worker accounts to enable OTP login access."
    }
    val overdueTitle = when (lang) {
        "hi" -> "बकाया पुनः प्रमाणन (जीवंत प्रमाणपत्र)"
        "sat" -> "ᱫᱩᱦᱨᱟ ᱥᱟᱴᱤᱯᱷᱤᱠᱮᱴ ᱞᱟᱹᱠᱛᱤ (Living Certificates)"
        else -> "Overdue Recertifications (Living Certificates)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(portalTitle, fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onClose) {
                        Text(closeLabel, color = MaterialTheme.colorScheme.primary)
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
                    Text(heatmapTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeatmapItem(
                            when (lang) { "hi" -> "अग्नि सुरक्षा"; "sat" -> "ᱥᱮᱸᱜᱮᱞ ᱨᱩᱠᱷᱤᱭᱟᱹ"; else -> "Fire Safety" },
                            "92%", Color(0xFF22C55E)
                        )
                        HeatmapItem(
                            when (lang) { "hi" -> "गैस/सीमित स्थान"; "sat" -> "ᱜᱮᱥ ᱥᱩᱢᱩᱝ ᱡᱟᱭᱜᱟ"; else -> "Gas/Confined" },
                            "64%", Color(0xFFEF4444)
                        )
                        HeatmapItem(
                            when (lang) { "hi" -> "विद्युत सुरक्षा"; "sat" -> "ᱵᱤᱡᱽᱞᱤ ᱨᱩᱠᱷᱤᱭᱟᱹ"; else -> "Electrical" },
                            "88%", Color(0xFF22C55E)
                        )
                        HeatmapItem(
                            when (lang) { "hi" -> "मशीनरी"; "sat" -> "ᱢᱮᱥᱤᱱ"; else -> "Machinery" },
                            "76%", Color(0xFFF59E0B)
                        )
                    }
                }
            }

            // Predictive Risk Modeling Alert
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(riskTitle, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(riskMsg, color = Color(0xFF7F1D1D), fontSize = 14.sp)
                }
            }

            // QR Certificate Verification Tool
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(qrTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrInputText,
                        onValueChange = { qrInputText = it },
                        label = { Text(qrHint) },
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
                        Text(verifyBtn)
                    }

                    verificationResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val (containerColor, contentColor, badgeLabel) = when (res.status) {
                            CertificateStatus.VALID -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "STATUS: VALID")
                            CertificateStatus.EXPIRED -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "STATUS: EXPIRED")
                            CertificateStatus.TAMPERED -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "STATUS: TAMPERED")
                            CertificateStatus.REVOKED -> Triple(Color(0xFFFEE2E2), Color(0xFF7F1D1D), "STATUS: REVOKED")
                            CertificateStatus.UNKNOWN -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), "STATUS: UNKNOWN")
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(containerColor, shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "$badgeLabel — ${res.statusMessage}",
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                if (res.status != CertificateStatus.UNKNOWN) {
                                    Text("Certificate ID: ${res.certificateId}", color = Color.Black, fontSize = 13.sp)
                                    Text("Issuer: ${res.issuer}", color = Color.Black, fontSize = 13.sp)
                                    Text("Worker ID: ${res.workerId}", color = Color.Black, fontSize = 13.sp)
                                    Text("Hazard Domain: ${res.hazardDomain}", color = Color.Black, fontSize = 13.sp)
                                    Text("Comprehension Score: ${res.score}%", color = Color.Black, fontSize = 13.sp)
                                    Text("Issued: ${if (res.issuedAt > 0) dateFormat.format(Date(res.issuedAt)) else "N/A"}", color = Color.Black, fontSize = 13.sp)
                                    Text("Expires: ${if (res.expiresAt > 0) dateFormat.format(Date(res.expiresAt)) else "N/A"}", color = Color.Black, fontSize = 13.sp)
                                    Text("Hash Ledger Chain Valid: ${res.isChainValid}", color = Color.Black, fontSize = 13.sp)
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
                    Text(pendingTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pendingDesc, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    PendingApprovalItem("Vikas Kumar (EMP002)", "+919876543211", "Coal Pit #2", lang)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PendingApprovalItem("Anita Tudu (EMP003)", "+919876543212", "Mica Unit #1", lang)
                }
            }

            // Overdue Recertification Alerts List
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(overdueTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
fun PendingApprovalItem(name: String, phone: String, site: String, lang: String = "en") {
    var approved by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }

    val approveBtn = when (lang) { "hi" -> "स्वीकृत करें"; "sat" -> "ᱥᱟᱹᱠᱷᱤ"; else -> "Approve" }
    val rejectBtn = when (lang) { "hi" -> "अस्वीकृत करें"; "sat" -> "ᱵᱟᱹᱜᱤ"; else -> "Reject" }
    val approvedLabel = when (lang) { "hi" -> "स्वीकृत ✓"; "sat" -> "ᱥᱟᱹᱠᱷᱤ ᱮᱱᱟ ✓"; else -> "Approved ✓" }
    val rejectedLabel = when (lang) { "hi" -> "अस्वीकृत ✗"; "sat" -> "ᱵᱟᱹᱜᱤ ᱮᱱᱟ ✗"; else -> "Rejected ✗" }

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
            Text(approvedLabel, color = Color(0xFF166534), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else if (rejected) {
            Text(rejectedLabel, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { approved = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
                ) {
                    Text(approveBtn, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { rejected = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(rejectBtn, fontSize = 12.sp, color = Color(0xFF991B1B))
                }
            }
        }
    }
}
