package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.quiz.QuizClient
import com.example.cramrescue.domain.quiz.QuizDraft
import com.example.cramrescue.domain.quiz.QuizPrompt
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

class GeminiQuizClient : QuizClient {

    override val modelId: String = GeminiConfig.MODEL

    override suspend fun generateQuiz(apiKey: String, prompt: QuizPrompt): QuizDraft {
        val request = GeminiQuizJson.interactionRequest(GeminiConfig.MODEL, prompt).toString()
        val response = execute("POST", "${GeminiConfig.BASE_URL}/interactions", apiKey, request)
        return GeminiQuizJson.parseInteractionResponse(response)
    }

    private suspend fun execute(method: String, url: String, apiKey: String, body: String?): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpsURLConnection
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
                ensureActive()
                throw RemoteFailureException(RemoteFailure.Network)
            } finally {
                cancellationWatcher.cancel()
                connection.disconnect()
            }
        }
}
