package com.wordpop.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class VoiceChatMessage(val text: String, val fromUser: Boolean)

private val VoiceInk = Color(0xFF121827)
private val VoiceMint = Color(0xFF27D5B0)
private val VoicePale = Color(0xFFDDF7F0)
private val VoiceDot = Color(0xFFD6E1E8)
private val VoiceMuted = Color(0xFF586477)
private val VoiceDeepMint = Color(0xFF087C70)

@Composable
fun VoiceChatScreen(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<VoiceChatMessage>() }
    val chatListState = rememberLazyListState()
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اضغط على الميكروفون وابدأ الكلام بالإنجليزية") }
    var transcript by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) chatListState.animateScrollToItem(messages.lastIndex)
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop_voice_reply")
    }

    fun sendToAi(text: String) {
        if (text.isBlank() || sending) return
        transcript = ""
        messages.add(VoiceChatMessage(text, true))
        sending = true
        status = "بفكر في رد مناسب..."
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
                val reply = result.optString("reply").trim().ifBlank { "Sorry, I didn't catch that. Could you say it again?" }
                messages.add(VoiceChatMessage(reply, false))
                status = "اتكلم تاني عشان نكمل المحادثة"
                speak(reply)
            } catch (e: Exception) {
                status = e.message ?: "حصل خطأ. حاول تاني."
            } finally { sending = false }
        }
    }

    val speechResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) sendToAi(spoken) else status = "ملقتش كلام واضح. جرّب تاني."
        } else status = "التسجيل اتوقف. اضغط على الميكروفون للمحاولة تاني."
    }

    fun launchRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            listening = true
            status = "بسمعك... اتكلم بالإنجليزي"
            speechResult.launch(intent)
        } catch (_: Exception) {
            listening = false
            status = "خدمة التعرف على الصوت مش متاحة على الجهاز."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchRecognition() else status = "لازم تسمح باستخدام الميكروفون عشان تتكلم."
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchRecognition()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F8FA))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 36.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(VoiceDot, radius = 2.2.dp.toPx(), center = Offset(x + step / 2, y + step / 2))
                    x += step
                }
                y += step
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .border(2.dp, VoiceInk, RoundedCornerShape(18.dp))
                        .clickable {
                            messages.clear(); transcript = ""; sending = false
                            listening = false; tts?.stop()
                            status = "اضغط على الميكروفون وابدأ الكلام بالإنجليزية"
                        },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = VoiceInk, fontSize = 34.sp, fontWeight = FontWeight.Normal) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("محادثة صوتية", color = VoiceInk, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("اتكلم بالإنجليزي، وWordPop هيرد عليك بصوت", color = VoiceMuted, fontSize = 13.sp, textAlign = TextAlign.End)
                }
                Spacer(Modifier.width(12.dp))
                WordPopFace(52.dp)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (listening) VoiceMint else VoicePale)
                    .border(2.dp, VoiceInk, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    when {
                        listening -> "بسمعك... اتكلم بالإنجليزي"
                        sending -> "بجهزلك الرد..."
                        else -> status
                    },
                    color = if (listening) VoiceInk else VoiceDeepMint,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (listening) VoiceInk else Color(0xFF9EDBD0)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (listening) VoiceMint else VoiceDeepMint))
                }
            }

            Spacer(Modifier.height(14.dp))
            if (messages.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    WordPopFace(112.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("يلا نتكلم إنجليزي", color = VoiceInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text("اختار موضوع تبدأ بيه، أو دوس على المايك\nواتكلم في أي حاجة.", color = VoiceMuted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 25.sp)
                    Spacer(Modifier.height(22.dp))
                    val topics = listOf("عن يومك", "الأكل", "السفر", "الشغل", "الأفلام")
                    topics.chunked(3).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            line.forEach { topic ->
                                Surface(
                                    shape = RoundedCornerShape(50), color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(2.dp, VoiceInk),
                                    modifier = Modifier.clickable {
                                        sendToAi("Let's talk about $topic.")
                                    }
                                ) { Text(topic, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = VoiceInk, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = chatListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(messages) { message ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
                            Text(if (message.fromUser) "أنت" else "WordPop", color = VoiceMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            if (message.fromUser) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 5.dp),
                                    color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, VoiceInk)
                                ) {
                                    Text(message.text, modifier = Modifier.padding(16.dp), color = VoiceInk, fontSize = 18.sp, lineHeight = 27.sp)
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                    WordPopFace(34.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(VoiceInk).padding(18.dp)) {
                                        Text(message.text, color = Color.White, fontSize = 18.sp, lineHeight = 28.sp)
                                        Spacer(Modifier.height(14.dp))
                                        Button(
                                            onClick = { speak(message.text) },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = VoiceMint, contentColor = VoiceInk),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp)
                                        ) { Text("اسمع الرد تاني  ▶", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (listening && transcript.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(22.dp), color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, VoiceInk)
                ) { Text(transcript, modifier = Modifier.padding(16.dp), color = VoiceInk, fontSize = 18.sp) }
            }

            if (listening) VoiceWaveform()
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape)
                        .background(if (listening) VoiceInk else VoiceMint)
                        .border(3.dp, VoiceInk, CircleShape)
                        .clickable(enabled = !sending && !listening) { startListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (listening) "■" else "🎙", color = if (listening) VoiceMint else VoiceInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    when { sending -> "الذكاء الاصطناعي بيجهز الرد..." ; listening -> "بسمعك..." ; else -> "اضغط للتحدث" },
                    color = VoiceInk, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WordPopFace(faceSize: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(faceSize).clip(RoundedCornerShape(faceSize * 0.27f))
            .background(VoiceMint).border(3.dp, VoiceInk, RoundedCornerShape(faceSize * 0.27f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(faceSize * 0.08f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(faceSize * 0.16f)) {
                Box(Modifier.size(faceSize * 0.09f).clip(CircleShape).background(VoiceInk))
                Box(Modifier.size(faceSize * 0.09f).clip(CircleShape).background(VoiceInk))
            }
            Box(Modifier.width(faceSize * 0.34f).height(faceSize * 0.14f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(VoiceInk, 15f, 150f, false, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveform() {
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val bars = listOf(10, 18, 28, 14, 34, 22, 40, 28, 14, 32, 24, 36, 18, 10)
        val gap = size.width / (bars.size * 2f)
        bars.forEachIndexed { i, h ->
            val x = gap + i * gap * 2
            drawLine(VoiceDeepMint, Offset(x, size.height / 2 - h.dp.toPx() / 2), Offset(x, size.height / 2 + h.dp.toPx() / 2), strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
