package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.AnalysisDraft
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.StudyAnalysisClient
import com.example.cramrescue.domain.analysis.StudyAnalysisPrompt
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The only place the app talks to the network. Single official endpoint
 * (generativelanguage.googleapis.com, requirements.md §5.3), HTTPS only with the platform's
 * default certificate/hostname validation, no logging of any kind.
 */
object GeminiConfig {
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    // The one place the model is named. Gemini 2.5 Flash by product-owner choice (2026-09-24).
    // Note: the official models page (September 2026) lists the 2.5 series as limited access for
    // active prior users; "Kiểm tra kết nối" (models.get) confirms whether a key can use it.
    const val MODEL = "gemini-3.5-flash"

    const val CONNECT_TIMEOUT_MILLIS = 15_000

    // requirements.md §5.1 targets ≤45 s from OCR to result; allow some network margin.
    const val READ_TIMEOUT_MILLIS = 120_000
}

/**
 * Plain HttpsURLConnection + platform org.json: no SDK and no new runtime dependency. The key is
 * sent only in the documented `x-goog-api-key` header — never in the URL — so it can't end up in
 * a URL-bearing log line or exception.
 */
class GeminiStudyAnalysisClient : StudyAnalysisClient {

    override val modelId: String = GeminiConfig.MODEL

    // models.get: authenticates the key and confirms access to our model, with zero tokens used.
    override suspend fun verifyCredential(apiKey: String) {
        execute("GET", "${GeminiConfig.BASE_URL}/models/${GeminiConfig.MODEL}", apiKey, body = null)
    }

    override suspend fun analyze(apiKey: String, prompt: StudyAnalysisPrompt): AnalysisDraft {
        val request = GeminiJson.interactionRequest(GeminiConfig.MODEL, prompt).toString()
        val response = execute("POST", "${GeminiConfig.BASE_URL}/interactions", apiKey, request)
        return GeminiJson.parseInteractionResponse(response)
    }

    private suspend fun execute(method: String, url: String, apiKey: String, body: String?): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpsURLConnection
            // Blocking socket reads ignore coroutine cancellation, so disconnect from a sibling
            // coroutine when the caller is cancelled (e.g. the user leaves the screen).
            val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = GeminiConfig.CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = GeminiConfig.READ_TIMEOUT_MILLIS
                connection.useCaches = false
                connection.setRequestProperty("x-goog-api-key", apiKey)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }

                val code = connection.responseCode
                if (code in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    throw RemoteFailureException(
                        GeminiJson.failureForStatus(code, connection.getHeaderField("Retry-After"), errorBody.orEmpty())
                    )
                }
            } catch (e: RemoteFailureException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw RemoteFailureException(RemoteFailure.Timeout)
            } catch (e: IOException) {
                // A disconnect caused by cancellation surfaces here; rethrow it as cancellation.
                ensureActive()
                throw RemoteFailureException(RemoteFailure.Network)
            } finally {
                cancellationWatcher.cancel()
                connection.disconnect()
            }
        }
}
