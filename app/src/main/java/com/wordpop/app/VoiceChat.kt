package com.wordpop.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.json.JSONObject
import java.util.Locale

private data class VoiceChatMessage(val text: String, val fromUser: Boolean)

@Composable
fun VoiceChatScreen(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<VoiceChatMessage>() }
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اضغط على الميكروفون وابدأ الكلام بالإنجليزية") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop_voice_reply")
    }

    fun sendToAi(transcript: String) {
        if (transcript.isBlank() || sending) return
        messages.add(VoiceChatMessage(transcript, true))
        sending = true
        status = "بفكر في رد مناسب..."
        scope.launch {
            try {
                val history = messages.takeLast(12).joinToString("\n") {
                    (if (it.fromUser) "Learner" else "Tutor") + ": " + it.text
                }
                val prompt = """You are WordPop's friendly English speaking partner for an Arabic-speaking learner.
Speak naturally in simple A2-B1 English. Keep replies short (1-3 sentences) and ask one easy follow-up question to keep the conversation going.
If the learner makes an important English mistake, first respond naturally, then add one short correction in Egyptian Arabic.
Conversation:
$history
Reply with JSON only: {"reply":"your spoken reply here"}"""
                val result = withContext(Dispatchers.IO) {
                    StoryApi.chatJson(store.ttsWorkerUrl, prompt, 350)
                }
                val reply = result.optString("reply").trim().ifBlank {
                    "Sorry, I didn't catch that. Could you say it again?"
                }
                messages.add(VoiceChatMessage(reply, false))
                status = "اتكلم تاني عشان نكمل المحادثة"
                speak(reply)
            } catch (e: Exception) {
                status = e.message ?: "حصل خطأ. حاول تاني."
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
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) sendToAi(spoken) else status = "ملقتش كلام واضح. جرّب تاني."
        } else {
            status = "التسجيل اتوقف. اضغط على الميكروفون للمحاولة تاني."
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
            status = "سامعك... اتكلم دلوقتي"
            speechResult.launch(intent)
        } catch (_: Exception) {
            listening = false
            status = "خدمة التعرف على الصوت مش متاحة على الجهاز."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchRecognition() else status = "لازم تسمح باستخدام الميكروفون عشان تتكلم."
    }

    Column(
        Modifier.fillMaxSize().background(Cloud).statusBarsPadding()
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("محادثة صوتية", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Night)
            Text("اتكلم بالإنجليزي، وWordPop هيرد عليك بصوت", fontSize = 13.sp, color = Muted)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Tint
        ) {
            Text(status, modifier = Modifier.padding(12.dp), color = TealDark, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎙️", fontSize = 48.sp)
                    Text("خلّينا نتكلم بالإنجليزي!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night)
                    Text("قول جملة بسيطة، وأنا هرد عليك وأساعدك تطوّر كلامك.", fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(messages) { message ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) Arrangement.Start else Arrangement.End
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 300.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (message.fromUser) Color.White else Night
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    message.text,
                                    color = if (message.fromUser) Night else Color.White,
                                    fontSize = 15.sp
                                )
                                if (!message.fromUser) {
                                    TextButton(onClick = { speak(message.text) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("▶ اسمع الرد تاني", color = Teal, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        launchRecognition()
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !sending && !listening,
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (sending) Muted else Teal, contentColor = Night)
            ) {
                Text(if (sending) "…" else if (listening) "●" else "🎙", fontSize = 28.sp)
            }
        }
        Text(
            when {
                sending -> "جاري تجهيز الرد..."
                listening -> "اتكلم دلوقتي"
                else -> "اضغط للتحدث"
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Muted,
            fontSize = 12.sp
        )
        if (messages.isNotEmpty()) {
            TextButton(
                onClick = {
                    messages.clear()
                    status = "بدأنا محادثة جديدة. اتكلم بالإنجليزية!"
                    tts?.stop()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("محادثة جديدة", color = TealDark) }
        }
    }
}
