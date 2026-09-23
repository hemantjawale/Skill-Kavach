package com.example.skilkavach.data

import org.json.JSONArray
import org.json.JSONObject

data class BehavioralEvent(
    val stepIndex: Int,
    val target: String,
    val actionType: String,
    val timestamp: Long,
    val hesitationMs: Long,
    val isCorrect: Boolean
)

data class FinalAssessmentResult(
    val quizScore: Float,
    val behavioralScore: Float,
    val voiceScore: Float,
    val combinedScore: Float,
    val passed: Boolean,
    val breakdown: String
)

class AssessmentEngine {
    private val events = mutableListOf<BehavioralEvent>()
    private var lastActionTime = System.currentTimeMillis()
    var isStressModeActive: Boolean = false

    fun startSession(stressMode: Boolean = false) {
        events.clear()
        lastActionTime = System.currentTimeMillis()
        isStressModeActive = stressMode
    }

    fun logAction(stepIndex: Int, target: String, actionType: String, isCorrect: Boolean) {
        val now = System.currentTimeMillis()
        val hesitationMs = now - lastActionTime
        lastActionTime = now

        events.add(
            BehavioralEvent(
                stepIndex = stepIndex,
                target = target,
                actionType = actionType,
                timestamp = System.currentTimeMillis(),
                hesitationMs = hesitationMs,
                isCorrect = isCorrect
            )
        )
    }

    /**
     * Calculates behavioral score based on hesitation times and accuracy.
     * Penalty applied for hesitation > 5000ms per step or incorrect action attempts.
     */
    fun calculateBehavioralScore(): Float {
        if (events.isEmpty()) return 100.0f

        var totalPoints = 100.0f
        val correctCount = events.count { it.isCorrect }
        val incorrectCount = events.size - correctCount

        // Accuracy penalty
        totalPoints -= (incorrectCount * 15.0f)

        // Hesitation penalty (ideal reaction < 3000ms per action)
        val avgHesitation = events.map { it.hesitationMs }.average()
        if (avgHesitation > 4000) {
            val extraSeconds = ((avgHesitation - 4000) / 1000).toFloat()
            totalPoints -= (extraSeconds * 3.0f)
        }

        return totalPoints.coerceIn(0f, 100f)
    }

    /**
     * Oral assessment rubric matcher scoring voice explanations against required safety keywords.
     */
    fun scoreOralExplanation(transcript: String, requiredKeywords: List<String>): Float {
        if (transcript.isBlank() || requiredKeywords.isEmpty()) return 0.0f

        val lowerTranscript = transcript.lowercase()
        val matchedKeywords = requiredKeywords.count { keyword ->
            lowerTranscript.contains(keyword.lowercase())
        }

        val matchRatio = matchedKeywords.toFloat() / requiredKeywords.size
        return (matchRatio * 100.0f).coerceIn(0f, 100f)
    }

    /**
     * Computes combined score:
     * 30% Quiz + 50% Practical Behavioral + 20% Oral Voice Explanation
     */
    fun computeFinalAssessment(
        quizScore: Float,
        transcript: String = "",
        requiredKeywords: List<String> = emptyList()
    ): FinalAssessmentResult {
        val behavioralScore = calculateBehavioralScore()
        val voiceScore = if (requiredKeywords.isNotEmpty()) {
            scoreOralExplanation(transcript, requiredKeywords)
        } else {
            behavioralScore // Fallback if no oral assessment required
        }

        val combinedScore = (quizScore * 0.30f) + (behavioralScore * 0.50f) + (voiceScore * 0.20f)
        val passed = combinedScore >= 80.0f

        val breakdown = "Quiz: ${quizScore.toInt()}%, Behavioral: ${behavioralScore.toInt()}%, Oral: ${voiceScore.toInt()}%"

        return FinalAssessmentResult(
            quizScore = quizScore,
            behavioralScore = behavioralScore,
            voiceScore = voiceScore,
            combinedScore = combinedScore,
            passed = passed,
            breakdown = breakdown
        )
    }

    fun getEventLogJson(): String {
        val array = JSONArray()
        for (e in events) {
            array.put(JSONObject().apply {
                put("step", e.stepIndex)
                put("target", e.target)
                put("action", e.actionType)
                put("hesitationMs", e.hesitationMs)
                put("isCorrect", e.isCorrect)
                put("timestamp", e.timestamp)
            })
        }
        return array.toString()
    }
}
