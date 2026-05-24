package pro.masterdoc.backend.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssistantDto(
    val id: Int,
    val name: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class CreateChatSessionRequest(
    @SerialName("persona_id") val personaId: Int = 0,
)

@Serializable
data class CreateChatSessionResponse(
    @SerialName("chat_session_id") val chatSessionId: String,
)

@Serializable
data class OnyxChatMessageDto(
    @SerialName("message_id") val messageId: Int,
    @SerialName("message_type") val messageType: String,
    val message: String,
    @SerialName("time_sent") val timeSent: String? = null,
)

@Serializable
data class GetChatSessionResponse(
    @SerialName("chat_session_id") val chatSessionId: String,
    val messages: List<OnyxChatMessageDto> = emptyList(),
)

@Serializable
data class SendChatMessageRequest(
    val message: String,
    @SerialName("chat_session_id") val chatSessionId: String,
    val stream: Boolean = false,
)

@Serializable
data class SendChatMessageResponse(
    val answer: String = "",
    @SerialName("message_id") val messageId: Int? = null,
    @SerialName("chat_session_id") val chatSessionId: String? = null,
    @SerialName("error_msg") val errorMsg: String? = null,
)

/** Minimal fields from Onyx GET /persona */
@Serializable
data class OnyxPersonaSnapshot(
    val id: Int,
    val name: String,
)

private const val CHAT_ASSISTANT_SUFFIX = "-chat"

/** Onyx personas exposed in the app as selectable chats (name suffix from admin UI). */
fun OnyxPersonaSnapshot.isChatAssistant(): Boolean =
    name.endsWith(CHAT_ASSISTANT_SUFFIX, ignoreCase = true)

/** Display name for chat personas (suffix is for Onyx admin only). */
fun OnyxPersonaSnapshot.chatAssistantDisplayName(): String =
    if (isChatAssistant()) name.dropLast(CHAT_ASSISTANT_SUFFIX.length) else name
