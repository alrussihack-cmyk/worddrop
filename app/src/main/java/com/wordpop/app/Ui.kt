package com.wordpop.app

import android.content.Intent
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
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

// ---------- brand ----------

val Teal = Color(0xFF2FE0B5)      // Mint: primary fill (text on it is Night)
val TealDark = Color(0xFF0A6F5C)  // Mint Deep: accent text on white / tint
val Night = Color(0xFF12182B)       // Ink: text and borders
val Mint = Color(0xFF2FE0B5)
val Cloud = Color(0xFFF6F8FC)       // Paper: screen background
val Muted = Color(0xFF5A6278)
val Line = Color(0xFFDCE1EE)
val SunTint = Color(0xFFFFF1C9)
val Spark = Color(0xFFFFC93C)       // Sun
val Sun = Color(0xFFFFC93C)
val Coral = Color(0xFFFF5C5C)
val Sky = Color(0xFF59B8FF)
val SkyTint = Color(0xFFDDEFFF)
val Tint = Color(0xFFD8F8EE)        // Mint tint
val Danger = Color(0xFFC62F3A)
val MissTint = Color(0xFFFFE3E3)

@Composable
fun WordPopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Night,
            background = Cloud,
            surface = Color.White,
            onSurface = Night
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
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 0.dp else 4.dp, tween(80), label = "primaryShadow")
    val offset by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(80), label = "primaryPress")
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 52.dp)
            .graphicsLayer { translationY = offset.toPx() }
            .hardShadow(shape, depth),
        shape = shape,
        border = BorderStroke(2.5.dp, Night),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Night)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 0.dp else 4.dp, tween(80), label = "secondaryShadow")
    val offset by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(80), label = "secondaryPress")
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 52.dp)
            .graphicsLayer { translationY = offset.toPx() }
            .hardShadow(shape, depth),
        shape = shape,
        border = BorderStroke(2.5.dp, Night),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Night)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .hardShadow(shape, 3.dp)
            .clip(shape)
            .background(if (selected) Teal else Color.White)
            .border(2.dp, Night, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Night,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 5.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(22.dp), 5.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 22.sp, fontFamily = Display, color = Night)
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
            .border(2.5.dp, Night, CircleShape)
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
            .clip(CircleShape)
            .background(Cloud)
            .border(2.dp, Night, CircleShape)
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
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
        Ltr {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepBtn("−", "نقّص ساعة") { onChange((value + 23) % 24) }
                Text(
                    hourLabel(value),
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Night
                )
                StepBtn("+", "زوّد ساعة") { onChange((value + 1) % 24) }
            }
        }
    }
}

/** Text-to-speech for the in-app screens. Returns a function that speaks English text. */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                holder[0]?.language = Locale.US
                ready = true
            }
        }
        holder[0] = engine
        onDispose {
            engine.shutdown()
            holder[0] = null
        }
    }
    return { text -> if (ready) holder[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop-ui") }
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
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(Night))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
            Page.values().forEach { p ->
                val selected = p == page
                val tint = Night
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab, onClick = { onSelect(p) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val pill = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(32.dp)
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
                        p.title, fontSize = 12.sp, color = tint,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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
            .size(44.dp)
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
    val learned = store.learned.size
    val total = wordsVersion.let { WordRepo.all.size }
    val reviewDueCount = WordRepo.reviewDueCount(store)
    val reviewNewCount = WordRepo.reviewNewCount(store)
    val customCount = wordsVersion.let { WordRepo.customWords.size }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importMsg = try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                val r = WordRepo.importText(ctx, text)
                wordsVersion++
                buildString {
                    append("تمت إضافة ${r.added} كلمة")
                    if (r.duplicates > 0) append("، وتخطّيت ${r.duplicates} متكررة")
                    if (r.skipped > 0) append("، و${r.skipped} سطر ناقص")
                    append(".")
                    if (r.added == 0 && r.duplicates == 0) append(" اتأكد إن كل سطر فيه: الكلمة، ثم الترجمة.")
                    if ('\uFFFD' in text) append(" الحروف العربي باينة بايظة؟ احفظ الملف بصيغة UTF-8.")
                }
            } catch (e: Exception) {
                "ماقدرتش أقرا الملف. جرّب ملف نصي (txt أو csv)."
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page == Page.Home) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(52.dp))
                Column {
                    Text("ووردبوب", fontSize = 30.sp, fontFamily = Display, color = Night)
                    Text("كلمة كل شوية.. وإنجليزيتك بتكبر", fontSize = 15.sp, color = Muted)
                }
            } else {
                Text(page.title, fontSize = 24.sp, fontFamily = Display, color = Night)
            }
        }

        if (page == Page.Home) {
        Surface(shape = RoundedCornerShape(16.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الكلمات العائمة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Night)
                    Text(
                        if (enabled) "شغّال: ${intervalLabel(interval)}، من ${hourLabel(startH)} لحد ${hourLabel(endH)}" else "متوقف",
                        fontSize = 15.sp,
                        color = Muted
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { toggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Mint,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFC3D3CF),
                        uncheckedBorderColor = Color(0xFF9FB5B0)
                    )
                )
            }
        }

        if (!overlayGranted) {
            Surface(shape = RoundedCornerShape(20.dp), color = SunTint, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "لازم تسمح للتطبيق يظهر فوق التطبيقات التانية، من غير كده الكلمات مش هتظهر.",
                        fontSize = 16.sp,
                        color = Night
                    )
                    PrimaryButton("افتح الإعدادات واسمح", Modifier.fillMaxWidth(), container = Sun) { onRequestOverlay() }
                }
            }
        }

        if (!exactAlarmGranted) {
            Surface(shape = RoundedCornerShape(20.dp), color = SunTint, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "عشان الكلمات تظهر تلقائيًا في الخلفية وفي مواعيدها، اسمح للتطبيق بالتنبيهات الدقيقة.",
                        fontSize = 16.sp,
                        color = Night
                    )
                    PrimaryButton("السماح بالتنبيهات الدقيقة", Modifier.fillMaxWidth(), container = Sun) { onRequestExactAlarm() }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuickAction("جرّب كلمة", R.drawable.ic_volume, true) {
                if (overlayGranted) sendToService(ctx, OverlayService.ACTION_SHOW_NOW) else onRequestOverlay()
            }
            QuickAction("ابدأ أتعلّم", R.drawable.ic_play, true) { onStartLearning() }
            QuickAction("المفضلة", R.drawable.ic_star_outline, false) { onNavigate(Page.Favorites) }
            QuickAction("كلماتي", R.drawable.ic_list, false) { onNavigate(Page.MyWords) }
        }

        Section("مراجعة اليوم") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (reviewDueCount + reviewNewCount > 0) "عندك ${reviewDueCount + reviewNewCount} كلمة جاهزة"
                        else "مراجعتك مكتملة حاليًا",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Night
                    )
                    Text(
                        "$reviewDueCount مستحقة · $reviewNewCount جديدة",
                        fontSize = 14.sp,
                        color = Muted
                    )
                }
                Boby(if (reviewDueCount > 0) Mood.Think else Mood.Happy, 54.dp)
            }
            PrimaryButton("ابدأ المراجعة الذكية", Modifier.fillMaxWidth()) {
                onNavigate(Page.Review)
            }
            Text("5 كلمات جديدة كحد أقصى يوميًا، والمواعيد بتتحدد حسب إجاباتك.", fontSize = 13.sp, color = Muted)
        }

        Section("إيقاف مؤقت") {
            val paused = pausedUntil > System.currentTimeMillis()
            val untilText = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(pausedUntil))
            if (paused) Boby(Mood.Sleep, 64.dp)
            Text(
                if (paused) "الكلمات متوقفة لحد $untilText" else "الكلمات شغالة",
                fontSize = 16.sp,
                color = if (paused) Muted else Night
            )
            ChipRow {
                listOf(30 to "30 دقيقة", 60 to "ساعة", 180 to "3 ساعات").forEach { (m, label) ->
                    Chip(label, false) {
                        val t = System.currentTimeMillis() + m * 60_000L
                        store.pausedUntil = t
                        pausedUntil = t
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
                SecondaryButton("استئناف", Modifier.fillMaxWidth()) {
                    store.pausedUntil = 0L
                    pausedUntil = 0L
                }
            }
        }

        Section("تقدّمك") {
            Text("حفظت $learned من $total", fontSize = 17.sp, color = Night)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else learned.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = Mint,
                trackColor = Line
            )
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
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
        Section("كلماتك الخاصة") {
            Text(
                "استورد قايمة من ملف txt أو csv. كل سطر: الكلمة، الترجمة، (مثال اختياري)، (ترجمة المثال اختيارية). مثال:",
                fontSize = 15.sp,
                color = Muted
            )
            Ltr {
                Text("lifeguard, منقذ", fontSize = 15.sp, color = Night)
            }
            SecondaryButton("استيراد قايمة من ملف", Modifier.fillMaxWidth()) {
                importLauncher.launch(arrayOf("*/*"))
            }
            importMsg?.let { Text(it, fontSize = 15.sp, color = TealDark) }
            if (customCount > 0) {
                Text("عندك $customCount كلمة مستوردة.", fontSize = 15.sp, color = Muted)
                SecondaryButton("امسح الكلمات المستوردة", Modifier.fillMaxWidth()) {
                    WordRepo.clearCustom(ctx)
                    wordsVersion++
                    importMsg = "اتمسحت الكلمات المستوردة."
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "رجوع", onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "رجوع", tint = Night)
            }
            Text(
                "تعلّم كلمة",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Night,
                modifier = Modifier.weight(1f)
            )
            Text("حفظت $learnedCount من ${WordRepo.all.size}", fontSize = 14.sp, color = Muted)
        }

        if (step == 0) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StarButton(favW) { favW = store.toggleFavWord(word.id) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Boby(Mood.Wow, 46.dp)
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
                            word.en.length > 22 -> 28.sp
                            word.en.length > 14 -> 34.sp
                            else -> 44.sp
                        },
                        Night, FontWeight.ExtraBold, TextAlign.Center,
                        Modifier.fillMaxWidth()
                    )
                    if (word.ipa.isNotEmpty()) {
                        En(word.ipa, 20.sp, Muted, FontWeight.Normal, TextAlign.Center, Modifier.fillMaxWidth())
                    }
                    SpeakButton(56.dp) { speak(word.en) }
                    Text(
                        word.ar,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Night,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (word.example.isNotEmpty()) Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(16.dp), 4.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("مثال", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark, modifier = Modifier.weight(1f))
                        StarButton(favS) { favS = store.toggleFavSentence(word.id) }
                    }
                    Ltr {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(word.example, fontSize = 20.sp, color = Night, modifier = Modifier.weight(1f))
                            SpeakButton(44.dp) { speak(word.example) }
                        }
                    }
                    if (word.exampleAr.isNotEmpty()) Text(word.exampleAr, fontSize = 18.sp, color = Muted)
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
                onNewWord = { next() }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("المفضلة", fontSize = 24.sp, fontFamily = Display, color = Night)
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
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Boby(Mood.Happy, 72.dp)
        Text(text, fontSize = 16.sp, color = Muted, modifier = Modifier.weight(1f))
    }
}

@Composable
fun FavCard(en: String, ar: String, onSpeak: () -> Unit, onRemove: () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
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
    onNewWord: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
    val total = questions.size

    if (index >= total) {
        QuizResult(
            score = score,
            total = total,
            best = best,
            wrong = wrong.toList(),
            speak = speak,
            onAgain = { round++ },
            onNewWord = onNewWord
        )
        return
    }

    val q = questions[index]
    val answered = picked != null
    LaunchedEffect(round, index) {
        if (q.type == QType.Listen) speak(q.word.en)
    }

    // progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("سؤال ${index + 1} من $total", fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f))
            if (streak >= 2) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SunTint)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("🔥 $streak ورا بعض", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                }
            }
        }
        LinearProgressIndicator(
            progress = { (index + if (answered) 1 else 0).toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Night, RoundedCornerShape(6.dp)),
            color = Teal,
            trackColor = Line
        )
    }

    // question card
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (q.type) {
                QType.Meaning -> {
                    Text("إيه معنى الكلمة دي؟", fontSize = 16.sp, color = Muted)
                    En(
                        q.word.en,
                        if (q.word.en.length > 14) 28.sp else 36.sp,
                        Night, FontWeight.ExtraBold, TextAlign.Center, Modifier.fillMaxWidth()
                    )
                    SpeakButton(48.dp) { speak(q.word.en) }
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
                    SpeakButton(72.dp) { speak(q.word.en) }
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
        val bg by animateColorAsState(
            when {
                !answered -> Color.White
                isCorrect -> Teal
                isPicked -> MissTint
                else -> Color.White
            },
            tween(220), label = "optBg"
        )
        val borderColor by animateColorAsState(
            when {
                !answered -> Line
                isCorrect -> Night
                isPicked -> Danger
                else -> Night
            },
            tween(220), label = "optBorder"
        )
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .hardShadow(shape, 4.dp)
                .clip(shape)
                .background(bg)
                .border(2.5.dp, borderColor, shape)
                .clickable(enabled = !answered, role = Role.Button) {
                    picked = opt
                    if (isCorrect) {
                        score++
                        streak++
                        if (streak > best) best = streak
                        store.markLearned(q.word.id)
                        store.hard = store.hard - q.word.id
                        if (store.reviewStates().containsKey(q.word.id)) {
                            store.recordReview(q.word.id, ReviewRating.Good)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else {
                        streak = 0
                        wrong.add(q.word)
                        store.learned = store.learned - q.word.id
                        store.hard = store.hard + q.word.id
                        if (store.reviewStates().containsKey(q.word.id)) {
                            store.recordReview(q.word.id, ReviewRating.Again)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
    AnimatedVisibility(visible = answered, enter = fadeIn(tween(200)) + expandVertically(tween(250))) {
        val ok = picked == q.correct
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (ok) Tint else MissTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Boby(if (ok) Mood.Happy else Mood.Think, 48.dp)
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
private fun CelebrationBurst() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(500),
        label = "wordPopCelebration"
    )
    val colors = listOf(Sun, Teal, Coral, Sky)
    val points = listOf(
        0.08f to 0.20f, 0.18f to 0.62f, 0.29f to 0.12f, 0.40f to 0.72f,
        0.59f to 0.18f, 0.70f to 0.66f, 0.82f to 0.10f, 0.93f to 0.55f
    )
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        points.forEachIndexed { index, point ->
            val x = size.width * point.first
            val y = size.height * (point.second + progress * (0.25f + (index % 3) * 0.12f))
            drawCircle(
                color = colors[index % colors.size],
                radius = 3.5.dp.toPx(),
                center = Offset(x, y)
            )
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
    onAgain: () -> Unit,
    onNewWord: () -> Unit
) {
    val pct = if (total == 0) 0 else score * 100 / total
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pct >= 80) CelebrationBurst()
            Boby(if (pct >= 80) Mood.Cheer else if (pct >= 50) Mood.Happy else Mood.Think, 96.dp)
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
    var queue by remember { mutableStateOf(WordRepo.reviewQueue(store)) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableIntStateOf(0) }

    fun reloadQueue() {
        queue = WordRepo.reviewQueue(store)
        index = 0
        revealed = false
        completed = 0
        remembered = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("المراجعة الذكية", fontSize = 25.sp, fontFamily = Display, color = Night)
                Text("راجع في الوقت المناسب عشان متنساش الكلمات.", fontSize = 14.sp, color = Muted)
            }
            Boby(Mood.Cheer, 54.dp)
        }

        val dueNow = WordRepo.reviewDueCount(store)
        val newToday = WordRepo.reviewNewCount(store)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = SunTint
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$dueNow", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("مستحقة", fontSize = 13.sp, color = Muted)
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Tint
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$newToday", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                    Text("جديدة اليوم", fontSize = 13.sp, color = Muted)
                }
            }
        }

        when {
            queue.isEmpty() -> {
                Section("أحسنت! 🎉") {
                    Boby(Mood.Cheer, 82.dp)
                    Text(
                        "مفيش كلمات مستحقة دلوقتي. هتظهرلك كلمات جديدة كل يوم، والكلمات اللي راجعتها هترجع في موعدها.",
                        fontSize = 16.sp,
                        color = Night
                    )
                    PrimaryButton("تحديث القائمة", Modifier.fillMaxWidth()) { reloadQueue() }
                }
            }
            index >= queue.size -> {
                Section("خلصت جلسة المراجعة!") {
                    Boby(Mood.Cheer, 82.dp)
                    Text("راجعت $completed كلمة، وتذكرت $remembered منها بشكل جيد أو سهل.", fontSize = 17.sp, color = Night)
                    LinearProgressIndicator(
                        progress = { if (completed == 0) 0f else remembered.toFloat() / completed },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Teal,
                        trackColor = Line
                    )
                    PrimaryButton("تحديث القائمة", Modifier.fillMaxWidth()) { reloadQueue() }
                }
            }
            else -> {
                val word = queue[index]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بطاقة ${index + 1} من ${queue.size}", fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f))
                        Text("تمت مراجعة $completed", fontSize = 13.sp, color = TealDark)
                    }
                    LinearProgressIndicator(
                        progress = { index.toFloat() / queue.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Teal,
                        trackColor = Line
                    )
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(2.5.dp, Night),
                    modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(22.dp), 5.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("افتكر معنى الكلمة قبل ما تكشف الإجابة", fontSize = 15.sp, color = Muted)
                        En(
                            word.en,
                            if (word.en.length > 18) 30.sp else 38.sp,
                            Night,
                            FontWeight.ExtraBold,
                            TextAlign.Center,
                            Modifier.fillMaxWidth()
                        )
                        if (word.ipa.isNotEmpty()) {
                            En(word.ipa, 18.sp, Muted, FontWeight.Normal, TextAlign.Center, Modifier.fillMaxWidth())
                        }
                        SpeakButton(52.dp) { speak(word.en) }

                        if (revealed) {
                            Text(
                                word.ar,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealDark,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (word.example.isNotEmpty()) {
                                Ltr {
                                    Text(word.example, fontSize = 17.sp, color = Night, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (word.exampleAr.isNotEmpty()) {
                                Text(word.exampleAr, fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                if (!revealed) {
                    PrimaryButton("اكشف المعنى", Modifier.fillMaxWidth()) { revealed = true }
                } else {
                    Text("قيّم مدى سهولة تذكرك للكلمة:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("نسيتها", Modifier.weight(1f), container = MissTint) {
                            store.recordReview(word.id, ReviewRating.Again)
                            completed++
                            index++
                            revealed = false
                        }
                        PrimaryButton("صعبة", Modifier.weight(1f), container = Sun) {
                            store.recordReview(word.id, ReviewRating.Hard)
                            completed++
                            index++
                            revealed = false
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("كويسة", Modifier.weight(1f), container = Teal) {
                            store.recordReview(word.id, ReviewRating.Good)
                            completed++
                            remembered++
                            index++
                            revealed = false
                        }
                        PrimaryButton("سهلة", Modifier.weight(1f), container = SkyTint) {
                            store.recordReview(word.id, ReviewRating.Easy)
                            completed++
                            remembered++
                            index++
                            revealed = false
                        }
                    }
                    Text(
                        "نسيتها: بعد 10 دقائق · صعبة: بعد يوم · كويسة: بعد 1–3 أيام · سهلة: بعد 4–7 أيام، وبعدها الفواصل بتزيد تدريجيًا.",
                        fontSize = 13.sp,
                        color = Muted
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
