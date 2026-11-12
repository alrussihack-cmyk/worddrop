package com.wordpop.app

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Date
import java.util.UUID

/**
 * Reports an anonymous device heartbeat so WordControl can list devices.
 * Sends no personal data: a random install id, phone model, Android and app version.
 * At most once every 6 hours.
 */
object DeviceReporter {
    private const val TAG = "DeviceReporter"
    private const val PREFS = "wordpop_device"
    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun report(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("lastReport", 0L) < INTERVAL_MS) return

        val installId = prefs.getString("installId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installId", it).apply()
        }
        val firstSeen = prefs.getLong("firstSeen", 0L).takeIf { it > 0 } ?: now.also {
            prefs.edit().putLong("firstSeen", it).apply()
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().take(20)

        val data = hashMapOf<String, Any>(
            "installId" to installId,
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60),
            "osVersion" to "Android ${Build.VERSION.RELEASE}".take(30),
            "appVersion" to version,
            "firstSeen" to Timestamp(Date(firstSeen)),
            "lastSeen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("devices").document(installId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { prefs.edit().putLong("lastReport", now).apply() }
            .addOnFailureListener { Log.w(TAG, "Device report failed; will retry next launch.", it) }
    }
}
