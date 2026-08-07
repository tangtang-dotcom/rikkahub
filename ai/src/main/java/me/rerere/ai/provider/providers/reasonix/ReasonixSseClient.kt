package me.rerere.ai.provider.providers.reasonix

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Reasonix SSE 客户端 — 连接 /events 端点，实时接收服务端推送的消息流。
 * 移植自 DeepSeek-Reasonix-android `ReasonixSseClient.kt`，增加认证头支持。
 */
class ReasonixSseClient(
    private val baseUrl: String,
    private val username: String = "",
    private val password: String = "",
    private val token: String = "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun connect(): Flow<SseEvent> = callbackFlow {
        val request =
            Request.Builder()
                .url(baseUrl.toHttpUrl()!!.resolve("/events")!!)
                .header("Accept", "text/event-stream")
                .applyAuth()
                .build()

        val listener =
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    try {
                        val event = json.decodeFromString<SseEvent>(data)
                        trySend(event)
                    } catch (_: Exception) {
                        // 解析失败则忽略
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    close(t)
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }

        val factory = EventSources.createFactory(client)
        val eventSource = factory.newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
        } else if (username.isNotBlank() || password.isNotBlank()) {
            header("Authorization", okhttp3.Credentials.basic(username, password))
        }
        return this
    }
}
