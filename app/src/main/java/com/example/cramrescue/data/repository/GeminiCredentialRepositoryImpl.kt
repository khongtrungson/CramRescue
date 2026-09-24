package com.example.cramrescue.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "cramrescue_gemini_credential"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128

// Must match the exclusions in res/xml/backup_rules.xml and data_extraction_rules.xml.
private const val PREFS_NAME = "gemini_credential"
private const val PREF_CIPHERTEXT = "ciphertext"
private const val PREF_IV = "iv"

/**
 * Stores the BYOK Gemini key encrypted with a non-exportable AES-256-GCM key held in the Android
 * Keystore. Only ciphertext + IV reach SharedPreferences; the plaintext never touches disk.
 *
 * androidx.security's EncryptedSharedPreferences is deprecated (security-crypto 1.1.0, "in favour
 * of existing platform APIs and direct use of Android Keystore"), so this uses Keystore directly.
 *
 * Nothing here logs, and no exception message contains the key.
 */
class GeminiCredentialRepositoryImpl(context: Context) : GeminiCredentialRepository {

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun hasCredential(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(PREF_CIPHERTEXT) && prefs.contains(PREF_IV)
    }

    override suspend fun getCredential(): String? = withContext(Dispatchers.IO) {
        val ciphertext = prefs.getString(PREF_CIPHERTEXT, null) ?: return@withContext null
        val iv = prefs.getString(PREF_IV, null) ?: return@withContext null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, decode(iv)))
            String(cipher.doFinal(decode(ciphertext)), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // The Keystore key is gone or changed (e.g. data restored onto another device): the
            // ciphertext is unrecoverable, so drop it and report "not configured".
            clear()
            null
        } catch (e: IllegalArgumentException) {
            clear()
            null
        }
    }

    override suspend fun saveCredential(apiKey: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_CIPHERTEXT, encode(ciphertext))
            .putString(PREF_IV, encode(cipher.iv))
            .commit()
        Unit
    }

    override suspend fun clearCredential() = withContext(Dispatchers.IO) { clear() }

    private fun clear() {
        prefs.edit().remove(PREF_CIPHERTEXT).remove(PREF_IV).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
