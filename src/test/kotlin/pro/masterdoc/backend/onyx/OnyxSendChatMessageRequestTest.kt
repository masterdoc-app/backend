package pro.masterdoc.backend.onyx

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pro.masterdoc.backend.model.OnyxSendChatMessageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnyxSendChatMessageRequestTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun serializesForcedToolIdWhenSet() {
        val payload = json.encodeToString(
            OnyxSendChatMessageRequest(
                message = "Много льда",
                chatSessionId = "session-1",
                stream = true,
                forcedToolId = 7,
            ),
        )
        assertTrue(payload.contains("\"forced_tool_id\":7"))
        assertTrue(payload.contains("\"stream\":true"))
    }

    @Test
    fun omitsForcedToolIdWhenNull() {
        val payload = json.encodeToString(
            OnyxSendChatMessageRequest(
                message = "test",
                chatSessionId = "session-1",
                stream = false,
                forcedToolId = null,
            ),
        )
        assertFalse(payload.contains("forced_tool_id"))
    }

    @Test
    fun searchToolInCodeIdMatchesOnyx() {
        assertEquals("SearchTool", OnyxClient.SEARCH_TOOL_IN_CODE_ID)
    }
}
