package com.example.cramrescue.domain.usecase.settings

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.StudyAnalysisClient
import com.example.cramrescue.domain.repository.GeminiCredentialRepository

sealed interface CredentialCheckResult {
    data object Valid : CredentialCheckResult
    data object Blank : CredentialCheckResult
    data object NotConfigured : CredentialCheckResult
    data class Failed(val failure: RemoteFailure) : CredentialCheckResult
}

/**
 * Validates a newly entered key against the Gemini API and saves it only if it is accepted, so an
 * invalid key is never stored. A network/rate-limit failure also leaves the stored key unchanged.
 */
class SaveGeminiCredentialUseCase(
    private val credentialRepository: GeminiCredentialRepository,
    private val client: StudyAnalysisClient
) {
    suspend operator fun invoke(rawKey: String): CredentialCheckResult {
        val apiKey = rawKey.trim()
        if (apiKey.isEmpty()) return CredentialCheckResult.Blank
        return try {
            client.verifyCredential(apiKey)
            credentialRepository.saveCredential(apiKey)
            CredentialCheckResult.Valid
        } catch (e: RemoteFailureException) {
            CredentialCheckResult.Failed(e.failure)
        }
    }
}

/** "Kiểm tra kết nối" (FR-16) for the already-stored key. Runs only when the user asks. */
class CheckStoredGeminiCredentialUseCase(
    private val credentialRepository: GeminiCredentialRepository,
    private val client: StudyAnalysisClient
) {
    suspend operator fun invoke(): CredentialCheckResult {
        val apiKey = credentialRepository.getCredential() ?: return CredentialCheckResult.NotConfigured
        return try {
            client.verifyCredential(apiKey)
            CredentialCheckResult.Valid
        } catch (e: RemoteFailureException) {
            CredentialCheckResult.Failed(e.failure)
        }
    }
}
