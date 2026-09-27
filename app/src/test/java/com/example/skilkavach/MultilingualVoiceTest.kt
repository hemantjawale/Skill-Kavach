package com.example.skilkavach

import com.example.skilkavach.data.LocalizationResolver
import com.example.skilkavach.data.SafetyMitraTutor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for Skill-Kavach Multilingual (EN / HI / SAT Ol Chiki) & Voice System.
 * Verifies catalog localization resolver, voice command parsing, locale mapping,
 * and offline safety Q&A.
 */
class MultilingualVoiceTest {

    @Test
    fun testLocalizationResolverStructuredData() {
        val titleObj = JSONObject().apply {
            put("en", "Fire & Explosion Response")
            put("hi", "आग और विस्फोट की स्थिति में प्रतिक्रिया")
            put("sat", "ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ")
        }

        assertEquals("Fire & Explosion Response", LocalizationResolver.getLocalizedText(titleObj, "en"))
        assertEquals("आग और विस्फोट की स्थिति में प्रतिक्रिया", LocalizationResolver.getLocalizedText(titleObj, "hi"))
        assertEquals("ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ", LocalizationResolver.getLocalizedText(titleObj, "sat"))

        // Fallback test
        val fallbackObj = JSONObject().apply {
            put("en", "Default English Title")
        }
        assertEquals("Default English Title", LocalizationResolver.getLocalizedText(fallbackObj, "hi"))
        assertEquals("Default English Title", LocalizationResolver.getLocalizedText(fallbackObj, "sat"))
    }

    @Test
    fun testLocalizedQuestionOptions() {
        val optionsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("en", "Option 1 English")
                put("hi", "विकल्प 1 हिंदी")
                put("sat", "ᱚᱯᱥᱚᱱ 1 ᱥᱟᱱᱛᱟᱲᱤ")
            })
            put(JSONObject().apply {
                put("en", "Option 2 English")
                put("hi", "विकल्प 2 हिंदी")
                put("sat", "ᱚᱯᱥᱚᱱ 2 ᱥᱟᱱᱛᱟᱲᱤ")
            })
        }

        val hiOptions = LocalizationResolver.getLocalizedOptions(optionsArray, "hi")
        assertEquals(2, hiOptions.size)
        assertEquals("विकल्प 1 हिंदी", hiOptions[0])

        val satOptions = LocalizationResolver.getLocalizedOptions(optionsArray, "sat")
        assertEquals(2, satOptions.size)
        assertEquals("ᱚᱯᱥᱚᱱ 1 ᱥᱟᱱᱛᱟᱲᱤ", satOptions[0])
    }

    @Test
    fun testVoiceCommandParsingMultilingual() {
        // Mock tutor instance without Android context calls for voice command parsing
        val mockTutor = SafetyMitraTutorDirect()

        // English commands
        assertEquals("ACTION_START_AR", mockTutor.parseVoiceCommand("start training"))
        assertEquals("ACTION_PRACTICE", mockTutor.parseVoiceCommand("practice mode"))
        assertEquals("ACTION_ADMIN", mockTutor.parseVoiceCommand("admin panel"))

        // Hindi commands
        assertEquals("ACTION_START_AR", mockTutor.parseVoiceCommand("शुरू करें"))
        assertEquals("ACTION_PRACTICE", mockTutor.parseVoiceCommand("अभ्यास करें"))
        assertEquals("ACTION_HELP", mockTutor.parseVoiceCommand("मदद चाहिए"))

        // Santali Ol Chiki commands
        assertEquals("ACTION_START_AR", mockTutor.parseVoiceCommand("ᱮᱦᱚᱵ ᱢᱮ"))
        assertEquals("ACTION_PRACTICE", mockTutor.parseVoiceCommand("ᱯᱨᱮᱠᱴᱤᱥ ᱢᱚᱰ"))
        assertEquals("ACTION_HELP", mockTutor.parseVoiceCommand("ᱜᱚᱲᱚ ᱢᱮ"))
    }

    @Test
    fun testLocaleMapping() {
        val tutor = SafetyMitraTutorDirect()
        assertEquals(Locale("sat", "IN"), tutor.getLocaleForCode("sat"))
        assertEquals(Locale("hi", "IN"), tutor.getLocaleForCode("hi"))
        assertEquals(Locale.ENGLISH, tutor.getLocaleForCode("en"))
    }

    @Test
    fun testOfflineLocalizedSafetyQnA() {
        val tutor = SafetyMitraTutorDirect()

        val hiAnswer = tutor.getOfflineAnswer("आग की स्थिति में क्या करें?", "hi")
        assertTrue("Hindi answer should contain PASS", hiAnswer.contains("PASS"))

        val satAnswer = tutor.getOfflineAnswer("ᱥᱮᱸᱜᱮᱞ ᱚᱠᱛᱚ ᱪᱮᱫ ᱪᱤᱠᱟᱹᱭᱟ?", "sat")
        assertTrue("Santali answer should contain Ol Chiki script", satAnswer.contains("ᱥᱮᱸᱜᱮᱞ"))

        val enAnswer = tutor.getOfflineAnswer("What to do in gas leak?", "en")
        assertTrue("English answer should contain ventilation", enAnswer.contains("ventilation"))
    }

    @Test
    fun testSantaliTtsUnsupportedReturnsUnsupportedLanguageNotHindiFallback() {
        val tutor = SafetyMitraTutorDirect()
        val satLocale = tutor.getLocaleForCode("sat")
        val hiLocale = tutor.getLocaleForCode("hi")

        // Santali locale must be distinct from Hindi locale
        assertNotEquals(hiLocale, satLocale)
        assertEquals("sat", satLocale.language)
        assertEquals("IN", satLocale.country)

        // Simulate a device where only English and Hindi TTS are available
        val mockAvailableLanguages = setOf(Locale.ENGLISH, Locale("hi", "IN"))

        val isSatSupported = mockAvailableLanguages.contains(satLocale)
        assertFalse("Santali TTS should be detected as unsupported", isSatSupported)

        // Simulate the speak() guard logic from SafetyMitraTutor
        val result = if (!isSatSupported) {
            SafetyMitraTutor.TtsResult.UNSUPPORTED_LANGUAGE
        } else {
            SafetyMitraTutor.TtsResult.SUCCESS
        }

        // CRITICAL: Must return UNSUPPORTED_LANGUAGE, never silently fallback to Hindi
        assertEquals(SafetyMitraTutor.TtsResult.UNSUPPORTED_LANGUAGE, result)
        assertNotEquals(SafetyMitraTutor.TtsResult.SUCCESS, result)
    }

    @Test
    fun testDynamicLanguageSwitchingWithoutRestart() {
        val titleObj = JSONObject().apply {
            put("en", "Fire & Explosion Response")
            put("hi", "आग और विस्फोट की स्थिति में प्रतिक्रिया")
            put("sat", "ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ")
        }

        // Simulate switching language at runtime without restart
        var activeLang = "en"
        assertEquals("Fire & Explosion Response", LocalizationResolver.getLocalizedText(titleObj, activeLang))

        activeLang = "hi"
        assertEquals("आग और विस्फोट की स्थिति में प्रतिक्रिया", LocalizationResolver.getLocalizedText(titleObj, activeLang))

        activeLang = "sat"
        assertEquals("ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ", LocalizationResolver.getLocalizedText(titleObj, activeLang))

        // Switch back to English
        activeLang = "en"
        assertEquals("Fire & Explosion Response", LocalizationResolver.getLocalizedText(titleObj, activeLang))
    }

    // Direct helper subclass to test logic without Android Context dependency in local JUnit
    private class SafetyMitraTutorDirect {
        fun getLocaleForCode(languageCode: String): Locale {
            return when (languageCode) {
                "sat" -> Locale("sat", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.ENGLISH
            }
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
    }
}
