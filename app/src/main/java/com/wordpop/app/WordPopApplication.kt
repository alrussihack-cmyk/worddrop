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

        AppCheckProviderInstaller.install()
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

/**
 * Reads the App Check debug token that Firebase's debug provider generated for this install,
 * so it can be registered in Firebase Console without Logcat. Returns null on release builds.
 */
object AppCheckDebugToken {
    private const val PREFS_PREFIX = "com.google.firebase.appcheck.debug.store."
    private const val KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    fun read(context: android.content.Context): String? {
        return try {
            val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles { f -> f.name.startsWith(PREFS_PREFIX) && f.name.endsWith(".xml") }
                ?.firstNotNullOfOrNull { f ->
                    context.getSharedPreferences(f.name.removeSuffix(".xml"), android.content.Context.MODE_PRIVATE)
                        .getString(KEY, null)?.takeIf { it.isNotBlank() }
                }
        } catch (_: Exception) {
            null
        }
    }
}
