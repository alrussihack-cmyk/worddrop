package com.wordpop.app

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** [spoken] is what Aria says aloud when it differs from what is shown (Arabic prompts stay on screen only). */
private data class ReviewCard(
    val type: Int,            // 0 question, 1 correct, 2 wrong
    val counter: String = "",
    val ar: String = "",
    val pos: String = "",
    val en: String = "",
    val example: String = ""
)

private data class VoiceChatMessage(
    val text: String, val fromUser: Boolean, val spoken: String? = null, val card: ReviewCard? = null
)

private fun normalizeAnswer(text: String): String =
    text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun editDistance(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return dp[b.length]
}

/** Speech recognisers often add words or slip a letter, so accept the word inside the phrase or one letter off. */
private fun answerMatches(heard: String, target: String): Boolean {
    val h = normalizeAnswer(heard)
    val t = normalizeAnswer(target)
    if (h.isEmpty() || t.isEmpty()) return false
    if (h == t || " $h ".contains(" $t ")) return true
    return h.split(" ").any { it.length >= 5 && t.length >= 5 && editDistance(it, t) <= 1 }
}

private val VoiceInk = Color(0xFF17171D)
private val VoiceMuted = Color(0xFF85858D)
private val VoiceLine = Color(0xFFEDEDF0)
private val VoiceBubble = Color(0xFFF0F0F2)
private val VoiceAvatar = Color(0xFFE9E3ED)
private val VoiceAvatarInk = Color(0xFF37364F)
private val VoiceGlowPink = Color(0xFFF6D7E9)
private val VoiceGlowBlue = Color(0xFFDCEBFA)

@Composable
fun VoiceChatScreen(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<VoiceChatMessage>() }
    val chatListState = rememberLazyListState()
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap to speak") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }

    // Aria's voice comes from the AI voice service (Worker/EasyVoice); the device voice is only a fallback.
    var speechBuffer by remember { mutableStateOf("") }
    fun playNow(text: String) {
        val fallback = {
            if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop_voice_reply")
            Unit
        }
        EasyVoiceClient.speak(context, store.ttsWorkerUrl, text, store.speechVoice, fallback)
    }
    fun speak(text: String, queue: Boolean = false) {
        if (queue) speechBuffer = (speechBuffer + " " + text).trim()
        else { speechBuffer = ""; playNow(text) }
    }
    fun flushSpeech() {
        if (speechBuffer.isNotBlank()) { val t = speechBuffer; speechBuffer = ""; playNow(t) }
    }

    // Favourites review: Aria shows the Arabic meaning, the learner says the English word.
    var reviewQueue by remember { mutableStateOf<List<Word>>(emptyList()) }
    var reviewIndex by remember { mutableIntStateOf(0) }
    var reviewScore by remember { mutableIntStateOf(0) }
    var reviewing by remember { mutableStateOf(false) }
    val favoriteCount = store.favWords.count { WordRepo.get(it) != null }

    fun askNext() {
        val w = reviewQueue.getOrNull(reviewIndex)
        if (w == null) {
            val total = reviewQueue.size
            val line = "Review finished. You got $reviewScore out of $total."
            messages.add(VoiceChatMessage("$line\nانتهت المراجعة: $reviewScore من $total", false, line))
            speak(line, queue = true)
            flushSpeech()
            reviewing = false
            status = "Tap to speak"
        } else {
            val kind = if (w.kind.isNotBlank()) " (${w.kind})" else ""
            messages.add(
                VoiceChatMessage(
                    "${reviewIndex + 1}/${reviewQueue.size}  •  قول الكلمة بالإنجليزي:\n«${w.ar}»$kind",
                    false, "Word ${reviewIndex + 1}. Say the English word for this meaning.",
                    ReviewCard(0, "${reviewIndex + 1}/${reviewQueue.size}", w.ar, w.kind)
                )
            )
            speak("Word ${reviewIndex + 1}. Say the English word for this meaning.", queue = true)
            flushSpeech()
            status = "Tap to speak"
        }
    }

    fun startReview() {
        if (sending || listening) return
        val words = store.favWords.mapNotNull { WordRepo.get(it) }.shuffled().take(10)
        if (words.isEmpty()) return
        tts?.stop()
        messages.clear()
        reviewQueue = words
        reviewIndex = 0
        reviewScore = 0
        reviewing = true
        messages.add(VoiceChatMessage("مراجعة المفضلة: ${words.size} كلمات. هقولك المعنى وانت قول الكلمة بالإنجليزي.", false,
            "Let's review your favorite words. I'll show the meaning, and you say the English word."))
        speechBuffer = ""
        speak("Let's review your favorite words. I'll show the meaning, and you say the English word.", queue = true)
        askNext()
    }

    fun finishAnswer(correct: Boolean, w: Word, heard: String?) {
        if (heard != null) messages.add(VoiceChatMessage(heard, true))
        if (correct) {
            reviewScore++
            store.markLearned(w.id)
            store.hard = store.hard - w.id
            store.recordReview(w.id, ReviewRating.Good)
            val ex = if (w.example.isNotBlank()) " ${w.example}" else ""
            messages.add(VoiceChatMessage("صح! ${w.en}${if (w.ar.isNotBlank()) " = ${w.ar}" else ""}" +
                (if (w.example.isNotBlank()) "\n${w.example}" else ""), false, "Correct! ${w.en}.$ex",
                ReviewCard(1, "", w.ar, "", w.en, w.example)))
            speechBuffer = ""
            speak("Correct! ${w.en}.$ex", queue = true)
        } else {
            store.learned = store.learned - w.id
            store.hard = store.hard + w.id
            store.recordReview(w.id, ReviewRating.Again)
            messages.add(VoiceChatMessage("مش بالظبط. الكلمة هي: ${w.en}", false, "Not quite. The word is ${w.en}.",
                ReviewCard(2, "", w.ar, "", w.en, w.example)))
            speechBuffer = ""
            speak("Not quite. The word is ${w.en}.", queue = true)
        }
        reviewIndex++
        askNext()
    }

    fun handleReviewAnswer(heard: String) {
        val w = reviewQueue.getOrNull(reviewIndex) ?: return
        finishAnswer(answerMatches(heard, w.en), w, heard)
    }

    fun endReview() {
        tts?.stop()
        reviewing = false
        reviewQueue = emptyList()
        messages.add(VoiceChatMessage("وقفنا المراجعة. تقدر تبدأها تاني في أي وقت.", false, "Okay, we stopped the review."))
        status = "Tap to speak"
    }

    fun sendToAi(text: String) {
        if (text.isBlank() || sending) return
        messages.add(VoiceChatMessage(text, true))
        sending = true
        status = "One moment"
        scope.launch {
            try {
                val history = messages.takeLast(12).joinToString("\n") {
                    (if (it.fromUser) "Learner" else "Tutor") + ": " + it.text
                }
                val prompt = """You are WordPop's friendly English speaking partner for an Arabic-speaking learner.
Speak naturally in simple A2-B1 English. Keep replies short (1-3 sentences) and ask one easy follow-up question.
If the learner makes an important English mistake, respond naturally and add one short correction in Egyptian Arabic.
Conversation:
$history
Reply with JSON only: {"reply":"your spoken reply here"}"""
                val result = withContext(Dispatchers.IO) {
                    StoryApi.chatJson(store.ttsWorkerUrl, prompt, 350)
                }
                val reply = result.optString("reply").trim()
                    .ifBlank { "Sorry, I didn't catch that. Could you say it again?" }
                messages.add(VoiceChatMessage(reply, false))
                status = "Tap to speak"
                speak(reply)
            } catch (e: Exception) {
                status = e.message ?: "Something went wrong. Please try again."
            } finally {
                sending = false
            }
        }
    }

    fun launchRecognition() {
        listening = true
        status = "Listening…  (tap to stop)"
        scope.launch {
            try {
                val wav = withContext(Dispatchers.IO) { VoiceRecorder.record() }
                listening = false
                if (wav == null) { status = "I didn't catch that. Tap to try again."; return@launch }
                sending = true
                status = "Understanding…"
                val spoken = withContext(Dispatchers.IO) { VoiceRecorder.transcribe(store.ttsWorkerUrl, wav) }
                sending = false
                if (spoken.isBlank()) status = "I didn't catch that. Tap to try again."
                else if (reviewing) handleReviewAnswer(spoken) else sendToAi(spoken)
            } catch (e: Exception) {
                status = e.message ?: "Something went wrong. Please try again."
            } finally {
                listening = false
                sending = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchRecognition()
        else status = "Please allow microphone access to speak."
    }

    fun startListening() {
        if (listening) { VoiceRecorder.stop(); return }
        if (sending) return
        EasyVoiceClient.stopPlayback()
        tts?.stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) launchRecognition()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        Modifier.fillMaxSize().background(Color(0xFFFAFAFB)).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().shadow(14.dp, RoundedCornerShape(34.dp)),
            shape = RoundedCornerShape(34.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F2))
        ) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(VoiceGlowPink.copy(alpha = glow * 0.65f), Color.Transparent),
                            center = Offset(size.width * 0.48f, size.height),
                            radius = size.width * 0.42f
                        ),
                        radius = size.width * 0.42f,
                        center = Offset(size.width * 0.48f, size.height)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(VoiceGlowBlue.copy(alpha = glow * 0.45f), Color.Transparent),
                            center = Offset(size.width * 0.78f, size.height),
                            radius = size.width * 0.28f
                        ),
                        radius = size.width * 0.28f,
                        center = Offset(size.width * 0.78f, size.height)
                    )
                }

                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(VoiceAvatar),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AR", color = VoiceAvatarInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Aria", color = VoiceInk, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                            Text("Voice assistant", color = VoiceMuted, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF7F7F8))
                                .clickable {
                                    messages.clear()
                                    reviewing = false
                                    reviewQueue = emptyList()
                                    tts?.stop()
                                    status = "Tap to speak"
                                    listening = false
                                    sending = false
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("↻", color = VoiceMuted, fontSize = 24.sp) }
                    }

                    HorizontalDivider(color = Color(0xFFF4F4F5))

                    if (messages.isEmpty()) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            Surface(
                                color = VoiceBubble,
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
                            ) {
                                Text(
                                    "How can I help you today?",
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
                                    color = VoiceInk, fontSize = 19.sp, lineHeight = 27.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
                            state = chatListState,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(vertical = 24.dp)
                        ) {
                            item {
                                Surface(
                                    color = VoiceBubble,
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Text(
                                        "How can I help you today?",
                                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                                        color = VoiceInk, fontSize = 16.sp
                                    )
                                }
                            }
                            items(messages) { message ->
                                if (message.fromUser) {
                                    Text(
                                        message.text,
                                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                                        color = VoiceInk, fontSize = 19.sp, lineHeight = 29.sp,
                                        textAlign = TextAlign.End
                                    )
                                } else {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        if (message.card != null) ReviewCardView(message.card)
                                        else Surface(color = VoiceBubble, shape = RoundedCornerShape(20.dp)) {
                                            Text(
                                                message.text,
                                                modifier = Modifier.padding(15.dp),
                                                color = VoiceInk, fontSize = 17.sp, lineHeight = 25.sp
                                            )
                                        }
                                        TextButton(onClick = { speak(message.spoken ?: message.text) }) {
                                            Text("▶  Play response", color = VoiceMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 28.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        if (reviewing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val w = reviewQueue.getOrNull(reviewIndex)
                                ReviewPill("Hint") {
                                    if (w != null) {
                                        val h = "يبدأ بحرف «${w.en.first()}» وعدد حروفه ${w.en.length}"
                                        messages.add(VoiceChatMessage(h, false, "It starts with ${w.en.first()}, and has ${w.en.length} letters."))
                                        speak("It starts with ${w.en.first()}, and has ${w.en.length} letters.")
                                    }
                                }
                                ReviewPill("Skip") { reviewQueue.getOrNull(reviewIndex)?.let { finishAnswer(false, it, null) } }
                                ReviewPill("End") { endReview() }
                            }
                        } else {
                            ReviewPill(
                                if (favoriteCount > 0) "Review my favorites ($favoriteCount)" else "Review favorites (add words ⭐ first)"
                            ) { if (favoriteCount > 0) startReview() else status = "ضيف كلمات للمفضلة (⭐) الأول." }
                        }
                        Text(
                            when {
                                listening -> "Listening…"
                                sending -> "One moment"
                                else -> status
                            },
                            color = VoiceMuted, fontSize = 17.sp, fontWeight = FontWeight.Medium
                        )
                        Box(
                            Modifier.size(82.dp)
                                .shadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE8E8EB), CircleShape)
                                .clickable(enabled = !sending) { startListening() },
                            contentAlignment = Alignment.Center
                        ) {
                            VoiceMicGlyph(
                                active = listening || sending,
                                color = VoiceInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMicGlyph(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "voiceBars")
    val a by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "barA"
    )
    val b by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(680), RepeatMode.Reverse),
        label = "barB"
    )
    Canvas(Modifier.size(width = 34.dp, height = 38.dp)) {
        val widths = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val heights = listOf(12f * a, 23f * b, 34f * a, 23f * b, 12f * a)
        val total = widths * 5 + gap * 4
        heights.forEachIndexed { index, height ->
            val h = (height * if (active) 1f else 0.82f).dp.toPx()
            val x = (size.width - total) / 2 + index * (widths + gap)
            drawLine(
                color = color,
                start = Offset(x + widths / 2, (size.height - h) / 2),
                end = Offset(x + widths / 2, (size.height + h) / 2),
                strokeWidth = widths,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ReviewPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = VoiceBubble,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = VoiceInk, fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
    }
}

/** Review prompt / result as a WordPop sticker: mint paper, ink outline, hard shadow, Lalezar for the word. */
@Composable
private fun ReviewCardView(card: ReviewCard) {
    val shape = RoundedCornerShape(18.dp)
    val bg = when (card.type) { 0 -> Tint; 1 -> Mint; else -> MissTint }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Column(
            Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 5.dp)
                .hardShadow(shape, 4.dp).clip(shape).background(bg)
                .border(2.dp, Night, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (card.type == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Night).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(card.counter, color = Color.White, fontFamily = Readex, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (card.pos.isNotBlank()) Box(
                        Modifier.clip(RoundedCornerShape(50)).background(SunTint).border(1.5.dp, Night, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) { Text(card.pos, color = Night, fontFamily = Readex, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
                }
                Text("قول الكلمة بالإنجليزي", color = MintInk, fontFamily = Readex, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(background = Spark.copy(alpha = 0.55f))) { append(" ${card.ar} ") }
                    },
                    color = Night, fontFamily = Display, fontSize = 22.sp, lineHeight = 30.sp
                )
            } else {
                Text(
                    if (card.type == 1) "صح! 🎉" else "الكلمة الصح",
                    color = if (card.type == 1) Night else Danger,
                    fontFamily = Readex, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(
                        card.en, color = Night, fontFamily = Display, fontSize = 28.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start
                    )
                }
                if (card.ar.isNotBlank()) Text(card.ar, color = Night, fontFamily = Readex, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (card.example.isNotBlank()) CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(
                        card.example, color = MintInk, fontFamily = Readex, fontSize = 13.sp, lineHeight = 19.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
