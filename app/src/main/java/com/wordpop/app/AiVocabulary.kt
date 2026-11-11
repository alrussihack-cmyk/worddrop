@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordpop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generates structured vocabulary using the OpenRouter settings already used by StoryTab. */
object AiVocabularyApi {
    fun generate(
        workerUrl: String,
        topic: String,
        request: String,
        count: Int,
        level: String,
        avoid: List<String> = emptyList()
    ): List<Word> {
        val avoidLine = if (avoid.isEmpty()) "" else
            "Do NOT include any of these words again: " + avoid.takeLast(150).joinToString(", ") + "\n"
        val prompt = """
You are an expert English vocabulary teacher creating a useful vocabulary pack for an Egyptian Arabic speaker.
Topic: $topic
Learner's exact request and intended meanings: $request
English level: $level
${avoidLine}Generate exactly $count useful English words or short expressions that fit the topic and the requested meanings.
Avoid duplicates, overly basic filler, obscure words, and words unrelated to the learner's request.
For every item provide:
- en: the English word or short expression.
- ipa: accurate General American IPA pronunciation, preferably between /slashes/.
- ar: a clear, natural Egyptian-Arabic meaning that matches the requested context.
- kind: Arabic part of speech: اسم، فعل، صفة، ظرف، حرف جر، حرف ربط، or عبارة for expressions.
- example: one natural, short English example sentence showing the intended meaning.
- exampleAr: a natural Egyptian-Arabic translation of that example.
Rules:
- Keep the chosen English level.
- Prefer practical vocabulary that a learner can use in real conversations.
- The Arabic meaning must match the topic context, not just a generic dictionary meaning.
- Use different words; do not repeat the same English item.
- Return valid JSON only, with this exact shape:
{"words":[{"en":"...","ipa":"/.../","ar":"...","kind":"اسم","example":"...","exampleAr":"..."}]}
""".trimIndent()

        val root = StoryApi.chatJson(workerUrl, prompt, (900 + count * 220).coerceAtMost(7500))
        val array = root.optJSONArray("words")
            ?: throw Exception("الذكاء الاصطناعي رجّع صيغة غير مفهومة. جرّب مرة تانية.")
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Word>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val english = item.optString("en").trim()
            val arabic = item.optString("ar").trim()
            if (english.isBlank() || arabic.isBlank() || !seen.add(english.lowercase())) continue

            val rawKind = item.optString("kind").trim()
            val kind = when (rawKind.lowercase()) {
                "noun", "اسم" -> "اسم"
                "verb", "فعل" -> "فعل"
                "adjective", "صفة" -> "صفة"
                "adverb", "ظرف" -> "ظرف"
                "preposition", "حرف جر" -> "حرف جر"
                "conjunction", "حرف ربط" -> "حرف ربط"
                "phrase", "expression", "عبارة" -> "عبارة"
                else -> rawKind.ifBlank { if (english.contains(" ")) "عبارة" else "كلمة" }
            }
            result += Word(
                id = 0,
                en = english,
                ipa = item.optString("ipa").trim(),
                ar = arabic,
                kind = kind,
                example = item.optString("example").trim(),
                exampleAr = item.optString("exampleAr").trim(),
                level = level
            )
        }

        if (result.isEmpty()) throw Exception("ملقتش كلمات مكتملة في الرد. جرّب وصفًا أوضح.")
        return result.take(count)
    }

    /** Large lists are requested in batches of 25 so each reply stays small and fast. */
    fun generateBatched(
        workerUrl: String,
        topic: String,
        request: String,
        count: Int,
        level: String,
        onProgress: (List<Word>) -> Unit
    ): List<Word> {
        val all = mutableListOf<Word>()
        val seen = mutableSetOf<String>()
        var failedRounds = 0
        while (all.size < count && failedRounds < 2) {
            val need = minOf(25, count - all.size)
            val batch = try {
                generate(workerUrl, topic, request, need, level, all.map { it.en })
            } catch (e: Exception) {
                if (all.isEmpty()) throw e
                failedRounds++
                continue
            }
            val fresh = batch.filter { seen.add(it.en.trim().lowercase()) }
            if (fresh.isEmpty()) failedRounds++ else {
                failedRounds = 0
                all += fresh
                onProgress(all.toList())
            }
        }
        return all.take(count)
    }
}

/** One row of the review list. */
private data class PreviewItem(val key: Int, val word: Word, val selected: Boolean = true)

private fun isKnown(word: Word) = WordRepo.all.any { it.en.trim().equals(word.en.trim(), ignoreCase = true) }

@Composable
fun AiVocabularyTab(
    store: SettingsStore,
    onAdded: (ImportResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var topic by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(20) }
    var level by remember { mutableStateOf("B1") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<PreviewItem>>(emptyList()) }
    var progress by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var expandedKey by remember { mutableIntStateOf(-1) }
    var editingKey by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf<Word?>(null) }
    var limit by remember { mutableIntStateOf(20) }

    Section("مولّد الكلمات بالذكاء الاصطناعي") {
        Text(
            "اختار موضوع، واشرح المعاني والمواقف اللي محتاجها. WordPop هيجهّز الكلمات والترجمة والنطق والأمثلة، وبعدها تراجع كل حاجة قبل الإضافة.",
            fontSize = 14.sp,
            color = Muted
        )

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("الموضوع") },
            placeholder = { Text("مثال: خدمة النزلاء في الفنادق") }
        )
        OutlinedTextField(
            value = request,
            onValueChange = { request = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            label = { Text("عايز الكلمات تعبر عن إيه بالضبط؟") },
            placeholder = { Text("مثال: كلمات للتعامل مع شكاوى النزلاء، الاعتذار، حل المشكلات، وشرح الخدمات بطريقة احترافية.") }
        )

        Text("عدد الكلمات", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
        ChipRow {
            listOf(10, 20, 30, 50, 100).forEach { n ->
                Chip("$n كلمة", count == n) { count = n }
            }
        }

        Text("مستوى اللغة", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
        ChipRow {
            listOf("A" to "أساسي", "B1" to "متوسط", "B2" to "متقدم").forEach { (code, label) ->
                Chip("$label $code", level == code) { level = code }
            }
        }

        Text(AiConfigSync.status, fontSize = 13.sp, color = Muted)

        if (error.isNotBlank()) Text(error, color = Danger, fontSize = 13.sp)
        if (notice.isNotBlank()) Text(notice, color = TealDark, fontSize = 13.sp)

        PrimaryButton(
            if (loading) (if (progress.isBlank()) "جاري تجهيز الكلمات..." else progress) else "ولّد قائمة الكلمات",
            Modifier.fillMaxWidth()
        ) {
            if (loading) return@PrimaryButton
            if (topic.isBlank()) {
                error = "اكتب الموضوع الأول."
                return@PrimaryButton
            }
            if (request.isBlank()) {
                error = "اشرح للذكاء الاصطناعي المعاني أو المواقف اللي محتاج الكلمات فيها."
                return@PrimaryButton
            }
            if (!AiConfigSync.isReady) {
                error = AiConfigSync.status
                return@PrimaryButton
            }

            error = ""
            notice = ""
            loading = true
            scope.launch {
                try {
                    progress = "جاري تجهيز الكلمات..."
                    val generated = withContext(Dispatchers.IO) {
                        AiVocabularyApi.generateBatched(
                            workerUrl = AiConfigSync.workerUrl,
                            topic = topic.trim(),
                            request = request.trim(),
                            count = count,
                            level = level
                        ) { partial -> progress = "جاهز ${partial.size} من $count..." }
                    }
                    items = generated.mapIndexed { i, w -> PreviewItem(i, w, selected = !isKnown(w)) }
                    query = ""; filter = 0; expandedKey = -1; editingKey = -1; draft = null; limit = 20
                    notice = "اتولدت ${generated.size} كلمة. راجعها وعدّلها قبل ما تضيفها."
                } catch (exception: Exception) {
                    error = exception.message ?: "حصل خطأ أثناء التوليد. جرّب تاني."
                } finally {
                    loading = false
                    progress = ""
                }
            }
        }
    }

    if (items.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Section("معاينة الكلمات قبل الإضافة") {
            val knownCount = items.count { isKnown(it.word) }
            val selectedCount = items.count { it.selected }
            Text(
                "${items.size} كلمة • ${items.size - knownCount} جديدة • $knownCount موجودة بالفعل • $selectedCount محددة للإضافة",
                fontSize = 13.sp, color = Muted
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; limit = 20 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ابحث في الكلمات") }
            )
            ChipRow {
                Chip("الكل", filter == 0) { filter = 0; limit = 20 }
                Chip("جديدة", filter == 1) { filter = 1; limit = 20 }
                Chip("موجودة بالفعل", filter == 2) { filter = 2; limit = 20 }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("حدّد الجديدة", Modifier.weight(1f)) {
                    items = items.map { it.copy(selected = !isKnown(it.word)) }
                }
                SecondaryButton("إلغاء التحديد", Modifier.weight(1f)) {
                    items = items.map { it.copy(selected = false) }
                }
            }
            if (knownCount > 0) {
                SecondaryButton("احذف الموجودة بالفعل ($knownCount)", Modifier.fillMaxWidth()) {
                    items = items.filterNot { isKnown(it.word) }
                    expandedKey = -1; editingKey = -1; draft = null
                }
            }

            val q = query.trim().lowercase()
            val shown = items.filter { item ->
                val known = isKnown(item.word)
                (filter == 0 || (filter == 1 && !known) || (filter == 2 && known)) &&
                    (q.isEmpty() || item.word.en.lowercase().contains(q) || item.word.ar.contains(q))
            }
            if (shown.isEmpty()) Text("مفيش كلمات مطابقة.", fontSize = 14.sp, color = Muted)

            shown.take(limit).forEach { item ->
                val word = item.word
                val known = isKnown(word)
                val open = expandedKey == item.key
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = if (known) Color(0xFFF6F7F9) else Color.White,
                    border = BorderStroke(if (open) 2.dp else 1.dp, if (open) Night else Line)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                expandedKey = if (open) -1 else item.key
                                if (editingKey != item.key) { editingKey = -1; draft = null }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val box = RoundedCornerShape(8.dp)
                            Box(
                                Modifier.size(28.dp).clip(box)
                                    .background(if (item.selected) Teal else Color.White)
                                    .border(2.dp, Night, box)
                                    .clickable { items = items.map { if (it.key == item.key) it.copy(selected = !it.selected) else it } },
                                contentAlignment = Alignment.Center
                            ) { if (item.selected) Text("✓", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                            Column(Modifier.weight(1f)) {
                                En(word.en, 17.sp, Night, FontWeight.Bold)
                                Text(
                                    if (known) "${word.ar} • موجودة بالفعل" else word.ar,
                                    fontSize = 14.sp, maxLines = 1,
                                    color = if (known) Danger else Muted
                                )
                            }
                            Text(if (open) "▲" else "▼", fontSize = 12.sp, color = Muted)
                        }
                        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Ltr { Text(word.ipa.ifBlank { "—" }, fontSize = 14.sp, color = Muted) }
                                Text(word.kind, fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                if (word.example.isNotBlank()) Ltr { Text(word.example, fontSize = 14.sp, color = Night) }
                                if (word.exampleAr.isNotBlank()) Text(word.exampleAr, fontSize = 13.sp, color = Muted)

                                if (editingKey == item.key && draft != null) {
                                    OutlinedTextField(value = draft!!.en, onValueChange = { draft = draft!!.copy(en = it) }, modifier = Modifier.fillMaxWidth(), label = { Text("الكلمة بالإنجليزية") })
                                    OutlinedTextField(value = draft!!.ipa, onValueChange = { draft = draft!!.copy(ipa = it) }, modifier = Modifier.fillMaxWidth(), label = { Text("النطق IPA") })
                                    OutlinedTextField(value = draft!!.ar, onValueChange = { draft = draft!!.copy(ar = it) }, modifier = Modifier.fillMaxWidth(), label = { Text("المعنى بالعربي") })
                                    OutlinedTextField(value = draft!!.example, onValueChange = { draft = draft!!.copy(example = it) }, modifier = Modifier.fillMaxWidth(), label = { Text("مثال بالإنجليزية") })
                                    OutlinedTextField(value = draft!!.exampleAr, onValueChange = { draft = draft!!.copy(exampleAr = it) }, modifier = Modifier.fillMaxWidth(), label = { Text("ترجمة المثال") })
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PrimaryButton("حفظ التعديل") {
                                            val edited = draft!!
                                            items = items.map { if (it.key == item.key) it.copy(word = edited, selected = !isKnown(edited)) else it }
                                            editingKey = -1; draft = null
                                        }
                                        SecondaryButton("إلغاء") { editingKey = -1; draft = null }
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SecondaryButton("تعديل", Modifier.weight(1f)) { editingKey = item.key; draft = word }
                                        SecondaryButton("حذف", Modifier.weight(1f)) {
                                            items = items.filterNot { it.key == item.key }
                                            expandedKey = -1
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (shown.size > limit) {
                SecondaryButton("اعرض المزيد (${shown.size - limit} كلمة)", Modifier.fillMaxWidth()) { limit += 20 }
            }

            PrimaryButton("اعتماد وإضافة المحدد ($selectedCount)", Modifier.fillMaxWidth()) {
                val chosen = items.filter { it.selected }.map { it.word }
                if (chosen.isEmpty()) {
                    error = "حدّد كلمة واحدة على الأقل."
                    return@PrimaryButton
                }
                val result = WordRepo.addCustomWords(context, chosen)
                items = emptyList(); editingKey = -1; expandedKey = -1; draft = null
                onAdded(result)
                error = ""
                notice = "تمت إضافة ${result.added} كلمة إلى مكتبتك."
                if (result.duplicates > 0) notice += " تم تجاهل ${result.duplicates} مكررة."
                if (result.skipped > 0) notice += " تم تجاهل ${result.skipped} غير مكتملة."
            }
            SecondaryButton("إلغاء القائمة", Modifier.fillMaxWidth()) {
                items = emptyList(); editingKey = -1; expandedKey = -1; draft = null
                notice = "تم إلغاء المعاينة من غير إضافة كلمات."
            }
        }
    }
}
