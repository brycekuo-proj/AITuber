package com.aituber.poc.claudechat

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams one reply from the Claude Messages API with the user's own API key.
 * The key goes to api.anthropic.com and nowhere else. Callbacks run on the
 * main thread; [Call.cancel] stops a reply mid-way (no callback after it).
 */
class ClaudeApiClient(
    private val endpoint: String = ENDPOINT
) {
    interface Listener {
        fun onText(text: String)
        fun onComplete(stopReason: String?)
        fun onError(error: ClaudeChatError)
    }

    class Call internal constructor() {
        internal val cancelled = AtomicBoolean(false)
        @Volatile internal var connection: HttpURLConnection? = null

        fun cancel() {
            if (cancelled.getAndSet(true)) return
            // disconnect() may do network I/O (TLS close): never on the main thread.
            val open = connection ?: return
            Thread({ runCatching { open.disconnect() } }, "aituber-claude-cancel").apply {
                isDaemon = true
                start()
            }
        }

        val isCancelled: Boolean get() = cancelled.get()
    }

    private val main = Handler(Looper.getMainLooper())

    fun stream(apiKey: String, requestJson: String, listener: Listener): Call {
        val call = Call()
        Thread({ run(call, apiKey, requestJson, listener) }, "aituber-claude-stream").apply {
            isDaemon = true
            start()
        }
        return call
    }

    private fun run(call: Call, apiKey: String, requestJson: String, listener: Listener) {
        fun post(block: () -> Unit) = main.post { if (!call.isCancelled) block() }
        var finished = false
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", API_VERSION)
                setRequestProperty("content-type", "application/json")
                setRequestProperty("accept", "text/event-stream")
            }
            call.connection = connection
            if (call.isCancelled) return
            connection.outputStream.use { it.write(requestJson.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val body = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(4096) }
                }.getOrNull().orEmpty()
                val error = ClaudeChatError.fromHttp(code, ClaudeSseParser.parseErrorBody(body))
                finished = true
                post { listener.onError(error) }
                return
            }

            val parser = ClaudeSseParser()
            var stopReason: String? = null
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (!call.isCancelled) {
                    val line = reader.readLine() ?: break
                    for (event in parser.feedLine(line)) {
                        when (event) {
                            is ClaudeSseParser.Event.Text -> post { listener.onText(event.text) }
                            is ClaudeSseParser.Event.StopReason -> stopReason = event.reason
                            is ClaudeSseParser.Event.MessageStop -> {
                                finished = true
                                val reason = stopReason
                                post { listener.onComplete(reason) }
                            }
                            is ClaudeSseParser.Event.Error -> {
                                finished = true
                                val error = ClaudeChatError.fromStream(event)
                                post { listener.onError(error) }
                            }
                        }
                    }
                    if (finished) break
                }
            }
            if (!finished && !call.isCancelled) {
                post { listener.onError(ClaudeChatError.Network("連線中斷了")) }
            }
        } catch (e: IOException) {
            if (!call.isCancelled && !finished) {
                post { listener.onError(ClaudeChatError.Network(e.message ?: e.javaClass.simpleName)) }
            }
        } catch (e: RuntimeException) {
            if (!call.isCancelled && !finished) {
                post { listener.onError(ClaudeChatError.Network(e.message ?: e.javaClass.simpleName)) }
            }
        } finally {
            runCatching { call.connection?.disconnect() }
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
    }
}

/** What went wrong, in words for the chat screen. */
sealed class ClaudeChatError(val userMessage: String) {
    object NoApiKey : ClaudeChatError("還沒設定 Claude API key")
    object InvalidKey : ClaudeChatError("API key 無效，請到設定重新輸入")
    object NoCredit : ClaudeChatError("API 額度不足或已達使用上限，請到 Anthropic Console 查看")
    object RateLimited : ClaudeChatError("說太快了，稍等一下再試")
    object Overloaded : ClaudeChatError("Claude 現在很忙，等一下再試")
    class Network(detail: String) : ClaudeChatError("連不上 Claude：$detail")
    class Api(detail: String) : ClaudeChatError("Claude 回了錯誤：$detail")

    companion object {
        fun fromHttp(code: Int, error: ClaudeSseParser.Event.Error?): ClaudeChatError = when {
            code == 401 -> InvalidKey
            code == 403 -> InvalidKey
            code == 402 -> NoCredit
            code == 429 -> RateLimited
            code == 529 || error?.type == "overloaded_error" -> Overloaded
            code == 400 && error?.message?.contains("credit", ignoreCase = true) == true -> NoCredit
            else -> Api("HTTP $code ${error?.message.orEmpty()}".trim())
        }

        fun fromStream(error: ClaudeSseParser.Event.Error): ClaudeChatError = when (error.type) {
            "overloaded_error" -> Overloaded
            "rate_limit_error" -> RateLimited
            "authentication_error", "permission_error" -> InvalidKey
            else -> Api(error.message.ifBlank { error.type })
        }
    }
}
