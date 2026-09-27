package com.example.skilkavach.data

import org.json.JSONArray
import org.json.JSONObject

data class BehavioralEvent(
    val stepIndex: Int,
    val target: String,
    val actionType: String,
    val timestamp: Long,
    val hesitationMs: Long,
    val isCorrect: Boolean,
    val moduleId: String = ""
)

data class FinalAssessmentResult(
    val quizScore: Float,
    val behavioralScore: Float,
    val voiceScore: Float,
    val combinedScore: Float,
    val passed: Boolean,
    val breakdown: String,
    val mistakesCount: Int = 0,
    val averageHesitationMs: Long = 0L,
    val improvementSuggestions: List<String> = emptyList()
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

    fun logAction(
        stepIndex: Int,
        target: String,
        actionType: String,
        isCorrect: Boolean,
        moduleId: String = ""
    ) {
        val now = System.currentTimeMillis()
        val hesitationMs = now - lastActionTime
        lastActionTime = now

        events.add(
            BehavioralEvent(
                stepIndex = stepIndex,
                target = target,
                actionType = actionType,
                timestamp = now,
                hesitationMs = hesitationMs,
                isCorrect = isCorrect,
                moduleId = moduleId
            )
        )
    }

    fun getEvents(): List<BehavioralEvent> = events.toList()

    fun getIncorrectCount(): Int = events.count { !it.isCorrect }

    fun getCorrectCount(): Int = events.count { it.isCorrect }

    fun getAverageHesitationMs(): Long {
        if (events.isEmpty()) return 0L
        return events.map { it.hesitationMs }.average().toLong()
    }

    fun generateImprovementSuggestions(lang: String = "en"): List<String> {
        val suggestions = mutableListOf<String>()
        val incorrectCount = getIncorrectCount()
        val avgHesitation = getAverageHesitationMs()

        if (incorrectCount > 0) {
            val msg = when (lang) {
                "hi" -> "प्रक्रिया चरण अनुक्रम की समीक्षा करें (गलतियां: $incorrectCount)"
                "sat" -> "ᱠᱟᱹᱢᱤᱦᱚᱨᱟ ᱫᱷᱟᱯ ᱧᱮᱞ ᱢᱮ (ᱵᱟᱹᱲᱤᱡ: $incorrectCount)"
                else -> "Review procedural step sequence (incorrect actions: $incorrectCount)"
            }
            suggestions.add(msg)
        }

        if (avgHesitation > 4000) {
            val msg = when (lang) {
                "hi" -> "आपातकालीन प्रतिक्रिया गति में सुधार करें (औसत हिचकिचाहट: ${avgHesitation / 1000}s)"
                "sat" -> "ᱞᱟᱹᱠᱛᱤᱭᱟᱱ ᱠᱟᱹᱢᱤ ᱩᱥᱟᱹᱨᱟᱭ ᱢᱮ (ᱚᱠᱛᱚ: ${avgHesitation / 1000}s)"
                else -> "Improve emergency reaction speed (average hesitation: ${avgHesitation / 1000}s)"
            }
            suggestions.add(msg)
        }

        if (suggestions.isEmpty()) {
            val msg = when (lang) {
                "hi" -> "उत्कृष्ट सुरक्षा प्रदर्शन! निरंतर सतर्कता बनाए रखें।"
                "sat" -> "ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱥᱩᱨᱚᱠᱷᱟ ᱠᱟᱹᱢᱤ! ᱱᱟᱯᱟᱭ ᱛᱟᱦᱮᱸᱱ ᱢᱮ᱾"
                else -> "Excellent safety protocol execution! Maintain vigilance."
            }
            suggestions.add(msg)
        }

        return suggestions
    }

    /**
     * Calculates behavioral score based on hesitation times and accuracy.
     * Penalty applied for hesitation > 4000ms per step or incorrect action attempts.
     */
    fun calculateBehavioralScore(): Float {
        if (events.isEmpty()) return 100.0f

        var totalPoints = 100.0f
        val correctCount = events.count { it.isCorrect }
        val incorrectCount = events.size - correctCount

        // Accuracy penalty
        totalPoints -= (incorrectCount * 15.0f)

        // Hesitation penalty (ideal reaction < 4000ms per action)
        val avgHesitation = events.map { it.hesitationMs }.average()
        if (avgHesitation > 4000) {
            val extraSeconds = ((avgHesitation - 4000) / 1000).toFloat()
            totalPoints -= (extraSeconds * 3.0f)
        }

        return totalPoints.coerceIn(0f, 100f)
    }

    /**
     * Returns language-aware keywords for oral voice assessment rubric.
     */
    fun getRequiredKeywords(moduleId: String, lang: String): List<String> {
        return when (moduleId) {
            "fire" -> when (lang) {
                "hi" -> listOf("निकास", "अलार्म", "अग्निशामक", "सुरक्षा")
                "sat" -> listOf("ᱚᱰᱚᱠᱚᱜ", "ᱟᱞᱟᱨᱢ", "ᱥᱮᱸᱜᱮᱞ", "ᱥᱩᱨᱚᱠᱷᱟ")
                else -> listOf("exit", "alarm", "extinguisher", "sweep")
            }
            "gas" -> when (lang) {
                "hi" -> listOf("खतरा", "परमिट", "वायुमंडल", "निकासी")
                "sat" -> listOf("ᱵᱚᱛᱚᱨ", "ᱯᱚᱨᱢᱤᱴ", "ᱦᱚᱭ", "ᱚᱰᱚᱠᱚᱜ")
                else -> listOf("hazard", "permit", "atmosphere", "evacuate")
            }
            else -> listOf("safety", "alarm", "exit")
        }
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
        requiredKeywords: List<String> = emptyList(),
        lang: String = "en"
    ): FinalAssessmentResult {
        val behavioralScore = calculateBehavioralScore()
        val voiceScore = if (requiredKeywords.isNotEmpty()) {
            scoreOralExplanation(transcript, requiredKeywords)
        } else {
            behavioralScore // Fallback if no oral assessment required
        }

        val combinedScore = (quizScore * 0.30f) + (behavioralScore * 0.50f) + (voiceScore * 0.20f)
        val passed = combinedScore >= 80.0f
        val mistakes = getIncorrectCount()
        val avgHesitation = getAverageHesitationMs()
        val suggestions = generateImprovementSuggestions(lang)

        val breakdown = when (lang) {
            "hi" -> "प्रश्नोत्तरी: ${quizScore.toInt()}%, व्यावहारिक: ${behavioralScore.toInt()}%, मौखिक: ${voiceScore.toInt()}%"
            "sat" -> "ᱡᱟᱹᱥᱛᱤ: ${quizScore.toInt()}%, ᱠᱟᱹᱢᱤ: ${behavioralScore.toInt()}%, ᱨᱚᱲ: ${voiceScore.toInt()}%"
            else -> "Quiz: ${quizScore.toInt()}%, Behavioral: ${behavioralScore.toInt()}%, Oral: ${voiceScore.toInt()}%"
        }

        return FinalAssessmentResult(
            quizScore = quizScore,
            behavioralScore = behavioralScore,
            voiceScore = voiceScore,
            combinedScore = combinedScore,
            passed = passed,
            breakdown = breakdown,
            mistakesCount = mistakes,
            averageHesitationMs = avgHesitation,
            improvementSuggestions = suggestions
        )
    }

    fun getEventLogJson(): String {
        val array = JSONArray()
        for (e in events) {
            array.put(JSONObject().apply {
                if (e.moduleId.isNotEmpty()) put("moduleId", e.moduleId)
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
