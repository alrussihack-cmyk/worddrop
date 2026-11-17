package com.wordpop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

const val DAILY_GOAL = 20

/** Page title row used by every tab except Home: big display title and an optional count pill. */
@Composable
fun PageHeader(title: String, pill: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 34.sp, lineHeight = 44.sp, fontFamily = Display, color = Night, modifier = Modifier.weight(1f))
        if (pill != null) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Tint).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(pill, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }
        }
    }
}

/** Speak button with a soft ring that keeps expanding, to say "tap me". */
@Composable
fun PulseSpeakButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "speakPulse")
    val p by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearOutSlowInEasing)),
        label = "speakPulseP"
    )
    Box(
        Modifier.size(size).drawBehind {
            drawCircle(Teal.copy(alpha = 0.65f * (1f - p)), radius = this.size.minDimension / 2f + 16.dp.toPx() * p)
        },
        contentAlignment = Alignment.Center
    ) { SpeakButton(size, onClick) }
}

@Composable
private fun GoalRing(done: Int) {
    val progress by animateFloatAsState((done.coerceAtMost(DAILY_GOAL)).toFloat() / DAILY_GOAL, tween(900, easing = FastOutSlowInEasing), label = "goalRing")
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = 14.dp.toPx()
            val topLeft = Offset(8.dp.toPx(), 8.dp.toPx())
            val arc = Size(104.dp.toPx(), 104.dp.toPx())
            drawArc(Tint, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (progress > 0f) {
                // starts at the bottom and runs counter-clockwise, like the design
                drawArc(Teal, 90f, -360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians((90f - 360f * progress).toDouble())
                val r = 52.dp.toPx()
                drawCircle(Night, 2.5.dp.toPx(), Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(done.coerceAtMost(DAILY_GOAL).toString(), fontSize = 38.sp, lineHeight = 46.sp, fontFamily = Display, color = Night)
            Text("من $DAILY_GOAL", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
        }
    }
}

private val weekLetters = mapOf(
    Calendar.SATURDAY to "س", Calendar.SUNDAY to "ح", Calendar.MONDAY to "ن", Calendar.TUESDAY to "ث",
    Calendar.WEDNESDAY to "ر", Calendar.THURSDAY to "خ", Calendar.FRIDAY to "ج"
)

@Composable
private fun WeekCard(store: SettingsStore) {
    val days = store.activityDays
    val now = System.currentTimeMillis()
    val week = (6 downTo 0).map { back ->
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }
        Triple(weekLetters[cal.get(Calendar.DAY_OF_WEEK)] ?: "", ReviewScheduler.dayKey(cal.timeInMillis) in days, back == 0)
    }
    SoftCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { (letter, active, today) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(letter, fontSize = 12.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.SemiBold, color = if (today) Night else Muted)
                    Box(
                        Modifier
                            .size(34.dp)
                            .then(if (today) Modifier.hardShadow(CircleShape, 3.dp) else Modifier)
                            .clip(CircleShape)
                            .background(if (active) Teal else if (today) Sun else Color.White)
                            .border(3.dp, if (active || today) Night else Line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) {
                            Icon(painterResource(R.drawable.ic_check), null, tint = Night, modifier = Modifier.size(16.dp))
                        } else if (today) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Night))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    val t = rememberInfiniteTransition(label = "flame")
    val s by t.animateFloat(1f, 1.14f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "flameScale")
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.hardShadow(shape, 3.dp).clip(shape).background(Color.White).border(3.dp, Night, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_flame), contentDescription = "أيام متتالية",
            modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s * 1.05f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f) }
        )
        Text(days.toString(), fontSize = 22.sp, lineHeight = 26.sp, fontFamily = Display, color = Night)
    }
}

@Composable
private fun WordOfDay(store: SettingsStore, onChanged: () -> Unit) {
    val speak = rememberSpeaker()
    var skips by remember { mutableIntStateOf(0) }
    val word = remember(skips) { WordRepo.pick(store) }
    var shown by remember(skips) { mutableStateOf(false) }

    HeroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Sun).border(2.dp, Night, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) { Text("كلمة اليوم", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Night) }
                Spacer(Modifier.weight(1f))
                Text("مستوى ${word.level}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            }
            En(word.en, 34.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    En(
                        listOf(word.ipa, word.kind).filter { it.isNotBlank() }.joinToString(" · "),
                        14.sp, Muted, FontWeight.Normal, TextAlign.Start, Modifier.fillMaxWidth()
                    )
                }
                PulseSpeakButton(52.dp) { speak(word.en) }
            }
            AnimatedVisibility(shown, enter = fadeIn(tween(400)) + expandVertically(tween(400))) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tint).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(word.ar, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    if (word.example.isNotBlank()) En(word.example, 14.sp, Night, FontWeight.Normal, TextAlign.Start, Modifier.fillMaxWidth())
                }
            }
            if (!shown) {
                SecondaryButton("اكشف المعنى", Modifier.fillMaxWidth()) { shown = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("أعرفها", Modifier.weight(1f)) {
                    store.recordReview(word.id, ReviewRating.Good)
                    store.learned = store.learned + word.id
                    skips++
                    onChanged()
                }
                SecondaryButton("تخطي", Modifier.weight(1f)) { skips++ }
            }
        }
    }
}

@Composable
private fun Tile(title: String, sub: String, container: Color, border: Color, subColor: Color, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "tileScale")
    SoftCard(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        radius = 22.dp, container = container, border = border
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontFamily = Display, color = Night)
            Text(sub, fontSize = 12.sp, lineHeight = 18.sp, color = subColor)
        }
    }
}

/** The whole Home tab, built to the WordPop 2.0 layout. */
@Composable
fun HomeContent(
    store: SettingsStore,
    enabled: Boolean,
    interval: Int,
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    reviewDueCount: Int,
    onToggle: (Boolean) -> Unit,
    onTryNow: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onStartLearning: () -> Unit,
    onNavigate: (Page) -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }
    val done = remember(tick) { store.doneToday() }
    val streak = remember(tick) { store.streakDays() }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when {
        hour < 12 -> "صباح الخير"
        hour < 17 -> "يومك سعيد"
        else -> "مساء الخير"
    }
    val left = (DAILY_GOAL - done).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Boby(Mood.Happy, 56.dp, Modifier.bob())
            Column(Modifier.weight(1f)) {
                Text(hello, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted)
                Text("أهلاً بيك", fontSize = 28.sp, lineHeight = 36.sp, fontFamily = Display, color = Night)
            }
            StreakPill(streak)
        }

        SoftCard(Modifier.fillMaxWidth(), radius = 24.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                GoalRing(done)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هدف النهاردة", fontSize = 24.sp, lineHeight = 30.sp, fontFamily = Display, color = Night)
                    Text(
                        if (left == 0) "خلصت هدفك! بوبي فخور بيك" else "باقي $left كلمات وتخلّص هدفك",
                        fontSize = 14.sp, lineHeight = 22.sp, color = Muted
                    )
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Tint).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("+${done * 10} نقطة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }
                }
            }
        }

        WeekCard(store)

        WordOfDay(store) { tick++ }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                "المراجعة الذكية",
                if (reviewDueCount > 0) "$reviewDueCount كلمة مستنياك" else "مفيش كلمات مستحقة",
                Tint, MintBorder, MintInk, Modifier.weight(1f)
            ) { onNavigate(Page.Review) }
            Tile("قصتك", "اعمل قصة من كلماتك", SunTint, SunBorder, SunInk, Modifier.weight(1f)) { onNavigate(Page.Favorites) }
        }

        SecondaryButton("ابدأ جلسة تعلّم", Modifier.fillMaxWidth()) { onStartLearning() }

        SoftCard(Modifier.fillMaxWidth(), radius = 22.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("الكلمات العائمة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text(
                            if (enabled) "شغّالة · ${intervalLabel(interval)}" else "متوقفة مؤقتًا",
                            fontSize = 12.sp, color = Muted
                        )
                    }
                    Switch(
                        checked = enabled, onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Night, checkedTrackColor = Teal, checkedBorderColor = Night,
                            uncheckedThumbColor = Night, uncheckedTrackColor = Color.White, uncheckedBorderColor = Night
                        )
                    )
                }
                SecondaryButton("جرّب ظهور كلمة الآن", Modifier.fillMaxWidth(), onClick = onTryNow)
            }
        }

        if (!overlayGranted || !exactAlarmGranted) {
            val needsOverlay = !overlayGranted
            SoftCard(Modifier.fillMaxWidth(), radius = 20.dp, container = SunTint, border = SunBorder) {
                Row(
                    Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Boby(Mood.Think, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("خطوة أخيرة للتشغيل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night)
                        Text(
                            if (needsOverlay) "اسمح بالظهور فوق التطبيقات." else "فعّل التنبيهات الدقيقة لمواعيد الكلمات.",
                            fontSize = 12.sp, lineHeight = 18.sp, color = SunInk
                        )
                    }
                    PrimaryButton("تفعيل") { if (needsOverlay) onRequestOverlay() else onRequestExactAlarm() }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/** The "الكلمات" board: filter chips and a list of word rows (speak, word, tag, favourite). */
@Composable
fun WordListTab(store: SettingsStore, speak: (String) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(40) }
    var cat by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    val all = WordRepo.all
    val favs = version.let { store.favWords }
    val learned = store.learned
    val hard = store.hard
    val categories = WordRepo.categories()
    val byFilter = when (filter) {
        1 -> all.filter { it.id in favs }
        2 -> all.filter { it.id in learned }
        3 -> all.filter { it.id in hard }
        else -> all
    }
    val list = if (cat.isBlank()) byFilter else byFilter.filter { it.category == cat }
    ChipRow {
        Chip("الكل", filter == 0) { filter = 0; shown = 40 }
        Chip("المفضلة", filter == 1) { filter = 1; shown = 40 }
        Chip("اتعلمتها", filter == 2) { filter = 2; shown = 40 }
        Chip("صعبة", filter == 3) { filter = 3; shown = 40 }
    }
    if (categories.isNotEmpty()) {
        ChipRow {
            Chip("كل التصنيفات", cat.isBlank()) { cat = ""; shown = 40 }
            categories.forEach { c -> Chip(c, cat == c) { cat = c; shown = 40 } }
        }
    }
    if (list.isEmpty()) {
        EmptyFav("مفيش كلمات هنا لسه.")
    }
    list.take(shown).forEach { w ->
        val fav = w.id in favs
        val off = WordRepo.isDisabled(w, store)
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (off) 0.45f else 1f }
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.5.dp, Line, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpeakButton(46.dp) { speak(w.en) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                En(w.en, 20.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.fillMaxWidth())
                Text(w.ar, fontSize = 15.sp, color = Muted, maxLines = 2)
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(Tint).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(w.category.ifBlank { w.level }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }
            StarButton(fav) { store.toggleFavWord(w.id); version++ }
        }
    }
    if (list.size > shown) {
        SecondaryButton("عرض المزيد (${list.size - shown})", Modifier.fillMaxWidth()) { shown += 40 }
    }
}
