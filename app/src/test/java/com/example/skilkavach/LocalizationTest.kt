package com.example.skilkavach

import com.example.skilkavach.data.AssessmentEngine
import com.example.skilkavach.data.LocalizationResolver
import com.example.skilkavach.data.localizedDescription
import com.example.skilkavach.data.localizedQuestionOptions
import com.example.skilkavach.data.localizedQuestionText
import com.example.skilkavach.data.localizedStepInstruction
import com.example.skilkavach.data.localizedTitle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {

    private val sampleModuleJson = JSONObject("""
        {
          "id": "fire",
          "title": {
            "en": "Fire & Explosion Response",
            "hi": "आग और विस्फोट की स्थिति में प्रतिक्रिया",
            "sat": "ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ"
          },
          "description": {
            "en": "Recognize a simulated fire.",
            "hi": "सिमुलेटेड आग को पहचानें।"
          },
          "steps": [
            {
              "target": "exit",
              "title": {
                "en": "Identify the exit",
                "hi": "निकास मार्ग की पहचान करें",
                "sat": "ᱚᱰᱚᱠᱚᱜ ᱦᱚᱨ ᱪᱤᱱᱦᱟᱹᱣ"
              },
              "instruction": {
                "en": "Keep a clear escape route behind you.",
                "hi": "अपने पीछे एक स्पष्ट निकास मार्ग रखें।",
                "sat": "ᱟᱢ ᱛᱟᱭᱚᱢ ᱨᱮ ᱥᱟᱯᱷᱟ ᱦᱚᱨ ᱫᱚᱦᱚᱭ ᱢᱮ᱾"
              }
            }
          ],
          "questions": [
            {
              "id": "f1",
              "text": {
                "en": "Before attempting to tackle a small fire, what must you have?",
                "hi": "छोटी आग बुझाने का प्रयास करने से पहले आपके पास क्या होना चाहिए?",
                "sat": "ᱥᱮᱸᱜᱮᱞ ᱵᱩᱡᱷᱟᱹᱣ ᱞᱟᱦᱟ ᱪᱮᱫ ᱛᱟᱦᱮᱸᱱ ᱞᱟᱹᱠᱛᱤᱭᱟ?"
              },
              "options": [
                {
                  "en": "A clear escape route and site authorization",
                  "hi": "एक स्पष्ट निकास मार्ग और स्थल प्राधिकरण",
                  "sat": "ᱥᱟᱯᱷᱟ ᱚᱰᱚᱠᱚᱜ ᱦᱚᱨ ᱟᱨ ᱪᱷᱟᱹᱴᱭᱟᱹᱨ"
                },
                {
                  "en": "A closed exit",
                  "hi": "एक बंद निकास द्वार",
                  "sat": "ᱵᱚᱸᱫᱽ ᱚᱰᱚᱠᱚᱜ ᱦᱚᱨ"
                }
              ]
            }
          ]
        }
    """.trimIndent())

    @Test
    fun testEnglishResolution() {
        val title = sampleModuleJson.localizedTitle("en")
        assertEquals("Fire & Explosion Response", title)
    }

    @Test
    fun testHindiResolution() {
        val title = sampleModuleJson.localizedTitle("hi")
        assertEquals("आग और विस्फोट की स्थिति में प्रतिक्रिया", title)
    }

    @Test
    fun testSantaliResolution() {
        val title = sampleModuleJson.localizedTitle("sat")
        assertEquals("ᱥᱮᱸᱜᱮᱞ ᱟᱨ ᱵᱚᱢ ᱯᱟᱹᱥᱱᱟᱹᱣ ᱨᱩᱠᱷᱤᱭᱟᱹ", title)
    }

    @Test
    fun testMissingTranslationFallbackToEnglish() {
        // Description has "en" and "hi", but missing "sat"
        val description = sampleModuleJson.localizedDescription("sat")
        assertEquals("Recognize a simulated fire.", description)
    }

    @Test
    fun testNullOrEmptySafetyResolver() {
        val resultNull = LocalizationResolver.getLocalizedText(null, "hi")
        assertEquals("", resultNull)

        val emptyJson = JSONObject()
        val resultEmpty = LocalizationResolver.getLocalizedText(emptyJson, "sat")
        assertEquals("", resultEmpty)
    }

    @Test
    fun testLocalizedQuestionAndOptions() {
        val questionsArray = sampleModuleJson.getJSONArray("questions")
        val question0 = questionsArray.getJSONObject(0)

        val questionHi = question0.localizedQuestionText("hi")
        assertTrue(questionHi.contains("छोटी आग"))

        val optionsHi = question0.localizedQuestionOptions("hi")
        assertEquals(2, optionsHi.size)
        assertEquals("एक स्पष्ट निकास मार्ग और स्थल प्राधिकरण", optionsHi[0])

        val optionsSat = question0.localizedQuestionOptions("sat")
        assertEquals(2, optionsSat.size)
        assertEquals("ᱥᱟᱯᱷᱟ ᱚᱰᱚᱠᱚᱜ ᱦᱚᱨ ᱟᱨ ᱪᱷᱟᱹᱴᱭᱟᱹᱨ", optionsSat[0])
    }

    @Test
    fun testLanguageAwareAssessmentKeywords() {
        val engine = AssessmentEngine()
        val enKeywords = engine.getRequiredKeywords("fire", "en")
        assertTrue(enKeywords.contains("exit"))

        val hiKeywords = engine.getRequiredKeywords("fire", "hi")
        assertTrue(hiKeywords.contains("निकास"))

        val satKeywords = engine.getRequiredKeywords("fire", "sat")
        assertTrue(satKeywords.contains("ᱚᱰᱚᱠᱚᱜ"))

        val hiResult = engine.computeFinalAssessment(
            quizScore = 90.0f,
            transcript = "मैंने निकास मार्ग की पहचान की और अलार्म बजाया",
            requiredKeywords = hiKeywords,
            lang = "hi"
        )
        assertTrue(hiResult.passed)
        assertTrue(hiResult.breakdown.contains("प्रश्नोत्तरी"))
    }

    @Test
    fun testOfflineTrainingResolutionInAllLanguages() {
        listOf("en", "hi", "sat").forEach { lang ->
            val title = sampleModuleJson.localizedTitle(lang)
            val desc = sampleModuleJson.localizedDescription(lang)
            assertNotNull(title)
            assertNotNull(desc)
            assertTrue(title.isNotEmpty())
            assertTrue(desc.isNotEmpty())
        }
    }
}
