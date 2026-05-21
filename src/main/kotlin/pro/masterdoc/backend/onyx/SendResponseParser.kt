package pro.masterdoc.backend.onyx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pro.masterdoc.backend.model.SendChatMessageResponse

internal object SendResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): SendChatMessageResponse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return SendChatMessageResponse()

        runCatching { json.decodeFromString<SendChatMessageResponse>(trimmed) }
            .getOrNull()
            ?.takeIf { it.answer.isNotBlank() }
            ?.let { return it }

        val answerParts = mutableListOf<String>()
        var messageId: Int? = null
        var errorMsg: String? = null

        trimmed.lineSequence()
            .map { it.trim().removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
            .forEach { line ->
                runCatching { json.decodeFromString<SendChatMessageResponse>(line) }
                    .getOrNull()
                    ?.let { dto ->
                        if (dto.answer.isNotBlank()) return dto
                        messageId = dto.messageId ?: messageId
                        errorMsg = dto.errorMsg ?: errorMsg
                    }

                val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                messageId = obj.intOrNull("message_id") ?: messageId
                errorMsg = obj.stringOrNull("error_msg") ?: errorMsg

                obj.stringOrNull("answer")?.takeIf { it.isNotBlank() }?.let {
                    return SendChatMessageResponse(
                        answer = it,
                        messageId = messageId,
                        errorMsg = errorMsg,
                    )
                }

                val packet = obj["obj"]?.jsonObject
                when (packet?.stringOrNull("type")) {
                    "message_delta" -> packet.stringOrNull("content")?.let(answerParts::add)
                }
            }

        return SendChatMessageResponse(
            answer = answerParts.joinToString(""),
            messageId = messageId,
            errorMsg = errorMsg,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.jsonPrimitive?.content

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.jsonPrimitive?.content?.toIntOrNull()
}
