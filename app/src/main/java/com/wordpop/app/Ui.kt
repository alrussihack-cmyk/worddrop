@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.wordpop.app

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import java.util.Calendar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

// ---------- brand ----------

val Teal = Color(0xFF45DDBE)       // WordPop mint
val TealDark = Color(0xFF0A6E5B)   // Readable deep mint
val Night = Color(0xFF12303A)      // Brand ink
val Mint = Color(0xFF45DDBE)
val Cloud = Color(0xFFFFFFFF)      // Clean white canvas
val Muted = Color(0xFF4C676E)      // Secondary copy
val Line = Color(0xFFD3DEE1)       // Soft outline
val SunTint = Color(0xFFFFF5DE)
val Spark = Color(0xFFF59E0B)      // Warm highlight
val Sun = Color(0xFFF59E0B)
val Coral = Color(0xFFF28C78)
val Sky = Color(0xFF8FDACB)
val SkyTint = Color(0xFFEAF5F2)
val Tint = Color(0xFFD4F6EC)       // Soft mint surface
val Danger = Color(0xFFB42318)
val MissTint = Color(0xFFFFE0DA)

@Composable
fun WordPopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Night,
            secondary = TealDark,
            onSecondary = Color.White,
            tertiary = Spark,
            background = Cloud,
            surface = Color.White,
            onSurface = Night,
            outline = Line,
            error = Danger
        ),
        typography = brandTypography()
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** English text is always laid out left-to-right, even inside the Arabic (RTL) screens. */
@Composable
fun En(
    text: String,
    size: TextUnit,
    color: Color = Night,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    Ltr {
        Text(
            text,
            modifier = modifier,
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (size.value >= 30f) Display else Readex,
            textAlign = align,
            lineHeight = size * 1.25f
        )
    }
}

// ---------- shared pieces ----------

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, container: Color = Teal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 0.dp else 4.dp, tween(80), label = "primaryShadow")
    val offset by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(80), label = "primaryPress")
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 56.dp)
            .graphicsLayer { translationY = offset.toPx() }
            .hardShadow(shape, depth),
        shape = shape,
        border = BorderStroke(2.dp, Night),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Night)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 0.dp else 4.dp, tween(80), label = "secondaryShadow")
    val offset by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(80), label = "secondaryPress")
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 56.dp)
            .graphicsLayer { translationY = offset.toPx() }
            .hardShadow(shape, depth),
        shape = shape,
        border = BorderStroke(2.dp, Night),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Night)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 46.dp)
            .hardShadow(shape, 3.dp)
            .clip(shape)
            .background(if (selected) Teal else Color.White)
            .border(2.dp, Night, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Night,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.5.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontFamily = Display, color = Night)
            content()
        }
    }
}

@Composable
fun SpeakButton(size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .hardShadow(CircleShape, 3.dp)
            .clip(CircleShape)
            .background(Teal)
            .border(2.dp, Night, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "نطق الكلمة", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_volume),
            contentDescription = "نطق الكلمة",
            tint = Night,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun StepBtn(symbol: String, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .hardShadow(RoundedCornerShape(14.dp), 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(2.dp, Night, RoundedCornerShape(14.dp))
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 24.sp, color = Night)
    }
}

@Composable
fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
        Ltr {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepBtn("−", "نقّص ساعة") { onChange((value + 23) % 24) }
                Text(
                    hourLabel(value),
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Night
                )
                StepBtn("+", "زوّد ساعة") { onChange((value + 1) % 24) }
            }
        }
    }
}

/** Text-to-speech for in-app screens, using the accent selected in Settings. */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var ready by remember { mutableStateOf(false) }
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                holder[0]?.let { applyEnglishAccent(it, store.speechAccent) }
                ready = true
            }
        }
        holder[0] = engine
        onDispose {
            engine.shutdown()
            holder[0] = null
        }
    }
    return { text ->
        val fallback = {
            if (ready) holder[0]?.let { engine ->
                applyEnglishAccent(engine, store.speechAccent)
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop-ui")
            }
        }
        val worker = AiConfigSync.workerUrl
        if (worker.isNotBlank()) {
            EasyVoiceClient.speak(ctx.applicationContext, worker, text, store.speechVoice, fallback)
        } else {
            fallback()
        }
    }
}

/** Small, original three-note success chime and descending error cue. */
private object AnswerFeedbackAudio {
    private const val SAMPLE_RATE = 22_050
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTrack: AudioTrack? = null

    private data class Note(val frequency: Double, val durationMs: Int, val gain: Double)

    @Synchronized
    fun play(correct: Boolean) {
        stopCurrent()
        val notes = if (correct) {
            listOf(
                Note(659.25, 90, 0.78),
                Note(783.99, 95, 0.82),
                Note(1046.50, 190, 0.92)
            )
        } else {
            listOf(
                Note(392.00, 135, 0.76),
                Note(293.66, 210, 0.88)
            )
        }
        val gapSamples = SAMPLE_RATE / 100
        val totalSamples = notes.sumOf { SAMPLE_RATE * it.durationMs / 1000 } +
            gapSamples * (notes.size - 1)
        val pcm = ShortArray(totalSamples)
        var cursor = 0

        notes.forEachIndexed { noteIndex, note ->
            val count = SAMPLE_RATE * note.durationMs / 1000
            val attack = (SAMPLE_RATE * 0.008).toInt().coerceAtLeast(1)
            val release = (SAMPLE_RATE * 0.035).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val envelope = minOf(
                    1.0,
                    i.toDouble() / attack,
                    (count - i).toDouble() / release
                ).coerceAtLeast(0.0)
                val phase = 2.0 * PI * note.frequency * i / SAMPLE_RATE
                val wave = sin(phase) + 0.18 * sin(phase * 2.0)
                val sample = (wave * envelope * note.gain * 0.34 * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[cursor + i] = sample.toShort()
            }
            cursor += count
            if (noteIndex < notes.lastIndex) cursor += gapSamples
        }

        var track: AudioTrack? = null
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val builtTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track = builtTrack

            val written = builtTrack.write(pcm, 0, pcm.size)
            if (written != pcm.size) {
                builtTrack.release()
                return
            }
            builtTrack.setVolume(0.9f)
            activeTrack = builtTrack
            builtTrack.play()

            val playingTrack = builtTrack
            val durationMs = totalSamples * 1000L / SAMPLE_RATE + 120L
            mainHandler.postDelayed({
                synchronized(this) {
                    if (activeTrack === playingTrack) activeTrack = null
                }
                runCatching {
                    if (playingTrack.playState == AudioTrack.PLAYSTATE_PLAYING) playingTrack.stop()
                }
                runCatching { playingTrack.release() }
            }, durationMs)
        } catch (_: Exception) {
            if (activeTrack === track) activeTrack = null
            runCatching { track?.release() }
        }
    }

    private fun stopCurrent() {
        val previous = activeTrack ?: return
        activeTrack = null
        runCatching {
            if (previous.playState == AudioTrack.PLAYSTATE_PLAYING) previous.stop()
        }
        runCatching { previous.release() }
    }
}

@Composable
fun rememberAnswerFeedback(store: SettingsStore): (Boolean) -> Unit =
    remember(store) {
        { correct ->
            if (store.feedbackSound) {
                // Sound generation is local; no network request or third-party asset is needed.
                AnswerFeedbackAudio.play(correct)
            }
        }
    }

/** Shared, settings-aware motion for correct and incorrect answers across every exercise. */
@Composable
fun AnswerFeedbackMotion(
    correct: Boolean,
    trigger: Int,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(trigger, correct, enabled) {
        if (trigger <= 0 || !enabled) {
            shake.snapTo(0f)
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (correct) {
            scale.snapTo(0.97f)
            scale.animateTo(1.055f, tween(110))
            scale.animateTo(1f, tween(190))
        } else {
            val distance = with(density) { 10.dp.toPx() }
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 320
                    -distance at 55
                    distance * 0.72f at 115
                    -distance * 0.42f at 180
                    distance * 0.2f at 245
                    0f at 320
                }
            )
        }
    }

    Box(Modifier.graphicsLayer {
        translationX = shake.value
        scaleX = scale.value
        scaleY = scale.value
    }) {
        content()
    }
}

// ---------- app root ----------

@Composable
fun WordPopApp(
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    openWordId: Int?,
    onConsumeOpenWord: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestExactAlarm: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var learnId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(openWordId) {
        if (openWordId != null) {
            learnId = openWordId
            onConsumeOpenWord()
        }
    }

    var page by remember { mutableStateOf(Page.Home) }
    var adminOpen by remember { mutableStateOf(false) }

    val id = learnId
    if (adminOpen) {
        BackHandler { adminOpen = false }
        AdminScreen(
            store = store,
            overlayGranted = overlayGranted,
            exactAlarmGranted = exactAlarmGranted,
            onRequestOverlay = onRequestOverlay,
            onRequestNotif = onRequestNotif,
            onRequestExactAlarm = onRequestExactAlarm,
            onClose = { adminOpen = false }
        )
    } else if (id != null) {
        BackHandler { learnId = null }
        LearnScreen(
            store = store,
            startWord = WordRepo.get(id) ?: WordRepo.pick(store),
            onClose = { learnId = null }
        )
    } else {
        BackHandler(enabled = page != Page.Home) { page = Page.Home }
        // The bottom bar hides while the keyboard is open so typing exercises get the whole screen.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize().background(Cloud).imePadding()) {
            Box(Modifier.weight(1f)) {
                if (page == Page.Review) {
                    ReviewScreen(store = store)
                } else if (page == Page.Favorites) {
                    FavoritesScreen(store = store, onOpenWord = { learnId = it })
                } else if (page == Page.Write) {
                    WriteScreen(store = store)
                } else if (page == Page.Settings) {
                    SettingsSwipeScreen(store = store, onOpenAdmin = { adminOpen = true })
                } else {
                    HomeScreen(
                        store = store,
                        page = page,
                        onNavigate = { page = it },
                        overlayGranted = overlayGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        onRequestOverlay = onRequestOverlay,
                        onRequestNotif = onRequestNotif,
                        onRequestExactAlarm = onRequestExactAlarm,
                        onStartLearning = { learnId = WordRepo.pick(store).id },
                        onOpenAdmin = { adminOpen = true }
                    )
                }
            }
            if (!imeVisible) BottomBar(page) { page = it }
        }
    }
}

/** Tabs of the bottom bar. */
enum class Page(val title: String, val icon: Int) {
    Home("الرئيسية", R.drawable.ic_home),
    Review("المراجعة", R.drawable.ic_check),
    Write("الكتابة", R.drawable.ic_edit),
    Favorites("المفضلة", R.drawable.ic_star_outline),
    MyWords("كلماتي", R.drawable.ic_list),
    Settings("الإعدادات", R.drawable.ic_tune)
}

@Composable
fun BottomBar(page: Page, onSelect: (Page) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Night))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
            Page.values().forEach { p ->
                val selected = p == page
                val tint = if (selected) Night else Muted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clickable(role = Role.Tab, onClick = { onSelect(p) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val pill = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .width(if (selected) 64.dp else 48.dp)
                            .height(36.dp)
                            .then(if (selected) Modifier.clip(pill).background(Teal).border(2.dp, Night, pill) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(if (selected && p == Page.Favorites) R.drawable.ic_star else p.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        p.title, fontSize = 12.sp, color = if (selected) TealDark else tint,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Round action button with a label under it. */
@Composable
fun QuickAction(label: String, icon: Int, filled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(64.dp)
                .hardShadow(CircleShape, 4.dp)
                .clip(CircleShape)
                .background(if (filled) Teal else Color.White)
                .border(2.5.dp, Night, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp))
        }
        Text(label, fontSize = 14.sp, color = Night, textAlign = TextAlign.Center)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, color = Night, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Teal, checkedBorderColor = Night, uncheckedBorderColor = Night, uncheckedThumbColor = Night, uncheckedTrackColor = Color.White)
        )
    }
}

@Composable
fun StarButton(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "المفضلة", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = if (selected) "شيل من المفضلة" else "ضيف للمفضلة",
            tint = if (selected) Spark else Muted,
            modifier = Modifier.size(28.dp)
        )
    }
}

// ---------- home ----------

/** Asks Android to stop limiting the app in the background. Returns true if a request dialog was opened. */
fun requestBatteryExemption(ctx: android.content.Context): Boolean {
    return try {
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            false
        } else {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    } catch (e: Exception) {
        false
    }
}

@Composable
fun HomeStat(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    val statShape = RoundedCornerShape(20.dp)
    Surface(modifier = modifier.hardShadow(statShape, 3.dp), shape = statShape, color = tint, border = BorderStroke(1.5.dp, Night)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Night)
            Text(label, fontSize = 11.sp, color = Night, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun HomeScreen(
    store: SettingsStore,
    page: Page,
    onNavigate: (Page) -> Unit,
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onStartLearning: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(store.enabled) }
    var interval by remember { mutableIntStateOf(store.intervalMin) }
    var startH by remember { mutableIntStateOf(store.startHour) }
    var endH by remember { mutableIntStateOf(store.endHour) }
    var content by remember { mutableIntStateOf(store.content) }
    var displaySec by remember { mutableIntStateOf(store.displaySec) }
    var autoSpeak by remember { mutableStateOf(store.autoSpeak) }
    var sound by remember { mutableStateOf(store.sound) }
    var speechAccent by remember { mutableStateOf(store.speechAccent) }
    var speechVoice by remember { mutableStateOf(store.speechVoice) }
    var level by remember { mutableIntStateOf(store.level) }
    var bannerPos by remember { mutableIntStateOf(store.bannerPos) }
    var bannerSize by remember { mutableIntStateOf(store.bannerSize) }
    var opacity by remember { mutableFloatStateOf(store.opacity.toFloat()) }
    var bannerTheme by remember { mutableIntStateOf(store.bannerTheme) }
    var actionSet by remember { mutableStateOf(store.actions) }
    var skipCalls by remember { mutableStateOf(store.skipCalls) }
    var skipGames by remember { mutableStateOf(store.skipGames) }
    var pausedUntil by remember { mutableStateOf(store.pausedUntil) }
    var usageGranted by remember { mutableStateOf(Focus.hasUsageAccess(ctx)) }
    DisposableEffect(Unit) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) usageGranted = Focus.hasUsageAccess(ctx)
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    var wordsVersion by remember { mutableIntStateOf(0) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var myWordsMode by remember { mutableIntStateOf(0) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    val learned = store.learned.size
    val total = wordsVersion.let { WordRepo.all.size }
    val reviewDueCount = WordRepo.reviewDueCount(store)
    val reviewNewCount = WordRepo.reviewNewCount(store)
    val customCount = wordsVersion.let { WordRepo.customWords.size }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                if (text.isBlank()) throw IllegalStateException("empty")
                pendingImportName = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف الكلمات"
                pendingImportText = text
                importMsg = "الملف جاهز للاستيراد. راجع الاسم واضغط استيراد."
            } catch (_: Exception) {
                pendingImportName = null
                pendingImportText = null
                importMsg = "ماقدرتش أقرا الملف. جرّب ملف TXT أو CSV بترميز UTF-8."
            }
        }
    }

    // Re-sends START so the service picks up the new interval and refreshes its notification.
    fun refresh() {
        if (enabled) sendToService(ctx, OverlayService.ACTION_START)
    }

    fun toggle(on: Boolean) {
        if (on) {
            if (!overlayGranted) {
                onRequestOverlay()
                return
            }
            if (!exactAlarmGranted) {
                // Remember the user's intent; MainActivity starts the service when access is granted.
                store.enabled = true
                enabled = true
                onRequestExactAlarm()
                return
            }
            onRequestNotif()
            requestBatteryExemption(ctx)
            store.enabled = true
            enabled = true
            sendToService(ctx, OverlayService.ACTION_START)
        } else {
            store.enabled = false
            enabled = false
            Scheduler.cancel(ctx)
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page == Page.Home) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = "WordPop", modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("صباح الخير 👋", fontSize = 23.sp, fontFamily = Display, color = Night)
                    Text("جاهز تتعلم كلمات جديدة؟", fontSize = 13.sp, color = Muted)
                }
                Text("WordPop", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TealDark)
            } else if (page != Page.MyWords) {
                Text(page.title, fontSize = 22.sp, fontFamily = Display, color = Night)
            }
        }

        if (page == Page.Home) {
            // One clear action, with supporting progress and tools kept secondary.
            val heroShape = RoundedCornerShape(28.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().hardShadow(heroShape, 5.dp),
                shape = heroShape,
                color = Tint,
                border = BorderStroke(2.dp, Night)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.8f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("مهمة اليوم", color = TealDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (reviewNewCount > 0) "$reviewNewCount كلمات جديدة" else "ابدأ بكلمة جديدة",
                                fontSize = 25.sp, fontFamily = Display, color = Night
                            )
                            Text("جلسة خفيفة · خطوة صغيرة كل يوم", fontSize = 13.sp, color = Muted)
                        }
                        Boby(Mood.Cheer, 94.dp)
                    }
                    PrimaryButton("ابدأ التعلّم", Modifier.fillMaxWidth()) { onStartLearning() }
                }
            }

            val dashboardCardShape = RoundedCornerShape(22.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().hardShadow(dashboardCardShape, 3.dp),
                shape = dashboardCardShape, color = Color.White, border = BorderStroke(1.5.dp, Night)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تقدمك في الكلمات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
                        Text("$learned / $total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else (learned.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)),
                        color = Teal, trackColor = Line
                    )
                    Text("كل كلمة بتتعلمها بتقرّبك من هدفك.", fontSize = 12.sp, color = Muted)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeStat(learned.toString(), "اتعلمتها", SkyTint, Modifier.weight(1f))
                HomeStat(reviewDueCount.toString(), "للمراجعة", SunTint, Modifier.weight(1f))
                HomeStat(store.favWords.size.toString(), "المفضلة", Tint, Modifier.weight(1f))
            }

            val smartReviewCardShape = RoundedCornerShape(22.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().hardShadow(smartReviewCardShape, 3.dp),
                shape = smartReviewCardShape, color = Color.White, border = BorderStroke(1.5.dp, Night)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Boby(Mood.Think, 54.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("المراجعة الذكية", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text(
                            if (reviewDueCount + reviewNewCount > 0) "$reviewDueCount مستحقة · $reviewNewCount جديدة"
                            else "ممتاز! مفيش كلمات مستحقة دلوقتي",
                            fontSize = 12.sp, color = Muted
                        )
                    }
                    SecondaryButton("ابدأ") { onNavigate(Page.Review) }
                }
            }

            val floatingShape = RoundedCornerShape(22.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().hardShadow(floatingShape, 3.dp),
                shape = floatingShape, color = Color.White, border = BorderStroke(1.5.dp, Night)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("الكلمات العائمة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                            Text(
                                if (enabled) "شغّالة · ${intervalLabel(interval)}" else "متوقفة مؤقتًا",
                                fontSize = 12.sp, color = Muted
                            )
                        }
                        Switch(
                            checked = enabled, onCheckedChange = { toggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Night, checkedTrackColor = Teal,
                                uncheckedThumbColor = Color.White, uncheckedTrackColor = Line
                            )
                        )
                    }
                    SecondaryButton("جرّب ظهور كلمة الآن", Modifier.fillMaxWidth()) {
                        if (overlayGranted) sendToService(ctx, OverlayService.ACTION_SHOW_NOW) else onRequestOverlay()
                    }
                }
            }

            if (!overlayGranted || !exactAlarmGranted) {
                val needsOverlay = !overlayGranted
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = SunTint) {
                    Row(
                        Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Boby(Mood.Think, 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("خطوة أخيرة للتشغيل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                            Text(
                                if (needsOverlay) "اسمح بالظهور فوق التطبيقات."
                                else "فعّل التنبيهات الدقيقة لمواعيد الكلمات.",
                                fontSize = 12.sp, color = Muted
                            )
                        }
                        PrimaryButton("تفعيل") {
                            if (needsOverlay) onRequestOverlay() else onRequestExactAlarm()
                        }
                    }
                }
            }
        }

        if (page == Page.Settings) {
            Section("إيقاف مؤقت") {
                val paused = pausedUntil > System.currentTimeMillis()
                if (paused) {
                    Text(
                        "الكلمات متوقفة لحد " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            .format(java.util.Date(pausedUntil)),
                        fontSize = 14.sp, color = Muted
                    )
                } else {
                    Text("اختار مدة راحة، وتقدر تستأنف في أي وقت.", fontSize = 14.sp, color = Muted)
                }
                ChipRow {
                    listOf(30 to "30 دقيقة", 60 to "ساعة", 180 to "3 ساعات").forEach { (m, label) ->
                        Chip(label, false) {
                            val until = System.currentTimeMillis() + m * 60_000L
                            store.pausedUntil = until
                            pausedUntil = until
                        }
                    }
                    Chip("لحد الصبح", false) {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, startH)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
                        store.pausedUntil = cal.timeInMillis
                        pausedUntil = cal.timeInMillis
                    }
                }
                if (paused) {
                    SecondaryButton("استئناف الكلمات", Modifier.fillMaxWidth()) {
                        store.pausedUntil = 0L
                        pausedUntil = 0L
                    }
                }
            }
        }

        if (page == Page.Settings) {
        Section("كل قد إيه تظهرلك كلمة؟") {
            ChipRow {
                listOf(5, 15, 30, 60, 120, 180, 360).forEach { m ->
                    Chip(intervalLabel(m), interval == m) {
                        interval = m
                        store.intervalMin = m
                        refresh()
                    }
                }
            }
        }
        }

        if (page == Page.Settings) {
        Section("ساعات الظهور") {
            HourStepper("من", startH) {
                startH = it
                store.startHour = it
                refresh()
            }
            HourStepper("لحد", endH) {
                endH = it
                store.endHour = it
                refresh()
            }
            Text("بره الساعات دي مش هتظهرلك كلمات.", fontSize = 14.sp, color = Muted)
        }
        }

        if (page == Page.Settings) {
        Section("نوع الكلمات") {
            ChipRow {
                listOf(0 to "كلمات وعبارات", 1 to "كلمات بس", 2 to "عبارات بس").forEach { (v, label) ->
                    Chip(label, content == v) {
                        content = v
                        store.content = v
                    }
                }
            }
        }
        }

        if (page == Page.Settings) {
        Section("المستوى") {
            ChipRow {
                listOf(0 to "الكل", 1 to "أساسي", 2 to "B1", 3 to "B2").forEach { (v, label) ->
                    Chip(label, level == v) {
                        level = v
                        store.level = v
                    }
                }
            }
            Text("B1 وB2 كلمات أصعب للمستوى المتوسط وفوق المتوسط.", fontSize = 14.sp, color = Muted)
        }
        }

        if (page == Page.Settings) {
        Section("شكل الظهور") {
            Text("مدة ظهور الشريط", fontSize = 16.sp, color = Muted)
            ChipRow {
                listOf(6, 10, 15, 25).forEach { s ->
                    Chip("$s ثانية", displaySec == s) {
                        displaySec = s
                        store.displaySec = s
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "صوت تنبيه لما النافذة تظهر",
                    fontSize = 17.sp,
                    color = Night,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        store.sound = it
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Teal, checkedBorderColor = Night, uncheckedBorderColor = Night, uncheckedThumbColor = Night, uncheckedTrackColor = Color.White)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "انطق الكلمة تلقائيًا أول ما تظهر",
                    fontSize = 17.sp,
                    color = Night,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoSpeak,
                    onCheckedChange = {
                        autoSpeak = it
                        store.autoSpeak = it
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Teal, checkedBorderColor = Night, uncheckedBorderColor = Night, uncheckedThumbColor = Night, uncheckedTrackColor = Color.White)
                )
            }
        }
        }

        if (page == Page.Settings) {
            val speakAccentSample = rememberSpeaker()
            Section("نطق الكلمات") {
                Text(
                    "اختار اللكنة الإنجليزية اللي تحب تسمع بيها الكلمات والأمثلة.",
                    fontSize = 14.sp,
                    color = Muted
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SpeechAccents.AMERICAN to "أمريكي 🇺🇸",
                        SpeechAccents.BRITISH to "بريطاني 🇬🇧",
                        SpeechAccents.AUSTRALIAN to "أسترالي 🇦🇺"
                    ).forEach { (id, label) ->
                        Chip(label, speechAccent == id) {
                            speechAccent = id
                            store.speechAccent = id
                        }
                    }
                }
                PrimaryButton("اسمع عينة من اللكنة", Modifier.fillMaxWidth()) {
                    speakAccentSample(
                        "Hello! How are you today? It is a beautiful day to learn something new."
                    )
                }
                SecondaryButton("تنزيل أو تحديث الأصوات", Modifier.fillMaxWidth()) {
                    try {
                        ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    } catch (_: Exception) {
                    }
                }
                Text(
                    "لأفضل صوت طبيعي، نزّل الصوت عالي الجودة للّكنة المختارة من محرك النطق في موبايلك. توفر الأصوات يختلف حسب الجهاز.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            Section("EasyVoice AI — صوت طبيعي") {
                Text(
                    "اختار صوتًا من أصوات EasyVoice المجانية. الأصوات البريطانية المتاحة حاليًا تشمل Emma وDaniel، والأصوات الأمريكية تشمل Nova وHeart وBella وSky.",
                    fontSize = 13.sp,
                    color = Muted
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "af_nova" to "Nova 🇺🇸",
                        "af_heart" to "Heart 🇺🇸",
                        "af_bella" to "Bella 🇺🇸",
                        "bf_emma" to "Emma 🇬🇧",
                        "bm_daniel" to "Daniel 🇬🇧"
                    ).forEach { (voiceId, label) ->
                        Chip(label, speechVoice == voiceId) {
                            speechVoice = voiceId
                            store.speechVoice = voiceId
                            store.speechAccent = if (voiceId.startsWith("b")) SpeechAccents.BRITISH else SpeechAccents.AMERICAN
                            speechAccent = store.speechAccent
                        }
                    }
                }
                Text(
                    if (AiConfigSync.workerUrl.isBlank()) {
                        "رابط خدمة الصوت والذكاء الاصطناعي يتم ضبطه مركزيًا من WordPop Control. الصوت الحالي: Android TTS."
                    } else {
                        "إعدادات الخدمة متصلة من WordPop Control. لو فشل EasyVoice، التطبيق يرجع تلقائيًا إلى Android TTS."
                    },
                    fontSize = 12.sp,
                    color = Muted
                )
                PrimaryButton("جرّب صوت EasyVoice", Modifier.fillMaxWidth()) {
                    speakAccentSample("Hello! Welcome to WordPop. Let's learn a new word together.")
                }
            }
        }

        if (page == Page.Settings) {
        Section("مكان النافذة") {
            Text(
                "اسحب النافذة بصباعك لأي مكان على الشاشة، وهتفتكر المكان.",
                fontSize = 15.sp,
                color = Muted
            )
            ChipRow {
                listOf(0 to "أعلى الشاشة", 1 to "نص الشاشة", 2 to "أسفل الشاشة").forEach { (v, label) ->
                    Chip(label, bannerPos == v) {
                        bannerPos = v
                        store.bannerPos = v
                        store.bannerX = 0
                        store.bannerY = -1
                    }
                }
            }
            SecondaryButton("رجّع النافذة لمكانها الأصلي", Modifier.fillMaxWidth()) {
                bannerPos = 0
                store.bannerPos = 0
                store.bannerX = 0
                store.bannerY = -1
            }
        }
        }

        if (page == Page.Settings) {
            Section("شكل النافذة العائمة") {
                Text("الحجم", fontSize = 16.sp, color = Muted)
                ChipRow {
                    listOf(0 to "صغير", 1 to "وسط", 2 to "كبير").forEach { (v, label) ->
                        Chip(label, bannerSize == v) {
                            bannerSize = v
                            store.bannerSize = v
                        }
                    }
                }
                Text("الشفافية: ${opacity.toInt()}%", fontSize = 16.sp, color = Muted)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = { store.opacity = opacity.toInt() },
                    valueRange = 40f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Teal,
                        activeTrackColor = Teal,
                        inactiveTrackColor = Line
                    )
                )
                Text("اللون", fontSize = 16.sp, color = Muted)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BannerThemes.all.forEachIndexed { i, th ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable {
                                bannerTheme = i
                                store.bannerTheme = i
                            }
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(th.bg))
                                    .border(
                                        if (bannerTheme == i) 3.dp else 1.dp,
                                        if (bannerTheme == i) Teal else Line,
                                        CircleShape
                                    )
                            )
                            Text(th.name, fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                Text("الأزرار اللي تظهر في النافذة", fontSize = 16.sp, color = Muted)
                ChipRow {
                    listOf("fav" to "مفضلة", "know" to "أعرفها", "skip" to "تخطي").forEach { (k, label) ->
                        Chip(label, k in actionSet) {
                            actionSet = if (k in actionSet) actionSet - k else actionSet + k
                            store.actions = actionSet
                        }
                    }
                }
                ChipRow {
                    listOf("dont" to "لسه", "example" to "مثال", "pause" to "إيقاف").forEach { (k, label) ->
                        Chip(label, k in actionSet) {
                            actionSet = if (k in actionSet) actionSet - k else actionSet + k
                            store.actions = actionSet
                        }
                    }
                }
                Text("الأساسي: مفضلة وأعرفها وتخطي. فعّل الباقي لو عايزه.", fontSize = 14.sp, color = Muted)
                Text("جرّب من الرئيسية بزرار \"جرّب كلمة\" عشان تشوف الشكل.", fontSize = 14.sp, color = Muted)
            }
        }

        if (page == Page.Settings) {
            Section("عدم الإزعاج") {
                SwitchRow("ماتظهرش أثناء المكالمات", skipCalls) {
                    skipCalls = it
                    store.skipCalls = it
                }
                SwitchRow("ماتظهرش في الألعاب والفيديوهات", skipGames) {
                    skipGames = it
                    store.skipGames = it
                }
                if (skipGames && !usageGranted) {
                    Text(
                        "عشان التطبيق يعرف إنك في لعبة أو فيديو، لازم تسمح له بـ \"الوصول لبيانات الاستخدام\".",
                        fontSize = 15.sp,
                        color = Muted
                    )
                    SecondaryButton("افتح الإعدادات واسمح", Modifier.fillMaxWidth()) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }

        if (page == Page.MyWords) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("كلماتي", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("ابني مكتبة كلماتك بطريقتك", fontSize = 13.sp, color = Muted)
                }
                Boby(Mood.Happy, 48.dp)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tint)
                    .border(1.5.dp, Night, RoundedCornerShape(20.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("ولّد بالذكاء الاصطناعي", "استيراد من ملف").forEachIndexed { index, label ->
                    val selected = myWordsMode == index
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Teal else Color.Transparent)
                            .then(if (selected) Modifier.border(2.dp, Night, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { myWordsMode = index }.padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 14.sp, color = Night, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
            if (myWordsMode == 0) {
                AiVocabularyTab(store = store, onAdded = { result ->
                    wordsVersion++
                    importMsg = buildString {
                        append("تمت إضافة ${result.added} كلمة إلى كلماتك.")
                        if (result.duplicates > 0) append(" تم تجاهل ${result.duplicates} مكررة.")
                        if (result.skipped > 0) append(" تم تجاهل ${result.skipped} غير مكتملة.")
                    }
                })
            } else {
                Spacer(Modifier.height(4.dp))
                val cardShape = RoundedCornerShape(26.dp)
                Surface(
                    shape = cardShape,
                    color = Color.White,
                    border = BorderStroke(2.dp, Night),
                    modifier = Modifier.fillMaxWidth().hardShadow(cardShape, 5.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Teal).border(2.dp, Night, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Text("⇧", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Night)
                            }
                            Text("إضافة كلمات من ملف", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                        }
                        Text("تقدر تستورد قايمة من ملف TXT أو CSV. كل سطر فيه الكلمة والترجمة، وممكن تضيف مثال وترجمته.", fontSize = 14.sp, lineHeight = 25.sp, color = Muted)
                        Surface(shape = RoundedCornerShape(16.dp), color = Tint) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("شكل كل سطر", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TealDark)
                                Text("الكلمة، الترجمة، (مثال اختياري)، (ترجمة المثال اختيارية)", fontSize = 14.sp, lineHeight = 23.sp, color = Night)
                                Ltr { Text("lifeguard, منقذ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Night) }
                            }
                        }
                        if (pendingImportName == null) {
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 132.dp).clip(RoundedCornerShape(20.dp))
                                    .border(2.5.dp, Night, RoundedCornerShape(20.dp))
                                    .clickable { importLauncher.launch(arrayOf("text/plain", "text/csv", "application/csv", "application/vnd.ms-excel", "*/*")) }
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("⇧", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Night)
                                Text("اختار ملف TXT أو CSV", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFEAF9F5)).border(2.dp, Night, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Teal).border(2.dp, Night, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                    Text("✓", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(pendingImportName ?: "", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Night, maxLines = 1)
                                    Text("الملف جاهز للاستيراد", fontSize = 13.sp, color = Muted)
                                }
                                Text("×", fontSize = 26.sp, color = Night, modifier = Modifier.clickable {
                                    pendingImportName = null
                                    pendingImportText = null
                                }.padding(8.dp))
                            }
                        }
                        importMsg?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("ماقدرتش")) Danger else TealDark) }
                        PrimaryButton("استيراد قايمة من ملف", Modifier.fillMaxWidth()) {
                            val raw = pendingImportText
                            if (raw == null) {
                                importLauncher.launch(arrayOf("text/plain", "text/csv", "application/csv", "application/vnd.ms-excel", "*/*"))
                            } else {
                                try {
                                    val result = WordRepo.importText(ctx, raw)
                                    wordsVersion++
                                    pendingImportName = null
                                    pendingImportText = null
                                    importMsg = buildString {
                                        append("تمت إضافة ${result.added} كلمة")
                                        if (result.duplicates > 0) append("، وتخطّيت ${result.duplicates} مكررة")
                                        if (result.skipped > 0) append("، و${result.skipped} سطر ناقص")
                                        append(".")
                                        if (result.added == 0) append(" راجع تنسيق السطور.")
                                    }
                                } catch (_: Exception) {
                                    importMsg = "ماقدرتش أستورد الملف. راجع تنسيق السطور وترميز UTF-8."
                                }
                            }
                        }
                        if (customCount > 0) {
                            Text("عندك $customCount كلمة مضافة يدويًا أو بالذكاء الاصطناعي.", fontSize = 14.sp, color = Muted)
                            SecondaryButton("امسح الكلمات الخاصة", Modifier.fillMaxWidth()) {
                                WordRepo.clearCustom(ctx)
                                wordsVersion++
                                importMsg = "اتمسحت الكلمات الخاصة."
                            }
                        }
                    }
                }
            }
        }

        if (page == Page.Settings) {
        Section("لو الكلمات بتتأخر") {
            Text(
                "بعض الموبايلات بتوقف التطبيقات في الخلفية. خلّي ووردبوب \"بدون قيود\" في إعدادات البطارية.",
                fontSize = 15.sp,
                color = Muted
            )
            SecondaryButton("افتح إعدادات البطارية", Modifier.fillMaxWidth()) {
                if (!requestBatteryExemption(ctx)) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e: Exception) {
                    }
                }
            }
        }
        }

        if (page == Page.Settings) {
            Section("حالة مزامنة الكلمات") {
                Text(
                    WordCloudSync.status,
                    fontSize = 15.sp,
                    color = if (WordCloudSync.status.startsWith("تم تحميل")) TealDark else Muted
                )
            }
        }

        if (page == Page.Settings) {
            Section("لوحة التحكم") {
                Text("إدارة كل التطبيق باسم مستخدم ورقم سري.", fontSize = 15.sp, color = Muted)
                SecondaryButton("افتح لوحة التحكم", Modifier.fillMaxWidth()) { onOpenAdmin() }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ---------- learn ----------

@Composable
fun LearnScreen(store: SettingsStore, startWord: Word, onClose: () -> Unit) {
    val speak = rememberSpeaker()
    var word by remember { mutableStateOf(startWord) }
    var step by remember { mutableIntStateOf(0) }
    var learnedCount by remember { mutableIntStateOf(store.learned.size) }

    var favW by remember(word) { mutableStateOf(word.id in store.favWords) }
    var favS by remember(word) { mutableStateOf(word.id in store.favSentences) }

    fun next() {
        word = WordRepo.pick(store)
        step = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (step == 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepBtn("‹", "رجوع", onClose)
                Text("تعلّم كلمة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
                Text("حفظت $learnedCount من ${WordRepo.all.size}", fontSize = 12.sp, color = Muted)
            }
        }

        if (step == 0) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StarButton(favW) { favW = store.toggleFavWord(word.id) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Boby(Mood.Wow, 40.dp)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Tint)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(word.kind, fontSize = 15.sp, color = TealDark, fontWeight = FontWeight.Bold)
                    }
                    En(
                        word.en,
                        when {
                            word.en.length > 22 -> 26.sp
                            word.en.length > 14 -> 32.sp
                            else -> 38.sp
                        },
                        Night, FontWeight.ExtraBold, TextAlign.Center,
                        Modifier.fillMaxWidth()
                    )
                    if (word.ipa.isNotEmpty()) {
                        En(word.ipa, 16.sp, Muted, FontWeight.Normal, TextAlign.Center, Modifier.fillMaxWidth())
                    }
                    SpeakButton(48.dp) { speak(word.en) }
                    Text(
                        word.ar,
                        fontSize = 23.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Night,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (word.example.isNotEmpty()) Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(16.dp), 4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("مثال", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark, modifier = Modifier.weight(1f))
                        StarButton(favS) { favS = store.toggleFavSentence(word.id) }
                    }
                    Ltr {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(word.example, fontSize = 18.sp, color = Night, modifier = Modifier.weight(1f))
                            SpeakButton(40.dp) { speak(word.example) }
                        }
                    }
                    if (word.exampleAr.isNotEmpty()) Text(word.exampleAr, fontSize = 15.sp, color = Muted)
                }
            }

            PrimaryButton("جاهز؟ اختبرني", Modifier.fillMaxWidth()) { step = 1 }
            SecondaryButton("كلمة تانية", Modifier.fillMaxWidth()) { next() }
        } else {
            QuizSession(
                store = store,
                first = word,
                speak = speak,
                onLearnedChanged = { learnedCount = store.learned.size },
                onNewWord = { next() },
                onClose = { step = 0 }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ---------- favorites ----------

@Composable
fun FavoritesScreen(store: SettingsStore, onOpenWord: (Int) -> Unit) {
    val speak = rememberSpeaker()
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val words = version.let { store.favWords.mapNotNull { WordRepo.get(it) }.sortedBy { it.en.lowercase() } }
    val sentences = version.let {
        store.favSentences.mapNotNull { WordRepo.get(it) }.filter { it.example.isNotEmpty() }
            .sortedBy { it.example.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("المفضلة", fontSize = 24.sp, fontFamily = Display, color = Night)
                Text("كلمات وجمل وقصص عايز ترجع لها", fontSize = 13.sp, color = Muted)
            }
            Boby(Mood.Happy, 48.dp)
        }
        ChipRow {
            Chip("كلمات (${words.size})", tab == 0) { tab = 0 }
            Chip("جمل (${sentences.size})", tab == 1) { tab = 1 }
            Chip("قصة", tab == 2) { tab = 2 }
        }

        if (tab == 2) {
            StoryTab(store = store, words = words, speak = speak)
        } else if (tab == 0) {
            if (words.isEmpty()) EmptyFav("لسه مضفتش كلمات. دوس على النجمة في شاشة التعلّم.")
            words.forEach { w ->
                FavCard(
                    en = w.en, ar = w.ar,
                    onSpeak = { speak(w.en) },
                    onRemove = { store.toggleFavWord(w.id); version++ },
                    onClick = { onOpenWord(w.id) }
                )
            }
        } else {
            if (sentences.isEmpty()) EmptyFav("لسه مضفتش جمل. دوس على النجمة جنب المثال في شاشة التعلّم.")
            sentences.forEach { w ->
                FavCard(
                    en = w.example, ar = w.exampleAr,
                    onSpeak = { speak(w.example) },
                    onRemove = { store.toggleFavSentence(w.id); version++ },
                    onClick = { onOpenWord(w.id) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun EmptyFav(text: String) {
    val emptyShape = RoundedCornerShape(22.dp)
    Surface(
        shape = emptyShape, color = Tint, border = BorderStroke(1.5.dp, Night),
        modifier = Modifier.fillMaxWidth().hardShadow(emptyShape, 3.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Boby(Mood.Happy, 68.dp)
            Text(text, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, color = Night, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FavCard(en: String, ar: String, onSpeak: () -> Unit, onRemove: () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, Night),
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(20.dp), 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                En(en, 19.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.fillMaxWidth())
                Text(ar, fontSize = 16.sp, color = Muted)
            }
            SpeakButton(40.dp, onSpeak)
            StarButton(true, onRemove)
        }
    }
}

// ---------- quiz ----------

private enum class QType { Meaning, Reverse, Listen }

private class QuizQ(val word: Word, val type: QType, val options: List<String>, val correct: String) {
    /** True when the options are Arabic meanings, false when they are English words. */
    val toArabic: Boolean get() = type == QType.Meaning
}

private fun buildQuestion(word: Word, index: Int): QuizQ {
    val type = if (index == 0) QType.Meaning else listOf(QType.Meaning, QType.Reverse, QType.Listen).random()
    val toArabic = type == QType.Meaning
    fun text(w: Word) = if (toArabic) w.ar else w.en
    val pool = WordRepo.all.filter { it.id != word.id && it.isPhrase == word.isPhrase }
    val correct = text(word)
    val distractors = (pool.filter { it.kind == word.kind }.shuffled() + pool.shuffled())
        .map { text(it) }
        .filter { it != correct }
        .distinct()
        .take(3)
    return QuizQ(word, type, (distractors + correct).shuffled(), correct)
}

/** A round of up to 10 questions with progress, feedback and a result screen. */
@Composable
private fun QuizSession(
    store: SettingsStore,
    first: Word,
    speak: (String) -> Unit,
    onLearnedChanged: () -> Unit,
    onNewWord: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val answerFeedback = rememberAnswerFeedback(store)
    var round by remember { mutableIntStateOf(0) }
    val questions = remember(round) {
        val start = if (round == 0) first else WordRepo.pick(store)
        WordRepo.quizWords(store, start, 10).mapIndexed { i, w -> buildQuestion(w, i) }
    }
    var index by remember(round) { mutableIntStateOf(0) }
    var picked by remember(round, index) { mutableStateOf<String?>(null) }
    var score by remember(round) { mutableIntStateOf(0) }
    var streak by remember(round) { mutableIntStateOf(0) }
    var best by remember(round) { mutableIntStateOf(0) }
    val wrong = remember(round) { mutableStateListOf<Word>() }
    val answers = remember(round) { mutableStateListOf<Boolean>() }
    val total = questions.size

    if (index >= total) {
        QuizResult(
            score = score,
            total = total,
            best = best,
            wrong = wrong.toList(),
            speak = speak,
            animationsEnabled = store.feedbackAnimations,
            onAgain = { round++ },
            onNewWord = onNewWord
        )
        return
    }

    val q = questions[index]
    val answered = picked != null
    val shakeDistancePx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(round, index) {
        if (q.type == QType.Listen) speak(q.word.en)
    }

    // Quiz header: close control, per-question progress segments, and live score.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepBtn("×", "إغلاق الاختبار", onClose)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(total) { i ->
                val segmentColor = when {
                    i < answers.size && answers[i] -> Teal
                    i < answers.size -> Coral
                    else -> Line
                }
                Box(
                    Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Line)
                ) {
                    if (i < answers.size) {
                        Box(Modifier.fillMaxSize().background(segmentColor))
                    } else if (i == index) {
                        Box(Modifier.fillMaxWidth(0.35f).height(12.dp).background(Night))
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(23.dp), color = Color.White, border = BorderStroke(2.dp, Night)) {
            Row(Modifier.height(46.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("★", fontSize = 20.sp, color = Spark)
                Text(score.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Night)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("السؤال ${index + 1} من $total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.weight(1f))
        if (streak >= 2) Text("🔥 $streak ورا بعض", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
    }

    // question card
    Surface(shape = RoundedCornerShape(26.dp), color = Color.White, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(26.dp), 5.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (q.type) {
                QType.Meaning -> {
                    Text("إيه معنى الكلمة دي؟", fontSize = 16.sp, color = Muted)
                    En(
                        q.word.en,
                        when { q.word.en.length > 18 -> 28.sp; q.word.en.length > 13 -> 34.sp; else -> 44.sp },
                        Night, FontWeight.ExtraBold, TextAlign.Center, Modifier.fillMaxWidth()
                    )
                    SpeakButton(56.dp) { speak(q.word.en) }
                }
                QType.Reverse -> {
                    Text("إيه الكلمة الإنجليزي اللي معناها؟", fontSize = 16.sp, color = Muted)
                    Text(
                        q.word.ar,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Night,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                QType.Listen -> {
                    Text("اسمع واختار الكلمة الصح", fontSize = 16.sp, color = Muted)
                    SpeakButton(56.dp) { speak(q.word.en) }
                    Text("دوس لو عايز تسمعها تاني", fontSize = 13.sp, color = Muted)
                }
            }
        }
    }

    // options
    val letters = listOf("أ", "ب", "ج", "د")
    q.options.forEachIndexed { i, opt ->
        val isCorrect = opt == q.correct
        val isPicked = picked == opt
        val shakeX = remember(q, opt) { Animatable(0f) }
        LaunchedEffect(answered, isPicked, isCorrect, store.feedbackAnimations) {
            if (answered && isPicked && !isCorrect && store.feedbackAnimations) {
                shakeX.snapTo(0.1f)
                shakeX.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 320
                        0.1f at 0
                        (-shakeDistancePx) at 60
                        (shakeDistancePx * 0.72f) at 120
                        (-shakeDistancePx * 0.42f) at 180
                        (shakeDistancePx * 0.22f) at 240
                        0f at 320
                    }
                )
            } else {
                shakeX.snapTo(0f)
            }
        }
        val bg by animateColorAsState(
            when {
                !answered -> Color.White
                isCorrect -> Teal
                isPicked -> Color(0xFFFFB9AE)
                else -> Color.White
            },
            tween(if (store.feedbackAnimations) 220 else 0), label = "optBg"
        )
        val borderColor by animateColorAsState(
            when {
                !answered -> Night
                isCorrect -> Night
                isPicked -> Danger
                else -> Night
            },
            tween(if (store.feedbackAnimations) 220 else 0), label = "optBorder"
        )
        val shape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .hardShadow(shape, 4.dp)
                .clip(shape)
                .background(bg)
                .border(2.5.dp, borderColor, shape)
                .graphicsLayer {
                    translationX = shakeX.value
                    alpha = if (answered && !isCorrect && !isPicked) 0.5f else 1f
                }
                .clickable(enabled = !answered, role = Role.Button) {
                    picked = opt
                    answers.add(isCorrect)
                    answerFeedback(isCorrect)
                    if (isCorrect) {
                        score++
                        streak++
                        if (streak > best) best = streak
                        store.markLearned(q.word.id)
                        store.hard = store.hard - q.word.id
                        if (store.reviewStates().containsKey(q.word.id)) {
                            store.recordReview(q.word.id, ReviewRating.Good)
                        }
                        if (store.feedbackHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else {
                        streak = 0
                        wrong.add(q.word)
                        store.learned = store.learned - q.word.id
                        store.hard = store.hard + q.word.id
                        if (store.reviewStates().containsKey(q.word.id)) {
                            store.recordReview(q.word.id, ReviewRating.Again)
                        }
                        if (store.feedbackHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onLearnedChanged()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            answered && isCorrect -> Color.White
                            answered && isPicked -> Danger
                            else -> Color.White
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (answered && isCorrect) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = Night, modifier = Modifier.size(20.dp))
                } else if (answered && isPicked) {
                    Icon(painterResource(R.drawable.ic_close), null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(letters.getOrElse(i) { "" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                }
            }
            if (q.toArabic) {
                Text(opt, fontSize = 19.sp, color = Night, modifier = Modifier.weight(1f))
            } else {
                En(opt, 20.sp, Night, FontWeight.SemiBold, TextAlign.Start, Modifier.weight(1f))
            }
        }
    }

    // feedback
    AnimatedVisibility(
        visible = answered,
        enter = fadeIn(tween(if (store.feedbackAnimations) 200 else 0)) +
            expandVertically(tween(if (store.feedbackAnimations) 250 else 0)) +
            scaleIn(tween(if (store.feedbackAnimations) 220 else 0))
    ) {
        val ok = picked == q.correct
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = if (ok) Color(0xFFDDF7F0) else Color(0xFFFFEAE6),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ok && store.feedbackAnimations) CelebrationBurst()
                Boby(if (ok) Mood.Cheer else Mood.Think, 48.dp)
                Text(
                    if (ok) {
                        if (streak >= 3) "ممتاز! $streak ورا بعض 🔥" else "تمام! إجابة صح 🎯"
                    } else "مش مشكلة، هنراجعها تاني",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) TealDark else Danger
                )
                if (!ok) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجابة الصح:", fontSize = 16.sp, color = Muted)
                        if (q.toArabic) Text(q.correct, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Night)
                        else En(q.correct, 17.sp, Night, FontWeight.Bold)
                    }
                }
                if (q.word.example.isNotEmpty()) {
                    En(q.word.example, 15.sp, Muted, FontWeight.Normal, TextAlign.Start, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (answered) {
        PrimaryButton(if (index == total - 1) "شوف النتيجة" else "التالي", Modifier.fillMaxWidth()) { index++ }
    }
}

@Composable
fun CelebrationBurst() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(720),
        label = "wordPopCelebration"
    )
    val colors = listOf(Sun, Teal, Coral, Sky)
    val particles = listOf(
        0.05f to 0.08f, 0.12f to 0.40f, 0.19f to 0.18f, 0.26f to 0.62f,
        0.33f to 0.10f, 0.40f to 0.46f, 0.47f to 0.20f, 0.54f to 0.66f,
        0.61f to 0.12f, 0.68f to 0.48f, 0.75f to 0.16f, 0.82f to 0.60f,
        0.89f to 0.10f, 0.96f to 0.42f, 0.29f to 0.30f, 0.72f to 0.32f
    )
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val pieceWidth = 4.dp.toPx()
        val pieceHeight = 8.dp.toPx()
        particles.forEachIndexed { index, point ->
            val drift = sin(progress * 5.5f + index * 1.7f) * 8.dp.toPx()
            val x = size.width * point.first + drift
            val fall = 0.72f + (index % 4) * 0.09f
            val y = size.height * (point.second + progress * fall)
            val fade = (1f - (progress - 0.78f).coerceAtLeast(0f) * 4.5f).coerceIn(0f, 1f)
            val color = colors[index % colors.size].copy(alpha = fade)
            rotate(
                degrees = progress * (if (index % 2 == 0) 420f else -360f) + index * 19f,
                pivot = Offset(x, y)
            ) {
                if (index % 4 == 0) {
                    drawCircle(color = color, radius = 2.6.dp.toPx(), center = Offset(x, y))
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x - pieceWidth / 2f, y - pieceHeight / 2f),
                        size = Size(pieceWidth, pieceHeight),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizResult(
    score: Int,
    total: Int,
    best: Int,
    wrong: List<Word>,
    speak: (String) -> Unit,
    animationsEnabled: Boolean,
    onAgain: () -> Unit,
    onNewWord: () -> Unit
) {
    val pct = if (total == 0) 0 else score * 100 / total
    val stars = when { pct >= 85 -> 3; pct >= 60 -> 2; pct >= 35 -> 1; else -> 0 }
    val resultShape = RoundedCornerShape(26.dp)
    Surface(shape = resultShape, color = Color.White, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(resultShape, 5.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pct >= 80 && animationsEnabled) CelebrationBurst()
            Boby(if (pct >= 80) Mood.Cheer else if (pct >= 50) Mood.Happy else Mood.Think, 96.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Text("★", fontSize = 30.sp, color = if (index < stars) Spark else Line)
                }
            }
            Box(
                Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(if (pct >= 50) Teal else Sun)
                    .border(3.dp, Night, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$score/$total", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Night)
            }
            Text(
                when {
                    pct >= 80 -> "ممتاز! 🏆"
                    pct >= 50 -> "كويس، كمّل 👏"
                    else -> "التكرار هو السر 💪"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Night
            )
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (pct >= 50) Teal else Sun,
                trackColor = Line
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultStat("صح", "$score")
                ResultStat("غلط", "${total - score}")
                ResultStat("أطول سلسلة", "$best")
            }
        }
    }

    if (wrong.isNotEmpty()) {
        Section("كلمات محتاجة مراجعة") {
            wrong.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        En(w.en, 18.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.fillMaxWidth())
                        Text(w.ar, fontSize = 15.sp, color = Muted)
                    }
                    SpeakButton(40.dp) { speak(w.en) }
                }
            }
            Text("الكلمات دي هتظهرلك أكتر عشان تثبّت.", fontSize = 14.sp, color = Muted)
        }
    }

    PrimaryButton("جولة جديدة", Modifier.fillMaxWidth(), onClick = onAgain)
    SecondaryButton("كلمة جديدة", Modifier.fillMaxWidth(), onClick = onNewWord)
}

@Composable
fun ResultStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night)
        Text(label, fontSize = 13.sp, color = Muted)
    }
}


// ---------- smart spaced repetition ----------

@Composable
fun ReviewScreen(store: SettingsStore) {
    val speak = rememberSpeaker()
    val answerFeedback = rememberAnswerFeedback(store)
    val haptic = LocalHapticFeedback.current
    var queue by remember { mutableStateOf(WordRepo.reviewQueue(store)) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableIntStateOf(0) }
    var ratingPending by remember { mutableStateOf(false) }
    var ratingFeedbackId by remember { mutableIntStateOf(0) }
    var ratingFeedbackCorrect by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun reloadQueue() {
        queue = WordRepo.reviewQueue(store)
        index = 0
        revealed = false
        completed = 0
        remembered = 0
        ratingPending = false
        ratingFeedbackId = 0
    }

    val dueNow = WordRepo.reviewDueCount(store)
    val newToday = WordRepo.reviewNewCount(store)
    val trackedStates = store.reviewStates().filterKeys { WordRepo.get(it) != null }
    val masteredCount = trackedStates.values.count { it.repetitions >= 3 && it.intervalDays >= 21 }
    val needsPracticeCount = trackedStates.values.count { it.lapses >= 2 }

    Column(
        modifier = Modifier.fillMaxSize().background(Cloud).statusBarsPadding()
            .navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("المراجعة الذكية", fontSize = 24.sp, fontFamily = Display, color = Night)
                Text("خطوة صغيرة النهارده تثبّت الكلمات لبكرة.", fontSize = 13.sp, color = Muted)
            }
            Boby(Mood.Cheer, 48.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val reviewdueShape = RoundedCornerShape(20.dp)
            Surface(Modifier.weight(1f).hardShadow(reviewdueShape, 3.dp), shape = reviewdueShape, color = SunTint, border = BorderStroke(1.5.dp, Night)) {
                Column(Modifier.padding(vertical = 9.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$dueNow", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("مستحقة", fontSize = 12.sp, color = Muted)
                }
            }
            val reviewnewShape = RoundedCornerShape(20.dp)
            Surface(Modifier.weight(1f).hardShadow(reviewnewShape, 3.dp), shape = reviewnewShape, color = Tint, border = BorderStroke(1.5.dp, Night)) {
                Column(Modifier.padding(vertical = 9.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$newToday", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("جديدة اليوم", fontSize = 12.sp, color = Muted)
                }
            }
            val reviewmasteredShape = RoundedCornerShape(20.dp)
            Surface(Modifier.weight(1f).hardShadow(reviewmasteredShape, 3.dp), shape = reviewmasteredShape, color = SkyTint, border = BorderStroke(1.5.dp, Night)) {
                Column(Modifier.padding(vertical = 9.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$masteredCount", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("ثبتت", fontSize = 12.sp, color = Muted)
                }
            }
        }

        if (trackedStates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تقدم تثبيت الكلمات", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text("${trackedStates.size} بطاقة · $needsPracticeCount محتاجة تركيز", fontSize = 11.sp, color = Muted)
                }
                LinearProgressIndicator(
                    progress = { (masteredCount.toFloat() / trackedStates.size).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                    color = Teal, trackColor = Line
                )
            }
        }

        when {
            queue.isEmpty() -> {
                val reviewStateShape = RoundedCornerShape(24.dp)
                Surface(shape = reviewStateShape, color = Color.White, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(reviewStateShape, 4.dp)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Boby(Mood.Cheer, 76.dp)
                        Text("كده زي الفل! 🎉", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text("مفيش كلمات مستحقة دلوقتي. ارجع بكرة وهتلاقي كلمات جديدة للمراجعة.", fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
                        PrimaryButton("تحديث القائمة", Modifier.fillMaxWidth()) { reloadQueue() }
                    }
                }
            }
            index >= queue.size -> {
                val reviewStateShape = RoundedCornerShape(24.dp)
                Surface(shape = reviewStateShape, color = Color.White, border = BorderStroke(2.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(reviewStateShape, 4.dp)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (completed > 0 && remembered == completed && store.feedbackAnimations) CelebrationBurst()
                        Boby(Mood.Cheer, 76.dp)
                        Text("خلصت جلسة المراجعة!", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text("راجعت $completed كلمة، وتذكرت $remembered منها بشكل جيد أو سهل.", fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
                        LinearProgressIndicator(
                            progress = { if (completed == 0) 0f else remembered.toFloat() / completed },
                            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)),
                            color = Teal, trackColor = Line
                        )
                        PrimaryButton("تحديث القائمة", Modifier.fillMaxWidth()) { reloadQueue() }
                    }
                }
            }
            else -> {
                val word = queue[index]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بطاقة ${index + 1} من ${queue.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(18.dp), color = SunTint, border = BorderStroke(1.dp, Night)) {
                            Text("★ $remembered", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Night, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(queue.size) { segment ->
                            Box(
                                Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                                    .background(if (segment < index) Teal else if (segment == index) Night else Line)
                            )
                        }
                    }
                }

                AnswerFeedbackMotion(
                    correct = ratingFeedbackCorrect,
                    trigger = ratingFeedbackId,
                    enabled = store.feedbackAnimations
                ) {
                val flashcardShape = RoundedCornerShape(26.dp)
                Surface(
                    shape = flashcardShape, color = Color.White,
                    border = BorderStroke(2.dp, Night),
                    modifier = Modifier.fillMaxWidth().hardShadow(flashcardShape, 5.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("فكّر في المعنى قبل ما تكشف الإجابة", fontSize = 14.sp, color = Muted)
                        En(word.en, if (word.en.length > 18) 30.sp else 38.sp, Night, FontWeight.ExtraBold, TextAlign.Center, Modifier.fillMaxWidth())
                        if (word.ipa.isNotEmpty()) En(word.ipa, 17.sp, Muted, FontWeight.Normal, TextAlign.Center, Modifier.fillMaxWidth())
                        SpeakButton(50.dp) { speak(word.en) }

                        if (revealed) {
                            if (ratingFeedbackId > 0 && ratingFeedbackCorrect && store.feedbackAnimations) CelebrationBurst()
                            Surface(shape = RoundedCornerShape(18.dp), color = Tint, border = BorderStroke(1.5.dp, Night), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(word.ar, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TealDark, textAlign = TextAlign.Center)
                                    if (word.example.isNotEmpty()) {
                                        Ltr { Text(word.example, fontSize = 16.sp, color = Night, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                                    }
                                    if (word.exampleAr.isNotEmpty()) Text(word.exampleAr, fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                }

                fun rate(rating: ReviewRating) {
                    if (ratingPending) return
                    ratingPending = true
                    ratingFeedbackCorrect = rating != ReviewRating.Again
                    ratingFeedbackId++
                    when (rating) {
                        ReviewRating.Again -> {
                            answerFeedback(false)
                            if (store.feedbackHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            store.recordReview(word.id, rating)
                        }
                        ReviewRating.Hard -> {
                            answerFeedback(true)
                            if (store.feedbackHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            store.recordReview(word.id, rating)
                        }
                        ReviewRating.Good, ReviewRating.Easy -> {
                            answerFeedback(true)
                            if (store.feedbackHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            store.recordReview(word.id, rating)
                            remembered++
                        }
                    }
                    completed++
                    scope.launch {
                        if (store.feedbackAnimations) delay(320)
                        index++
                        revealed = false
                        ratingPending = false
                        ratingFeedbackId = 0
                    }
                }

                if (!revealed) {
                    PrimaryButton("اكشف المعنى", Modifier.fillMaxWidth()) { revealed = true }
                } else {
                    Text("قيّم مدى سهولة تذكرك للكلمة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PrimaryButton("نسيتها", Modifier.weight(1f), container = MissTint) { rate(ReviewRating.Again) }
                        PrimaryButton("صعبة", Modifier.weight(1f), container = Sun) { rate(ReviewRating.Hard) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PrimaryButton("كويسة", Modifier.weight(1f), container = Teal) { rate(ReviewRating.Good) }
                        PrimaryButton("سهلة", Modifier.weight(1f), container = SkyTint) { rate(ReviewRating.Easy) }
                    }
                    Text("تقييمك بيحدد ميعاد المراجعة الجاية تلقائيًا.", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
