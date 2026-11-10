package com.wordpop.app

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Loads the user's imported words once per process, before any screen or service needs them. */
class WordPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WordRepo.init(this)
        // Migrate legacy preferences and protect the OpenRouter API key before the UI starts.
        SettingsStore(this)
        // Keep the shared cloud vocabulary cached locally for offline learning.
        WordCloudSync.start(this)
    }
}

/** Result of reading an import file. */
data class ImportResult(val added: Int, val duplicates: Int, val skipped: Int)

object CustomWords {
    /** Built-in words use ids below this value, imported ones start here so ids never clash. */
    const val FIRST_ID = 1000

    private const val PREFS = "wordpop_custom"
    private const val KEY = "words"

    fun load(ctx: Context): List<Word> {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Word(
                    id = o.getInt("id"),
                    en = o.getString("en"),
                    ipa = o.optString("ipa"),
                    ar = o.getString("ar"),
                    kind = o.optString("kind", "كلمة"),
                    example = o.optString("ex"),
                    exampleAr = o.optString("exAr"),
                    level = o.optString("level", "A")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun exportRaw(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"

    fun importRaw(ctx: Context, raw: String) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, raw).apply()
    }

    fun save(ctx: Context, words: List<Word>) {
        val arr = JSONArray()
        words.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("en", it.en)
                    .put("ipa", it.ipa)
                    .put("ar", it.ar)
                    .put("kind", it.kind)
                    .put("ex", it.example)
                    .put("exAr", it.exampleAr)
                    .put("level", it.level)
            )
        }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * Parses a text/CSV file. Each line is `english, arabic[, example, example translation]`.
     * The separator can be a comma, semicolon, tab, "|" or " - ". A header line and blank lines are ignored.
     */
    fun parse(text: String, existing: List<Word>, nextId: Int): Pair<List<Word>, ImportResult> {
        val seen = existing.map { norm(it.en) }.toMutableSet()
        val out = mutableListOf<Word>()
        var duplicates = 0
        var skipped = 0
        var id = nextId

        val lines = text.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() }
        for ((index, line) in lines.withIndex()) {
            val cols = splitLine(line).map { it.trim() }
            if (cols.size < 2 || cols[0].isEmpty() || cols[1].isEmpty()) {
                skipped++
                continue
            }
            if (index == 0 && isHeader(cols)) continue

            val en = cols[0]
            if (!seen.add(norm(en))) {
                duplicates++
                continue
            }
            val isPhrase = en.trim().split(Regex("\\s+")).size >= 3 || en.last() in ".!?"
            out += Word(
                id = id++,
                en = en,
                ipa = "",
                ar = cols[1],
                kind = if (isPhrase) "عبارة" else "كلمة",
                example = cols.getOrElse(2) { "" },
                exampleAr = cols.getOrElse(3) { "" }
            )
        }
        return out to ImportResult(out.size, duplicates, skipped)
    }

    private fun norm(s: String) = s.trim().lowercase()

    private fun isHeader(cols: List<String>): Boolean {
        val first = cols[0].lowercase()
        return first in setOf("english", "en", "word", "words", "الكلمة", "انجليزي", "إنجليزي", "انجليزى")
    }

    private fun splitLine(line: String): List<String> {
        val delim = when {
            '\t' in line -> "\t"
            ';' in line -> ";"
            '|' in line -> "|"
            ',' in line -> ","
            " - " in line -> " - "
            else -> return listOf(line)
        }
        if (delim.length > 1) return line.split(delim)
        return splitQuoted(line, delim[0])
    }

    /** Splits on [delim] while keeping delimiters that appear inside "double quotes". */
    private fun splitQuoted(line: String, delim: Char): List<String> {
        val cols = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delim && !inQuotes -> {
                    cols += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        cols += sb.toString()
        return cols
    }
}
