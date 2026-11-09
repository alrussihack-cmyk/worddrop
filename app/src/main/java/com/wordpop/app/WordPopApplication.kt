package com.wordpop.app

import android.app.Application

class WordPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load imported vocabulary before any screen or overlay asks WordRepo for words.
        WordRepo.init(this)
        // Run the plaintext API-key migration as early as possible after an app upgrade.
        SettingsStore(this)
    }
}
