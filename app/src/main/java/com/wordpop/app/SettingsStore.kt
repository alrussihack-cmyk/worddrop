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

    var orKey: String
        get() = prefs.getString("orKey", "") ?: ""
        set(v) = prefs.edit().putString("orKey", v.trim()).apply()

    var orModel: String
        get() = prefs.getString("orModel", null) ?: "openai/gpt-4o-mini"
        set(v) = prefs.edit().putString("orModel", v.trim()).apply()

    var storiesJson: String
        get() = prefs.getString("stories", "[]") ?: "[]"
        set(v) = prefs.edit().putString("stories", v).apply()

    /** Removes the given keys (used by the admin panel to reset parts of the data). */
    fun remove(vararg keys: String) {
        val e = prefs.edit()
        keys.forEach { e.remove(it) }
        e.apply()
    }

    /** All settings and learning data as JSON text, for backup. */
    fun exportAll(): String {
        val o = org.json.JSONObject()
        prefs.all.forEach { (k, v) ->
            val item = org.json.JSONObject()
            when (v) {
                is Boolean -> item.put("t", "b").put("v", v)
                is Int -> item.put("t", "i").put("v", v)
                is Long -> item.put("t", "l").put("v", v)
                is String -> item.put("t", "s").put("v", v)
                is Set<*> -> item.put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            o.put(k, item)
        }
        return o.toString()
    }

    /** Restores data written by [exportAll]. Returns false if the text is not a valid backup. */
    fun importAll(json: String): Boolean = try {
        val o = org.json.JSONObject(json)
        val e = prefs.edit()
        e.clear()
        o.keys().forEach { k ->
            val item = o.getJSONObject(k)
            when (item.getString("t")) {
                "b" -> e.putBoolean(k, item.getBoolean("v"))
                "i" -> e.putInt(k, item.getInt("v"))
                "l" -> e.putLong(k, item.getLong("v"))
                "s" -> e.putString(k, item.getString("v"))
                "set" -> {
                    val a = item.getJSONArray("v")
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        e.apply()
        true
    } catch (ex: Exception) {
        false
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
        BannerTheme("فوم", 0xFFFFFFFF.toInt(), 0xFF12211F.toInt(), 0xFF5B6F6B.toInt(), 0xFF0B7F6C.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9E6E3.toInt(), 0xFFE6F1EE.toInt()),
        BannerTheme("أبيس", 0xFF0F1C1A.toInt(), 0xFFFFFFFF.toInt(), 0xFFB6CBC6.toInt(), 0xFF2BB89E.toInt(), 0xFF07120F.toInt(), 0xFF1F3532.toInt(), 0xFF1C302C.toInt()),
        BannerTheme("لاجون", 0xFF0B7F6C.toInt(), 0xFFFFFFFF.toInt(), 0xFFF2FBF8.toInt(), 0xFFFFFFFF.toInt(), 0xFF0B7F6C.toInt(), 0xFF0A6F5D.toInt(), 0xFF096C5C.toInt()),
        BannerTheme("رمل", 0xFFF1EADB.toInt(), 0xFF1F1B14.toInt(), 0xFF6E6552.toInt(), 0xFFA8741A.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0D6BF.toInt(), 0xFFE5DCC8.toInt()),
        BannerTheme("مدّ", 0xFF1F5AA6.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6E4F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF1F5AA6.toInt(), 0xFF2A68B8.toInt(), 0xFF1B4687.toInt()),
        BannerTheme("بوب", 0xFFCF3560.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFF6F8.toInt(), 0xFFFFFFFF.toInt(), 0xFFCF3560.toInt(), 0xFFD9496F.toInt(), 0xFFBD3359.toInt())
    )

    fun get(i: Int): BannerTheme = all.getOrElse(i) { all[0] }
}
