package com.wordpop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

    /** Calls the shared Worker; the OpenRouter credential never enters the learner app. */
    internal fun chatJson(workerUrl: String, prompt: String, maxTokens: Int): JSONObject {
        val endpoint = workerUrl.trim().trimEnd('/') + "/ai/chat"
        val body = JSONObject()
            .put("prompt", prompt)
            .put("maxTokens", maxTokens)
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val message = try { JSONObject(text).optString("error") } catch (_: Exception) { "" }
                val fallback = when (code) {
                    401, 403 -> "تعذر التحقق من إعدادات الخدمة."
                    402 -> "رصيد OpenRouter غير كافٍ."
                    429 -> "طلبات كتير في وقت قصير. حاول بعد شوية."
                    503 -> "خدمة الذكاء الاصطناعي غير مفعّلة من WordPop Control."
                    else -> "حصل خطأ أثناء الاتصال بخدمة الذكاء الاصطناعي ($code)."
                }
                throw Exception(message.ifBlank { fallback })
            }
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

    fun generate(workerUrl: String, words: List<Word>, genre: String, level: String): Story {
        val o = chatJson(workerUrl, storyPrompt(words, genre, level), 1200)
        return Story(
            title = o.optString("title", "قصتي"),
            en = o.getString("story").trim(),
            ar = o.optString("translation").trim(),
            words = words.map { it.en }
        )
    }

    /** Returns the corrected text and short Arabic tips. */
    fun feedback(workerUrl: String, story: Story, text: String): Pair<String, String> {
        val prompt = """A learner (Arabic speaker, B1) read this story and then wrote their own short text about it.
Story: ${story.en.replace("**", "")}
Learner text: $text
Correct the learner's text keeping their meaning and simple wording. Then give at most 3 short tips in Egyptian Arabic about the mistakes (grammar, spelling, word choice), each with a tiny example.
Reply with JSON only: {"corrected":"...","tips":"..."}"""
        val o = chatJson(workerUrl, prompt, 700)
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
private fun ClozeQuiz(story: Story, store: SettingsStore, onFinish: (List<String>) -> Unit) {
    val answerFeedback = rememberAnswerFeedback(store)
    val qs = remember(story) { buildCloze(story) }
    var idx by remember(story) { mutableIntStateOf(0) }
    var picked by remember(story) { mutableStateOf<String?>(null) }
    var score by remember(story) { mutableIntStateOf(0) }
    val answers = remember(story) { mutableStateListOf<Boolean>() }
    val missed = remember(story) { mutableListOf<String>() }

    if (qs.isEmpty()) {
        Text("القصة دي مفيهاش كلمات كفاية للاختبار. اعمل قصة جديدة.", fontSize = 15.sp, color = Muted)
        return
    }
    if (idx >= qs.size) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (score == qs.size && store.feedbackAnimations) CelebrationBurst()
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(qs.size) { i ->
                    val segment = when {
                        i < answers.size && answers[i] -> Teal
                        i < answers.size -> Coral
                        else -> Line
                    }
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(segment))
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.dp, Night)) {
                Text("★ " + score, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Night, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Text("السؤال " + (idx + 1) + " من " + qs.size + ": اختار الكلمة الناقصة", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(shape = RoundedCornerShape(24.dp), color = Tint, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth()) {
                Text(q.sentence, fontSize = 19.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, color = Night, modifier = Modifier.padding(16.dp).fillMaxWidth())
            }
            q.options.forEach { o ->
                val done = picked != null
                val right = o == q.answer
                val bg = when {
                    done && right -> Teal
                    done && o == picked -> Color(0xFFFFB9AE)
                    else -> Color.White
                }
                val line = when {
                    done && right -> Night
                    done && o == picked -> Danger
                    else -> Night
                }
                val shape = RoundedCornerShape(18.dp)
                AnswerFeedbackMotion(
                    correct = right,
                    trigger = if (done && o == picked) 1 else 0,
                    enabled = store.feedbackAnimations
                ) {
                    Surface(
                        shape = shape, color = bg,
                        border = BorderStroke(2.dp, line),
                        modifier = Modifier.fillMaxWidth().hardShadow(shape, 3.dp).clickable(enabled = !done) {
                            picked = o
                            answers.add(right)
                            answerFeedback(right)
                            if (right) score++ else missed += q.answer
                        }
                    ) {
                        Text(o, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp))
                    }
                }
            }
        }
        if (picked != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (picked == q.answer && store.feedbackAnimations) CelebrationBurst()
                Text(
                    if (picked == q.answer) "تمام!" else "الصح: ${q.answer}. هنراجعها تاني.",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (picked == q.answer) TealDark else Danger
                )
            }
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
    var picked by remember(words.size) { mutableStateOf(words.take(6).map { it.id }.toSet()) }
    var genre by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(StoryApi.load(store)) }
    var current by remember { mutableStateOf<Story?>(saved.firstOrNull()) }
    var builderExpanded by remember { mutableStateOf(saved.isEmpty()) }
    var historyExpanded by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var myText by remember { mutableStateOf("") }
    var fbLoading by remember { mutableStateOf(false) }
    var fb by remember { mutableStateOf<Pair<String, String>?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var selectedStoryWord by remember { mutableStateOf<String?>(null) }
    var writeError by remember { mutableStateOf("") }

    val labels = listOf("اقرأ", "ترجمة", "كلمات", "اختبر", "اكتب")
    val story = current

    if (story == null) {
        Surface(shape = RoundedCornerShape(24.dp), color = Tint, border = BorderStroke(1.5.dp, Line)) {
            Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("قصتك من كلماتك", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("اختار كلماتك، واعمل قصة، وبعدها اختبر نفسك واكتب ملخصك.", fontSize = 13.sp, lineHeight = 20.sp, color = TealDark)
                }
                Boby(Mood.Happy, 48.dp)
            }
        }
    } else {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Boby(Mood.Happy, 46.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("قصتك", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(story.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealDark, maxLines = 1)
                    }
                }
                StepBtn("⚙", "إعداد القصة") { builderExpanded = !builderExpanded }
                StepBtn("☷", "قصصك السابقة") { historyExpanded = !historyExpanded }
            }
        }
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Teal,
            trackColor = Line
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFEEF4F5))
                .border(1.5.dp, Line, RoundedCornerShape(18.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val selected = step == index
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Teal else Color.Transparent)
                        .then(if (selected) Modifier.border(2.dp, Night, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { step = index; quiz = false; selectedStoryWord = null }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (selected) Night else Muted)
                }
            }
        }
    }

    if (builderExpanded || story == null) {
        val builderShape = RoundedCornerShape(24.dp)
        Surface(
            shape = builderShape,
            color = Color.White,
            border = BorderStroke(1.5.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("إعداد القصة", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                        Text(if (picked.isEmpty()) "اختار من كلماتك المفضلة" else picked.size.toString() + " كلمات مختارة", fontSize = 12.sp, color = Muted)
                    }
                    if (story != null) SecondaryButton("إغلاق") { builderExpanded = false }
                }

                if (words.size < 2) {
                    EmptyFav("ضيف كلمتين مفضلين على الأقل من شاشة التعلّم، وبعدها تقدر تعمل قصة.")
                } else {
                    Text("1 · اختار الكلمات", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        words.forEach { w ->
                            Chip(w.en, w.id in picked) {
                                picked = if (w.id in picked) picked - w.id else if (picked.size < 8) picked + w.id else picked
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("الصعبة أولًا", Modifier.weight(1f)) {
                            val hard = store.hard
                            picked = (words.filter { it.id in hard } + words.shuffled()).distinctBy { it.id }.take(6).map { it.id }.toSet()
                        }
                        SecondaryButton("اختيار عشوائي", Modifier.weight(1f)) { picked = words.shuffled().take(6).map { it.id }.toSet() }
                    }

                    Text("2 · اختار نوع القصة", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        StoryApi.genres.forEachIndexed { i, g -> Chip(g.first, genre == i) { genre = i } }
                    }

                    Text("3 · اختار المستوى", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        StoryApi.levels.forEachIndexed { i, l -> Chip(l.first, level == i) { level = i } }
                    }

                    Text(
                        if (AiConfigSync.isReady) "إعدادات OpenRouter تتم إدارتها مركزيًا من WordPop Control."
                        else "خدمة الذكاء الاصطناعي غير مفعّلة. اطلب من المسؤول ضبطها في WordPop Control.",
                        fontSize = 13.sp, color = if (AiConfigSync.isReady) TealDark else Muted
                    )
                    if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = Danger)
                    PrimaryButton(if (loading) "بنكتب القصة..." else "اعمل قصتي", Modifier.fillMaxWidth()) {
                        if (loading) return@PrimaryButton
                        if (!AiConfigSync.isReady) { error = "خدمة الذكاء الاصطناعي غير مفعّلة من WordPop Control."; return@PrimaryButton }
                        if (picked.size < 2) { error = "اختار كلمتين على الأقل."; return@PrimaryButton }
                        error = ""
                        loading = true
                        val chosen = words.filter { it.id in picked }
                        val g = StoryApi.genres[genre].second
                        val l = StoryApi.levels[level].second
                        scope.launch {
                            try {
                                val generated = withContext(Dispatchers.IO) { StoryApi.generate(AiConfigSync.workerUrl, chosen, g, l) }
                                saved = listOf(generated) + saved
                                StoryApi.save(store, saved)
                                current = generated
                                step = 0
                                selectedStoryWord = null
                                quiz = false
                                fb = null
                                myText = ""
                                builderExpanded = false
                                historyExpanded = false
                            } catch (e: Exception) {
                                error = e.message ?: "حصل خطأ، جرب تاني."
                            }
                            loading = false
                        }
                    }
                }
            }
        }
    }

    if (historyExpanded && saved.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قصصك السابقة", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                        Text(saved.size.toString() + " قصة محفوظة", fontSize = 12.sp, color = Muted)
                    }
                    SecondaryButton("إخفاء") { historyExpanded = false }
                }
                saved.forEach { oldStory ->
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = Tint, border = BorderStroke(1.dp, Line),
                        modifier = Modifier.fillMaxWidth().clickable {
                            current = oldStory
                            step = 0
                            selectedStoryWord = null
                            quiz = false
                            fb = null
                            myText = ""
                            builderExpanded = false
                            historyExpanded = false
                        }
                    ) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(oldStory.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                            Text(oldStory.words.joinToString(", "), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                Text("مسح كل القصص", fontSize = 13.sp, color = Danger, modifier = Modifier.clickable {
                    saved = emptyList()
                    StoryApi.save(store, saved)
                    current = null
                    builderExpanded = true
                    historyExpanded = false
                }.padding(vertical = 5.dp))
            }
        }
    }

    story?.let { activeStory ->
        when (step) {
            0 -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("اضغط على الكلمة الملوّنة لمعناها", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(highlight(activeStory.en), fontSize = 17.sp, lineHeight = 28.sp, color = Night, modifier = Modifier.fillMaxWidth())
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            activeStory.words.forEach { target ->
                                val isSelected = selectedStoryWord == target
                                Chip(target, isSelected) {
                                    selectedStoryWord = if (isSelected) null else target
                                }
                            }
                        }
                        selectedStoryWord?.let { selected ->
                            val meaning = words.firstOrNull { it.en.equals(selected, ignoreCase = true) }?.ar.orEmpty()
                            Surface(shape = RoundedCornerShape(18.dp), color = Night, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        En(selected, 16.sp, Color.White, FontWeight.ExtraBold)
                                        Text(meaning, fontSize = 14.sp, color = Color(0xFF8FF0DA))
                                    }
                                    SpeakButton(44.dp) { speak(selected) }
                                }
                            }
                        }
                        PrimaryButton("اسمع القصة", Modifier.fillMaxWidth()) { speak(plain(activeStory.en)) }
                    }
                }
            }
            1 -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Tint, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Text(highlight(activeStory.ar), fontSize = 16.sp, lineHeight = 31.sp, color = Night, modifier = Modifier.padding(16.dp).fillMaxWidth())
                }
            }
            2 -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الكلمات المستخدمة · اضغط لسماعها", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
                        activeStory.words.forEach { target ->
                            val ar = words.firstOrNull { it.en.equals(target, ignoreCase = true) }?.ar.orEmpty()
                            Surface(shape = RoundedCornerShape(18.dp), color = Tint, border = BorderStroke(1.5.dp, Night), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        En(target, 17.sp, TealDark, FontWeight.ExtraBold)
                                        Text(ar, fontSize = 14.sp, color = Night)
                                    }
                                    SpeakButton(44.dp) { speak(target) }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("اختبر فهمك", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                        if (!quiz) {
                            Text("كمّل الجمل الناقصة من ذاكرتك عشان تثبّت الكلمات.", fontSize = 13.sp, lineHeight = 20.sp, color = Muted)
                            PrimaryButton("ابدأ الاختبار", Modifier.fillMaxWidth()) { quiz = true }
                        } else {
                            ClozeQuiz(activeStory, store) { missedAnswers ->
                                val ids = missedAnswers.flatMap { answer ->
                                    words.filter { w ->
                                        val stem = w.en.lowercase().let { it.take(maxOf(3, it.length - 3)) }
                                        answer.contains(stem)
                                    }.map { it.id }
                                }
                                if (ids.isNotEmpty()) store.hard = store.hard + ids
                                quiz = false
                                step = 4
                            }
                        }
                    }
                }
            }
            else -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("استخدم الكلمات بنفسك", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                        Text("اكتب ملخص القصة في جملتين أو تلاتة بالإنجليزي.", fontSize = 13.sp, color = Muted)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(value = myText, onValueChange = { myText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Write here...") })
                        }
                        PrimaryButton(if (fbLoading) "بنراجع كتابتك..." else "راجع كتابتي", Modifier.fillMaxWidth()) {
                            if (fbLoading) return@PrimaryButton
                            if (myText.trim().length < 10) { writeError = "اكتب جملة كاملة الأول."; return@PrimaryButton }
                            writeError = ""
                            fbLoading = true
                            scope.launch {
                                try {
                                    fb = withContext(Dispatchers.IO) { StoryApi.feedback(AiConfigSync.workerUrl, activeStory, myText.trim()) }
                                } catch (e: Exception) {
                                    writeError = e.message ?: "حصل خطأ، جرب تاني."
                                }
                                fbLoading = false
                            }
                        }
                        if (writeError.isNotEmpty()) Text(writeError, fontSize = 13.sp, color = Danger)
                        fb?.let { (fixed, tips) ->
                            Text("النسخة المصححة", fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.ExtraBold)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Surface(shape = RoundedCornerShape(16.dp), color = Tint) {
                                    Text(fixed, fontSize = 16.sp, lineHeight = 25.sp, color = Night, modifier = Modifier.padding(12.dp).fillMaxWidth())
                                }
                            }
                            if (tips.isNotBlank()) {
                                Text("ملاحظات للتطوير", fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.ExtraBold)
                                Text(tips, fontSize = 14.sp, lineHeight = 22.sp, color = Night)
                            }
                        }
                    }
                }
            }
        }

        if (step != 3 || !quiz) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) SecondaryButton("السابق", Modifier.weight(1f)) {
                    step--
                    quiz = false
                }
                PrimaryButton(if (step == 4) "من الأول" else "التالي", Modifier.weight(1.2f)) {
                    if (step < 4) step++ else step = 0
                    selectedStoryWord = null
                    quiz = false
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
