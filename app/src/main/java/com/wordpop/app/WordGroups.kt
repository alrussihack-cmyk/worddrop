package com.wordpop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WordGroup(val key: String, val title: String, val count: Int, val deletable: Boolean)

/** Switch groups of words off or delete the learner's own ones: WordControl words, own words, categories. */
@Composable
fun WordGroupsSection(store: SettingsStore, ink: Color, muted: Color, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<WordGroup?>(null) }
    var note by remember { mutableStateOf("") }
    val off = version.let { store.disabledGroups }
    val groups = version.let {
        val words = WordRepo.all
        buildList {
            words.count { it.id < 0 }.takeIf { it > 0 }?.let { add(WordGroup("cloud", "كلمات من WordControl", it, false)) }
            words.count { WordRepo.groupOf(it) == "mine" }.takeIf { it > 0 }?.let { add(WordGroup("mine", "كلماتي بدون تصنيف", it, true)) }
            WordRepo.categories().forEach { c ->
                add(WordGroup("cat:$c", c, words.count { WordRepo.groupOf(it) == "cat:$c" }, true))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إدارة الكلمات", fontSize = 20.sp, fontFamily = Display, color = ink)
        Text("عطّل أي مجموعة عشان متظهرش في الإشعارات والمراجعة والاختبار، أو امسح كلماتك الخاصة.", fontSize = 13.sp, color = muted)
        if (groups.isEmpty()) {
            Text("مفيش مجموعات كلمات إضافية لسه.", fontSize = 13.sp, color = muted)
        }
        groups.forEach { g ->
            val on = g.key !in off
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 2)
                    Text(if (on) "${g.count} كلمة" else "${g.count} كلمة • معطّلة", fontSize = 12.sp, color = muted)
                }
                if (g.deletable) {
                    TextButton(onClick = { confirm = g }) { Text("مسح", color = Danger, fontWeight = FontWeight.Bold) }
                }
                Switch(
                    checked = on,
                    onCheckedChange = { value ->
                        store.disabledGroups = if (value) store.disabledGroups - g.key else store.disabledGroups + g.key
                        version++
                        onChanged()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Teal, checkedThumbColor = Night, checkedBorderColor = Night)
                )
            }
        }
        if (note.isNotEmpty()) Text(note, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
    }

    confirm?.let { g ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("مسح «${g.title}»؟") },
            text = { Text("هتتمسح ${g.count} كلمة من كلماتك نهائيًا، ومش هتقدر ترجّعها غير من نسخة احتياطية.") },
            confirmButton = {
                TextButton(onClick = {
                    val n = WordRepo.deleteGroup(ctx, g.key)
                    store.disabledGroups = store.disabledGroups - g.key
                    note = "اتمسح $n كلمة."
                    confirm = null
                    version++
                    onChanged()
                }) { Text("امسح", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("لأ") } }
        )
    }
}
