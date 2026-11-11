package com.wordpop.app

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import java.util.concurrent.TimeUnit

/**
 * Initializes Firebase App Check before WordPop starts using Firestore or the shared API Worker.
 * Debug builds use Firebase's one-device debug provider; release builds use Play Integrity.
 */
class WordPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.w(TAG, "Firebase is not configured; cloud and protected API features stay unavailable.")
            return
        }

        AppCheckProviderInstaller.install()
    }

    private companion object {
        const val TAG = "WordPopApplication"
    }
}

/** Obtains a fresh, SDK-managed App Check token for requests to the Cloudflare Worker. */
object AppCheckTokens {
    fun currentToken(): String {
        return try {
            val token = Tasks.await(
                FirebaseAppCheck.getInstance().getAppCheckToken(false),
                30,
                TimeUnit.SECONDS
            ).token
            token.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Firebase returned an empty App Check token.")
        } catch (error: Exception) {
            throw IllegalStateException(
                "تعذر التحقق من أمان التطبيق. اتأكد من اتصال الإنترنت وحاول مرة تانية.",
                error
            )
        }
    }
}
