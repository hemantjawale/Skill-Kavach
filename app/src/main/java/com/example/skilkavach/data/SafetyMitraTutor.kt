package com.example.skilkavach.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SafetyMitraTutor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.language = Locale("hi", "IN")
            }
        }
    }

    fun speak(text: String, languageCode: String = "hi") {
        if (!isReady) return
        val locale = when (languageCode) {
            "sat" -> Locale("hi", "IN") // Santali fallback to regional voice
            "en" -> Locale.ENGLISH
            else -> Locale("hi", "IN")
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "safety_mitra_${System.currentTimeMillis()}")
    }

    fun parseVoiceCommand(spokenText: String): String {
        val input = spokenText.lowercase()
        return when {
            input.contains("start") || input.contains("शुरू") || input.contains("ᱮᱦᱚᱵ") -> "ACTION_START_AR"
            input.contains("practice") || input.contains("अभ्यास") || input.contains("ᱯᱨᱮᱠᱴᱤᱥ") -> "ACTION_PRACTICE"
            input.contains("verify") || input.contains("जांच") || input.contains("ᱪᱮᱠ") -> "ACTION_VERIFY_QR"
            input.contains("admin") || input.contains("मालिक") || input.contains("ᱢᱮᱱᱮᱡᱚᱨ") -> "ACTION_ADMIN"
            input.contains("help") || input.contains("मदद") || input.contains("ᱜᱚᱲᱚ") -> "ACTION_HELP"
            else -> "ACTION_UNKNOWN"
        }
    }

    fun getOfflineAnswer(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("fire") || q.contains("आग") -> "For electrical fires, use CO2 or Dry Powder extinguishers. PASS procedure: Pull pin, Aim low, Squeeze handle, Sweep side-to-side."
            q.contains("gas") || q.contains("गैस") -> "In gas leak scenarios, check oxygen levels above 19.5%, turn on ventilation, and never light matches."
            q.contains("exit") || q.contains("निकास") -> "Always locate primary and secondary emergency exits before beginning shift work in high-risk zones."
            else -> "Always wear safety helmet, high-visibility vest, and safety boots. Contact your safety supervisor for site-specific clearance."
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
