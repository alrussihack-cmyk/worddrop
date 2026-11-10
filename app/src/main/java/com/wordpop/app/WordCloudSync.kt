package com.wordpop.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirrors the active shared vocabulary from WordPop Control into a local cache.
 * Firestore's Android offline persistence is enabled by default; SharedPreferences
 * gives WordRepo a fast, stable copy immediately when the app launches offline.
 */
object WordCloudSync {
    private const val TAG = "WordCloudSync"

    @Volatile
    private var registration: ListenerRegistration? = null

    @Synchronized
    fun start(context: Context) {
        if (registration != null) return

        // Firebase is optional until the app's own google-services.json is installed.
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "Firebase is not configured yet; using built-in and local words.")
            return
        }

        registration = FirebaseFirestore.getInstance()
            .collection("words")
            .whereEqualTo("active", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    // Keep the last successful local cache if the device is offline or rules reject access.
                    Log.w(TAG, "Cloud vocabulary sync failed; keeping cached words.", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener
                // An empty first cache snapshot must not erase our previously saved offline copy.
                if (snapshot.metadata.isFromCache && snapshot.isEmpty) {
                    return@addSnapshotListener
                }

                val words = snapshot.documents.mapNotNull { document ->
                    val cloudId = document.getLong("id") ?: return@mapNotNull null
                    val english = document.getString("en")?.trim().orEmpty()
                    val arabic = document.getString("ar")?.trim().orEmpty()
                    if (cloudId <= 0L || cloudId > Int.MAX_VALUE || english.isBlank() || arabic.isBlank()) {
                        return@mapNotNull null
                    }

                    Word(
                        // Negative IDs keep cloud cards separate from built-in and user-imported IDs.
                        id = -cloudId.toInt(),
                        en = english,
                        ipa = document.getString("ipa").orEmpty(),
                        ar = arabic,
                        kind = document.getString("kind")?.ifBlank { "كلمة" } ?: "كلمة",
                        example = document.getString("ex").orEmpty(),
                        exampleAr = document.getString("exAr").orEmpty(),
                        level = document.getString("level")?.ifBlank { "A" } ?: "A"
                    )
                }.sortedBy { -it.id }

                WordRepo.setCloudWords(context.applicationContext, words)
                Log.i(TAG, "Synced ${words.size} active cloud words.")
            }
    }
}

/** Durable local copy of the last successful cloud snapshot. */
object CloudWordsStore {
    private const val PREFS = "wordpop_cloud_words"
    private const val KEY = "active_words"

    fun load(context: Context): List<Word> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Word(
                    id = item.getInt("id"),
                    en = item.getString("en"),
                    ipa = item.optString("ipa"),
                    ar = item.getString("ar"),
                    kind = item.optString("kind", "كلمة"),
                    example = item.optString("ex"),
                    exampleAr = item.optString("exAr"),
                    level = item.optString("level", "A")
                )
            }
        } catch (exception: Exception) {
            Log.w("CloudWordsStore", "Could not read cached cloud words.", exception)
            emptyList()
        }
    }

    fun save(context: Context, words: List<Word>) {
        val array = JSONArray()
        words.forEach { word ->
            array.put(
                JSONObject()
                    .put("id", word.id)
                    .put("en", word.en)
                    .put("ipa", word.ipa)
                    .put("ar", word.ar)
                    .put("kind", word.kind)
                    .put("ex", word.example)
                    .put("exAr", word.exampleAr)
                    .put("level", word.level)
            )
        }

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
