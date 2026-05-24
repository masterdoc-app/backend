package pro.masterdoc.backend.detect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectResponseParserTest {
    private val candidates = listOf("Атлант-стиралки", "Атлант-холодильники")

    @Test
    fun parsesQuotedChatName() {
        val result = DetectResponseParser.parse(
            """Фото относится к чату "Атлант-холодильники".""",
            candidates,
        )
        assertEquals("Атлант-холодильники", result)
    }

    @Test
    fun parsesNameEmbeddedInSentence() {
        val result = DetectResponseParser.parse(
            "Это похоже на Атлант-стиралки, судя по панели управления.",
            candidates,
        )
        assertEquals("Атлант-стиралки", result)
    }

    @Test
    fun returnsNullWhenNoCandidateMatches() {
        assertNull(
            DetectResponseParser.parse("Не могу определить категорию.", candidates),
        )
    }
}
