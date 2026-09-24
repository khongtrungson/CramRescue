package com.example.cramrescue.domain.analysis

/**
 * Untrusted, unvalidated analysis as decoded from the model's structured JSON. Nothing here may
 * reach Room or the UI before [StudyAnalysisValidator] accepts it.
 */
data class AnalysisDraft(
    val summary: String,
    val priorities: List<DraftPriority>
)

data class DraftPriority(
    val title: String,
    /** Raw triage string; mapped to a domain enum only by the validator. */
    val triage: String,
    val bullets: List<String>,
    val sourceBlockIds: List<String>
)

/** What went wrong talking to the AI service, in categories the UI can act on. */
sealed interface RemoteFailure {
    /** 401/403, or 400 API_KEY_INVALID: wrong, expired, restricted or no-longer-accepted key. */
    data object InvalidCredential : RemoteFailure

    /** 429. [retryAfterSeconds] comes from the Retry-After header when the service sends one. */
    data class RateLimited(val retryAfterSeconds: Long?) : RemoteFailure

    /** 402: prepaid credit / quota exhausted. */
    data object QuotaExhausted : RemoteFailure

    /** No connectivity, DNS or connection failure. */
    data object Network : RemoteFailure

    data object Timeout : RemoteFailure

    /** 5xx. */
    data object Service : RemoteFailure

    /** Any other 4xx — the request itself was rejected (an app-side problem). */
    data object RequestRejected : RemoteFailure

    /** The call succeeded but the body wasn't the structured JSON we asked for. */
    data object MalformedResponse : RemoteFailure
}

/**
 * Thrown by [StudyAnalysisClient]. The message is only the category name — never a key, header,
 * URL or response body.
 */
class RemoteFailureException(val failure: RemoteFailure) :
    Exception("AI service call failed: ${failure::class.simpleName}")

/**
 * The boundary to the AI service. The domain never sees HTTP or Gemini types. The API key is
 * passed per call rather than held by the client, so it only lives as long as that call.
 */
interface StudyAnalysisClient {
    /** Identifier of the model analyses are generated with (stored with each analysis). */
    val modelId: String

    /** Cheap credential check that consumes no generation tokens. Throws [RemoteFailureException]. */
    suspend fun verifyCredential(apiKey: String)

    /** Throws [RemoteFailureException]. */
    suspend fun analyze(apiKey: String, prompt: StudyAnalysisPrompt): AnalysisDraft
}
