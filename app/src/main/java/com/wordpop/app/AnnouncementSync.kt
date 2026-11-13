package com.wordpop.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Announcement(val id: String, val title: String, val body: String, val kind: String)

/** Receives the single announcement written from WordControl. Each one is shown once per id. */
object AnnouncementSync {
    private const val TAG = "AnnouncementSync"
    private const val PREFS = "wordpop_announcement"

    @Volatile private var started = false

    var current: Announcement? by mutableStateOf(null)
        private set

    @Synchronized
    fun start(context: Context) {
        if (started || FirebaseApp.getApps(context).isEmpty()) return
        started = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        FirebaseFirestore.getInstance().collection("appConfig").document("announcement")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "Could not read announcement.", error)
                    return@addSnapshotListener
                }
                val active = snap?.getBoolean("active") == true
                val id = snap?.getString("id").orEmpty()
                val title = snap?.getString("title").orEmpty()
                val body = snap?.getString("body").orEmpty()
                current = if (active && id.isNotEmpty() && body.isNotBlank() &&
                    prefs.getString("dismissed", "") != id
                ) {
                    Announcement(id, title, body, snap?.getString("kind") ?: "info")
                } else null
            }
    }

    fun dismiss(context: Context) {
        val a = current ?: return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("dismissed", a.id).apply()
        current = null
    }
}

@androidx.compose.runtime.Composable
fun AnnouncementHost() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val a = AnnouncementSync.current ?: return
    val alert = a.kind == "alert"
    androidx.compose.ui.window.Dialog(onDismissRequest = { AnnouncementSync.dismiss(ctx) }) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = androidx.compose.ui.graphics.Color.White,
            border = androidx.compose.foundation.BorderStroke(2.5.dp, Night),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Boby(if (alert) Mood.Think else Mood.Cheer, 72.dp, Modifier.bob())
                if (a.title.isNotBlank()) {
                    Text(
                        a.title, fontSize = 22.sp, fontFamily = Display,
                        color = if (alert) Danger else Night, textAlign = TextAlign.Center
                    )
                }
                Text(a.body, fontSize = 16.sp, lineHeight = 24.sp, color = Muted, textAlign = TextAlign.Center)
                PrimaryButton("تمام", Modifier.fillMaxWidth()) { AnnouncementSync.dismiss(ctx) }
            }
        }
    }
}
