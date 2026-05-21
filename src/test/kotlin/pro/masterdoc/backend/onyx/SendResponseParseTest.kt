package pro.masterdoc.backend.onyx

import kotlinx.serialization.json.Json
import pro.masterdoc.backend.model.SendChatMessageResponse
import kotlin.test.Test
import kotlin.test.assertTrue

class SendResponseParseTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parsesOnyxSendPayload() {
        val raw = """
            {"answer":"Принято!","message_id":94,"chat_session_id":null,"error_msg":null,"tool_calls":[]}
        """.trimIndent()
        val parsed = json.decodeFromString<SendChatMessageResponse>(raw)
        assertTrue(parsed.answer.contains("Принято"))
    }
}
