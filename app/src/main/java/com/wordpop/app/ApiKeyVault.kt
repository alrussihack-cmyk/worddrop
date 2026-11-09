package com.wordpop.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the OpenRouter API key encrypted with an AES-GCM key held by Android Keystore.
 * The preferences file is excluded from Android Auto Backup because its Keystore key is
 * device-bound and cannot be restored to another device.
 */
internal object ApiKeyVault {
    private const val TAG = "ApiKeyVault"
    private const val PREFS = "wordpop_secrets"
    private const val PREF_KEY = "openrouter_api_key"
    private const val LEGACY_KEY = "orKey"
    private const val KEY_ALIAS = "com.wordpop.app.openrouter.v1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val lock = Any()

    fun get(context: Context): String = synchronized(lock) {
        val prefs = secretPrefs(context)
        val encoded = prefs.getString(PREF_KEY, null) ?: return@synchronized ""
        try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_BYTES) { "Invalid encrypted API-key payload" }
            val iv = payload.copyOfRange(0, IV_BYTES)
            val encrypted = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Stored OpenRouter key could not be decrypted; it must be entered again.", e)
            prefs.edit().remove(PREF_KEY).apply()
            ""
        }
    }

    fun set(context: Context, value: String) = synchronized(lock) {
        val prefs = secretPrefs(context)
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            prefs.edit().remove(PREF_KEY).apply()
            return@synchronized
        }
        encryptAndStore(prefs, normalized)
    }

    /**
     * One-time, loss-averse migration: remove the plaintext legacy value only after
     * the encrypted replacement has been committed successfully.
     */
    fun migrateLegacy(context: Context, legacyPrefs: SharedPreferences) = synchronized(lock) {
        val legacy = legacyPrefs.getString(LEGACY_KEY, null) ?: return@synchronized
        val prefs = secretPrefs(context)

        if (prefs.contains(PREF_KEY) && get(context).isNotBlank()) {
            check(legacyPrefs.edit().remove(LEGACY_KEY).commit()) {
                "Could not remove the legacy plaintext OpenRouter key"
            }
            return@synchronized
        }
        if (legacy.isBlank()) {
            legacyPrefs.edit().remove(LEGACY_KEY).commit()
            return@synchronized
        }

        try {
            encryptAndStore(prefs, legacy.trim())
            check(legacyPrefs.edit().remove(LEGACY_KEY).commit()) {
                "Could not remove the legacy plaintext OpenRouter key"
            }
        } catch (e: Exception) {
            // Keep the old value locally rather than silently destroying it.
            Log.e(TAG, "Could not migrate the legacy OpenRouter key.", e)
        }
    }

    private fun secretPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encryptAndStore(prefs: SharedPreferences, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(prefs.edit().putString(PREF_KEY, encoded).commit()) {
            "Could not persist the encrypted OpenRouter key"
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
