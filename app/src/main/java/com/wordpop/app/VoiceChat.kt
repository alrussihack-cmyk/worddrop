package com.wordpop.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
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

    val speechResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) sendToAi(spoken)
            else status = "I didn't catch that. Tap to try again."
        } else {
            status = "Tap to speak"
        }
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
            status = "Listening…"
            speechResult.launch(intent)
        } catch (_: Exception) {
            listening = false
            status = "Speech recognition isn't available on this device."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchRecognition()
        else status = "Please allow microphone access to speak."
    }

    fun startListening() {
        if (sending || listening) return
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
                                        Surface(color = VoiceBubble, shape = RoundedCornerShape(20.dp)) {
                                            Text(
                                                message.text,
                                                modifier = Modifier.padding(15.dp),
                                                color = VoiceInk, fontSize = 17.sp, lineHeight = 25.sp
                                            )
                                        }
                                        TextButton(onClick = { speak(message.text) }) {
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
                                .clickable(enabled = !sending && !listening) { startListening() },
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
