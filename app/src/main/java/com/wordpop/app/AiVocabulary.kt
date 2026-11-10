@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordpop.app

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generates vocabulary through the secure Worker; the OpenRouter key never enters this app. */
object AiVocabularyApi {
    fun generate(
        workerUrl: String,
        topic: String,
        request: String,
        count: Int,
        level: String
    ): List<Word> {
        val prompt = """
You are an expert English vocabulary teacher creating a useful vocabulary pack for an Egyptian Arabic speaker.
Topic: $topic
Learner's exact request and intended meanings: $request
English level: $level
Generate exactly $count useful English words or short expressions that fit the topic and the requested meanings.
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

        val root = StoryApi.chatJson(workerUrl, prompt, (900 + count * 220).coerceAtMost(8000))
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
}

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
    var preview by remember { mutableStateOf<List<Word>>(emptyList()) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf<Word?>(null) }

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
            listOf(10, 20, 30).forEach { n ->
                Chip("$n كلمة", count == n) { count = n }
            }
        }

        Text("مستوى اللغة", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
        ChipRow {
            listOf("A" to "أساسي", "B1" to "متوسط", "B2" to "متقدم").forEach { (code, label) ->
                Chip("$label $code", level == code) { level = code }
            }
        }

        Text(
            if (AiConfigSync.isReady) {
                "إعدادات OpenRouter ومفتاح API تتم إدارتها مركزيًا من WordPop Control."
            } else {
                "خدمة الذكاء الاصطناعي غير جاهزة. اطلب من المسؤول ضبط مفتاح OpenRouter ورابط الخدمة من WordPop Control."
            },
            fontSize = 13.sp,
            color = if (AiConfigSync.isReady) TealDark else Muted
        )

        if (error.isNotBlank()) Text(error, color = Danger, fontSize = 13.sp)
        if (notice.isNotBlank()) Text(notice, color = TealDark, fontSize = 13.sp)

        PrimaryButton(
            if (loading) "جاري تجهيز الكلمات..." else "ولّد قائمة الكلمات",
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
                error = "خدمة الذكاء الاصطناعي غير مفعّلة. راجع إعدادات WordPop Control."
                return@PrimaryButton
            }

            error = ""
            notice = ""
            loading = true
            scope.launch {
                try {
                    val generated = withContext(Dispatchers.IO) {
                        AiVocabularyApi.generate(
                            workerUrl = AiConfigSync.workerUrl,
                            topic = topic.trim(),
                            request = request.trim(),
                            count = count,
                            level = level
                        )
                    }
                    preview = generated
                    editingIndex = -1
                    draft = null
                    notice = "اتولدت ${generated.size} كلمة. راجعها وعدّلها قبل ما تضيفها."
                } catch (exception: Exception) {
                    error = exception.message ?: "حصل خطأ أثناء التوليد. جرّب تاني."
                } finally {
                    loading = false
                }
            }
        }
    }

    if (preview.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Section("معاينة الكلمات قبل الإضافة") {
            val duplicateCount = preview.count { candidate ->
                WordRepo.all.any { it.en.trim().equals(candidate.en.trim(), ignoreCase = true) }
            }
            Text(
                "${preview.size} كلمة في المعاينة • $duplicateCount مكررة وموجودة بالفعل",
                fontSize = 13.sp,
                color = Muted
            )
            Text(
                "اضغط تعديل لمراجعة أي كلمة. الكلمات لن تُضاف إلا بعد ضغط زر اعتماد وإضافة.",
                fontSize = 13.sp,
                color = Muted
            )

            preview.forEachIndexed { index, word ->
                val isDuplicate = WordRepo.all.any {
                    it.en.trim().equals(word.en.trim(), ignoreCase = true)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Line)
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                En(word.en, 20.sp, Night, FontWeight.Bold)
                                Text(
                                    if (isDuplicate) "موجودة بالفعل — لن تُضاف مرة ثانية" else word.kind,
                                    fontSize = 12.sp,
                                    color = if (isDuplicate) Danger else TealDark
                                )
                            }
                            SecondaryButton("حذف") {
                                preview = preview.filterIndexed { itemIndex, _ -> itemIndex != index }
                                if (editingIndex == index) {
                                    editingIndex = -1
                                    draft = null
                                } else if (editingIndex > index) {
                                    editingIndex--
                                }
                            }
                        }

                        Ltr { Text(word.ipa.ifBlank { "النطق الصوتي متاح من زر الصوت بعد الإضافة" }, fontSize = 14.sp, color = Muted) }
                        Text(word.ar, fontSize = 16.sp, color = Night, fontWeight = FontWeight.Medium)
                        if (word.example.isNotBlank()) {
                            Ltr { Text(word.example, fontSize = 14.sp, color = Night) }
                        }
                        if (word.exampleAr.isNotBlank()) {
                            Text(word.exampleAr, fontSize = 13.sp, color = Muted)
                        }

                        if (editingIndex == index && draft != null) {
                            OutlinedTextField(
                                value = draft!!.en,
                                onValueChange = { draft = draft!!.copy(en = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("الكلمة بالإنجليزية") }
                            )
                            OutlinedTextField(
                                value = draft!!.ipa,
                                onValueChange = { draft = draft!!.copy(ipa = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("النطق IPA") }
                            )
                            OutlinedTextField(
                                value = draft!!.ar,
                                onValueChange = { draft = draft!!.copy(ar = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("المعنى بالعربي") }
                            )
                            OutlinedTextField(
                                value = draft!!.example,
                                onValueChange = { draft = draft!!.copy(example = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("مثال بالإنجليزية") }
                            )
                            OutlinedTextField(
                                value = draft!!.exampleAr,
                                onValueChange = { draft = draft!!.copy(exampleAr = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("ترجمة المثال") }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("حفظ التعديل") {
                                    val updated = preview.toMutableList()
                                    updated[index] = draft!!
                                    preview = updated
                                    editingIndex = -1
                                    draft = null
                                }
                                SecondaryButton("إلغاء") {
                                    editingIndex = -1
                                    draft = null
                                }
                            }
                        } else {
                            SecondaryButton("تعديل الكلمة", Modifier.fillMaxWidth()) {
                                editingIndex = index
                                draft = word
                            }
                        }
                    }
                }
            }

            PrimaryButton("اعتماد وإضافة (${preview.size})", Modifier.fillMaxWidth()) {
                val result = WordRepo.addCustomWords(context, preview)
                preview = emptyList()
                editingIndex = -1
                draft = null
                onAdded(result)
                notice = "تمت إضافة ${result.added} كلمة إلى مكتبتك."
                if (result.duplicates > 0) notice += " تم تجاهل ${result.duplicates} مكررة."
                if (result.skipped > 0) notice += " تم تجاهل ${result.skipped} غير مكتملة."
            }
            SecondaryButton("إلغاء القائمة", Modifier.fillMaxWidth()) {
                preview = emptyList()
                editingIndex = -1
                draft = null
                notice = "تم إلغاء المعاينة من غير إضافة كلمات."
            }
        }
    }
}
