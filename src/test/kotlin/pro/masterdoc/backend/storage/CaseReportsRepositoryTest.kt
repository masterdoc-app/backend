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
    fun insertAndListPaginatedByAssistant() {
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
        repository.insert(
            CreateCaseReportRequest(
                assistantId = 2,
                result = "Второй кейс по той же станции",
                transcript = emptyList(),
            ),
        )

        val page0 = repository.list(assistantId = 2, page = 0, size = 1)
        assertEquals(2, page0.total)
        assertEquals(1, page0.items.size)
        assertTrue(page0.hasMore)
        assertEquals("Второй кейс по той же станции", page0.items.single().result)

        val page1 = repository.list(assistantId = 2, page = 1, size = 1)
        assertEquals(1, page1.items.size)
        assertEquals("Заменили датчик, станция в работе", page1.items.single().result)
        assertFalse(page1.hasMore)

        val otherAssistant = repository.list(assistantId = 1, page = 0, size = 10)
        assertEquals(1, otherAssistant.total)
        assertEquals("Другой кейс", otherAssistant.items.single().result)
    }

    @Test
    fun searchFiltersByAssistantAndResultText() {
        val dir = createTempDirectory("case-reports-test")
        val dbPath = dir.resolve("test.db").toString()
        val repository = CaseReportsRepository(dbPath).also { it.init() }

        repository.insert(
            CreateCaseReportRequest(
                assistantId = 5,
                result = "Заменили компрессор холодильника",
                transcript = emptyList(),
            ),
        )
        repository.insert(
            CreateCaseReportRequest(
                assistantId = 5,
                result = "Промыли фильтр стиральной",
                transcript = emptyList(),
            ),
        )
        repository.insert(
            CreateCaseReportRequest(
                assistantId = 6,
                result = "Заменили компрессор",
                transcript = emptyList(),
            ),
        )

        val hits = repository.search(assistantId = 5, query = "компрессор", page = 0, size = 10)
        assertEquals(1, hits.total)
        assertEquals("Заменили компрессор холодильника", hits.items.single().result)
    }

    @Test
    fun deleteShortResults_removesOnlyShortReports() {
        val dir = createTempDirectory("case-reports-test")
        val dbPath = dir.resolve("test.db").toString()
        val repository = CaseReportsRepository(dbPath).also { it.init() }

        val long = repository.insert(
            CreateCaseReportRequest(
                assistantId = 2,
                result = "Достаточно длинный итоговый отчёт мастера",
                transcript = emptyList(),
            ),
        )
        repository.insert(
            CreateCaseReportRequest(
                assistantId = 2,
                result = "короткий",
                transcript = emptyList(),
            ),
        )

        val removed = repository.deleteShortResults(minLength = 21)
        assertEquals(1, removed)

        val page = repository.list(assistantId = 2, page = 0, size = 10)
        assertEquals(1, page.total)
        assertEquals(long.id, page.items.single().id)
    }
}
