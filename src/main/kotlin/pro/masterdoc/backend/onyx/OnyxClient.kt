package pro.masterdoc.backend.onyx

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
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
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pro.masterdoc.backend.config.AppConfig
import pro.masterdoc.backend.model.AssistantDto
import pro.masterdoc.backend.model.CategorizedFilesSnapshot
import pro.masterdoc.backend.model.CreateChatSessionRequest
import pro.masterdoc.backend.model.CreateChatSessionResponse
import pro.masterdoc.backend.model.GetChatSessionResponse
import pro.masterdoc.backend.model.OnyxFileDescriptor
import pro.masterdoc.backend.model.OnyxPersonaSnapshot
import pro.masterdoc.backend.model.OnyxSendChatMessageRequest
import pro.masterdoc.backend.model.OnyxSendMessageWithFilesRequest
import pro.masterdoc.backend.model.OnyxToolSnapshot
import pro.masterdoc.backend.model.SendChatMessageResponse
import pro.masterdoc.backend.model.TranscribeVoiceResponse
import pro.masterdoc.backend.model.UserFileSnapshot
import pro.masterdoc.backend.model.chatAssistantDisplayName
import pro.masterdoc.backend.model.isChatAssistant

class OnyxClient(
    private val config: AppConfig,
    private val http: HttpClient = defaultHttp(),
) {
    private var resolvedSearchToolId: Int? = null
    suspend fun listAssistants(): List<AssistantDto> {
        val personas: List<OnyxPersonaSnapshot> = authorizedGet("/persona")
        return personas
            .filter { it.isChatAssistant() }
            .map { AssistantDto(id = it.id, name = it.chatAssistantDisplayName()) }
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
                    buildOnyxSendRequest(
                        message = message,
                        sessionId = sessionId,
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

    suspend fun transcribeAudio(
        bytes: ByteArray,
        fileName: String,
        contentType: String = "audio/wav",
    ): TranscribeVoiceResponse {
        val response: HttpResponse = http.post("${config.onyxBaseUrl}/voice/transcribe") {
            header(HttpHeaders.Authorization, bearer())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "audio",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"$fileName\"",
                                )
                            },
                        )
                    },
                ),
            )
        }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OnyxException(response.status, raw.take(500))
        }
        val parsed = json.decodeFromString<Map<String, String>>(raw)
        val text = parsed["text"]?.trim().orEmpty()
        if (text.isEmpty()) {
            throw OnyxException(HttpStatusCode.BadGateway, "Onyx transcribe returned empty text")
        }
        return TranscribeVoiceResponse(text = text)
    }

    suspend fun uploadUserChatFile(
        bytes: ByteArray,
        fileName: String,
        contentType: String,
    ): UserFileSnapshot {
        val response: HttpResponse = http.post("${config.onyxBaseUrl}/user/projects/file/upload") {
            header(HttpHeaders.Authorization, bearer())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "files",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        val snapshot: CategorizedFilesSnapshot = decodeResponse(response)
        val file = snapshot.userFiles.firstOrNull()
            ?: throw OnyxException(HttpStatusCode.BadGateway, "Onyx returned no uploaded files")
        return file
    }

    suspend fun sendDetectMessage(
        message: String,
        uploadedFile: UserFileSnapshot,
        personaId: Int,
    ): String = streamDetectMessage(
        message = message,
        uploadedFile = uploadedFile,
        personaId = personaId,
        onLine = {},
    )

    suspend fun streamDetectMessage(
        message: String,
        uploadedFile: UserFileSnapshot,
        personaId: Int,
        onLine: suspend (String) -> Unit,
    ): String {
        val rawLines = mutableListOf<String>()
        http.preparePost("${config.onyxBaseUrl}/chat/send-chat-message") {
            header(HttpHeaders.Authorization, bearer())
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    buildDetectSendRequest(
                        message = message,
                        personaId = personaId,
                        uploadedFile = uploadedFile,
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
                    rawLines += line
                    onLine(line)
                }
            }
        }
        return SendResponseParser.parse(rawLines.joinToString("\n")).answer
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
                    buildOnyxSendRequest(
                        message = message,
                        sessionId = sessionId,
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

    private suspend fun buildOnyxSendRequest(
        message: String,
        sessionId: String,
        stream: Boolean,
    ): OnyxSendChatMessageRequest = OnyxSendChatMessageRequest(
        message = message,
        chatSessionId = sessionId,
        stream = stream,
        forcedToolId = resolveForcedSearchToolId(),
    )

    private suspend fun resolveForcedSearchToolId(): Int? {
        if (!config.forceInternalSearch) {
            return null
        }
        config.searchToolId?.let { return it }
        resolvedSearchToolId?.let { return it }
        resolvedSearchToolId = fetchSearchToolId()
        return resolvedSearchToolId
    }

    private suspend fun fetchSearchToolId(): Int? {
        val tools = try {
            authorizedGet<List<OnyxToolSnapshot>>("/tool")
        } catch (error: Exception) {
            println("[OnyxClient] GET /tool failed, internal search will not be forced: ${error.message}")
            return null
        }
        val toolId = tools.firstOrNull { it.inCodeToolId == SEARCH_TOOL_IN_CODE_ID }?.id
        if (toolId == null) {
            println("[OnyxClient] SearchTool not found in GET /tool; internal search will not be forced")
        }
        return toolId
    }

    private fun bearer(): String = "Bearer ${config.onyxPat}"

    private fun buildDetectSendRequest(
        message: String,
        personaId: Int,
        uploadedFile: UserFileSnapshot,
        stream: Boolean,
    ): OnyxSendMessageWithFilesRequest = OnyxSendMessageWithFilesRequest(
        message = message,
        stream = stream,
        chatSessionInfo = CreateChatSessionRequest(personaId = personaId),
        fileDescriptors = listOf(
            OnyxFileDescriptor(
                id = uploadedFile.fileId,
                type = uploadedFile.chatFileType,
                userFileId = uploadedFile.id,
            ),
        ),
        // Detect is vision-only: do not run internal search (saves ~60–90s per request).
        allowedToolIds = emptyList(),
        forcedToolId = null,
    )

    companion object {
        internal const val SEARCH_TOOL_IN_CODE_ID = "SearchTool"
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /** Detect persona may run multi-minute agent search; must stay below nginx proxy_read_timeout. */
        private const val ONYX_HTTP_TIMEOUT_MS = 600_000L

        private fun defaultHttp(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = ONYX_HTTP_TIMEOUT_MS
                socketTimeoutMillis = ONYX_HTTP_TIMEOUT_MS
            }
        }
    }
}

class OnyxException(
    val status: HttpStatusCode,
    val bodySnippet: String,
) : Exception("Onyx ${status.value}: $bodySnippet")
