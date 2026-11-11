package com.wordpop.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (the sideloaded APK) use Firebase's debug provider with ONE fixed secret,
 * so every install and update is already trusted and nothing has to be registered again.
 * Register this single value once in Firebase Console, App Check, Manage debug tokens.
 * This file exists only in the debug source set; release builds use Play Integrity.
 */
internal object AppCheckProviderInstaller {
    private const val DEBUG_SECRET = "e7126859-d1eb-4874-a842-8133df9bfae8"
    private const val PREFS_PREFIX = "com.google.firebase.appcheck.debug.store."
    private const val KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    fun install(context: Context) {
        val app = FirebaseApp.getInstance()
        context.getSharedPreferences(PREFS_PREFIX + app.persistenceKey, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, DEBUG_SECRET)
            .commit()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
