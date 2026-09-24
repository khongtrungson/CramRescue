package com.example.cramrescue.domain.repository

/**
 * The user's own Gemini API key (BYOK). Implementations must keep it encrypted at rest and must
 * never log it. Callers should read it immediately before a call and not hold on to it.
 */
interface GeminiCredentialRepository {
    suspend fun hasCredential(): Boolean
    suspend fun getCredential(): String?
    suspend fun saveCredential(apiKey: String)

    /** Removes only the key — campaigns, OCR and analyses are unaffected. */
    suspend fun clearCredential()
}
