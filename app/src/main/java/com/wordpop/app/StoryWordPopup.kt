package com.wordpop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** What the learner tapped in a story: the shown text, which highlight it was, and its language. */
data class TappedWord(val shown: String, val index: Int, val fromArabic: Boolean)

/** Highlighted (** **) parts of [text] in order. */
private fun highlights(text: String): List<String> =
    text.split("**").filterIndexed { i, _ -> i % 2 == 1 }

/** Same look as the plain highlight, but every highlighted word can be tapped. */
fun clickableHighlight(text: String, fromArabic: Boolean, onTap: (TappedWord) -> Unit): AnnotatedString =
    buildAnnotatedString {
        var hit = 0
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) {
                val tapped = TappedWord(part.trim(), hit++, fromArabic)
                val link = LinkAnnotation.Clickable(
                    tag = "w${tapped.index}",
                    styles = TextLinkStyles(SpanStyle(color = TealDark, background = Tint, fontWeight = FontWeight.Bold))
                ) { onTap(tapped) }
                withLink(link) { append(part) }
            } else append(part)
        }
    }

private fun stem(en: String) = en.lowercase().let { it.take(maxOf(3, it.length - 3)) }
private fun norm(s: String) = s.lowercase().filter { it.isLetter() || it == ' ' || it == '\'' || it == '’' }.trim()
private fun arNorm(s: String) = s.filter { it.isLetter() }.removePrefix("ال")

/**
 * Finds the vocabulary entry behind a tapped highlight. Arabic highlights follow the same order as
 * the English ones, so they are matched by position when the counts agree, otherwise by text.
 */
fun resolveStoryWord(story: Story, words: List<Word>, tapped: TappedWord): Word? {
    val pool = story.words.mapNotNull { en -> words.firstOrNull { it.en.equals(en, ignoreCase = true) } }
        .ifEmpty { words }

    fun byEnglish(shown: String): Word? {
        val n = norm(shown)
        return pool.filter { n == norm(it.en) }.firstOrNull()
            ?: pool.filter { n.contains(stem(it.en)) }.maxByOrNull { stem(it.en).length }
    }

    if (!tapped.fromArabic) return byEnglish(tapped.shown)

    val en = highlights(story.en)
    val ar = highlights(story.ar)
    if (en.size == ar.size) byEnglish(en[tapped.index])?.let { return it }
    val a = arNorm(tapped.shown)
    return pool.firstOrNull { w ->
        val m = arNorm(w.ar)
        a.isNotEmpty() && m.isNotEmpty() && (a.contains(m) || m.contains(a))
    }
}

/** Pop-up with the meaning and pronunciation of a tapped story word. */
@Composable
fun StoryWordDialog(tapped: TappedWord, word: Word?, store: SettingsStore, speak: (String) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shape, color = Color.White, border = BorderStroke(2.dp, Night),
            modifier = Modifier.fillMaxWidth().hardShadow(shape, 4.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (word == null) {
                    Text(tapped.shown, fontSize = 20.sp, fontFamily = Display, color = Night)
                    Text("مالقيتش تفاصيل الكلمة دي في مكتبتك.", fontSize = 14.sp, color = Muted)
                    if (!tapped.fromArabic) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpeakButton(44.dp) { speak(tapped.shown) }
                            Text("اسمع النطق", fontSize = 14.sp, color = TealDark, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    if (tapped.fromArabic) {
                        Text(tapped.shown, fontSize = 15.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                        Text("بالإنجليزي", fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            En(word.en, 26.sp, Night, FontWeight.ExtraBold)
                            if (word.ipa.isNotBlank()) En(word.ipa, 15.sp, Muted)
                        }
                        SpeakButton(48.dp) { speak(word.en) }
                    }
                    if (!tapped.fromArabic) {
                        Text(word.ar, fontSize = 20.sp, fontFamily = Display, color = Night)
                    }
                    Text(word.kind, fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.Bold)
                    if (word.example.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                En(word.example, 15.sp, Night)
                                if (word.exampleAr.isNotBlank()) Text(word.exampleAr, fontSize = 13.sp, color = Muted)
                            }
                        }
                    }
                }
                if (word != null) {
                    var added by remember(word.id) { mutableStateOf(store.isInMyWords(word.id)) }
                    if (added) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Tint, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "اتضافت لكلماتي وللمراجعة الذكية",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealDark,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        PrimaryButton("أضف لكلماتي", Modifier.fillMaxWidth()) {
                            store.addToMyWords(word.id)
                            added = true
                            Sfx.correct()
                        }
                    }
                }
                SecondaryButton("تمام", Modifier.fillMaxWidth()) { onDismiss() }
            }
        }
    }
}
