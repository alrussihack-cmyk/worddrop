package com.wordpop.app

import android.content.Context

private const val LEGACY_MIGRATION_MARKER = "__legacy_settings_migrated"

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("wordpop_settings", Context.MODE_PRIVATE)
    private val legacyPrefs = appContext.getSharedPreferences("wordpop", Context.MODE_PRIVATE)

    init {
        migrateLegacySettings()
        // Move any API key saved by older versions into Android Keystore-backed storage.
        ApiKeyVault.migrateLegacy(appContext, legacyPrefs)
    }

    /**
     * Preserve existing preferences while moving them out of the legacy file that may
     * contain a plaintext API key. The new preferences file contains no secrets and can
     * safely participate in Android Auto Backup.
     */
    private fun migrateLegacySettings() {
        if (prefs.getBoolean(LEGACY_MIGRATION_MARKER, false)) return

        val editor = prefs.edit()
        legacyPrefs.all.forEach { (key, value) ->
            if (key == "orKey") return@forEach
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.putBoolean(LEGACY_MIGRATION_MARKER, true).commit()
    }

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

    /** Persistent spaced-repetition schedule; included in normal app backups. */
    var reviewCardsJson: String
        get() = prefs.getString("reviewCards", "{}") ?: "{}"
        set(v) = prefs.edit().putString("reviewCards", v).apply()

    fun reviewStates(): Map<Int, ReviewState> = ReviewScheduler.decode(reviewCardsJson)

    fun newReviewCardsRemaining(now: Long = System.currentTimeMillis()): Int {
        val used = if (prefs.getString("reviewNewDate", "") == ReviewScheduler.dayKey(now)) {
            prefs.getInt("reviewNewCount", 0)
        } else 0
        return (ReviewScheduler.NEW_CARDS_PER_DAY - used).coerceAtLeast(0)
    }

    /** Records one self-assessed review and consumes a daily-new slot only for a first review. */
    fun recordReview(id: Int, rating: ReviewRating, now: Long = System.currentTimeMillis()) {
        val cards = reviewStates()
        val previous = cards[id]
        val updated = cards + (id to ReviewScheduler.next(previous, rating, now))
        val today = ReviewScheduler.dayKey(now)
        val usedToday = if (prefs.getString("reviewNewDate", "") == today) {
            prefs.getInt("reviewNewCount", 0)
        } else 0
        val newCount = if (previous == null) usedToday + 1 else usedToday
        prefs.edit()
            .putString("reviewCards", ReviewScheduler.encode(updated))
            .putString("reviewNewDate", today)
            .putInt("reviewNewCount", newCount)
            .apply()
    }

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
        get() = ApiKeyVault.get(appContext)
        set(v) = ApiKeyVault.set(appContext, v)

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
        // Never export the legacy API-key field, even if an old install has not migrated yet.
        prefs.all.filterKeys { it != "orKey" && it != LEGACY_MIGRATION_MARKER }.forEach { (k, v) ->
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
            // Ignore keys from older or hand-crafted backups; secrets are device-local.
            if (k == "orKey") return@forEach
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
        e.putBoolean(LEGACY_MIGRATION_MARKER, true)
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
        BannerTheme("ورق", 0xFFFFFFFF.toInt(), 0xFF12182B.toInt(), 0xFF5A6278.toInt(), 0xFF2FE0B5.toInt(), 0xFF12182B.toInt(), 0xFF12182B.toInt(), 0xFFEEF1F8.toInt()),
        BannerTheme("ليل", 0xFF12182B.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9D2F0.toInt(), 0xFF2FE0B5.toInt(), 0xFF12182B.toInt(), 0xFF2FE0B5.toInt(), 0xFF232B48.toInt()),
        BannerTheme("نعناع", 0xFF2FE0B5.toInt(), 0xFF12182B.toInt(), 0xFF26304D.toInt(), 0xFF12182B.toInt(), 0xFFFFFFFF.toInt(), 0xFF12182B.toInt(), 0xFF8EF0D5.toInt()),
        BannerTheme("شمس", 0xFFFFC93C.toInt(), 0xFF12182B.toInt(), 0xFF26304D.toInt(), 0xFF12182B.toInt(), 0xFFFFFFFF.toInt(), 0xFF12182B.toInt(), 0xFFFFDE85.toInt()),
        BannerTheme("سماء", 0xFF59B8FF.toInt(), 0xFF12182B.toInt(), 0xFF26304D.toInt(), 0xFF12182B.toInt(), 0xFFFFFFFF.toInt(), 0xFF12182B.toInt(), 0xFFA6D8FF.toInt()),
        BannerTheme("مرجان", 0xFFFF5C5C.toInt(), 0xFF12182B.toInt(), 0xFF2A1218.toInt(), 0xFF12182B.toInt(), 0xFFFFFFFF.toInt(), 0xFF12182B.toInt(), 0xFFFF9A9A.toInt())
    )

    fun get(i: Int): BannerTheme = all.getOrElse(i) { all[0] }
}
