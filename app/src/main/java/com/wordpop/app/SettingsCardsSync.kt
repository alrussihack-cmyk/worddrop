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
 * Downloads the administrator-controlled visibility map for the 12 settings cards.
 * The last known map is cached locally so settings remain usable without a connection.
 */
object SettingsCardsSync {
    private const val TAG = "SettingsCardsSync"
    private const val PREFS = "wordpop_settings_cards"
    private const val CACHE_KEY = "visibility"

    val cardIds = listOf(
        "pause", "interval", "hours", "content", "level", "display",
        "pronunciation", "easyvoice", "position", "appearance", "dnd", "maintenance"
    )

    private val defaults = cardIds.associateWith { true }

    @Volatile
    private var registration: ListenerRegistration? = null

    var visibility: Map<String, Boolean> by mutableStateOf(defaults)
        private set

    var status: String by mutableStateOf("في انتظار مزامنة إعدادات البطاقات")
        private set

    val visibleCardIds: List<String>
        get() {
            val current = visibility
            return cardIds.filter { current[it] != false }.ifEmpty { cardIds }
        }

    @Synchronized
    fun start(context: Context) {
        if (registration != null) return

        loadCache(context)
        if (FirebaseApp.getApps(context).isEmpty()) {
            status = "Firebase غير مهيأ؛ كل بطاقات الإعدادات ظاهرة."
            return
        }

        registration = FirebaseFirestore.getInstance()
            .collection("appConfig")
            .document("settingsCards")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    status = "تعذر تحميل إعدادات البطاقات: " +
                        (error.localizedMessage ?: error.javaClass.simpleName)
                    Log.w(TAG, "Could not sync settings-card visibility; keeping local cache.", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    status = "في انتظار إعدادات البطاقات من السحابة."
                    return@addSnapshotListener
                }

                if (!snapshot.exists()) {
                    visibility = defaults
                    saveCache(context, defaults)
                    status = "لم يتم تخصيص البطاقات بعد؛ كل البطاقات ظاهرة."
                    return@addSnapshotListener
                }

                val remote = snapshot.get("cards") as? Map<*, *>
                val updated = cardIds.associateWith { id ->
                    (remote?.get(id) as? Boolean) ?: true
                }
                // A configuration with all cards disabled is treated as invalid to avoid a blank page.
                visibility = if (updated.values.none { it }) defaults else updated
                saveCache(context, visibility)

                status = if (snapshot.metadata.isFromCache) {
                    "تم تحميل إعدادات البطاقات المحفوظة على الجهاز."
                } else {
                    "تم تحديث ظهور البطاقات من WordPop Control."
                }
            }
    }

    private fun loadCache(context: Context) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null) ?: return

        try {
            val json = JSONObject(raw)
            val cached = cardIds.associateWith { id -> json.optBoolean(id, true) }
            visibility = if (cached.values.none { it }) defaults else cached
            status = "تم تحميل آخر إعدادات البطاقات المحفوظة."
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read cached settings-card visibility.", exception)
        }
    }

    private fun saveCache(context: Context, values: Map<String, Boolean>) {
        val json = JSONObject()
        values.forEach { (id, visible) -> json.put(id, visible) }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CACHE_KEY, json.toString())
            .apply()
    }
}
