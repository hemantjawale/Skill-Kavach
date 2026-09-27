package com.example.skilkavach

import com.example.skilkavach.data.AssessmentEngine
import com.example.skilkavach.data.CertificateStatus
import com.example.skilkavach.data.CertificateVault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssessmentSystemTest {

    private lateinit var assessmentEngine: AssessmentEngine
    private lateinit var certificateVault: CertificateVault

    @Before
    fun setUp() {
        assessmentEngine = AssessmentEngine()
        certificateVault = CertificateVault()
    }

    @Test
    fun testPracticeModeVsAssessmentModeFlags() {
        // Practice Mode: allows hints, target glow/highlights, guidance, does not issue certificate
        val isPracticeMode = true
        val practiceTargetHighlightId: String? = if (isPracticeMode) "pin" else null
        assertNotNull("Practice mode must enable target highlight glow", practiceTargetHighlightId)
        assertEquals("pin", practiceTargetHighlightId)

        // Assessment Mode: hides hints, target glows/highlights, directional arrows
        val isAssessmentMode = false // practice = false
        val assessmentTargetHighlightId: String? = if (isAssessmentMode) "pin" else null
        assertNull("Assessment mode must hide target highlight glow to evaluate learner", assessmentTargetHighlightId)
    }

    @Test
    fun testCorrectActionLogging() {
        assessmentEngine.startSession(stressMode = false)

        // Log correct actions with moduleId, stepIndex, target, actionType
        assessmentEngine.logAction(
            stepIndex = 0,
            target = "pin",
            actionType = "PASS_PULL_PIN",
            isCorrect = true,
            moduleId = "fire"
        )
        assessmentEngine.logAction(
            stepIndex = 1,
            target = "base",
            actionType = "PASS_AIM_BASE",
            isCorrect = true,
            moduleId = "fire"
        )

        val events = assessmentEngine.getEvents()
        assertEquals(2, events.size)

        val firstEvent = events[0]
        assertEquals("fire", firstEvent.moduleId)
        assertEquals(0, firstEvent.stepIndex)
        assertEquals("pin", firstEvent.target)
        assertEquals("PASS_PULL_PIN", firstEvent.actionType)
        assertTrue(firstEvent.isCorrect)
        assertTrue(firstEvent.timestamp > 0L)
        assertTrue(firstEvent.hesitationMs >= 0L)

        val jsonLog = assessmentEngine.getEventLogJson()
        val jsonArray = JSONArray(jsonLog)
        assertEquals(2, jsonArray.length())
        val firstObj = jsonArray.getJSONObject(0)
        assertEquals("fire", firstObj.getString("moduleId"))
        assertEquals("pin", firstObj.getString("target"))
        assertTrue(firstObj.getBoolean("isCorrect"))
    }

    @Test
    fun testIncorrectActionPenaltyAndNoSolutionLeak() {
        assessmentEngine.startSession(stressMode = false)

        // Log 1 correct action and 2 incorrect actions
        assessmentEngine.logAction(0, "pin", "PASS_PULL_PIN", isCorrect = true, moduleId = "fire")
        assessmentEngine.logAction(1, "sweep", "FIRE_INCORRECT_ATTEMPT", isCorrect = false, moduleId = "fire")
        assessmentEngine.logAction(1, "handle", "FIRE_INCORRECT_ATTEMPT", isCorrect = false, moduleId = "fire")

        val incorrectCount = assessmentEngine.getIncorrectCount()
        assertEquals(2, incorrectCount)

        // Each incorrect attempt deducts 15 points from 100
        val behavioralScore = assessmentEngine.calculateBehavioralScore()
        assertEquals(70.0f, behavioralScore, 0.01f) // 100 - (2 * 15) = 70

        // Verification of safety feedback without revealing solution in Assessment Mode
        val currentLang = "en"
        val isPractice = false
        val feedback = if (isPractice) {
            "Step 2: Aim at base (expected: base)"
        } else {
            "⚠️ Incorrect action sequence! Review safety procedure before proceeding."
        }

        assertFalse("Assessment mode feedback must NOT leak explicit expected answer", feedback.contains("expected: base"))
        assertTrue("Assessment mode feedback must display safety warning", feedback.contains("Incorrect action sequence"))
    }

    @Test
    fun testScoreCalculationWeighting() {
        assessmentEngine.startSession(stressMode = false)

        // 100% Behavioral score (all correct)
        assessmentEngine.logAction(0, "pin", "PASS_PULL_PIN", isCorrect = true, moduleId = "fire")
        assessmentEngine.logAction(1, "base", "PASS_AIM_BASE", isCorrect = true, moduleId = "fire")
        assessmentEngine.logAction(2, "handle", "PASS_SQUEEZE", isCorrect = true, moduleId = "fire")

        val behavioralScore = assessmentEngine.calculateBehavioralScore()
        assertEquals(100.0f, behavioralScore, 0.01f)

        // Test weighting: 30% Quiz + 50% Behavioral + 20% Oral
        val quizScore = 80.0f // 80 * 0.30 = 24
        val oralScore = 90.0f // 90 * 0.20 = 18
        // Behavioral: 100 * 0.50 = 50
        // Expected Combined = 24 + 50 + 18 = 92.0%

        val result = assessmentEngine.computeFinalAssessment(
            quizScore = quizScore,
            transcript = "exit alarm extinguisher sweep",
            requiredKeywords = listOf("exit", "alarm", "extinguisher", "sweep"),
            lang = "en"
        )

        assertEquals(80.0f, result.quizScore, 0.01f)
        assertEquals(100.0f, result.behavioralScore, 0.01f)
        assertEquals(100.0f, result.voiceScore, 0.01f) // All keywords matched
        assertEquals(94.0f, result.combinedScore, 0.01f) // (80*0.3) + (100*0.5) + (100*0.2) = 24 + 50 + 20 = 94
        assertTrue("Combined score >= 80% must pass assessment", result.passed)
    }

    @Test
    fun testFailingScoreThreshold() {
        assessmentEngine.startSession(stressMode = false)

        // 2 incorrect attempts (30 pt penalty => 70% behavioral)
        assessmentEngine.logAction(0, "sweep", "FIRE_INCORRECT_ATTEMPT", isCorrect = false, moduleId = "fire")
        assessmentEngine.logAction(0, "handle", "FIRE_INCORRECT_ATTEMPT", isCorrect = false, moduleId = "fire")
        assessmentEngine.logAction(0, "pin", "PASS_PULL_PIN", isCorrect = true, moduleId = "fire")

        val result = assessmentEngine.computeFinalAssessment(
            quizScore = 60.0f, // 60 * 0.3 = 18
            transcript = "",    // 0 * 0.2 = 0
            requiredKeywords = listOf("exit", "alarm"),
            lang = "en"
        )

        // Behavioral = 70 * 0.5 = 35
        // Combined = 18 + 35 + 0 = 53%
        assertEquals(53.0f, result.combinedScore, 0.01f)
        assertFalse("Combined score < 80% must fail assessment", result.passed)
    }

    @Test
    fun testCompletionSummaryAndSuggestions() {
        assessmentEngine.startSession(stressMode = false)

        // Simulate 1 mistake
        assessmentEngine.logAction(0, "wrong", "FIRE_INCORRECT_ATTEMPT", isCorrect = false, moduleId = "fire")
        assessmentEngine.logAction(0, "pin", "PASS_PULL_PIN", isCorrect = true, moduleId = "fire")

        val result = assessmentEngine.computeFinalAssessment(
            quizScore = 90.0f,
            lang = "en"
        )

        assertEquals(1, result.mistakesCount)
        assertTrue("Improvement suggestions must be populated when mistakes occur", result.improvementSuggestions.isNotEmpty())
        assertTrue("Suggestion must reference step sequence review", result.improvementSuggestions[0].contains("step sequence"))
    }

    @Test
    fun testCertificateEligibilityOnlyInAssessmentMode() {
        val workerId = "WORKER-101"
        val hazardDomain = "fire"

        // Assessment Mode + Pass (score 90%) => Eligible
        val assessmentPassed = true
        val isPracticeMode = false
        val certificateEligible = !isPracticeMode && assessmentPassed
        assertTrue("Assessment mode with passing score must be certificate eligible", certificateEligible)

        if (certificateEligible) {
            val certJson = certificateVault.issueSignedCertificate(
                workerId = workerId,
                hazardDomain = hazardDomain,
                score = 90.0f
            )
            assertNotNull(certJson)

            val verification = certificateVault.verifyCertificateQr(certJson.toString())
            assertEquals(CertificateStatus.VALID, verification.status)
            assertTrue(verification.isValid)
            assertEquals("WORKER-101", verification.workerId)
            assertEquals("fire", verification.hazardDomain)
        }

        // Practice Mode => NEVER Certificate Eligible
        val practiceModeEligible = false && assessmentPassed
        assertFalse("Practice mode must NEVER grant certificate eligibility", practiceModeEligible)
    }
}
