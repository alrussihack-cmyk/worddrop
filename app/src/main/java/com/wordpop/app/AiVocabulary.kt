@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordpop.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
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

private fun isKnown(word: Word) = WordRepo.all.any { it.en.trim().equals(word.en.trim(), ignoreCase = true) }

private enum class WordsStage { Form, Review, Done }

private const val SWIPE_THRESHOLD = 90f

@Composable
fun AiVocabularyTab(
    store: SettingsStore,
    onAdded: (ImportResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(WordsStage.Form) }
    var topic by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(20) }
    var level by remember { mutableStateOf("B1") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf<List<Word>>(emptyList()) }
    var addedCount by remember { mutableIntStateOf(0) }
    var skippedCount by remember { mutableIntStateOf(0) }

    when (stage) {
        WordsStage.Form -> Section("مولّد الكلمات بالذكاء الاصطناعي") {
            Text(
                "اختار موضوع، واشرح المعاني والمواقف اللي محتاجها. WordPop هيجهّز الكلمات والترجمة والنطق والأمثلة، وبعدها تراجع كل كلمة بالسحب قبل الإضافة.",
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
                minLines = 2,
                maxLines = 4,
                label = { Text("عايز الكلمات تعبّر عن إيه بالظبط؟") },
                placeholder = { Text("مثال: كلمات للتعامل مع شكاوى النزلاء، الاعتذار، وحل المشكلات.") }
            )

            Text("عدد الكلمات", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 100).forEach { n ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Chip("$n كلمة", count == n) { count = n } }
                }
            }

            Text("مستوى اللغة", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A" to "أساسي A", "B1" to "متوسط B1", "B2" to "متقدم B2").forEach { (code, label) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Chip(label, level == code) { level = code } }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("→", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TealDark)
                    Text(
                        "هتراجعهم ككروت: اسحب يمين للإضافة، وشمال للتخطي. من غير ما تعمل scroll.",
                        fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.Medium, lineHeight = 21.sp
                    )
                }
            }

            Text(AiConfigSync.status, fontSize = 13.sp, color = Muted)
            if (error.isNotBlank()) Text(error, color = Danger, fontSize = 13.sp)
            if (notice.isNotBlank()) Text(notice, color = TealDark, fontSize = 13.sp)

            PrimaryButton(
                if (loading) (if (progress.isBlank()) "جاري تجهيز الكلمات..." else progress) else "ولّد $count كلمة",
                Modifier.fillMaxWidth()
            ) {
                if (loading) return@PrimaryButton
                if (topic.isBlank()) { error = "اكتب الموضوع الأول."; return@PrimaryButton }
                if (request.isBlank()) { error = "اشرح للذكاء الاصطناعي المعاني أو المواقف اللي محتاج الكلمات فيها."; return@PrimaryButton }
                if (!AiConfigSync.isReady) { error = AiConfigSync.status; return@PrimaryButton }

                error = ""
                notice = ""
                loading = true
                progress = "جاري تجهيز الكلمات..."
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            AiVocabularyApi.generateBatched(
                                workerUrl = AiConfigSync.workerUrl,
                                topic = topic.trim(),
                                request = request.trim(),
                                count = count,
                                level = level
                            ) { partial -> progress = "جاهز ${partial.size} من $count..." }
                        }
                        val fresh = result.filterNot { isKnown(it) }
                        if (fresh.isEmpty()) {
                            error = "كل الكلمات اللي اتولدت موجودة عندك بالفعل. جرّب موضوع مختلف."
                        } else {
                            generated = fresh
                            val already = result.size - fresh.size
                            if (already > 0) notice = "$already كلمة موجودة عندك بالفعل اتشالت من المراجعة."
                            stage = WordsStage.Review
                        }
                    } catch (exception: Exception) {
                        error = exception.message ?: "حصل خطأ أثناء التوليد. جرّب تاني."
                    } finally {
                        loading = false
                        progress = ""
                    }
                }
            }
        }

        WordsStage.Review -> SwipeReview(
            words = generated,
            onBack = { stage = WordsStage.Form },
            onFinish = { added, skipped ->
                val result = if (added.isEmpty()) ImportResult(0, 0, 0) else WordRepo.addCustomWords(context, added)
                if (added.isNotEmpty()) onAdded(result)
                addedCount = result.added
                skippedCount = skipped
                generated = emptyList()
                stage = WordsStage.Done
            }
        )

        WordsStage.Done -> Surface(
            shape = RoundedCornerShape(28.dp), color = Color.White,
            border = androidx.compose.foundation.BorderStroke(2.dp, Night),
            modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(28.dp), 4.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Boby(Mood.Cheer, 120.dp, Modifier.bob())
                Text("خلّصت المراجعة!", fontSize = 32.sp, fontFamily = Display, color = Night)
                Text(
                    "الكلمات اللي اخترتها اتضافت لقايمتك وجاهزة للمراجعة الذكية.",
                    fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("$addedCount", "كلمة اتضافت", Tint, TealDark, Modifier.weight(1f))
                    StatBox("$skippedCount", "كلمة اتخطّت", MissTint, Danger, Modifier.weight(1f))
                }
                PrimaryButton("ولّد قايمة جديدة", Modifier.fillMaxWidth()) {
                    notice = ""
                    stage = WordsStage.Form
                }
                SecondaryButton("تمام", Modifier.fillMaxWidth()) {
                    notice = "تمت إضافة $addedCount كلمة إلى مكتبتك."
                    stage = WordsStage.Form
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(bg).border(2.dp, Night, shape).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 40.sp, fontFamily = Display, color = fg)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Reviews generated words as swipeable cards: right adds, left skips. */
@Composable
private fun SwipeReview(
    words: List<Word>,
    onBack: () -> Unit,
    onFinish: (added: List<Word>, skipped: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val speak = rememberSpeaker()
    val decisions = remember(words) { mutableStateListOf<Boolean>() }
    val total = words.size
    val idx = decisions.size
    val offsetX = remember(idx) { Animatable(0f) }
    var busy by remember(idx) { mutableStateOf(false) }

    fun commit(add: Boolean) {
        if (busy || idx >= total) return
        busy = true
        scope.launch {
            offsetX.animateTo(if (add) 900f else -900f, tween(260))
            decisions.add(add)
        }
    }

    fun finishNow() {
        val added = words.filterIndexed { i, _ -> i < decisions.size && decisions[i] }
        onFinish(added, total - added.size)
    }

    LaunchedEffect(idx) { if (idx >= total) finishNow() }

    val addedNow = decisions.count { it }
    val skippedNow = decisions.size - addedNow

    Surface(
        shape = RoundedCornerShape(28.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, Night),
        modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(28.dp), 4.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(44.dp, Color.White, "رجوع", onBack) { Text("›", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                Text("راجع الكلمات", fontSize = 22.sp, fontFamily = Display, color = Night)
                Box(Modifier.clip(CircleShape).background(Night).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Ltr { Text("${minOf(idx + 1, total)} / $total", fontSize = 18.sp, fontFamily = Display, color = Teal) }
                }
            }
            val barShape = RoundedCornerShape(50)
            Box(Modifier.fillMaxWidth().height(16.dp).clip(barShape).background(Color.White).border(2.dp, Night, barShape)) {
                Box(Modifier.fillMaxWidth(if (total == 0) 0f else idx.toFloat() / total).fillMaxHeight().background(Teal))
            }
            Ltr {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("← تخطي · $skippedNow", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                    Text("إضافة · $addedNow →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
                }
            }

            Box(Modifier.fillMaxWidth().height(430.dp)) {
                for (k in 2 downTo 0) {
                    val i = idx + k
                    if (i >= total) continue
                    val word = words[i]
                    if (k == 0) {
                        WordCard(
                            word = word, speak = speak,
                            modifier = Modifier
                                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                .graphicsLayer {
                                    rotationZ = offsetX.value / 16f
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .pointerInput(idx) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val dx = offsetX.value
                                            when {
                                                dx > SWIPE_THRESHOLD -> commit(true)
                                                dx < -SWIPE_THRESHOLD -> commit(false)
                                                else -> scope.launch { offsetX.animateTo(0f, spring()) }
                                            }
                                        },
                                        onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            if (!busy) scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                                        }
                                    )
                                },
                            addAlpha = { (offsetX.value / SWIPE_THRESHOLD).coerceIn(0f, 1f) },
                            skipAlpha = { (-offsetX.value / SWIPE_THRESHOLD).coerceIn(0f, 1f) }
                        )
                    } else {
                        WordCard(
                            word = word, speak = speak,
                            modifier = Modifier.graphicsLayer {
                                val prog = (abs(offsetX.value) / 160f).coerceAtMost(1f)
                                val base = if (k == 1) 0.95f else 0.90f
                                val scale = base + 0.05f * prog
                                scaleX = scale; scaleY = scale
                                translationY = ((if (k == 1) 14f else 28f) - 14f * prog) * density
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            },
                            addAlpha = { 0f }, skipAlpha = { 0f }
                        )
                    }
                }
            }

            Ltr {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(64.dp, Coral, "تخطي الكلمة", { commit(false) }) { Text("✕", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                    RoundButton(48.dp, Color(0xFFFFC93C), "تراجع", {
                        if (!busy && decisions.isNotEmpty()) decisions.removeAt(decisions.lastIndex)
                    }, alpha = if (decisions.isEmpty()) 0.4f else 1f) { Text("↶", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                    RoundButton(64.dp, Teal, "إضافة الكلمة", { commit(true) }) { Text("✓", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                }
            }

            SecondaryButton("اعتمد المضاف ($addedNow) وخلّص", Modifier.fillMaxWidth()) { finishNow() }
        }
    }
}

@Composable
private fun RoundButton(
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    description: String,
    onClick: () -> Unit,
    alpha: Float = 1f,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.size(size).graphicsLayer { this.alpha = alpha }
            .hardShadow(CircleShape, 3.dp)
            .clip(CircleShape).background(color).border(2.5.dp, Night, CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun WordCard(
    word: Word,
    speak: (String) -> Unit,
    modifier: Modifier,
    addAlpha: () -> Float,
    skipAlpha: () -> Float
) {
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.fillMaxSize().padding(bottom = 6.dp)) {
        Surface(
            shape = shape, color = Color.White,
            border = androidx.compose.foundation.BorderStroke(2.5.dp, Night),
            modifier = Modifier.fillMaxSize().hardShadow(shape, 5.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.clip(CircleShape).background(Tint).padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text(word.kind, fontSize = 13.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                    }
                    SpeakButton(44.dp) { speak(word.en) }
                }
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    En(word.en, if (word.en.length > 12) 34.sp else 46.sp, Night, FontWeight.ExtraBold, TextAlign.Center, Modifier.fillMaxWidth())
                    if (word.ipa.isNotBlank()) En(word.ipa, 15.sp, Muted, FontWeight.Normal, TextAlign.Center, Modifier.fillMaxWidth())
                }
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(Line))
                Text(
                    word.ar, fontSize = 32.sp, fontFamily = Display, color = TealDark,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.weight(1f))
                if (word.example.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F7FB), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            En(word.example, 15.sp, Night, FontWeight.Medium)
                            if (word.exampleAr.isNotBlank()) Text(word.exampleAr, fontSize = 13.sp, color = Muted)
                        }
                    }
                }
            }
        }
        Stamp("أضف ✓", Teal, Alignment.TopStart, -12f, addAlpha)
        Stamp("تخطي ✕", Coral, Alignment.TopEnd, 12f, skipAlpha)
    }
}

@Composable
private fun BoxScope.Stamp(text: String, color: Color, align: Alignment, angle: Float, alpha: () -> Float) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.align(align).padding(18.dp)
            .graphicsLayer { this.alpha = alpha(); rotationZ = angle }
            .clip(shape).background(color).border(3.dp, Night, shape)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 24.sp, fontFamily = Display, color = Night) }
}
