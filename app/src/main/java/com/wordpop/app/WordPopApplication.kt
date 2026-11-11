package com.wordpop.app

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import java.util.concurrent.TimeUnit

/** Initializes Firebase security and the 1.9 data services before the first screen. */
class WordPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        // Local learning must remain available even if Firebase is temporarily unavailable.
        WordRepo.init(this)
        SettingsStore(this)

        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.w(TAG, "Firebase is not configured; cloud and protected API features stay unavailable.")
            return
        }

        AppCheckProviderInstaller.install(this)
        AiConfigSync.start(this)
        WordCloudSync.start(this)
        SettingsCardsSync.start(this)
    }

    private companion object {
        const val TAG = "WordPopApplication"
    }
}

/** Obtains an SDK-managed App Check token for protected Cloudflare Worker requests. */
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
