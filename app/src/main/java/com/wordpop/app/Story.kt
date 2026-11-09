package com.wordpop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A generated story. In [en] and [ar] the target words are wrapped in ** markers. */
data class Story(val title: String, val en: String, val ar: String, val words: List<String>)

object StoryApi {
    private const val URL_CHAT = "https://openrouter.ai/api/v1/chat/completions"

    val genres = listOf("مغامرة" to "an adventure", "كوميدي" to "a funny comedy", "غموض" to "a light mystery",
        "يوم عادي" to "an everyday-life situation", "سفر" to "a travel story", "شغل" to "a workplace story")
    val levels = listOf("بسيط" to "simple A2-level", "متوسط" to "B1-level", "متقدم" to "B2-level")

    private fun storyPrompt(words: List<Word>, genre: String, level: String): String {
        val list = words.joinToString("\n") { "- ${it.en} (${it.ar})" }
        return """Write a short, engaging story in $level English for an Arabic-speaking learner. The genre: $genre.
Rules:
- 100 to 140 words, 2 short paragraphs, a clear beginning, a small problem and an ending.
- Use EVERY one of these words or phrases at least once (small grammar changes are fine):
$list
- Wrap each use of these words in double asterisks, like **word**.
- Make the scene vivid and memorable (a funny or surprising detail) so the words stick.
- Also give a natural Egyptian-Arabic translation of the story. In the translation wrap the Arabic equivalent of each target word in ** too.
Reply with JSON only, no other text: {"title":"...","story":"...","translation":"..."}"""
    }

    private fun friendly(code: Int, text: String): String {
        val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { text.take(120) }
        return when (code) {
            401, 403 -> "المفتاح مش مقبول. تأكد منه."
            402 -> "الرصيد خلص في حساب OpenRouter."
            429 -> "طلبات كتير، جرب بعد شوية."
            else -> "خطأ $code: $msg"
        }
    }

    /** Blocking chat call returning the JSON object inside the reply. Run off the main thread. */
    private fun chatJson(key: String, model: String, prompt: String, maxTokens: Int): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.8)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = URL(URL_CHAT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Title", "WordPop")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception(friendly(code, text))
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) throw Exception("الرد جه بشكل غير متوقع، جرب تاني.")
            return JSONObject(content.substring(start, end + 1))
        } catch (e: java.io.IOException) {
            throw Exception("مفيش اتصال بالنت.")
        } catch (e: org.json.JSONException) {
            throw Exception("الرد جه بشكل غير متوقع، جرب تاني.")
        } finally {
            conn.disconnect()
        }
    }

    fun generate(key: String, model: String, words: List<Word>, genre: String, level: String): Story {
        val o = chatJson(key, model, storyPrompt(words, genre, level), 1200)
        return Story(
            title = o.optString("title", "قصتي"),
            en = o.getString("story").trim(),
            ar = o.optString("translation").trim(),
            words = words.map { it.en }
        )
    }

    /** Returns the corrected text and short Arabic tips. */
    fun feedback(key: String, model: String, story: Story, text: String): Pair<String, String> {
        val prompt = """A learner (Arabic speaker, B1) read this story and then wrote their own short text about it.
Story: ${story.en.replace("**", "")}
Learner text: $text
Correct the learner's text keeping their meaning and simple wording. Then give at most 3 short tips in Egyptian Arabic about the mistakes (grammar, spelling, word choice), each with a tiny example.
Reply with JSON only: {"corrected":"...","tips":"..."}"""
        val o = chatJson(key, model, prompt, 700)
        return o.optString("corrected", text) to o.optString("tips")
    }

    fun load(store: SettingsStore): List<Story> = try {
        val arr = JSONArray(store.storiesJson)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val w = o.getJSONArray("words")
            Story(o.getString("title"), o.getString("en"), o.optString("ar"), (0 until w.length()).map { i -> w.getString(i) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(store: SettingsStore, list: List<Story>) {
        val arr = JSONArray()
        list.take(20).forEach {
            arr.put(
                JSONObject().put("title", it.title).put("en", it.en).put("ar", it.ar)
                    .put("words", JSONArray(it.words))
            )
        }
        store.storiesJson = arr.toString()
    }
}

private fun highlight(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(SpanStyle(color = TealDark, background = Tint, fontWeight = FontWeight.Bold)) { append(part) }
        } else append(part)
    }
}

private fun plain(text: String) = text.replace("**", "")

/** One fill-in-the-blank question built from a story. */
class Cloze(val sentence: String, val answer: String, val options: List<String>)

private fun buildCloze(story: Story): List<Cloze> {
    val t = story.en.split("**")
    val pool = (1 until t.size step 2).map { t[it].trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    val extra = story.words.map { it.lowercase() }
    val out = mutableListOf<Cloze>()
    for (k in 1 until t.size step 2) {
        if (k + 1 >= t.size) break
        val ans = t[k].trim().lowercase()
        if (ans.isEmpty()) continue
        val prefix = t[k - 1]
        val cut = prefix.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        val before = if (cut >= 0) prefix.substring(cut + 1) else prefix
        val suffix = t[k + 1]
        val end = suffix.indexOfAny(charArrayOf('.', '!', '?'))
        val after = if (end >= 0) suffix.substring(0, end + 1) else suffix
        val wrong = (pool + extra).filter { it != ans }.distinct().shuffled().take(3)
        if (wrong.isEmpty()) continue
        out += Cloze((before + "_____" + after).trim(), ans, (wrong + ans).shuffled())
    }
    return out.distinctBy { it.answer }.take(6)
}

@Composable
private fun ClozeQuiz(story: Story, onFinish: (List<String>) -> Unit) {
    val qs = remember(story) { buildCloze(story) }
    var idx by remember(story) { mutableIntStateOf(0) }
    var picked by remember(story) { mutableStateOf<String?>(null) }
    var score by remember(story) { mutableIntStateOf(0) }
    val missed = remember(story) { mutableListOf<String>() }

    if (qs.isEmpty()) {
        Text("القصة دي مفيهاش كلمات كفاية للاختبار. اعمل قصة جديدة.", fontSize = 15.sp, color = Muted)
        return
    }
    if (idx >= qs.size) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("نتيجتك: $score من ${qs.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night)
            Text(
                if (missed.isEmpty()) "ممتاز! كل الكلمات ثبتت."
                else "الكلمات اللي غلطت فيها هتظهر لك أكتر في النافذة: " + missed.joinToString("، "),
                fontSize = 15.sp, color = Muted
            )
            PrimaryButton("تمام", Modifier.fillMaxWidth()) { onFinish(missed.toList()) }
        }
        return
    }
    val q = qs[idx]
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("سؤال ${idx + 1} من ${qs.size}: اختار الكلمة الناقصة", fontSize = 14.sp, color = Muted)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(q.sentence, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = Night, modifier = Modifier.fillMaxWidth())
            q.options.forEach { o ->
                val done = picked != null
                val right = o == q.answer
                val bg = when {
                    done && right -> Teal
                    done && o == picked -> MissTint
                    else -> Cloud
                }
                val line = when {
                    done && right -> Night
                    done && o == picked -> Danger
                    else -> Night
                }
                Surface(
                    shape = RoundedCornerShape(14.dp), color = bg,
                    border = BorderStroke(1.5.dp, line),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !done) {
                        picked = o
                        if (right) score++ else missed += q.answer
                    }
                ) {
                    Text(o, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Night, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        }
        if (picked != null) {
            Text(
                if (picked == q.answer) "تمام!" else "الصح: ${q.answer}. هنراجعها تاني.",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (picked == q.answer) TealDark else Danger
            )
            PrimaryButton(if (idx + 1 >= qs.size) "شوف النتيجة" else "التالي", Modifier.fillMaxWidth()) {
                idx++; picked = null
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryTab(store: SettingsStore, words: List<Word>, speak: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(store.orKey) }
    var model by remember { mutableStateOf(store.orModel) }
    var editKey by remember { mutableStateOf(store.orKey.isEmpty()) }
    var picked by remember(words.size) { mutableStateOf(words.take(6).map { it.id }.toSet()) }
    var genre by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(StoryApi.load(store)) }
    var current by remember { mutableStateOf<Story?>(saved.firstOrNull()) }
    var showAr by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var myText by remember { mutableStateOf("") }
    var fbLoading by remember { mutableStateOf(false) }
    var fb by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = Tint) {
            Text(
                "اختار كلماتك المفضلة وأعمل منها قصة قصيرة. بعد القصة اتختبر في الكلمات واكتب بكلماتك وهنصحح لك.",
                fontSize = 15.sp, color = TealDark, modifier = Modifier.padding(14.dp)
            )
        }

        if (words.size < 2) {
            EmptyFav("ضيف كلمتين مفضلين على الأقل (النجمة في شاشة التعلّم) وبعدها اعمل قصة.")
        } else {
            Text("الكلمات (${picked.size} من 8)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { w ->
                    Chip(w.en, w.id in picked) {
                        picked = if (w.id in picked) picked - w.id else if (picked.size < 8) picked + w.id else picked
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("الصعبة أولًا", false) {
                    val hard = store.hard
                    picked = (words.filter { it.id in hard } + words.shuffled()).distinctBy { it.id }.take(6).map { it.id }.toSet()
                }
                Chip("عشوائي", false) { picked = words.shuffled().take(6).map { it.id }.toSet() }
            }

            Text("نوع القصة", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Night)
            ChipRow { StoryApi.genres.forEachIndexed { i, g -> Chip(g.first, genre == i) { genre = i } } }
            Text("المستوى", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Night)
            ChipRow { StoryApi.levels.forEachIndexed { i, l -> Chip(l.first, level == i) { level = i } } }

            if (editKey) {
                Text("مفتاح OpenRouter", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Night)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    placeholder = { Text("sk-or-...") }
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الموديل") }
                )
                PrimaryButton("حفظ المفتاح", Modifier.fillMaxWidth()) {
                    store.orKey = key; store.orModel = model.ifBlank { "openai/gpt-4o-mini" }
                    model = store.orModel
                    editKey = key.isBlank()
                }
            } else {
                Text(
                    "تعديل المفتاح والموديل", fontSize = 14.sp, color = TealDark,
                    modifier = Modifier.clickable { editKey = true }.padding(vertical = 8.dp)
                )
            }

            if (error.isNotEmpty()) Text(error, fontSize = 15.sp, color = Danger)

            PrimaryButton(if (loading) "بنكتب القصة..." else "اعمل قصة", Modifier.fillMaxWidth()) {
                if (loading) return@PrimaryButton
                if (store.orKey.isBlank()) { editKey = true; error = "ضيف مفتاح OpenRouter الأول."; return@PrimaryButton }
                if (picked.size < 2) { error = "اختار كلمتين على الأقل."; return@PrimaryButton }
                error = ""; loading = true
                val chosen = words.filter { it.id in picked }
                val g = StoryApi.genres[genre].second
                val l = StoryApi.levels[level].second
                scope.launch {
                    try {
                        val story = withContext(Dispatchers.IO) { StoryApi.generate(store.orKey, store.orModel, chosen, g, l) }
                        saved = listOf(story) + saved
                        StoryApi.save(store, saved)
                        current = story; showAr = false; quiz = false; fb = null; myText = ""
                    } catch (e: Exception) {
                        error = e.message ?: "حصل خطأ، جرب تاني."
                    }
                    loading = false
                }
            }
        }

        current?.let { st ->
            Surface(
                shape = RoundedCornerShape(16.dp), color = Cloud,
                border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(st.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(highlight(st.en), fontSize = 18.sp, lineHeight = 28.sp, color = Night, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("اسمع القصة", false) { speak(plain(st.en)) }
                        Chip(if (showAr) "اخفي الترجمة" else "الترجمة", showAr) { showAr = !showAr }
                    }
                    if (showAr && st.ar.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Beige) {
                            Text(highlight(st.ar), fontSize = 16.sp, lineHeight = 26.sp, color = Night, modifier = Modifier.padding(12.dp).fillMaxWidth())
                        }
                    }
                    Text("الكلمات (دوس تسمع)", fontSize = 14.sp, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.words.forEach { w ->
                            val ar = words.firstOrNull { it.en == w }?.ar ?: ""
                            Surface(
                                shape = RoundedCornerShape(12.dp), color = Tint,
                                modifier = Modifier.clickable { speak(w) }
                            ) {
                                Text(if (ar.isEmpty()) w else "$w: $ar", fontSize = 14.sp, color = TealDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }

            // Step 2: retrieval practice
            Surface(shape = RoundedCornerShape(16.dp), color = Cloud, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختبار القصة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Night)
                    if (!quiz) {
                        Text("الاسترجاع من الذاكرة بيثبّت الكلمة أكتر من القراءة. كمّل الجمل الناقصة.", fontSize = 14.sp, color = Muted)
                        PrimaryButton("ابدأ الاختبار", Modifier.fillMaxWidth()) { quiz = true }
                    } else {
                        ClozeQuiz(st) { missedAnswers ->
                            val ids = missedAnswers.flatMap { a ->
                                words.filter { w ->
                                    val stem = w.en.lowercase().let { it.take(maxOf(3, it.length - 3)) }
                                    a.contains(stem)
                                }.map { it.id }
                            }
                            if (ids.isNotEmpty()) store.hard = store.hard + ids
                            quiz = false
                        }
                    }
                }
            }

            // Step 3: production + AI feedback
            Surface(shape = RoundedCornerShape(16.dp), color = Cloud, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اكتب بكلماتك", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Night)
                    Text("لخّص القصة في جملتين أو تلاتة بالإنجليزي، وهنصحح لك.", fontSize = 14.sp, color = Muted)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = myText, onValueChange = { myText = it },
                            modifier = Modifier.fillMaxWidth(), minLines = 3,
                            placeholder = { Text("Write here...") }
                        )
                    }
                    PrimaryButton(if (fbLoading) "بنراجع..." else "راجع كتابتي", Modifier.fillMaxWidth()) {
                        if (fbLoading) return@PrimaryButton
                        if (myText.trim().length < 10) { error = "اكتب جملة كاملة الأول."; return@PrimaryButton }
                        error = ""; fbLoading = true
                        scope.launch {
                            try {
                                fb = withContext(Dispatchers.IO) { StoryApi.feedback(store.orKey, store.orModel, st, myText.trim()) }
                            } catch (e: Exception) {
                                error = e.message ?: "حصل خطأ، جرب تاني."
                            }
                            fbLoading = false
                        }
                    }
                    fb?.let { (fixed, tips) ->
                        Text("النسخة الصح", fontSize = 14.sp, color = Muted)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Tint) {
                                Text(fixed, fontSize = 17.sp, lineHeight = 26.sp, color = Night, modifier = Modifier.padding(12.dp).fillMaxWidth())
                            }
                        }
                        if (tips.isNotBlank()) {
                            Text("ملاحظات", fontSize = 14.sp, color = Muted)
                            Text(tips, fontSize = 15.sp, lineHeight = 24.sp, color = Night)
                        }
                    }
                }
            }
        }

        if (saved.size > 1) {
            Text("قصصك اللي فاتت", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
            saved.forEach { st ->
                Surface(
                    shape = RoundedCornerShape(14.dp), color = Beige,
                    modifier = Modifier.fillMaxWidth().clickable { current = st; showAr = false; quiz = false; fb = null }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(st.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text(st.words.joinToString(", "), fontSize = 13.sp, color = Muted)
                    }
                }
            }
            Text(
                "مسح كل القصص", fontSize = 14.sp, color = Danger,
                modifier = Modifier.clickable { saved = emptyList(); StoryApi.save(store, saved); current = null }.padding(vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
