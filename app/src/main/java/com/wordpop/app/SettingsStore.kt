package com.wordpop.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wordpop", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** Minutes between two words. */
    var intervalMin: Int
        get() = prefs.getInt("interval", 60)
        set(v) = prefs.edit().putInt("interval", v).apply()

    var startHour: Int
        get() = prefs.getInt("startHour", 9)
        set(v) = prefs.edit().putInt("startHour", v).apply()

    var endHour: Int
        get() = prefs.getInt("endHour", 22)
        set(v) = prefs.edit().putInt("endHour", v).apply()

    /** 0 = words and phrases, 1 = words only, 2 = phrases only. */
    var content: Int
        get() = prefs.getInt("content", 0)
        set(v) = prefs.edit().putInt("content", v).apply()

    /** Seconds the banner stays on screen. */
    var displaySec: Int
        get() = prefs.getInt("displaySec", 10)
        set(v) = prefs.edit().putInt("displaySec", v).apply()

    /** 0 = all levels, 1 = basic, 2 = B1, 3 = B2. */
    var level: Int
        get() = prefs.getInt("level", 0)
        set(v) = prefs.edit().putInt("level", v).apply()

    /** Plays a short notification sound when the banner appears. */
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    /** Banner position set by dragging. Y = -1 means the default spot under the status bar. */
    var bannerX: Int
        get() = prefs.getInt("bannerX", 0)
        set(v) = prefs.edit().putInt("bannerX", v).apply()

    var bannerY: Int
        get() = prefs.getInt("bannerY", -1)
        set(v) = prefs.edit().putInt("bannerY", v).apply()

    /** Where the banner appears when it has not been dragged: 0 = top, 1 = middle, 2 = bottom. */
    var bannerPos: Int
        get() = prefs.getInt("bannerPos", 0)
        set(v) = prefs.edit().putInt("bannerPos", v).apply()

    /** 0 = small, 1 = medium, 2 = large. */
    var bannerSize: Int
        get() = prefs.getInt("bannerSize", 1)
        set(v) = prefs.edit().putInt("bannerSize", v).apply()

    /** Banner opacity in percent (40..100). */
    var opacity: Int
        get() = prefs.getInt("opacity", 100)
        set(v) = prefs.edit().putInt("opacity", v.coerceIn(40, 100)).apply()

    var bannerTheme: Int
        get() = prefs.getInt("bannerTheme", 0)
        set(v) = prefs.edit().putInt("bannerTheme", v).apply()

    /** Buttons shown inside the banner: any of fav, know, dont, example, skip, pause. */
    var actions: Set<String>
        get() = prefs.getStringSet("actions", null) ?: setOf("fav", "know", "skip")
        set(v) = prefs.edit().putStringSet("actions", v).apply()

    /** Old master switch, no longer used. */
    var showActions: Boolean
        get() = prefs.getBoolean("showActions", true)
        set(v) = prefs.edit().putBoolean("showActions", v).apply()

    /** No words are shown before this time (epoch millis). */
    var pausedUntil: Long
        get() = prefs.getLong("pausedUntil", 0L)
        set(v) = prefs.edit().putLong("pausedUntil", v).apply()

    var skipCalls: Boolean
        get() = prefs.getBoolean("skipCalls", true)
        set(v) = prefs.edit().putBoolean("skipCalls", v).apply()

    var skipGames: Boolean
        get() = prefs.getBoolean("skipGames", false)
        set(v) = prefs.edit().putBoolean("skipGames", v).apply()

    /** Words the user marked as "still learning"; they come back more often. */
    var hard: Set<Int>
        get() = (prefs.getStringSet("hard", null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("hard", v.map { it.toString() }.toSet()).apply()

    var autoSpeak: Boolean
        get() = prefs.getBoolean("autoSpeak", false)
        set(v) = prefs.edit().putBoolean("autoSpeak", v).apply()

    var lastWordId: Int
        get() = prefs.getInt("lastWordId", -1)
        set(v) = prefs.edit().putInt("lastWordId", v).apply()

    var learned: Set<Int>
        get() = (prefs.getStringSet("learned", null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("learned", v.map { it.toString() }.toSet()).apply()

    /** Favorite words (by word id) and favorite example sentences (by the id of the word they belong to). */
    var favWords: Set<Int>
        get() = (prefs.getStringSet("favWords", null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("favWords", v.map { it.toString() }.toSet()).apply()

    var favSentences: Set<Int>
        get() = (prefs.getStringSet("favSentences", null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("favSentences", v.map { it.toString() }.toSet()).apply()

    fun toggleFavWord(id: Int): Boolean {
        val now = favWords
        favWords = if (id in now) now - id else now + id
        return id !in now
    }

    fun toggleFavSentence(id: Int): Boolean {
        val now = favSentences
        favSentences = if (id in now) now - id else now + id
        return id !in now
    }

    // Generic helpers used by the writing practice (spelling and sentence building).
    fun intSet(key: String): Set<Int> =
        (prefs.getStringSet(key, null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    fun addTo(key: String, id: Int) {
        prefs.edit().putStringSet(key, (intSet(key) + id).map { it.toString() }.toSet()).apply()
    }

    fun removeFrom(key: String, id: Int) {
        prefs.edit().putStringSet(key, (intSet(key) - id).map { it.toString() }.toSet()).apply()
    }

    fun stat(key: String): Int = prefs.getInt(key, 0)

    fun setStat(key: String, v: Int) {
        prefs.edit().putInt(key, v).apply()
    }

    fun markLearned(id: Int) {
        learned = learned + id
    }
}

fun intervalLabel(min: Int): String = when (min) {
    5 -> "كل 5 دقايق"
    15 -> "كل 15 دقيقة"
    30 -> "كل نص ساعة"
    60 -> "كل ساعة"
    120 -> "كل ساعتين"
    180 -> "كل 3 ساعات"
    360 -> "كل 6 ساعات"
    else -> "كل $min دقيقة"
}

fun hourLabel(h: Int): String = when {
    h == 0 -> "12 ص"
    h < 12 -> "$h ص"
    h == 12 -> "12 م"
    else -> "${h - 12} م"
}

/** Colors of the floating banner. */
class BannerTheme(
    val name: String,
    val bg: Int,
    val text: Int,
    val sub: Int,
    val accent: Int,
    val onAccent: Int,
    val stroke: Int,
    val chip: Int
)

object BannerThemes {
    val all = listOf(
        BannerTheme("أبيض", 0xFFFFFFFF.toInt(), 0xFF1C1C1C.toInt(), 0xFF6B6B6B.toInt(), 0xFF549E86.toInt(), 0xFFFFFFFF.toInt(), 0xFFECE9E4.toInt(), 0xFFF1EDE4.toInt()),
        BannerTheme("داكن", 0xFF1C1C1C.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9C9C9.toInt(), 0xFF549E86.toInt(), 0xFFFFFFFF.toInt(), 0xFF333333.toInt(), 0xFF2E2E2E.toInt()),
        BannerTheme("تركواز", 0xFF549E86.toInt(), 0xFFFFFFFF.toInt(), 0xFFE4F1EC.toInt(), 0xFFFFFFFF.toInt(), 0xFF549E86.toInt(), 0xFF4A8D77.toInt(), 0xFF4A8D77.toInt()),
        BannerTheme("بيج", 0xFFF1EDE4.toInt(), 0xFF1C1C1C.toInt(), 0xFF6B6B6B.toInt(), 0xFFA67F3D.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2DCCD.toInt(), 0xFFE6E0D2.toInt()),
        BannerTheme("أزرق", 0xFF2F5D9E.toInt(), 0xFFFFFFFF.toInt(), 0xFFDCE8F8.toInt(), 0xFFFFFFFF.toInt(), 0xFF2F5D9E.toInt(), 0xFF284F87.toInt(), 0xFF284F87.toInt()),
        BannerTheme("وردي", 0xFFD6577E.toInt(), 0xFFFFFFFF.toInt(), 0xFFFBE3EA.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6577E.toInt(), 0xFFC24A70.toInt(), 0xFFC24A70.toInt())
    )

    fun get(i: Int): BannerTheme = all.getOrElse(i) { all[0] }
}
