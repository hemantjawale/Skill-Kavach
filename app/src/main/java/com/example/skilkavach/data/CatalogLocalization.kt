package com.example.skilkavach.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reusable localization resolver for Skill-Kavach data objects.
 * Resolves localized text using priority: requested language (hi/sat) -> English (en) -> fallback string.
 * Guaranteed never to throw an exception or crash during resolution.
 */
object LocalizationResolver {
    fun getLocalizedText(element: Any?, lang: String): String {
        if (element == null) return ""
        if (element is JSONObject) {
            val target = element.optString(lang)
            if (target.isNotBlank()) return target
            val en = element.optString("en")
            if (en.isNotBlank()) return en
            val keys = element.keys()
            if (keys.hasNext()) return element.optString(keys.next())
            return ""
        }
        return element.toString()
    }

    fun getLocalizedOptions(optionsArray: JSONArray?, lang: String): List<String> {
        if (optionsArray == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until optionsArray.length()) {
            val item = optionsArray.opt(i)
            result.add(getLocalizedText(item, lang))
        }
        return result
    }
}

fun JSONObject.localizedTitle(lang: String): String {
    val obj = optJSONObject("title")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("title_hi").ifBlank { optString("title") }
        "sat" -> optString("title_sat").ifBlank { optString("title") }
        else -> optString("title")
    }
}

fun JSONObject.localizedDescription(lang: String): String {
    val obj = optJSONObject("description")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("description_hi").ifBlank { optString("description") }
        "sat" -> optString("description_sat").ifBlank { optString("description") }
        else -> optString("description")
    }
}

fun JSONObject.localizedObjective(lang: String): String {
    val obj = optJSONObject("objective")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("objective_hi").ifBlank { optString("objective") }
        "sat" -> optString("objective_sat").ifBlank { optString("objective") }
        else -> optString("objective")
    }
}

fun JSONObject.localizedSafetyBriefing(lang: String): String {
    val obj = optJSONObject("safetyBriefing")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("safetyBriefing_hi").ifBlank { optString("safetyBriefing") }
        "sat" -> optString("safetyBriefing_sat").ifBlank { optString("safetyBriefing") }
        else -> optString("safetyBriefing")
    }
}

fun JSONObject.localizedEquipment(lang: String): String {
    val obj = optJSONObject("equipment")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("equipment_hi").ifBlank { optString("equipment") }
        "sat" -> optString("equipment_sat").ifBlank { optString("equipment") }
        else -> optString("equipment")
    }
}

fun JSONObject.localizedStepTitle(lang: String): String {
    val obj = optJSONObject("title")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("title_hi").ifBlank { optString("title") }
        "sat" -> optString("title_sat").ifBlank { optString("title") }
        else -> optString("title")
    }
}

fun JSONObject.localizedStepInstruction(lang: String): String {
    val obj = optJSONObject("instruction")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("instruction_hi").ifBlank { optString("instruction") }
        "sat" -> optString("instruction_sat").ifBlank { optString("instruction") }
        else -> optString("instruction")
    }
}

fun JSONObject.localizedQuestionTopic(lang: String): String {
    val obj = optJSONObject("topic")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("topic_hi").ifBlank { optString("topic") }
        "sat" -> optString("topic_sat").ifBlank { optString("topic") }
        else -> optString("topic")
    }
}

fun JSONObject.localizedQuestionText(lang: String): String {
    val obj = optJSONObject("text")
    return if (obj != null) LocalizationResolver.getLocalizedText(obj, lang)
    else when (lang) {
        "hi" -> optString("text_hi").ifBlank { optString("text") }
        "sat" -> optString("text_sat").ifBlank { optString("text") }
        else -> optString("text")
    }
}

fun JSONObject.localizedQuestionOptions(lang: String): List<String> {
    val optionsArray = optJSONArray("options") ?: return emptyList()
    return LocalizationResolver.getLocalizedOptions(optionsArray, lang)
}
