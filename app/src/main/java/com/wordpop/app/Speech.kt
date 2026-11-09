package com.wordpop.app

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Accent presets for English playback. The actual voice quality and availability
 * depend on the speech engine and voice data installed on the device.
 */
object SpeechAccents {
    const val AMERICAN = "en-US"
    const val BRITISH = "en-GB"
    const val AUSTRALIAN = "en-AU"

    fun locale(id: String): Locale = when (id) {
        BRITISH -> Locale.UK
        AUSTRALIAN -> Locale("en", "AU")
        else -> Locale.US
    }

    fun label(id: String): String = when (id) {
        BRITISH -> "بريطاني"
        AUSTRALIAN -> "أسترالي"
        else -> "أمريكي"
    }
}

/**
 * Select the best installed voice for the requested English accent.
 * Prefer the highest-quality exact regional voice; fall back to the locale
 * when that regional voice has not been downloaded on the device.
 */
fun applyEnglishAccent(tts: TextToSpeech, accent: String) {
    val target = SpeechAccents.locale(accent)
    val matchingVoice = try {
        tts.voices
            ?.filter {
                it.locale.language.equals("en", ignoreCase = true) &&
                    it.locale.country.equals(target.country, ignoreCase = true)
            }
            ?.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
            )
            ?.firstOrNull()
    } catch (_: Exception) {
        null
    }

    try {
        if (matchingVoice != null) {
            tts.voice = matchingVoice
        } else {
            val result = tts.setLanguage(target)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setLanguage(Locale.US)
            }
        }
    } catch (_: Exception) {
        try {
            tts.setLanguage(Locale.US)
        } catch (_: Exception) {
        }
    }
}
