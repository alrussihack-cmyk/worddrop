package com.wordpop.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.Alignment
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
            conn.setRequestProperty("X-Firebase-AppCheck", AppCheckTokens.currentToken())
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
private fun ClozeQuiz(story: Story, onFinish: (List<String>) -> Unit) {
    val qs = remember(story) { buildCloze(story) }
    var idx by remember(story) { mutableIntStateOf(0) }
    var picked by remember(story) { mutableStateOf<String?>(null) }
    var score by remember(story) { mutableIntStateOf(0) }
    val missed = remember(story) { mutableListOf<String>() }
    val results = remember(story) { mutableStateListOf<Boolean>() }

    if (qs.isEmpty()) {
        Text("القصة دي مفيهاش كلمات كفاية للاختبار. اعمل قصة جديدة.", fontSize = 15.sp, color = Muted)
        return
    }
    if (idx >= qs.size) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Boby(if (missed.isEmpty()) Mood.Cheer else Mood.Happy, 72.dp, Modifier.bob())
            Text("نتيجتك: $score من ${qs.size}", fontSize = 22.sp, fontFamily = Display, color = Night)
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
    val answered = picked != null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("سؤال ${idx + 1} من ${qs.size}: اختار الكلمة الناقصة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            qs.indices.forEach { i ->
                val segment = when {
                    i < results.size -> if (results[i]) Teal else Coral
                    i == idx -> Night
                    else -> Line
                }
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(segment))
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(q.sentence, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = Night, modifier = Modifier.fillMaxWidth())
            q.options.forEach { o ->
                val right = o == q.answer
                val bg = when {
                    answered && right -> Teal
                    answered && o == picked -> MissTint
                    else -> Color.White
                }
                val line = if (answered && o == picked && !right) Danger else Night
                val optionShape = RoundedCornerShape(14.dp)
                Surface(
                    shape = optionShape, color = bg,
                    border = BorderStroke(2.dp, line),
                    modifier = Modifier.fillMaxWidth().hardShadow(optionShape, 3.dp).clickable(enabled = !answered) {
                        picked = o
                        results.add(right)
                        if (right) score++ else missed += q.answer
                    }
                ) {
                    Text(o, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Night, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
            }
        }
        AnimatedVisibility(visible = answered, enter = fadeIn(tween(200)) + expandVertically(tween(250))) {
            val ok = picked == q.answer
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = if (ok) Tint else MissTint, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Boby(if (ok) Mood.Cheer else Mood.Think, 40.dp)
                        Text(
                            if (ok) "تمام! إجابة صح" else "الصح: ${q.answer}. هنراجعها تاني.",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (ok) TealDark else Danger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                PrimaryButton(if (idx + 1 >= qs.size) "شوف النتيجة" else "التالي", Modifier.fillMaxWidth()) {
                    idx++; picked = null
                }
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
    var step by remember { mutableIntStateOf(0) }
    var quiz by remember { mutableStateOf(false) }
    var myText by remember { mutableStateOf("") }
    var fbLoading by remember { mutableStateOf(false) }
    var fb by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("إعداد القصة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text(if (picked.isEmpty()) "اختار من كلماتك المفضلة" else "${picked.size} كلمات مختارة", fontSize = 12.sp, color = Muted)
                    }
                    SecondaryButton(if (builderExpanded) "إخفاء" else "تعديل") { builderExpanded = !builderExpanded }
                }

                if (builderExpanded) {
                    if (words.size < 2) {
                        EmptyFav("ضيف كلمتين مفضلين على الأقل من شاشة التعلّم، وبعدها تقدر تعمل قصة.")
                    } else {
                        Text("1 · اختار الكلمات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
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

                        Text("2 · اختار نوع القصة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            StoryApi.genres.forEachIndexed { i, g -> Chip(g.first, genre == i) { genre = i } }
                        }

                        Text("3 · اختار المستوى", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            StoryApi.levels.forEachIndexed { i, l -> Chip(l.first, level == i) { level = i } }
                        }

                        Text(AiConfigSync.status, fontSize = 13.sp, color = Muted)

                        if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = Danger)
                        PrimaryButton(if (loading) "بنكتب القصة..." else "اعمل قصتي", Modifier.fillMaxWidth()) {
                            if (loading) return@PrimaryButton
                            if (!AiConfigSync.isReady) { error = AiConfigSync.status; return@PrimaryButton }
                            if (picked.size < 2) { error = "اختار كلمتين على الأقل."; return@PrimaryButton }
                            error = ""; loading = true
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
                } else {
                    Text("الكلمات والنوع والمستوى محفوظين لحد ما تحب تعدّلهم.", fontSize = 13.sp, color = Muted)
                }
            }
        }

        current?.let { story ->
            val labels = listOf("اقرأ", "ترجمة", "كلمات", "اختبر", "اكتب")
            val progress by animateFloatAsState((step + 1) / labels.size.toFloat(), tween(420), label = "storyProgress")
            val cardShape = RoundedCornerShape(24.dp)
            Surface(
                shape = cardShape, color = Color.White, border = BorderStroke(2.dp, Night),
                modifier = Modifier.fillMaxWidth().hardShadow(cardShape, 4.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Boby(Mood.Cheer, 44.dp, Modifier.bob())
                        Column(Modifier.weight(1f)) {
                            Text("قصتك من كلماتك", fontSize = 17.sp, fontFamily = Display, color = Night)
                            Text(story.title, fontSize = 14.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Teal,
                        trackColor = Line
                    )
                    StepTabs(labels, step) { step = it }

                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val dir = if (targetState > initialState) -1 else 1
                            (slideInHorizontally(tween(320)) { dir * it / 4 } + fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(tween(200)) { -dir * it / 4 } + fadeOut(tween(120)))
                        },
                        label = "storyStep"
                    ) { s ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (s) {
                                0 -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(highlight(story.en), fontSize = 17.sp, lineHeight = 28.sp, color = Night, modifier = Modifier.fillMaxWidth())
                                }
                                1 -> if (story.ar.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
                                        Text(highlight(story.ar), fontSize = 16.sp, lineHeight = 27.sp, color = Night, modifier = Modifier.padding(14.dp).fillMaxWidth())
                                    }
                                } else {
                                    Text("مفيش ترجمة للقصة دي.", fontSize = 14.sp, color = Muted)
                                }
                                2 -> {
                                    Text("الكلمات المستخدمة · اضغط على السماعة للاستماع", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                                    story.words.forEach { w ->
                                        val ar = words.firstOrNull { it.en == w }?.ar ?: ""
                                        Surface(shape = RoundedCornerShape(16.dp), color = Tint, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    En(w, 17.sp, TealDark, FontWeight.ExtraBold)
                                                    if (ar.isNotEmpty()) Text(ar, fontSize = 15.sp, color = Night)
                                                }
                                                SpeakButton(40.dp) { speak(w) }
                                            }
                                        }
                                    }
                                }
                                3 -> if (!quiz) {
                                    Text("كمّل الجمل الناقصة من ذاكرتك عشان تثبّت الكلمات.", fontSize = 14.sp, color = Muted)
                                    PrimaryButton("ابدأ الاختبار", Modifier.fillMaxWidth()) { quiz = true }
                                } else {
                                    ClozeQuiz(story) { missedAnswers ->
                                        val ids = missedAnswers.flatMap { answer ->
                                            words.filter { w ->
                                                val stem = w.en.lowercase().let { it.take(maxOf(3, it.length - 3)) }
                                                answer.contains(stem)
                                            }.map { it.id }
                                        }
                                        if (ids.isNotEmpty()) store.hard = store.hard + ids
                                        quiz = false
                                    }
                                }
                                else -> {
                                    Text("اكتب ملخص القصة في جملتين أو تلاتة بالإنجليزي.", fontSize = 14.sp, color = Muted)
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        OutlinedTextField(value = myText, onValueChange = { myText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Write here...") })
                                    }
                                    PrimaryButton(if (fbLoading) "بنراجع كتابتك..." else "راجع كتابتي", Modifier.fillMaxWidth()) {
                                        if (fbLoading) return@PrimaryButton
                                        if (myText.trim().length < 10) { error = "اكتب جملة كاملة الأول."; return@PrimaryButton }
                                        error = ""; fbLoading = true
                                        scope.launch {
                                            try {
                                                fb = withContext(Dispatchers.IO) { StoryApi.feedback(AiConfigSync.workerUrl, story, myText.trim()) }
                                            } catch (e: Exception) {
                                                error = e.message ?: "حصل خطأ، جرب تاني."
                                            }
                                            fbLoading = false
                                        }
                                    }
                                    if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = Danger)
                                    fb?.let { (fixed, tips) ->
                                        Text("النسخة المصححة", fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Surface(shape = RoundedCornerShape(14.dp), color = Tint) {
                                                Text(fixed, fontSize = 16.sp, lineHeight = 25.sp, color = Night, modifier = Modifier.padding(12.dp).fillMaxWidth())
                                            }
                                        }
                                        if (tips.isNotBlank()) {
                                            Text("ملاحظات للتطوير", fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                            Text(tips, fontSize = 14.sp, lineHeight = 22.sp, color = Night)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("اسمع القصة", Modifier.weight(1.25f)) { speak(plain(story.en)) }
                        SecondaryButton(if (step < labels.lastIndex) "التالي" else "من الأول", Modifier.weight(1f)) {
                            step = (step + 1) % labels.size
                        }
                    }
                }
            }
        }

        if (saved.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("قصصك السابقة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                            Text("${saved.size} قصة محفوظة", fontSize = 12.sp, color = Muted)
                        }
                        SecondaryButton(if (historyExpanded) "إخفاء" else "عرض") { historyExpanded = !historyExpanded }
                    }
                    if (historyExpanded) {
                        saved.forEach { oldStory ->
                            Surface(shape = RoundedCornerShape(13.dp), color = Tint, modifier = Modifier.fillMaxWidth().clickable {
                                current = oldStory
                                step = 0
                                quiz = false
                                fb = null
                                builderExpanded = false
                                historyExpanded = false
                            }) {
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
                        }.padding(vertical = 5.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
