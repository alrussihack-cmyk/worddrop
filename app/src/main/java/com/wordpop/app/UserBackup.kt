package com.wordpop.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Lets the learner save and restore their own data: words, favourites, stories, progress and settings. */
object UserBackup {
    fun export(ctx: Context, store: SettingsStore, uri: Uri): String = try {
        val o = JSONObject().put("app", "wordpop").put("v", 1)
            .put("prefs", store.exportAll()).put("custom", CustomWords.exportRaw(ctx))
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
        "اتحفظت بياناتك في الملف."
    } catch (e: Exception) {
        "مقدرتش أحفظ الملف."
    }

    fun restore(ctx: Context, store: SettingsStore, uri: Uri): String = try {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        val o = JSONObject(text)
        when {
            o.optString("app") != "wordpop" -> "الملف ده مش نسخة من ووردبوب."
            !store.importAll(o.getString("prefs")) -> "الملف تالف."
            else -> {
                CustomWords.importRaw(ctx, o.optString("custom", "[]"))
                WordRepo.init(ctx)
                "اتسترجعت بياناتك."
            }
        }
    } catch (e: Exception) {
        "مقدرتش أقرأ الملف."
    }
}
