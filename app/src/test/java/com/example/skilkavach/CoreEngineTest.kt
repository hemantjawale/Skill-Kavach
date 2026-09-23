package com.example.skilkavach

import com.example.skilkavach.ar.HazardDiffusionSimulator
import com.example.skilkavach.data.AssessmentEngine
import com.example.skilkavach.data.CertificateVault
import org.junit.Assert.*
import org.junit.Test

class CoreEngineTest {

    @Test
    fun testHazardDiffusionSimulator() {
        val simulator = HazardDiffusionSimulator(width = 10, height = 10, diffusionRate = 0.5f, dissipationRate = 0.01f)
        simulator.setObstacle(5, 5, true)
        simulator.injectHazard(4, 5, 0.8f)

        // Initial hazard at (4,5)
        assertEquals(0.8f, simulator.getConcentration(4, 5), 0.01f)
        assertEquals(0.0f, simulator.getConcentration(5, 5), 0.01f) // Obstacle

        // Step cellular automaton
        simulator.step()

        // Hazard should diffuse to neighbors, but obstacle at (5,5) remains zero
        assertEquals(0.0f, simulator.getConcentration(5, 5), 0.001f)
        assertTrue(simulator.getConcentration(3, 5) > 0.0f)
    }

    @Test
    fun testAssessmentEngineScoring() {
        val engine = AssessmentEngine()
        engine.startSession(stressMode = true)

        engine.logAction(stepIndex = 0, target = "exit", actionType = "TAP", isCorrect = true)
        engine.logAction(stepIndex = 1, target = "alarm", actionType = "TAP", isCorrect = true)

        val behavioralScore = engine.calculateBehavioralScore()
        assertTrue(behavioralScore >= 80.0f)

        val oralScore = engine.scoreOralExplanation(
            transcript = "I identified the emergency exit first then pressed the alarm button to alert workers",
            requiredKeywords = listOf("exit", "alarm", "workers")
        )
        assertEquals(100.0f, oralScore, 0.01f)

        val finalResult = engine.computeFinalAssessment(
            quizScore = 90.0f,
            transcript = "I identified the exit and pressed alarm",
            requiredKeywords = listOf("exit", "alarm")
        )
        assertTrue(finalResult.passed)
        assertTrue(finalResult.combinedScore >= 80.0f)
    }

    @Test
    fun testCertificateHashChainingAndDecay() {
        val vault = CertificateVault()
        val hash1 = vault.computeSha256("WORKER001_FIRE_SAFETY")
        val hash2 = vault.computeSha256("WORKER001_GAS_SAFETY_$hash1")

        assertNotNull(hash1)
        assertNotNull(hash2)
        assertNotEquals(hash1, hash2)

        // Confidence score decay test
        val issuedNow = System.currentTimeMillis()
        val currentConfidence = vault.calculateCurrentConfidence(issuedNow, 100.0f)
        assertEquals(100.0f, currentConfidence, 0.1f)

        // 180 days in past (~50% confidence remaining)
        val issued180DaysAgo = issuedNow - (180L * 24 * 60 * 60 * 1000L)
        val decayedConfidence = vault.calculateCurrentConfidence(issued180DaysAgo, 100.0f)
        assertTrue(decayedConfidence < 60.0f && decayedConfidence > 40.0f)
    }
}
