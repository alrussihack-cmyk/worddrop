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

    private fun prompt(words: List<Word>): String {
        val list = words.joinToString("\n") { "- ${it.en} (${it.ar})" }
        return """Write a short, engaging story in simple B1-level English for an Arabic-speaking learner.
Rules:
- 100 to 140 words, 2 short paragraphs, a clear beginning, a small problem and an ending.
- Use EVERY one of these words or phrases at least once (small grammar changes are fine):
$list
- Wrap each use of these words in double asterisks, like **word**.
- Make the situation memorable and visual so the words are easy to remember.
- Also give a natural Egyptian-Arabic translation of the story. In the translation wrap the Arabic equivalent of each target word in ** too.
Reply with JSON only, no other text: {"title":"...","story":"...","translation":"..."}"""
    }

    /** Blocking call. Run it off the main thread. Throws Exception with a readable message. */
    fun generate(key: String, model: String, words: List<Word>): Story {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.8)
            .put("max_tokens", 1200)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt(words))))
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
            if (code !in 200..299) {
                val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { text.take(120) }
                throw Exception(
                    when (code) {
                        401, 403 -> "المفتاح مش مقبول. تأكد منه."
                        402 -> "الرصيد خلص في حساب OpenRouter."
                        429 -> "طلبات كتير، جرب بعد شوية."
                        else -> "خطأ $code: $msg"
                    }
                )
            }
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) throw Exception("الرد جه بشكل غير متوقع، جرب تاني.")
            val o = JSONObject(content.substring(start, end + 1))
            return Story(
                title = o.optString("title", "قصتي"),
                en = o.getString("story").trim(),
                ar = o.optString("translation").trim(),
                words = words.map { it.en }
            )
        } catch (e: java.io.IOException) {
            throw Exception("مفيش اتصال بالنت.")
        } catch (e: org.json.JSONException) {
            throw Exception("الرد جه بشكل غير متوقع، جرب تاني.")
        } finally {
            conn.disconnect()
        }
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
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(SpanStyle(color = TealDark, background = Tint, fontWeight = FontWeight.Bold)) { append(part) }
        } else append(part)
    }
}

private fun plain(text: String) = text.replace("**", "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryTab(store: SettingsStore, words: List<Word>, speak: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(store.orKey) }
    var model by remember { mutableStateOf(store.orModel) }
    var editKey by remember { mutableStateOf(store.orKey.isEmpty()) }
    var picked by remember(words.size) { mutableStateOf(words.take(6).map { it.id }.toSet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(StoryApi.load(store)) }
    var current by remember { mutableStateOf<Story?>(saved.firstOrNull()) }
    var showAr by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = Tint) {
            Text(
                "اختار كلماتك المفضلة وأعمل منها قصة قصيرة. القصة بتخلي الكلمات تتحفظ في سياق، وهتلاقي كل كلمة ملونة جواها.",
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

            PrimaryButton(
                if (loading) "بنكتب القصة..." else "اعمل قصة",
                Modifier.fillMaxWidth()
            ) {
                if (loading) return@PrimaryButton
                if (store.orKey.isBlank()) { editKey = true; error = "ضيف مفتاح OpenRouter الأول."; return@PrimaryButton }
                if (picked.size < 2) { error = "اختار كلمتين على الأقل."; return@PrimaryButton }
                error = ""; loading = true
                val chosen = words.filter { it.id in picked }
                scope.launch {
                    try {
                        val story = withContext(Dispatchers.IO) { StoryApi.generate(store.orKey, store.orModel, chosen) }
                        saved = listOf(story) + saved
                        StoryApi.save(store, saved)
                        current = story; showAr = false; version++
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
                border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(st.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night)
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                    ) {
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
                    Text("الكلمات: " + st.words.joinToString("، "), fontSize = 14.sp, color = Muted)
                }
            }
        }

        if (saved.size > 1) {
            Text("قصصك اللي فاتت", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
            saved.forEach { st ->
                Surface(
                    shape = RoundedCornerShape(14.dp), color = Beige,
                    modifier = Modifier.fillMaxWidth().clickable { current = st; showAr = false }
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
