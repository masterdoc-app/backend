package pro.masterdoc.backend.storage

import pro.masterdoc.backend.model.CreateCaseReportRequest
import pro.masterdoc.backend.model.TranscriptTurnDto
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaseReportsRepositoryTest {

    @Test
    fun insertAndListPaginated() {
        val dir = createTempDirectory("case-reports-test")
        val dbPath = dir.resolve("test.db").toString()
        val repository = CaseReportsRepository(dbPath).also { it.init() }

        repository.insert(
            CreateCaseReportRequest(
                assistantId = 2,
                conversationId = "sess-1",
                result = "Заменили датчик, станция в работе",
                transcript = listOf(
                    TranscriptTurnDto(ask = "Не морозит", answer = "Проверьте датчик"),
                ),
            ),
        )
        repository.insert(
            CreateCaseReportRequest(
                assistantId = 1,
                result = "Другой кейс",
                transcript = emptyList(),
            ),
        )

        val page0 = repository.list(page = 0, size = 1, assistantId = null)
        assertEquals(2, page0.total)
        assertEquals(1, page0.items.size)
        assertTrue(page0.hasMore)

        val filtered = repository.list(page = 0, size = 10, assistantId = 2)
        assertEquals(1, filtered.total)
        assertEquals("sess-1", filtered.items.single().conversationId)
        assertEquals("Не морозит", filtered.items.single().transcript.single().ask)

        val page1 = repository.list(page = 1, size = 1, assistantId = null)
        assertEquals(1, page1.items.size)
        assertFalse(page1.hasMore)
    }
}
