package com.wordpop.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class WordPopAgeGroup { CHILD, TEEN, ADULT }

object WordPopAgeProfile {
    @Volatile private var currentGroup: WordPopAgeGroup? = null

    /** External AI/TTS endpoints are available only to adult profiles. */
    fun allowsExternalServices(): Boolean = currentGroup == WordPopAgeGroup.ADULT

    private const val PREFS = "wordpop_age_profile"
    private const val KEY = "age_group"

    fun get(context: Context): WordPopAgeGroup? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val group = runCatching { value?.let(WordPopAgeGroup::valueOf) }.getOrNull()
        currentGroup = group
        return group
    }

    fun save(context: Context, group: WordPopAgeGroup) {
        currentGroup = group
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, group.name).apply()
    }
}

@Composable
fun WordPopAgeGate(onContinue: (WordPopAgeGroup) -> Unit) {
    var selected by remember { mutableStateOf<WordPopAgeGroup?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("قبل ما نبدأ", style = MaterialTheme.typography.headlineMedium)
            Text(
                "اختار الفئة العمرية المناسبة للمستخدم. الاختيار بيحدد إعدادات الخصوصية والإعلانات.",
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            listOf(
                WordPopAgeGroup.CHILD to "أقل من 13 سنة",
                WordPopAgeGroup.TEEN to "من 13 إلى 17 سنة",
                WordPopAgeGroup.ADULT to "18 سنة أو أكثر"
            ).forEach { (group, label) ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == group, onClick = { selected = group })
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Button(
                onClick = { selected?.let(onContinue) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                Text("متابعة")
            }
        }
    }
}
