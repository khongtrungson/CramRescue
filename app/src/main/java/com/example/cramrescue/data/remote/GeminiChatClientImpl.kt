package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.chat.ChatPrompt
import com.example.cramrescue.domain.chat.GeminiChatClient
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class GeminiChatClientImpl : GeminiChatClient {

    override val modelId: String = GeminiConfig.MODEL

    override fun streamChat(apiKey: String, prompt: ChatPrompt): Flow<String> = flow {
        coroutineScope {
            val requestJson = GeminiChatJson.createStreamChatRequest(prompt).toString()
            val url = "${GeminiConfig.BASE_URL}/models/${GeminiConfig.MODEL}:streamGenerateContent?alt=sse"

            val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = GeminiConfig.CONNECT_TIMEOUT_MILLIS
                readTimeout = GeminiConfig.READ_TIMEOUT_MILLIS
                useCaches = false
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                doOutput = true
            }

            val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }

            try {
                connection.outputStream.use { it.write(requestJson.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    throw RemoteFailureException(
                        GeminiJson.failureForStatus(code, connection.getHeaderField("Retry-After"), errorBody.orEmpty())
                    )
                }

                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        currentCoroutineContext().ensureActive()
                        val delta = GeminiChatJson.parseSseLine(line)
                        if (!delta.isNullOrEmpty()) {
                            emit(delta)
                        }
                        line = reader.readLine()
                    }
                }
            } catch (e: RemoteFailureException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw RemoteFailureException(RemoteFailure.Timeout)
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw RemoteFailureException(RemoteFailure.Network)
            } finally {
                cancellationWatcher.cancel()
                connection.disconnect()
            }
        }
    }.flowOn(Dispatchers.IO)
}
