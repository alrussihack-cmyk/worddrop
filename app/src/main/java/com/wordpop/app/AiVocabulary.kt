@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordpop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
    private const val BATCH_SIZE = 25
    private const val MAX_ATTEMPTS_PER_BATCH = 3

    fun generate(
        workerUrl: String,
        topic: String,
        request: String,
        count: Int,
        level: String,
        onProgress: (completed: Int, total: Int, batch: Int, batches: Int) -> Unit = { _, _, _, _ -> }
    ): List<Word> {
        val targetCount = count.coerceIn(1, 100)
        val totalBatches = (targetCount + BATCH_SIZE - 1) / BATCH_SIZE
        val result = mutableListOf<Word>()
        val seen = mutableSetOf<String>()

        for (batchNumber in 1..totalBatches) {
            val batchTarget = minOf(targetCount, batchNumber * BATCH_SIZE)
            var attempts = 0

            while (result.size < batchTarget && attempts < MAX_ATTEMPTS_PER_BATCH) {
                attempts++
                val needed = batchTarget - result.size
                val excluded = result.joinToString(", ") { it.en }
                val exclusionRule = if (excluded.isBlank()) "" else
                    "Do not repeat any of these already generated words or expressions: $excluded"

                val prompt = """
You are an expert English vocabulary teacher creating a useful vocabulary pack for an Egyptian Arabic speaker.
Topic: $topic
Learner's exact request and intended meanings: $request
English level: $level
Generate exactly $needed useful English words or short expressions that fit the topic and the requested meanings.
$exclusionRule
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
- Return exactly $needed items in valid JSON only, with this exact shape:
{"words":[{"en":"...","ipa":"/.../","ar":"...","kind":"اسم","example":"...","exampleAr":"..."}]}
""".trimIndent()

                val root = StoryApi.chatJson(workerUrl, prompt, (900 + needed * 220).coerceAtMost(8000))
                val array = root.optJSONArray("words")
                    ?: throw Exception("الذكاء الاصطناعي رجّع صيغة غير مفهومة. جرّب مرة تانية.")

                val before = result.size
                for (index in 0 until array.length()) {
                    if (result.size >= batchTarget) break
                    val item = array.optJSONObject(index) ?: continue
                    val english = item.optString("en").trim()
                    val arabic = item.optString("ar").trim()
                    val key = english.lowercase()
                    if (english.isBlank() || arabic.isBlank() || !seen.add(key)) continue

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

                onProgress(result.size, targetCount, batchNumber, totalBatches)
                if (result.size == before && attempts == MAX_ATTEMPTS_PER_BATCH) {
                    throw Exception("تم تجهيز ${result.size} من ${targetCount} كلمة فقط. جرّب توليد الباقي مرة تانية.")
                }
            }

            if (result.size < batchTarget) {
                throw Exception("تم تجهيز ${result.size} من ${targetCount} كلمة فقط. جرّب مرة تانية.")
            }
        }

        if (result.size != targetCount) {
            throw Exception("تم تجهيز ${result.size} من ${targetCount} كلمة فقط. جرّب مرة تانية.")
        }
        return result
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
    var generatedCount by remember { mutableIntStateOf(0) }
    var currentBatch by remember { mutableIntStateOf(0) }
    var totalBatches by remember { mutableIntStateOf(0) }
    var generationProgress by remember { mutableStateOf(0f) }
    var compactReview by remember { mutableStateOf(false) }
    var expandedGroups by remember(preview.size) { mutableStateOf(setOf(0)) }

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
                error = AiConfigSync.status
                return@PrimaryButton
            }

            error = ""
            notice = ""
            generatedCount = 0
            generationProgress = 0f
            currentBatch = 1
            totalBatches = (count + 24) / 25
            loading = true
            scope.launch {
                try {
                    val generated = withContext(Dispatchers.IO) {
                        AiVocabularyApi.generate(
                            workerUrl = AiConfigSync.workerUrl,
                            topic = topic.trim(),
                            request = request.trim(),
                            count = count,
                            level = level,
                            onProgress = { completed, total, batch, batches ->
                                scope.launch {
                                    generatedCount = completed
                                    generationProgress = if (total > 0) completed.toFloat() / total else 0f
                                    currentBatch = batch
                                    totalBatches = batches
                                }
                            }
                        )
                    }
                    preview = generated
                    generatedCount = generated.size
                    generationProgress = 1f
                    compactReview = false
                    expandedGroups = setOf(0)
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
        AnimatedVisibility(visible = loading) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "جاري تجهيز الدفعة $currentBatch من $totalBatches • تم تجهيز $generatedCount من $count كلمة",
                    fontSize = 13.sp,
                    color = TealDark,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = generationProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = Teal,
                    trackColor = Line
                )
            }
        }
    }

    if (preview.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Section("معاينة الكلمات قبل الإضافة") {
            val duplicateCount = preview.count { candidate ->
                WordRepo.all.any { it.en.trim().equals(candidate.en.trim(), ignoreCase = true) }
            }
            val groupCount = (preview.size + 9) / 10

            fun addPreviewWords() {
                val result = WordRepo.addCustomWords(context, preview)
                preview = emptyList()
                editingIndex = -1
                draft = null
                onAdded(result)
                notice = "تمت إضافة ${result.added} كلمة إلى مكتبتك."
                if (result.duplicates > 0) notice += " تم تجاهل ${result.duplicates} مكررة."
                if (result.skipped > 0) notice += " تم تجاهل ${result.skipped} غير مكتملة."
            }

            fun cancelPreview() {
                preview = emptyList()
                editingIndex = -1
                draft = null
                notice = "تم إلغاء المعاينة من غير إضافة كلمات."
            }

            Text(
                "${preview.size} كلمة في المعاينة • $duplicateCount مكررة وموجودة بالفعل",
                fontSize = 13.sp,
                color = Muted
            )
            Text(
                "قسّمنا الكلمات إلى مجموعات من 10 لتسهيل المراجعة. تقدر تراجعها في قائمة مختصرة أو تفتح كل مجموعة.",
                fontSize = 13.sp,
                color = Muted
            )

            PrimaryButton("اعتماد وإضافة (${preview.size})", Modifier.fillMaxWidth()) {
                addPreviewWords()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    if (compactReview) "عرض المجموعات" else "مراجعة مختصرة",
                    Modifier.weight(1f)
                ) {
                    compactReview = !compactReview
                }
                if (!compactReview) {
                    SecondaryButton(
                        if (expandedGroups.size == groupCount) "طي الكل" else "فتح الكل",
                        Modifier.weight(1f)
                    ) {
                        expandedGroups = if (expandedGroups.size == groupCount) {
                            emptySet()
                        } else {
                            (0 until groupCount).toSet()
                        }
                    }
                }
            }

            if (compactReview) {
                preview.forEachIndexed { index, word ->
                    val isDuplicate = WordRepo.all.any {
                        it.en.trim().equals(word.en.trim(), ignoreCase = true)
                    }
                    VocabularyPreviewCard(
                        word = word,
                        compact = true,
                        isDuplicate = isDuplicate,
                        editing = editingIndex == index,
                        draft = draft,
                        onDelete = {
                            preview = preview.filterIndexed { itemIndex, _ -> itemIndex != index }
                            if (editingIndex == index) {
                                editingIndex = -1
                                draft = null
                            } else if (editingIndex > index) {
                                editingIndex--
                            }
                        },
                        onEdit = {
                            editingIndex = index
                            draft = word
                        },
                        onDraftChange = { draft = it },
                        onSave = {
                            val updated = preview.toMutableList()
                            updated[index] = draft!!
                            preview = updated
                            editingIndex = -1
                            draft = null
                        },
                        onCancel = {
                            editingIndex = -1
                            draft = null
                        }
                    )
                }
            } else {
                preview.chunked(10).forEachIndexed { groupIndex, group ->
                    val firstNumber = groupIndex * 10 + 1
                    val lastNumber = firstNumber + group.size - 1
                    val expanded = groupIndex in expandedGroups

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable {
                                expandedGroups = if (expanded) {
                                    expandedGroups - groupIndex
                                } else {
                                    expandedGroups + groupIndex
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (expanded) Color(0xFFE9FBF7) else Color.White,
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "المجموعة ${groupIndex + 1} · الكلمات $firstNumber–$lastNumber",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Night
                                )
                                Text("${group.size} كلمات", fontSize = 12.sp, color = Muted)
                            }
                            Text(
                                if (expanded) "طي ▲" else "عرض ▼",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TealDark
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEachIndexed { localIndex, word ->
                                val index = groupIndex * 10 + localIndex
                                val isDuplicate = WordRepo.all.any {
                                    it.en.trim().equals(word.en.trim(), ignoreCase = true)
                                }
                                VocabularyPreviewCard(
                                    word = word,
                                    compact = false,
                                    isDuplicate = isDuplicate,
                                    editing = editingIndex == index,
                                    draft = draft,
                                    onDelete = {
                                        preview = preview.filterIndexed { itemIndex, _ -> itemIndex != index }
                                        if (editingIndex == index) {
                                            editingIndex = -1
                                            draft = null
                                        } else if (editingIndex > index) {
                                            editingIndex--
                                        }
                                    },
                                    onEdit = {
                                        editingIndex = index
                                        draft = word
                                    },
                                    onDraftChange = { draft = it },
                                    onSave = {
                                        val updated = preview.toMutableList()
                                        updated[index] = draft!!
                                        preview = updated
                                        editingIndex = -1
                                        draft = null
                                    },
                                    onCancel = {
                                        editingIndex = -1
                                        draft = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            PrimaryButton("اعتماد وإضافة (${preview.size})", Modifier.fillMaxWidth()) {
                addPreviewWords()
            }
            SecondaryButton("إلغاء القائمة", Modifier.fillMaxWidth()) {
                cancelPreview()
            }
        }
    }


@Composable
private fun VocabularyPreviewCard(
    word: Word,
    compact: Boolean,
    isDuplicate: Boolean,
    editing: Boolean,
    draft: Word?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDraftChange: (Word) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 12.dp else 15.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            Modifier.padding(if (compact) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    En(word.en, if (compact) 16.sp else 20.sp, Night, FontWeight.Bold)
                    Text(
                        if (isDuplicate) "موجودة بالفعل — لن تُضاف مرة ثانية" else word.kind,
                        fontSize = 12.sp,
                        color = if (isDuplicate) Danger else TealDark
                    )
                    if (compact) Text(word.ar, fontSize = 13.sp, color = Muted)
                }
                SecondaryButton("تعديل") { onEdit() }
                SecondaryButton("حذف") { onDelete() }
            }

            if (!compact) {
                Ltr {
                    Text(
                        word.ipa.ifBlank { "النطق الصوتي متاح من زر الصوت بعد الإضافة" },
                        fontSize = 14.sp,
                        color = Muted
                    )
                }
                Text(word.ar, fontSize = 16.sp, color = Night, fontWeight = FontWeight.Medium)
                if (word.example.isNotBlank()) {
                    Ltr { Text(word.example, fontSize = 14.sp, color = Night) }
                }
                if (word.exampleAr.isNotBlank()) {
                    Text(word.exampleAr, fontSize = 13.sp, color = Muted)
                }
            }

            if (editing && draft != null) {
                OutlinedTextField(
                    value = draft.en,
                    onValueChange = { onDraftChange(draft.copy(en = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الكلمة بالإنجليزية") }
                )
                OutlinedTextField(
                    value = draft.ipa,
                    onValueChange = { onDraftChange(draft.copy(ipa = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("النطق IPA") }
                )
                OutlinedTextField(
                    value = draft.ar,
                    onValueChange = { onDraftChange(draft.copy(ar = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("المعنى بالعربي") }
                )
                OutlinedTextField(
                    value = draft.example,
                    onValueChange = { onDraftChange(draft.copy(example = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مثال بالإنجليزية") }
                )
                OutlinedTextField(
                    value = draft.exampleAr,
                    onValueChange = { onDraftChange(draft.copy(exampleAr = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ترجمة المثال") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("حفظ التعديل", Modifier.weight(1f)) { onSave() }
                    SecondaryButton("إلغاء", Modifier.weight(1f)) { onCancel() }
                }
            }
        }
    }
}

}
