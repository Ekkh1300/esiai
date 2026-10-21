package com.sokhan.chat.provider.openai

import com.sokhan.chat.domain.model.ChatError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** OkHttp request probe helper: reads a response body without throwing. */
internal fun readBody(response: Response?): String? = runCatching {
    response?.body?.string()
}.getOrNull()

/**
 * Bridges a raw OkHttp call into a cold [Flow] of SSE `data:` payloads —
 * no okhttp-sse module required by the flow itself.
 *
 * Contract:
 *  - Each event's data is emitted with the `data:` prefix and the one
 *    optional separating space stripped (per the SSE spec).
 *  - The `[DONE]` sentinel is forwarded verbatim before completing; the
 *    caller decides what "finished" means.
 *  - Completes normally when the server closes the stream.
 *  - Fails with a typed [ChatError] on transport/HTTP errors; cancellation
 *    of the collector cancels the underlying call and ends the flow quietly.
 */
fun OkHttpClient.sseDataFlow(request: Request): Flow<String> = channelFlow {
    val call = newCall(request)
    launch(Dispatchers.IO) {
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpStatusToError(response.code, readBody(response))
                }
                val source = response.body?.source()
                    ?: throw ChatError.EmptyResponse
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").removePrefix(" ")
                    if (payload.isEmpty()) continue
                    send(payload)
                    if (payload == "[DONE]") break
                }
            }
            close()
        } catch (e: CancellationException) {
            // The collector went away; nothing left to surface.
        } catch (t: Throwable) {
            if (!isActive) return@launch
            when (t) {
                is ChatError -> close(t)
                else -> close(mapTransportToChatError(t) ?: ChatError.Unreachable(t.message))
            }
        }
    }
    awaitClose { call.cancel() }
}

/**
 * Maps OkHttp/network failures onto the Sokhan error taxonomy.
 * Returns null for cancellation-like failures the collector should interpret.
 */
internal fun mapTransportToChatError(t: Throwable?): ChatError? = when (t) {
    null -> null
    is CancellationException -> null
    is SocketTimeoutException -> ChatError.Timeout(t.message)
    is UnknownHostException -> ChatError.Unreachable("DNS lookup failed")
    is ConnectException -> ChatError.Unreachable(t.message)
    is SSLException -> ChatError.Unreachable("TLS: ${t.message}")
    is IOException -> ChatError.Unreachable(t.message)
    else -> ChatError.Unknown(t, t.message)
}

/** Maps HTTP status + optional error body onto the taxonomy. */
internal fun mapHttpStatusToError(code: Int, body: String?): ChatError {
    val detail = parseErrorDetail(body)
    return when (code) {
        400 -> ChatError.BadRequest(detail)
        401, 403 -> ChatError.Auth(detail)
        404 -> ChatError.NotFound(detail)
        408 -> ChatError.Timeout(detail)
        429 -> ChatError.RateLimit(detail ?: "rate limited")
        in 500..599 -> ChatError.Server(code, detail)
        else -> ChatError.Server(code, detail ?: "HTTP $code")
    }
}
