@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordpop.app

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar

private val SettingsInk = Color(0xFF12201C)
private val SettingsPaper = Color(0xFFFFF8EC)
private val SettingsMuted = Color(0xFF4A5C55)
private val SettingsLilac = Color(0xFFB9A3FF)
private val SettingsMint = Color(0xFF35D9B0)
private val SettingsSun = Color(0xFFFFC93C)
private val SettingsCoral = Color(0xFFFF7A66)
private val SettingsSky = Color(0xFF62BDFF)

@Composable
fun SettingsSwipeScreen(
    store: SettingsStore,
    onOpenAdmin: () -> Unit
) {
    val ctx = LocalContext.current
    val visibleCardIds = SettingsCardsSync.visibleCardIds
    val pagerState = rememberPagerState(pageCount = { visibleCardIds.size })
    LaunchedEffect(visibleCardIds.size) {
        if (visibleCardIds.isNotEmpty() && pagerState.currentPage >= visibleCardIds.size) {
            pagerState.scrollToPage(visibleCardIds.lastIndex)
        }
    }
    val scope = rememberCoroutineScope()
    val speak = rememberSpeaker()

    var pausedUntil by remember { mutableStateOf(store.pausedUntil) }
    var interval by remember { mutableIntStateOf(store.intervalMin) }
    var startHour by remember { mutableIntStateOf(store.startHour) }
    var endHour by remember { mutableIntStateOf(store.endHour) }
    var contentType by remember { mutableIntStateOf(store.content) }
    var level by remember { mutableIntStateOf(store.level) }
    var displaySec by remember { mutableIntStateOf(store.displaySec) }
    var sound by remember { mutableStateOf(store.sound) }
    var autoSpeak by remember { mutableStateOf(store.autoSpeak) }
    var feedbackSound by remember { mutableStateOf(store.feedbackSound) }
    var feedbackAnimations by remember { mutableStateOf(store.feedbackAnimations) }
    var feedbackHaptics by remember { mutableStateOf(store.feedbackHaptics) }
    var accent by remember { mutableStateOf(store.speechAccent) }
    var voice by remember { mutableStateOf(store.speechVoice) }
    var bannerPos by remember { mutableIntStateOf(store.bannerPos) }
    var bannerSize by remember { mutableIntStateOf(store.bannerSize) }
    var opacity by remember { mutableFloatStateOf(store.opacity.toFloat()) }
    var bannerTheme by remember { mutableIntStateOf(store.bannerTheme) }
    var actions by remember { mutableStateOf(store.actions) }
    var skipCalls by remember { mutableStateOf(store.skipCalls) }
    var skipGames by remember { mutableStateOf(store.skipGames) }
    var usageGranted by remember { mutableStateOf(Focus.hasUsageAccess(ctx)) }

    DisposableEffect(ctx) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageGranted = Focus.hasUsageAccess(ctx)
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    fun refreshOverlay() {
        if (store.enabled) sendToService(ctx, OverlayService.ACTION_START)
    }

    fun choosePause(minutes: Int?) {
        val until = if (minutes != null) {
            System.currentTimeMillis() + minutes * 60_000L
        } else {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }
        store.pausedUntil = until
        pausedUntil = until
    }

    val accentColors = listOf(
        SettingsLilac, SettingsMint, SettingsSun, SettingsCoral, SettingsSky, SettingsMint,
        SettingsLilac, SettingsMint, SettingsSun, SettingsCoral, SettingsSky, SettingsMint
    )

    Column(
        Modifier.fillMaxSize()
            .background(SettingsPaper)
            .drawBehind {
                // Match the supplied settings identity: warm paper with a subtle dotted texture.
                val step = 16.dp.toPx()
                var y = 4.dp.toPx()
                while (y < size.height) {
                    var x = 4.dp.toPx()
                    while (x < size.width) {
                        drawCircle(
                            color = SettingsInk.copy(alpha = 0.075f),
                            radius = 1.15.dp.toPx(),
                            center = Offset(x, y)
                        )
                        x += step
                    }
                    y += step
                }
            }
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("الإعدادات", fontSize = 34.sp, fontFamily = Display, color = SettingsInk)
                Text("كل إعداد ليه كارت، اسحب للي بعده", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SettingsMuted)
            }
            Surface(
                color = SettingsInk,
                shape = CircleShape,
                shadowElevation = 3.dp,
                border = BorderStroke(2.dp, SettingsMint)
            ) {
                Text(
                    ((pagerState.currentPage + 1).coerceAtMost(visibleCardIds.size)).toString().padStart(2, '0') + " / " + visibleCardIds.size,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontFamily = Display,
                    color = SettingsPaper
                )
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = minOf(318.dp, maxWidth - 40.dp)
            val sidePadding = (maxWidth - cardWidth) / 2
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 14.dp
            ) { page ->
                val cardIndex = SettingsCardsSync.cardIds.indexOf(visibleCardIds[page]).coerceAtLeast(0)
                SettingsCardFrame(
                    number = cardIndex + 1,
                    title = listOf(
                        "خد استراحة", "كل قد إيه؟", "ساعات الظهور", "نوع الكلمات",
                        "المستوى", "شكل الظهور", "نطق الكلمات", "صوت طبيعي EasyVoice AI",
                        "مكان النافذة", "شكل النافذة", "عدم الإزعاج", "صيانة"
                    )[cardIndex],
                    description = listOf(
                        "اختار مدة راحة وتقدر تستأنف في أي وقت.",
                        "كل قد إيه تظهرلك كلمة؟",
                        "بره الساعات دي مش هتظهرلك كلمات.",
                        "اختار اللي تحب تتعلمه.",
                        "اختار مستوى الكلمات المناسب ليك.",
                        "مدة ظهور الشريط وصوته وتأثيرات التعلّم.",
                        "اختار اللكنة اللي تحب تسمع بيها الكلمات والأمثلة.",
                        "اختار صوتاً طبيعياً من أصوات EasyVoice.",
                        "اسحب النافذة بصباعك لأي مكان على الشاشة.",
                        "الحجم والشفافية واللون والأزرار.",
                        "قرّر إمتى النافذة متظهرش.",
                        "لو الكلمات بتتأخر أو عايز تتحكم في التطبيق."
                    )[cardIndex],
                    accent = accentColors[cardIndex],
                    modifier = Modifier.fillMaxHeight()
                ) {
                    when (cardIndex) {
                        0 -> {
                            val paused = pausedUntil > System.currentTimeMillis()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30 to "30 دقيقة", 60 to "ساعة", 180 to "3 ساعات").forEach { (minutes, label) ->
                                    SettingsChoice(label, paused && pausedUntil - System.currentTimeMillis() <= minutes * 60_000L + 5_000L, SettingsLilac) {
                                        choosePause(minutes)
                                    }
                                }
                                SettingsChoice("لحد الصبح", false, SettingsLilac) { choosePause(null) }
                            }
                            Surface(
                                color = Color(0xFFEFE9FF),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, SettingsInk)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        if (paused) "الكلمات متوقفة مؤقتاً لحد " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(pausedUntil))
                                        else "الكلمات شغالة دلوقتي.",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SettingsInk
                                    )
                                    if (paused) {
                                        SettingsAction("كمّل دلوقتي", true) {
                                            store.pausedUntil = 0L
                                            pausedUntil = 0L
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            val span = when {
                                startHour == endHour -> 24
                                endHour > startHour -> endHour - startHour
                                else -> 24 - startHour + endHour
                            }
                            val perDay = maxOf(1, (span * 60) / interval)
                            Surface(color = SettingsInk, shape = RoundedCornerShape(18.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(perDay.toString(), fontSize = 42.sp, fontFamily = Display, color = SettingsMint)
                                    Text("كلمة تقريباً في اليوم، حسب ساعات الظهور", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsPaper)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 15, 30, 60, 120, 180, 360).forEach { minutes ->
                                    SettingsChoice(intervalLabel(minutes), interval == minutes, SettingsMint) {
                                        interval = minutes
                                        store.intervalMin = minutes
                                        refreshOverlay()
                                    }
                                }
                            }
                        }
                        2 -> {
                            SettingsHourStepper("من", startHour, onChange = {
                                startHour = it
                                store.startHour = it
                                refreshOverlay()
                            })
                            SettingsHourStepper("لحد", endHour, onChange = {
                                endHour = it
                                store.endHour = it
                                refreshOverlay()
                            })
                            Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, SettingsInk, RoundedCornerShape(14.dp))) {
                                val span = when {
                                    startHour == endHour -> 24
                                    endHour > startHour -> endHour - startHour
                                    else -> 24 - startHour + endHour
                                }
                                drawRect(Color.White)
                                val start = startHour / 24f
                                val width = span / 24f
                                drawRect(SettingsSun, topLeft = Offset(size.width * start, 0f), size = androidx.compose.ui.geometry.Size(size.width * width, size.height))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("12 ص", "6 ص", "12 م", "6 م", "12 ص").forEach {
                                    Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SettingsMuted)
                                }
                            }
                        }
                        3 -> {
                            listOf(
                                Triple(0, "كلمات وعبارات", "الاتنين مع بعض"),
                                Triple(1, "كلمات بس", "كلمة كلمة"),
                                Triple(2, "عبارات بس", "جمل جاهزة")
                            ).forEach { (value, label, sub) ->
                                SettingsTile(label, sub, contentType == value, SettingsCoral, Modifier.fillMaxWidth()) {
                                    contentType = value
                                    store.content = value
                                }
                            }
                        }
                        4 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "الكل", 1 to "أساسي").forEach { (value, label) ->
                                    SettingsTile(label, if (value == 0) "كل المستويات" else "كلمات يومية", level == value, SettingsSky, Modifier.weight(1f)) {
                                        level = value
                                        store.level = value
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2 to "B1", 3 to "B2").forEach { (value, label) ->
                                    SettingsTile(label, if (value == 2) "متوسط" else "فوق المتوسط", level == value, SettingsSky, Modifier.weight(1f)) {
                                        level = value
                                        store.level = value
                                    }
                                }
                            }
                            Text("B1 وB2 كلمات أصعب للمستوى المتوسط وفوق المتوسط.", fontSize = 13.sp, color = SettingsMuted)
                        }
                        5 -> {
                            Text("مدة ظهور الشريط", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(6, 10, 15, 25).forEach { seconds ->
                                    SettingsChoice(seconds.toString() + " ثانية", displaySec == seconds, SettingsLilac) {
                                        displaySec = seconds
                                        store.displaySec = seconds
                                    }
                                }
                            }
                            SettingsSwitch("صوت تنبيه لما النافذة تظهر", sound, SettingsLilac) {
                                sound = it
                                store.sound = it
                            }
                            SettingsSwitch("انطق الكلمة تلقائيًا أول ما تظهر", autoSpeak, SettingsLilac) {
                                autoSpeak = it
                                store.autoSpeak = it
                            }
                            SettingsDivider()
                            Text("تأثيرات التعلّم", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = SettingsInk)
                            SettingsSwitch("صوت الإجابة الصح والغلط", feedbackSound, SettingsMint) {
                                feedbackSound = it
                                store.feedbackSound = it
                            }
                            SettingsSwitch("أنيميشن الفوز والخطأ", feedbackAnimations, SettingsMint) {
                                feedbackAnimations = it
                                store.feedbackAnimations = it
                            }
                            SettingsSwitch("الاهتزاز عند الإجابة", feedbackHaptics, SettingsMint) {
                                feedbackHaptics = it
                                store.feedbackHaptics = it
                            }
                        }
                        6 -> {
                            Text("اختار اللكنة الإنجليزية اللي تحب تسمع بيها الكلمات والأمثلة.", fontSize = 14.sp, color = SettingsMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    SpeechAccents.AMERICAN to "أمريكي",
                                    SpeechAccents.BRITISH to "بريطاني",
                                    SpeechAccents.AUSTRALIAN to "أسترالي"
                                ).forEach { (id, label) ->
                                    SettingsTile(label, when (id) {
                                        SpeechAccents.BRITISH -> "UK"
                                        SpeechAccents.AUSTRALIAN -> "AU"
                                        else -> "US"
                                    }, accent == id, SettingsMint, Modifier.weight(1f)) {
                                        accent = id
                                        store.speechAccent = id
                                    }
                                }
                            }
                            SettingsAction("اسمع عينة من اللكنة", true) {
                                speak("Hello! How are you today? It is a beautiful day to learn something new.")
                            }
                            SettingsAction("تنزيل أو تحديث الأصوات", false) {
                                try {
                                    ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                                } catch (_: Exception) { }
                            }
                            Text("توفر الأصوات وجودتها بيختلف حسب محرك النطق والجهاز.", fontSize = 12.sp, color = SettingsMuted)
                        }
                        7 -> {
                            Text("اختار صوتاً من أصوات EasyVoice المجانية.", fontSize = 14.sp, color = SettingsMuted)
                            val voices = listOf(
                                "af_nova" to "Nova · US",
                                "af_heart" to "Heart · US",
                                "af_bella" to "Bella · US",
                                "bf_emma" to "Emma · UK",
                                "bm_daniel" to "Daniel · UK"
                            )
                            voices.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { (id, label) ->
                                        SettingsChoice(label, voice == id, SettingsMint, Modifier.weight(1f)) {
                                            voice = id
                                            store.speechVoice = id
                                            val selectedAccent = if (id.startsWith("b")) SpeechAccents.BRITISH else SpeechAccents.AMERICAN
                                            accent = selectedAccent
                                            store.speechAccent = selectedAccent
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Text(
                                if (AiConfigSync.workerUrl.isBlank()) {
                                    "رابط خدمة الصوت والذكاء الاصطناعي يتم ضبطه من WordPop Control."
                                } else {
                                    "رابط خدمة الصوت والذكاء الاصطناعي متصل من WordPop Control."
                                },
                                fontSize = 12.sp,
                                color = Muted
                            )
                            Text(
                                if (AiConfigSync.workerUrl.isBlank()) "الصوت الحالي: Android TTS. يتم ضبط رابط Worker من WordPop Control."
                                else "EasyVoice مفعّل من WordPop Control. لو فشل الاتصال، التطبيق يرجع تلقائياً إلى Android TTS.",
                                fontSize = 12.sp,
                                color = SettingsMuted
                            )
                            SettingsAction("جرّب صوت EasyVoice", true) {
                                speak("Hello! Welcome to WordPop. Let's learn a new word together.")
                            }
                        }
                        8 -> {
                            Text("مكان ظهور النافذة العائمة.", fontSize = 14.sp, color = SettingsMuted)
                            Column(
                                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFE9E2D2)).border(2.dp, SettingsInk, RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                val previewAlignment = when (bannerPos) {
                                    1 -> Alignment.Center
                                    2 -> Alignment.BottomCenter
                                    else -> Alignment.TopCenter
                                }
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = previewAlignment) {
                                    Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp)).background(SettingsSun).border(2.dp, SettingsInk, RoundedCornerShape(9.dp)))
                                }
                                Text("معاينة مكان النافذة", modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 11.sp, color = SettingsMuted)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "أعلى الشاشة", 1 to "نص الشاشة", 2 to "أسفل الشاشة").forEach { (value, label) ->
                                    SettingsChoice(label, bannerPos == value, SettingsSun, Modifier.fillMaxWidth()) {
                                        bannerPos = value
                                        store.bannerPos = value
                                        store.bannerX = 0
                                        store.bannerY = -1
                                    }
                                }
                            }
                            SettingsAction("رجّع النافذة لمكانها الأصلي", false) {
                                bannerPos = 0
                                store.bannerPos = 0
                                store.bannerX = 0
                                store.bannerY = -1
                            }
                        }
                        9 -> {
                            val theme = BannerThemes.get(bannerTheme)
                            val previewBg = Color(theme.bg).copy(alpha = opacity / 100f)
                            val previewText = Color(theme.text)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = previewBg,
                                border = BorderStroke(2.dp, SettingsInk)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    En("Serendipity", when (bannerSize) { 0 -> 18.sp; 2 -> 28.sp; else -> 22.sp }, previewText, FontWeight.ExtraBold, TextAlign.Start, Modifier.fillMaxWidth())
                                    Text("صدفة سعيدة", fontSize = when (bannerSize) { 0 -> 11.sp; 2 -> 17.sp; else -> 14.sp }, color = previewText)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("مفضلة" to "fav", "أعرفها" to "know", "تخطي" to "skip", "مثال" to "example", "لسه" to "dont", "إيقاف" to "pause").forEach { (label, key) ->
                                            if (key in actions) {
                                                Surface(color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.dp, previewText)) {
                                                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = previewText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Text("الحجم", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "صغير", 1 to "وسط", 2 to "كبير").forEach { (value, label) ->
                                    SettingsChoice(label, bannerSize == value, SettingsCoral, Modifier.weight(1f)) {
                                        bannerSize = value
                                        store.bannerSize = value
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الشفافية", modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                                Text(opacity.toInt().toString() + "%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                            }
                            Slider(
                                value = opacity,
                                onValueChange = { opacity = it },
                                onValueChangeFinished = { store.opacity = opacity.toInt() },
                                valueRange = 40f..100f,
                                steps = 11,
                                colors = SliderDefaults.colors(thumbColor = SettingsInk, activeTrackColor = SettingsInk, inactiveTrackColor = Color(0xFFE7E1D4))
                            )
                            Text("اللون", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BannerThemes.all.forEachIndexed { index, item ->
                                    SettingsChoice(item.name, bannerTheme == index, Color(item.accent), Modifier.weight(0.30f)) {
                                        bannerTheme = index
                                        store.bannerTheme = index
                                    }
                                }
                            }
                            Text("الأزرار اللي تظهر في النافذة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("fav" to "مفضلة", "know" to "أعرفها", "skip" to "تخطي", "dont" to "لسه", "example" to "مثال", "pause" to "إيقاف").forEach { (key, label) ->
                                    SettingsChoice(label, key in actions, SettingsCoral) {
                                        actions = if (key in actions) actions - key else actions + key
                                        store.actions = actions
                                    }
                                }
                            }
                            Text("الأساسي: مفضلة وأعرفها وتخطي. فعّل الباقي لو عايزه.", fontSize = 12.sp, color = SettingsMuted)
                        }
                        10 -> {
                            SettingsSwitch("ماتظهرش أثناء المكالمات", skipCalls, SettingsSky) {
                                skipCalls = it
                                store.skipCalls = it
                            }
                            SettingsSwitch("ماتظهرش في الألعاب والفيديوهات", skipGames, SettingsSky) {
                                skipGames = it
                                store.skipGames = it
                            }
                            if (skipGames && !usageGranted) {
                                Text("عشان WordPop يعرف إنك في لعبة أو فيديو، لازم تسمح له بالوصول لبيانات الاستخدام.", fontSize = 13.sp, color = SettingsMuted)
                                SettingsAction("افتح الإعدادات واسمح", false) {
                                    try {
                                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        11 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("لو الكلمات بتتأخر", fontSize = 20.sp, fontFamily = Display, color = SettingsInk)
                                Text("بعض الموبايلات بتوقف التطبيقات في الخلفية. خلّي ووردبوب «بدون قيود» في إعدادات البطارية.", fontSize = 13.sp, color = SettingsMuted)
                                SettingsAction("افتح إعدادات البطارية", false) {
                                    if (!requestBatteryExemption(ctx)) {
                                        try {
                                            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            SettingsDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("حالة مزامنة الكلمات", fontSize = 20.sp, fontFamily = Display, color = SettingsInk)
                                val syncStatus = WordCloudSync.status
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (syncStatus.startsWith("تم تحميل")) Color(0xFF1FAE7F) else SettingsMuted))
                                    Text(syncStatus, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (syncStatus.startsWith("تم تحميل")) Color(0xFF16866C) else SettingsMuted)
                                }
                            }
                            SettingsDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("حالة بطاقات الإعدادات", fontSize = 16.sp, fontFamily = Display, color = SettingsInk)
                                Text(SettingsCardsSync.status, fontSize = 12.sp, color = SettingsMuted)
                            }
                            SettingsDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("لوحة التحكم", fontSize = 20.sp, fontFamily = Display, color = SettingsInk)
                                Text("إدارة كل التطبيق باسم مستخدم ورقم سري.", fontSize = 13.sp, color = SettingsMuted)
                                SettingsAction("افتح لوحة التحكم", true, onOpenAdmin)
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(26.dp).padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(visibleCardIds.size) { index ->
                Box(
                    Modifier.padding(horizontal = 3.dp).width(if (pagerState.currentPage == index) 24.dp else 8.dp)
                        .height(8.dp).clip(CircleShape)
                        .background(if (pagerState.currentPage == index) SettingsInk else SettingsInk.copy(alpha = 0.25f))
                        .clickable(role = Role.Button) { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }
    }
}

@Composable
private fun SettingsCardFrame(
    number: Int,
    title: String,
    description: String,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val outer = RoundedCornerShape(30.dp)
    val inner = RoundedCornerShape(22.dp)
    Box(
        modifier.shadow(4.dp, outer).clip(outer).background(accent).border(3.dp, SettingsInk, outer)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var y = 8.dp.toPx()
            while (y < size.height) {
                var x = 8.dp.toPx()
                while (x < size.width) {
                    drawCircle(SettingsInk.copy(alpha = 0.12f), radius = 1.5.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(58.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(SettingsInk),
                    contentAlignment = Alignment.Center
                ) {
                    Text(number.toString().padStart(2, '0'), fontSize = 27.sp, fontFamily = Display, color = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 27.sp, fontFamily = Display, color = SettingsInk, lineHeight = 31.sp)
                    Text(description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SettingsInk, lineHeight = 17.sp)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(inner).background(SettingsPaper)
                    .border(3.dp, SettingsInk, inner).padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (selected) SettingsInk else Color.White,
        border = BorderStroke(2.dp, SettingsInk),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) SettingsPaper else SettingsInk, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (selected) SettingsInk else Color.White,
        border = BorderStroke(2.dp, SettingsInk),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) SettingsPaper else SettingsInk)
            Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) SettingsPaper.copy(alpha = 0.8f) else SettingsMuted)
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SettingsInk,
                checkedTrackColor = accent,
                checkedBorderColor = SettingsInk,
                uncheckedThumbColor = SettingsInk,
                uncheckedTrackColor = Color(0xFFE7E1D4),
                uncheckedBorderColor = SettingsInk
            )
        )
    }
}

@Composable
private fun SettingsAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (primary) SettingsInk else Color.White,
        border = BorderStroke(2.dp, SettingsInk),
        shadowElevation = 2.dp
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (primary) SettingsPaper else SettingsInk, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsHourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 19.sp, fontFamily = Display, color = SettingsInk)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsRoundButton("−", "قلل ساعة " + label) { onChange((value + 23) % 24) }
            Text(hourLabel(value), modifier = Modifier.width(62.dp), fontSize = 22.sp, fontFamily = Display, color = SettingsInk, textAlign = TextAlign.Center)
            SettingsRoundButton("+", "زود ساعة " + label) { onChange((value + 1) % 24) }
        }
    }
}

@Composable
private fun SettingsRoundButton(text: String, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(2.dp, SettingsInk)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = SettingsInk)
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(SettingsInk.copy(alpha = 0.14f)))
}
