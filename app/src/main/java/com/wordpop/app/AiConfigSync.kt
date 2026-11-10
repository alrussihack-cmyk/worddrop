package com.wordpop.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import org.json.JSONObject

/**
 * Public runtime settings for the shared AI/TTS Worker.
 * The OpenRouter key is never read or stored by this app; Firestore contains only
 * an AES-GCM ciphertext that can be decrypted by the Cloudflare Worker.
 */
object AiConfigSync {
    private const val TAG = "AiConfigSync"
    private const val PREFS = "wordpop_ai_runtime_cache"
    private const val URL_KEY = "workerUrl"
    private const val ENABLED_KEY = "enabled"
    private const val CONFIGURED_KEY = "configured"

    @Volatile
    private var registration: ListenerRegistration? = null

    var workerUrl: String by mutableStateOf("")
        private set

    var enabled: Boolean by mutableStateOf(false)
        private set

    var keyConfigured: Boolean by mutableStateOf(false)
        private set

    var status: String by mutableStateOf("في انتظار إعدادات الذكاء الاصطناعي من WordPop Control")
        private set

    val isReady: Boolean
        get() = enabled && keyConfigured && workerUrl.startsWith("https://")

    @Synchronized
    fun start(context: Context) {
        if (registration != null) return
        loadCache(context)

        if (FirebaseApp.getApps(context).isEmpty()) {
            status = "Firebase غير مهيأ؛ خدمات الذكاء الاصطناعي غير متاحة."
            return
        }

        registration = FirebaseFirestore.getInstance()
            .collection("appConfig")
            .document("aiRuntime")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    status = "تعذر تحديث إعدادات الذكاء الاصطناعي؛ سيتم استخدام آخر إعدادات محفوظة."
                    Log.w(TAG, "Could not sync AI runtime configuration.", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    workerUrl = ""
                    enabled = false
                    keyConfigured = false
                    status = "لم يضبط المسؤول إعدادات الذكاء الاصطناعي بعد."
                    saveCache(context)
                    return@addSnapshotListener
                }

                workerUrl = snapshot.getString("workerUrl")
                    ?.trim()
                    ?.trimEnd('/')
                    ?.takeIf { it.startsWith("https://") }
                    .orEmpty()
                enabled = snapshot.getBoolean("enabled") == true
                keyConfigured = !snapshot.getString("encryptedKey").isNullOrBlank() &&
                    !snapshot.getString("iv").isNullOrBlank()

                status = when {
                    snapshot.metadata.isFromCache -> "تم تحميل آخر إعدادات محفوظة على الجهاز."
                    isReady -> "خدمة الذكاء الاصطناعي جاهزة من WordPop Control."
                    !keyConfigured -> "مفتاح OpenRouter غير مضبوط من WordPop Control."
                    !enabled -> "خدمة الذكاء الاصطناعي متوقفة من WordPop Control."
                    else -> "رابط خدمة الذكاء الاصطناعي غير مضبوط."
                }
                saveCache(context)
            }
    }

    private fun loadCache(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        workerUrl = prefs.getString(URL_KEY, "").orEmpty()
            .trim().trimEnd('/').takeIf { it.startsWith("https://") }.orEmpty()
        enabled = prefs.getBoolean(ENABLED_KEY, false)
        keyConfigured = prefs.getBoolean(CONFIGURED_KEY, false)
        if (workerUrl.isNotBlank() || keyConfigured) {
            status = "تم تحميل آخر إعدادات الذكاء الاصطناعي المحفوظة."
        }
    }

    private fun saveCache(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(URL_KEY, workerUrl)
            .putBoolean(ENABLED_KEY, enabled)
            .putBoolean(CONFIGURED_KEY, keyConfigured)
            .apply()
    }
}
