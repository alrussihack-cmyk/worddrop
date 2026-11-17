package com.wordpop.app

import org.json.JSONObject

/** Looks up any English word the learner taps inside a story. */
object WordLookup {
    private val cache = HashMap<String, Word>()

    /** A library word for [token], trying simple inflections (plural, -ed, -ing, 's). */
    fun inLibrary(token: String): Word? {
        val t = token.lowercase().replace('’', '\'').trim('\'', ' ')
        if (t.isBlank()) return null
        val base = t.removeSuffix("'s")
        val candidates = linkedSetOf(t, base)
        listOf("s", "es", "ed", "d", "ing").forEach { suf ->
            if (base.endsWith(suf) && base.length > suf.length + 2) {
                val cut = base.dropLast(suf.length)
                candidates += cut; candidates += cut + "e"
                if (cut.length > 2 && cut.last() == cut[cut.length - 2]) candidates += cut.dropLast(1)
            }
        }
        if (base.endsWith("ies") && base.length > 4) candidates += base.dropLast(3) + "y"
        return candidates.firstNotNullOfOrNull { c -> WordRepo.all.firstOrNull { it.en.equals(c, ignoreCase = true) } }
    }

    /** Asks the shared Worker for the meaning of [token] as used in [sentence]. Call off the main thread. */
    fun translate(workerUrl: String, token: String, sentence: String): Word {
        val key = token.lowercase()
        cache[key]?.let { return it }
        val prompt = """
You are an English-to-Egyptian-Arabic dictionary for learners.
Give the dictionary form of the English word "$token" as it is used in this sentence: "${sentence.take(400)}"
Return ONLY JSON, no markdown:
{"en":"dictionary form","ipa":"/.../","ar":"short Arabic meaning that fits the sentence","kind":"noun, verb, adjective, adverb or other (in Arabic: اسم، فعل، صفة، حال، أخرى)","example":"one short simple English example sentence","exampleAr":"natural Egyptian-Arabic translation of the example"}
""".trimIndent()
        val o: JSONObject = StoryApi.chatJson(workerUrl, prompt, 400)
        val en = o.optString("en").trim().ifBlank { token }
        val ar = o.optString("ar").trim()
        if (ar.isBlank()) throw Exception("مقدرتش أترجم الكلمة دي.")
        val w = Word(
            id = 0, en = en, ipa = o.optString("ipa").trim(), ar = ar,
            kind = o.optString("kind").trim().ifBlank { "كلمة" },
            example = o.optString("example").trim(), exampleAr = o.optString("exampleAr").trim()
        )
        cache[key] = w
        return w
    }
}
