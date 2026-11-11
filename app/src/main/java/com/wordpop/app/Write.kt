@file:OptIn(ExperimentalLayoutApi::class)

package com.wordpop.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WrongBg = Color(0xFFFDE7E8)

// ---------- entry screen ----------

/** The "Writing" tab: pick spelling or sentence building. */
@Composable
fun WriteScreen(store: SettingsStore) {
    val speak = rememberSpeaker()
    var mode by remember { mutableIntStateOf(-1) }
    BackHandler(enabled = mode >= 0) { mode = -1 }

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
            if (mode >= 0) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "رجوع") { mode = -1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "رجوع", tint = Night)
                }
            }
            Text(
                when (mode) {
                    0 -> "تهجئة الكلمات"
                    1 -> "بناء الجمل"
                    else -> "تدريب الكتابة"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Night
            )
        }

        when (mode) {
            0 -> SpellSession(store, speak)
            1 -> BuildSession(store, speak)
            else -> WriteMenu(store) { mode = it }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WriteMenu(store: SettingsStore, onPick: (Int) -> Unit) {
    fun stats(prefix: String): String {
        val rounds = store.stat(prefix + "Rounds")
        return if (rounds == 0) "لسه ماجربتش" else "جولات: $rounds  ·  أحسن نتيجة: ${store.stat(prefix + "Best")}%"
    }
    Text(
        "الكتابة بتتعلم بالتكرار. جولة قصيرة كل يوم أحسن من ساعة مرة في الأسبوع.",
        fontSize = 16.sp,
        color = Muted
    )
    ModeCard("✍️", "تهجئة الكلمات", "اسمع الكلمة أو شوف معناها واكتبها صح. بتتصحح حرف حرف.", stats("spell")) { onPick(0) }
    ModeCard("🧩", "بناء الجمل", "رتّب الكلمات المتلخبطة، أو اختار الكلمة الناقصة في الجملة.", stats("build")) { onPick(1) }
    Surface(shape = RoundedCornerShape(16.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("إزاي بنختار الأسئلة؟", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Night)
            Text(
                "الكلمات اللي غلطت فيها بتيجي الأول، وبعدها الكلمات اللي ماتدرّبتش عليها. وبتتحترم المستوى اللي اخترته في الإعدادات.",
                fontSize = 15.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun ModeCard(emoji: String, title: String, desc: String, stats: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(2.5.dp, Night),
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(20.dp), 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Tint),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 28.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Night)
                Text(desc, fontSize = 14.sp, color = Muted)
                Text(stats, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }
        }
    }
}

// ---------- shared pieces ----------

@Composable
private fun WriteProgress(index: Int, total: Int, answered: Boolean, streak: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تمرين ${index + 1} من $total", fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f))
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
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Teal,
            trackColor = Line
        )
    }
}

@Composable
private fun PromptCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun FeedbackPanel(
    ok: Boolean,
    title: String,
    trigger: Int,
    animationsEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AnswerFeedbackMotion(correct = ok, trigger = trigger, enabled = animationsEnabled) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(if (animationsEnabled) 200 else 0)) +
                expandVertically(tween(if (animationsEnabled) 250 else 0))
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = if (ok) Tint else WrongBg, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ok && animationsEnabled) CelebrationBurst()
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (ok) TealDark else Danger)
                    content()
                }
            }
        }
    }
}

/** Typed letters as boxes: green when the letter is right in that position, red when it is not. */
@Composable
private fun LetterBoxes(typed: String, target: String, showTarget: Boolean) {
    val n = maxOf(typed.length, target.length)
    val size = when {
        n > 13 -> 14.sp
        n > 10 -> 17.sp
        else -> 21.sp
    }
    @Composable
    fun row(chars: String, compare: Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until n) {
                val c = chars.getOrNull(i)
                val good = if (compare) c != null && target.getOrNull(i)?.equals(c, ignoreCase = true) == true else true
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (good) Color.White else MissTint)
                        .border(2.dp, if (good) Night else Danger, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(c?.toString() ?: "", fontSize = size, fontWeight = FontWeight.Bold, color = Night)
                }
            }
        }
    }
    Ltr {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            row(typed, compare = true)
            if (showTarget) row(target, compare = false)
        }
    }
}

private fun scorePct(perfect: Int, helped: Int, total: Int): Int =
    if (total == 0) 0 else (perfect * 2 + helped) * 100 / (total * 2)

/** Saves the round in the stats shown on the Writing tab. */
private fun saveRound(store: SettingsStore, prefix: String, pct: Int) {
    store.setStat(prefix + "Rounds", store.stat(prefix + "Rounds") + 1)
    if (pct > store.stat(prefix + "Best")) store.setStat(prefix + "Best", pct)
}

@Composable
private fun WriteResult(
    prefix: String,
    store: SettingsStore,
    perfect: Int,
    helped: Int,
    missed: Int,
    best: Int,
    total: Int,
    missedItems: List<Pair<String, String>>,
    speak: (String) -> Unit,
    onAgain: () -> Unit
) {
    val pct = scorePct(perfect, helped, total)
    LaunchedEffect(Unit) { saveRound(store, prefix, pct) }

    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(2.5.dp, Night), modifier = Modifier.fillMaxWidth().hardShadow(RoundedCornerShape(20.dp), 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pct >= 80 && store.feedbackAnimations) CelebrationBurst()
            Boby(if (pct >= 80) Mood.Cheer else if (pct >= 50) Mood.Happy else Mood.Think, 96.dp)
            Box(
                Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(if (pct >= 50) Teal else Sun)
                    .border(3.dp, Night, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$pct%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Night)
            }
            Text(
                when {
                    pct >= 85 -> "كتابتك ممتازة! 🏆"
                    pct >= 60 -> "كويس جدًا، كمّل 👏"
                    else -> "كل جولة بتقرّبك 💪"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Night
            )
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, Night, RoundedCornerShape(6.dp)),
                color = if (pct >= 50) Teal else Sun,
                trackColor = Line
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultStat("من أول مرة", "$perfect")
                ResultStat("بمساعدة", "$helped")
                ResultStat("غلط", "$missed")
                ResultStat("أطول سلسلة", "$best")
            }
        }
    }

    if (missedItems.isNotEmpty()) {
        Section("محتاج مراجعة") {
            missedItems.forEach { (en, ar) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        En(en, 17.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.fillMaxWidth())
                        Text(ar, fontSize = 15.sp, color = Muted)
                    }
                    SpeakButton(40.dp) { speak(en) }
                }
            }
            Text("هتيجيلك أول في الجولة الجاية عشان تثبّت.", fontSize = 14.sp, color = Muted)
        }
    }
    PrimaryButton("جولة جديدة", Modifier.fillMaxWidth(), onClick = onAgain)
}

// ---------- spelling ----------

private fun spellable(w: Word): Boolean =
    !w.isPhrase && w.en.length in 3..16 && w.en.all { it.isLetter() || it == '-' }

@Composable
private fun SpellSession(store: SettingsStore, speak: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val answerFeedback = rememberAnswerFeedback(store)
    var round by remember { mutableIntStateOf(0) }
    val items = remember(round) { WordRepo.writingWords(store, "spell", 10) { spellable(it) } }
    var index by remember(round) { mutableIntStateOf(0) }
    var perfect by remember(round) { mutableIntStateOf(0) }
    var helped by remember(round) { mutableIntStateOf(0) }
    var missed by remember(round) { mutableIntStateOf(0) }
    var streak by remember(round) { mutableIntStateOf(0) }
    var best by remember(round) { mutableIntStateOf(0) }
    val missedWords = remember(round) { mutableStateListOf<Word>() }
    val total = items.size

    if (total == 0) {
        Text("مفيش كلمات مناسبة للتدريب. غيّر المستوى من الإعدادات.", fontSize = 16.sp, color = Muted)
        return
    }
    if (index >= total) {
        WriteResult(
            prefix = "spell", store = store,
            perfect = perfect, helped = helped, missed = missed, best = best, total = total,
            missedItems = missedWords.map { it.en to it.ar },
            speak = speak,
            onAgain = { round++ }
        )
        return
    }

    val w = items[index]
    val listen = index % 2 == 0
    var text by remember(round, index) { mutableStateOf("") }
    var hints by remember(round, index) { mutableIntStateOf(0) }
    var attempts by remember(round, index) { mutableIntStateOf(0) }
    // 0 = typing, 1 = wrong (can retry), 2 = correct, 3 = answer revealed
    var status by remember(round, index) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(round, index) {
        if (listen) speak(w.en)
        runCatching { focus.requestFocus() }
    }

    fun check() {
        val typed = text.trim()
        if (typed.isEmpty() || status >= 2) return
        attempts++
        if (typed.equals(w.en, ignoreCase = true)) {
            status = 2
            answerFeedback(true)
            if (attempts == 1 && hints == 0) {
                perfect++
                streak++
                if (streak > best) best = streak
                store.addTo("spellOk", w.id)
                store.removeFrom("spellMiss", w.id)
            } else {
                helped++
                streak = 0
                store.addTo("spellMiss", w.id)
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else if (attempts >= 2) {
            status = 3
            answerFeedback(false)
            missed++
            streak = 0
            missedWords.add(w)
            store.addTo("spellMiss", w.id)
            store.removeFrom("spellOk", w.id)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            status = 1
            answerFeedback(false)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    WriteProgress(index, total, status >= 2, streak)

    PromptCard {
        if (listen) {
            Text("اسمع واكتب الكلمة", fontSize = 16.sp, color = Muted)
            SpeakButton(72.dp) { speak(w.en) }
            Text("المعنى: ${w.ar}", fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
        } else {
            Text("اكتب الكلمة بالإنجليزي", fontSize = 16.sp, color = Muted)
            Text(
                w.ar,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Night,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(w.kind, fontSize = 14.sp, color = TealDark, fontWeight = FontWeight.Bold)
        }
        // _ _ _ _ pattern; hints reveal letters from the start
        val pattern = w.en.mapIndexed { i, c -> if (i < hints) c else '_' }.joinToString(" ")
        En(pattern, 26.sp, Night, FontWeight.Bold, TextAlign.Center, Modifier.fillMaxWidth())
    }

    if (status < 2) {
        Ltr {
            BasicTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isLetter() || it == '-' }.take(24) },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Night,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Night),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { check() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(2.5.dp, if (status == 1) Danger else Night, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (text.isEmpty()) {
                            Text("اكتب هنا", fontSize = 22.sp, color = Muted.copy(alpha = 0.5f))
                        }
                        inner()
                    }
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("تلميح 💡", Modifier.weight(1f)) {
                if (hints < w.en.length - 1) hints++
            }
            PrimaryButton("تحقق ✓", Modifier.weight(1.4f)) { check() }
        }
    }

    when (status) {
        1 -> FeedbackPanel(false, "قريب! فيه حروف غلط", status, store.feedbackAnimations) {
            LetterBoxes(text.trim(), w.en, showTarget = false)
            Text("عدّل الحروف الحمرا وجرّب تاني. عندك محاولة كمان.", fontSize = 15.sp, color = Muted)
        }
        2 -> FeedbackPanel(true, if (attempts == 1 && hints == 0) "تمام! كتبتها صح من أول مرة ✓" else "صح! بمساعدة بسيطة ✓", status, store.feedbackAnimations) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                En(w.en, 24.sp, Night, FontWeight.ExtraBold, TextAlign.Start, Modifier.weight(1f))
                SpeakButton(44.dp) { speak(w.en) }
            }
            if (w.example.isNotEmpty()) En(w.example, 15.sp, Muted, FontWeight.Normal, TextAlign.Start, Modifier.fillMaxWidth())
        }
        3 -> FeedbackPanel(false, "الكتابة الصح", status, store.feedbackAnimations) {
            LetterBoxes(text.trim(), w.en, showTarget = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                En(w.en, 24.sp, Night, FontWeight.ExtraBold, TextAlign.Start, Modifier.weight(1f))
                SpeakButton(44.dp) { speak(w.en) }
            }
            Text("هتيجيلك تاني في الجولات الجاية.", fontSize = 14.sp, color = Muted)
        }
    }
    if (status >= 2) {
        PrimaryButton(if (index == total - 1) "شوف النتيجة" else "التالي", Modifier.fillMaxWidth()) { index++ }
    }
}

// ---------- sentence building ----------

/** One sentence exercise. kind: 0 = arrange from the Arabic, 1 = arrange from audio, 2 = fill the blank. */
private class BuildQ(
    val word: Word,
    val kind: Int,
    val tokens: List<String>,
    val order: List<Int>,
    val blank: Int,
    val options: List<String>
)

private fun buildable(w: Word): Boolean = w.example.trim().split(Regex("\\s+")).size in 3..12

private fun makeBuildQ(w: Word, i: Int): BuildQ {
    val tokens = w.example.trim().split(Regex("\\s+"))
    val blank = tokens.indexOfFirst { it.trim { c -> !c.isLetter() }.equals(w.en, ignoreCase = true) }
    var kind = when (i % 3) {
        0 -> 0
        1 -> 2
        else -> 1
    }
    if (kind == 2 && blank < 0) kind = 0
    var order = tokens.indices.shuffled()
    var tries = 0
    while (order == tokens.indices.toList() && tokens.size > 1 && tries < 6) {
        order = tokens.indices.shuffled()
        tries++
    }
    var options = emptyList<String>()
    if (kind == 2) {
        val correct = tokens[blank].trim { c -> !c.isLetter() }
        val pool = WordRepo.all.filter { it.id != w.id && !it.isPhrase && ' ' !in it.en }
        val distractors = (pool.filter { it.kind == w.kind }.shuffled() + pool.shuffled())
            .map { it.en }
            .filter { !it.equals(correct, ignoreCase = true) }
            .distinct()
            .take(3)
        options = (distractors + correct).shuffled()
    }
    return BuildQ(w, kind, tokens, order, blank, options)
}

@Composable
private fun WordChip(text: String, state: Int, onClick: () -> Unit) {
    // state: 0 normal, 1 wrong position
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .hardShadow(shape, 3.dp)
            .clip(shape)
            .background(if (state == 1) MissTint else Teal)
            .border(2.dp, if (state == 1) Danger else Night, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Night)
    }
}

@Composable
private fun BuildSession(store: SettingsStore, speak: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val answerFeedback = rememberAnswerFeedback(store)
    var round by remember { mutableIntStateOf(0) }
    val questions = remember(round) {
        WordRepo.writingWords(store, "build", 8) { buildable(it) }.mapIndexed { i, w -> makeBuildQ(w, i) }
    }
    var index by remember(round) { mutableIntStateOf(0) }
    var perfect by remember(round) { mutableIntStateOf(0) }
    var helped by remember(round) { mutableIntStateOf(0) }
    var missed by remember(round) { mutableIntStateOf(0) }
    var streak by remember(round) { mutableIntStateOf(0) }
    var best by remember(round) { mutableIntStateOf(0) }
    val missedWords = remember(round) { mutableStateListOf<Word>() }
    val total = questions.size

    if (total == 0) {
        Text("مفيش جمل مناسبة للتدريب. غيّر المستوى من الإعدادات.", fontSize = 16.sp, color = Muted)
        return
    }
    if (index >= total) {
        WriteResult(
            prefix = "build", store = store,
            perfect = perfect, helped = helped, missed = missed, best = best, total = total,
            missedItems = missedWords.map { it.example to it.exampleAr },
            speak = speak,
            onAgain = { round++ }
        )
        return
    }

    val q = questions[index]
    val w = q.word
    val placed = remember(round, index) { mutableStateListOf<Int>() }
    var hints by remember(round, index) { mutableIntStateOf(0) }
    var attempts by remember(round, index) { mutableIntStateOf(0) }
    // 0 = working, 1 = wrong (can retry), 2 = correct, 3 = answer revealed
    var status by remember(round, index) { mutableIntStateOf(0) }
    var picked by remember(round, index) { mutableStateOf<String?>(null) }

    LaunchedEffect(round, index) {
        if (q.kind == 1) speak(w.example)
    }

    fun finish(ok: Boolean, clean: Boolean) {
        answerFeedback(ok)
        if (ok && clean) {
            perfect++
            streak++
            if (streak > best) best = streak
            store.addTo("buildOk", w.id)
            store.removeFrom("buildMiss", w.id)
        } else if (ok) {
            helped++
            streak = 0
            store.addTo("buildMiss", w.id)
        } else {
            missed++
            streak = 0
            missedWords.add(w)
            store.addTo("buildMiss", w.id)
            store.removeFrom("buildOk", w.id)
        }
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
    }

    fun isRight(i: Int): Boolean =
        i < placed.size && q.tokens[placed[i]].equals(q.tokens[i], ignoreCase = true)

    fun checkArrange() {
        if (placed.size != q.tokens.size || status >= 2) return
        attempts++
        if (q.tokens.indices.all { isRight(it) }) {
            status = 2
            finish(ok = true, clean = attempts == 1 && hints == 0)
        } else if (attempts >= 2) {
            status = 3
            finish(ok = false, clean = false)
        } else {
            status = 1
            answerFeedback(false)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    WriteProgress(index, total, status >= 2, streak)

    PromptCard {
        when (q.kind) {
            0 -> {
                Text("رتّب الكلمات عشان تطلع الجملة دي بالإنجليزي", fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center)
                Text(
                    w.exampleAr.ifEmpty { w.ar },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Night,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            1 -> {
                Text("اسمع الجملة ورتّب الكلمات", fontSize = 16.sp, color = Muted)
                SpeakButton(72.dp) { speak(w.example) }
                Text("دوس لو عايز تسمعها تاني", fontSize = 13.sp, color = Muted)
            }
            else -> {
                Text("اختار الكلمة الناقصة", fontSize = 16.sp, color = Muted)
                En(
                    q.tokens.mapIndexed { i, t -> if (i == q.blank) "_____" + t.takeLastWhile { c -> !c.isLetter() } else t }
                        .joinToString(" "),
                    22.sp, Night, FontWeight.Bold, TextAlign.Center, Modifier.fillMaxWidth()
                )
                if (w.exampleAr.isNotEmpty()) Text(w.exampleAr, fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center)
            }
        }
    }

    if (q.kind == 2) {
        // ----- fill the blank -----
        val correct = q.tokens[q.blank].trim { c -> !c.isLetter() }
        val letters = listOf("أ", "ب", "ج", "د")
        q.options.forEachIndexed { i, opt ->
            val answered = picked != null
            val isCorrect = opt.equals(correct, ignoreCase = true)
            val isPicked = picked == opt
            val bg by animateColorAsState(
                when {
                    !answered -> Color.White
                    isCorrect -> Teal
                    isPicked -> MissTint
                    else -> Color.White
                }, tween(if (store.feedbackAnimations) 220 else 0), label = "bg"
            )
            val borderC by animateColorAsState(
                when {
                    !answered -> Night
                    isCorrect -> Night
                    isPicked -> Danger
                    else -> Night
                }, tween(if (store.feedbackAnimations) 220 else 0), label = "borderC"
            )
            val shape = RoundedCornerShape(14.dp)
            AnswerFeedbackMotion(
                correct = isCorrect,
                trigger = if (answered && isPicked) 1 else 0,
                enabled = store.feedbackAnimations
            ) {
                Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .hardShadow(shape, 4.dp)
                    .clip(shape)
                    .background(bg)
                    .border(2.5.dp, borderC, shape)
                    .clickable(enabled = !answered, role = Role.Button) {
                        picked = opt
                        attempts = 1
                        status = if (isCorrect) 2 else 3
                        finish(ok = isCorrect, clean = isCorrect)
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
                En(opt, 20.sp, Night, FontWeight.SemiBold, TextAlign.Start, Modifier.weight(1f))
                }
            }
        }
    } else {
        // ----- arrange the words -----
        Ltr {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(2.5.dp, if (status == 1) Danger else Night),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
            ) {
                FlowRow(
                    Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (placed.isEmpty()) {
                        Text("دوس على الكلمات تحت عشان تبني الجملة هنا", fontSize = 15.sp, color = Muted.copy(alpha = 0.6f))
                    }
                    placed.forEachIndexed { pos, tokenIndex ->
                        WordChip(q.tokens[tokenIndex], if (status == 1 && !isRight(pos)) 1 else 0) {
                            if (status < 2) {
                                placed.removeAt(pos)
                                if (status == 1) status = 0
                            }
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                q.order.filter { it !in placed }.forEach { tokenIndex ->
                    WordChip(q.tokens[tokenIndex], 0) {
                        if (status < 2) {
                            placed.add(tokenIndex)
                            if (status == 1) status = 0
                        }
                    }
                }
            }
        }
        if (status < 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("تلميح 💡", Modifier.weight(1f)) {
                    // keep the correctly placed prefix, then place the next right word
                    var keep = 0
                    while (keep < placed.size && isRight(keep)) keep++
                    while (placed.size > keep) placed.removeAt(placed.size - 1)
                    if (keep < q.tokens.size) {
                        val next = q.order.firstOrNull { it !in placed && q.tokens[it].equals(q.tokens[keep], ignoreCase = true) }
                        if (next != null) {
                            placed.add(next)
                            hints++
                        }
                    }
                    if (status == 1) status = 0
                }
                SecondaryButton("امسح", Modifier.weight(0.8f)) {
                    placed.clear()
                    if (status == 1) status = 0
                }
                PrimaryButton("تحقق ✓", Modifier.weight(1.2f)) { checkArrange() }
            }
        }
    }

    // ----- feedback -----
    when (status) {
        1 -> FeedbackPanel(false, "الترتيب مش مظبوط", status, store.feedbackAnimations) {
            Text("الكلمات الحمرا في مكان غلط. دوس عليها لتشيلها وجرّب تاني. عندك محاولة كمان.", fontSize = 15.sp, color = Muted)
        }
        2 -> FeedbackPanel(true, if (attempts == 1 && hints == 0) "تمام! جملة صح ✓" else "صح! بمساعدة بسيطة ✓", status, store.feedbackAnimations) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                En(w.example, 18.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.weight(1f))
                SpeakButton(44.dp) { speak(w.example) }
            }
            if (w.exampleAr.isNotEmpty()) Text(w.exampleAr, fontSize = 15.sp, color = Muted)
        }
        3 -> FeedbackPanel(false, "الجملة الصح", status, store.feedbackAnimations) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                En(w.example, 18.sp, Night, FontWeight.Bold, TextAlign.Start, Modifier.weight(1f))
                SpeakButton(44.dp) { speak(w.example) }
            }
            if (w.exampleAr.isNotEmpty()) Text(w.exampleAr, fontSize = 15.sp, color = Muted)
            Text("هتيجيلك تاني في الجولات الجاية.", fontSize = 14.sp, color = Muted)
        }
    }
    if (status >= 2) {
        PrimaryButton(if (index == total - 1) "شوف النتيجة" else "التالي", Modifier.fillMaxWidth()) { index++ }
    }
}
