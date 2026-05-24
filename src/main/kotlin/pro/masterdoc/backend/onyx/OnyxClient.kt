package pro.masterdoc.backend.onyx

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pro.masterdoc.backend.config.AppConfig
import pro.masterdoc.backend.model.AssistantDto
import pro.masterdoc.backend.model.CreateChatSessionRequest
import pro.masterdoc.backend.model.CreateChatSessionResponse
import pro.masterdoc.backend.model.GetChatSessionResponse
import pro.masterdoc.backend.model.isChatAssistant
import pro.masterdoc.backend.model.OnyxPersonaSnapshot
import pro.masterdoc.backend.model.SendChatMessageRequest
import pro.masterdoc.backend.model.SendChatMessageResponse

class OnyxClient(
    private val config: AppConfig,
    private val http: HttpClient = defaultHttp(),
) {
    suspend fun listAssistants(): List<AssistantDto> {
        val personas: List<OnyxPersonaSnapshot> = authorizedGet("/persona")
        return personas
            .filter { it.isChatAssistant() }
            .map { AssistantDto(id = it.id, name = it.name) }
    }

    suspend fun createChatSession(personaId: Int): CreateChatSessionResponse =
        authorizedPost("/chat/create-chat-session", CreateChatSessionRequest(personaId))

    suspend fun getChatSession(sessionId: String): GetChatSessionResponse =
        authorizedGet("/chat/get-chat-session/$sessionId")

    suspend fun sendChatMessage(
        sessionId: String,
        message: String,
    ): SendChatMessageResponse {
        val response: HttpResponse = http.post("${config.onyxBaseUrl}/chat/send-chat-message") {
            header(HttpHeaders.Authorization, bearer())
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    SendChatMessageRequest(
                        message = message,
                        chatSessionId = sessionId,
                        stream = false,
                    ),
                ),
            )
        }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OnyxException(response.status, raw.take(500))
        }
        return SendResponseParser.parse(raw)
    }

    suspend fun streamChatMessage(
        sessionId: String,
        message: String,
        onLine: suspend (String) -> Unit,
    ) {
        http.preparePost("${config.onyxBaseUrl}/chat/send-chat-message") {
            header(HttpHeaders.Authorization, bearer())
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    SendChatMessageRequest(
                        message = message,
                        chatSessionId = sessionId,
                        stream = true,
                    ),
                ),
            )
        }.execute { response ->
            if (response.status.value !in 200..299) {
                val raw = response.bodyAsText()
                throw OnyxException(response.status, raw.take(500))
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isNotBlank()) {
                    onLine(line)
                }
            }
        }
    }

    private suspend inline fun <reified T> authorizedGet(path: String): T {
        val response: HttpResponse = http.get("${config.onyxBaseUrl}$path") {
            header(HttpHeaders.Authorization, bearer())
        }
        return decodeResponse(response)
    }

    private suspend inline fun <reified B, reified T> authorizedPost(path: String, body: B): T {
        val response: HttpResponse = http.post("${config.onyxBaseUrl}$path") {
            header(HttpHeaders.Authorization, bearer())
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }
        return decodeResponse(response)
    }

    private suspend inline fun <reified T> decodeResponse(response: HttpResponse): T {
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OnyxException(response.status, raw.take(500))
        }
        return json.decodeFromString(raw)
    }

    private fun bearer(): String = "Bearer ${config.onyxPat}"

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private fun defaultHttp(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                socketTimeoutMillis = 180_000
            }
        }
    }
}

class OnyxException(
    val status: HttpStatusCode,
    val bodySnippet: String,
) : Exception("Onyx ${status.value}: $bodySnippet")
