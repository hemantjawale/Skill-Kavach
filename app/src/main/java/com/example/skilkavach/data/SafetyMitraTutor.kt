package com.example.skilkavach.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * SafetyMitra Voice & Multilingual Assistant for Skill-Kavach.
 * Handles Text-To-Speech (TTS), voice command parsing, and localized offline safety Q&A.
 * Supports English (en), Hindi (hi), and Santali (sat / Ol Chiki).
 *
 * CRITICAL RULE: Never silently default Santali speech to Hindi voice without user notification.
 * If Santali voice synthesis is unsupported on the host device, returns UNSUPPORTED_LANGUAGE
 * so callers display clear visual text & captions.
 */
class SafetyMitraTutor(private val context: Context) {

    enum class TtsResult {
        SUCCESS,
        NOT_INITIALIZED,
        UNSUPPORTED_LANGUAGE,
        MUTED,
        ERROR
    }

    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
            }
        }
    }

    /**
     * Checks whether the device's TTS engine supports the requested language code.
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        val engine = tts ?: return false
        val locale = getLocaleForCode(languageCode)
        val availability = engine.isLanguageAvailable(locale)
        return availability >= TextToSpeech.LANG_AVAILABLE
    }

    var voiceVolume: Float = 1.0f

    /**
     * Speaks the given instruction or warning text in the specified language.
     * Returns TtsResult status for UI handling.
     */
    fun speak(text: String, languageCode: String = "hi"): TtsResult {
        if (!isReady) return TtsResult.NOT_INITIALIZED
        val engine = tts ?: return TtsResult.ERROR

        val targetLocale = getLocaleForCode(languageCode)
        val availability = engine.isLanguageAvailable(targetLocale)

        if (availability < TextToSpeech.LANG_AVAILABLE) {
            // Language is missing or unsupported by host TTS engine (e.g. Santali engine missing)
            // DO NOT silently fall back to Hindi voice! Report UNSUPPORTED_LANGUAGE.
            return TtsResult.UNSUPPORTED_LANGUAGE
        }

        return try {
            engine.language = targetLocale
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume.coerceIn(0.0f, 1.0f))
            }
            val result = engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "safety_mitra_${System.currentTimeMillis()}"
            )
            if (result == TextToSpeech.SUCCESS) TtsResult.SUCCESS else TtsResult.ERROR
        } catch (e: Exception) {
            TtsResult.ERROR
        }
    }

    /**
     * Maps ISO language codes to Java Locales.
     */
    fun getLocaleForCode(languageCode: String): Locale {
        return when (languageCode) {
            "sat" -> Locale("sat", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.ENGLISH
        }
    }

    /**
     * Parses spoken voice commands across English, Hindi, and Santali (Ol Chiki).
     */
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

    /**
     * Localized offline safety advice for voice Q&A queries.
     */
    fun getOfflineAnswer(question: String, lang: String = "hi"): String {
        val q = question.lowercase()
        return when (lang) {
            "hi" -> when {
                q.contains("fire") || q.contains("आग") -> "विद्युत आग के लिए CO2 या शुष्क पाउडर अग्निशामक का उपयोग करें। PASS नियम: पिन खींचें, निशाना लगाएं, लीवर दबाएं, दायें-बायें घुमाएं।"
                q.contains("gas") || q.contains("गैस") -> "गैस रिसाव परिदृश्य में, 19.5% से अधिक ऑक्सीजन स्तर की जांच करें, वेंटिलेशन चालू करें, और कभी भी माचिस न जलाएं।"
                q.contains("exit") || q.contains("निकास") -> "उच्च जोखिम वाले क्षेत्रों में काम शुरू करने से पहले हमेशा प्राथमिक और द्वितीयक आपातकालीन निकास का पता लगाएं।"
                else -> "हमेशा सुरक्षा हेलमेट, उच्च-दृश्यता वास्कट और सुरक्षा जूते पहनें। साइट-विशिष्ट मंजूरी के लिए अपने सुरक्षा पर्यवेक्षक से संपर्क करें।"
            }
            "sat" -> when {
                q.contains("fire") || q.contains("ᱥᱮᱸᱜᱮᱞ") -> "ᱵᱤᱡᱽᱞᱤ ᱥᱮᱸᱜᱮᱞ ᱞᱟᱹᱜᱤᱫ CO2 ᱥᱮ ᱯᱟᱣᱰᱟᱨ ᱵᱮᱣᱦᱟᱨ ᱢᱮ᱾ PASS ᱱᱤᱭᱚᱢ: ᱯᱤᱱ ᱚᱨ, ᱵᱩᱴᱟᱹ ᱛᱟᱹᱠ, ᱞᱤᱵᱷᱟᱨ ᱞᱤᱱ, ᱜᱩᱨᱞᱟᱹᱣ ᱢᱮ᱾"
                q.contains("gas") || q.contains("ᱜᱮᱥ") -> "ᱜᱮᱥ ᱞᱤᱠ ᱚᱠᱛᱚ 19.5% ᱪᱮᱛᱟᱱ ᱚᱠᱥᱤᱡᱚᱱ ᱪᱮᱠ ᱢᱮ, ᱵᱷᱮᱱᱴᱤᱞᱮᱥᱚᱱ ᱪᱟᱹᱞᱩ ᱢᱮ ᱟᱨ ᱥᱮᱸᱜᱮᱞ ᱟᱞᱚᱢ ᱡᱩᱞᱟ᱾"
                q.contains("exit") || q.contains("ᱚᱰᱚᱠᱚᱜ") -> "ᱠᱟᱹᱢᱤ ᱮᱦᱚᱵ ᱢᱟᱲᱟᱝ ᱚᱰᱚᱠᱚᱜ ᱦᱚᱨ ᱪᱤᱱᱦᱟᱹᱣ ᱢᱮ᱾"
                else -> "ᱡᱟᱣ ᱜᱮ ᱥᱩᱨᱚᱠᱷᱟ ᱦᱮᱞᱢᱮᱴ, ᱡᱮᱠᱮᱴ ᱟᱨ ᱡᱩᱛᱟᱹ ᱦᱚᱨᱚᱜ ᱢᱮ᱾"
            }
            else -> when {
                q.contains("fire") || q.contains("flame") -> "For electrical fires, use CO2 or Dry Powder extinguishers. PASS procedure: Pull pin, Aim low, Squeeze handle, Sweep side-to-side."
                q.contains("gas") || q.contains("leak") -> "In gas leak scenarios, check oxygen levels above 19.5%, turn on ventilation, and never light matches."
                q.contains("exit") || q.contains("escape") -> "Always locate primary and secondary emergency exits before beginning shift work in high-risk zones."
                else -> "Always wear safety helmet, high-visibility vest, and safety boots. Contact your safety supervisor for site-specific clearance."
            }
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        }
    }
}
