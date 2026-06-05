package pro.masterdoc.backend.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pro.masterdoc.backend.model.CaseReportDto
import pro.masterdoc.backend.model.CreateCaseReportRequest
import pro.masterdoc.backend.model.PaginatedCaseReportsResponse
import pro.masterdoc.backend.model.TranscriptTurnDto
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

class CaseReportsRepository(
    private val dbPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun init() {
        DriverManager.getConnection(jdbcUrl()).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS case_reports (
                        id TEXT PRIMARY KEY,
                        created_at TEXT NOT NULL,
                        assistant_id INTEGER NOT NULL,
                        conversation_id TEXT,
                        result TEXT NOT NULL,
                        transcript_json TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_case_reports_created ON case_reports(created_at DESC)",
                )
                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_case_reports_assistant_created
                    ON case_reports(assistant_id, created_at DESC)
                    """.trimIndent(),
                )
            }
        }
    }

    fun insert(request: CreateCaseReportRequest): CaseReportDto {
        val id = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        val transcriptJson = json.encodeToString(request.transcript)
        DriverManager.getConnection(jdbcUrl()).use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO case_reports (id, created_at, assistant_id, conversation_id, result, transcript_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, createdAt)
                statement.setInt(3, request.assistantId)
                statement.setString(4, request.conversationId)
                statement.setString(5, request.result.trim())
                statement.setString(6, transcriptJson)
                statement.executeUpdate()
            }
        }
        println(
            "[masterdoc case-report] saved id=$id assistant=${request.assistantId} " +
                "conversation=${request.conversationId} result=${request.result.take(120)}",
        )
        return CaseReportDto(
            id = id,
            createdAt = createdAt,
            assistantId = request.assistantId,
            conversationId = request.conversationId,
            result = request.result.trim(),
            transcript = request.transcript,
        )
    }

    fun delete(id: String): Boolean {
        DriverManager.getConnection(jdbcUrl()).use { connection ->
            connection.prepareStatement("DELETE FROM case_reports WHERE id = ?").use { statement ->
                statement.setString(1, id)
                return statement.executeUpdate() > 0
            }
        }
    }

    /** Removes reports whose trimmed result is shorter than [minLength] characters. */
    fun deleteShortResults(minLength: Int): Int {
        DriverManager.getConnection(jdbcUrl()).use { connection ->
            connection.prepareStatement(
                "DELETE FROM case_reports WHERE LENGTH(TRIM(result)) < ?",
            ).use { statement ->
                statement.setInt(1, minLength)
                return statement.executeUpdate()
            }
        }
    }

    fun list(assistantId: Int, page: Int, size: Int): PaginatedCaseReportsResponse =
        paginate(
            whereClause = "WHERE assistant_id = ?",
            bindArgs = listOf(BindArg.IntValue(assistantId)),
            page = page,
            size = size,
        )

    /**
     * Paginated text search within one assistant (for future GET /v1/report/search).
     */
    fun search(assistantId: Int, query: String, page: Int, size: Int): PaginatedCaseReportsResponse {
        val pattern = "%${escapeLike(query.trim())}%"
        return paginate(
            whereClause = "WHERE assistant_id = ? AND result LIKE ? ESCAPE '\\'",
            bindArgs = listOf(BindArg.IntValue(assistantId), BindArg.StringValue(pattern)),
            page = page,
            size = size,
        )
    }

    private fun paginate(
        whereClause: String,
        bindArgs: List<BindArg>,
        page: Int,
        size: Int,
    ): PaginatedCaseReportsResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val offset = safePage * safeSize
        DriverManager.getConnection(jdbcUrl()).use { connection ->
            val total = connection.prepareStatement(
                "SELECT COUNT(*) FROM case_reports $whereClause",
            ).use { statement ->
                bindArgs.forEachIndexed { index, arg -> arg.apply(statement, index + 1) }
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
            val items = connection.prepareStatement(
                """
                SELECT id, created_at, assistant_id, conversation_id, result, transcript_json
                FROM case_reports
                $whereClause
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """.trimIndent(),
            ).use { statement ->
                var paramIndex = 1
                bindArgs.forEach { arg ->
                    arg.apply(statement, paramIndex++)
                }
                statement.setInt(paramIndex++, safeSize)
                statement.setInt(paramIndex, offset)
                statement.executeQuery().use { result -> mapResultRows(result) }
            }
            return PaginatedCaseReportsResponse(
                items = items,
                page = safePage,
                size = safeSize,
                total = total,
                hasMore = offset + items.size < total,
            )
        }
    }

    private fun mapResultRows(result: java.sql.ResultSet): List<CaseReportDto> =
        buildList {
            while (result.next()) {
                add(
                    CaseReportDto(
                        id = result.getString("id"),
                        createdAt = result.getString("created_at"),
                        assistantId = result.getInt("assistant_id"),
                        conversationId = result.getString("conversation_id"),
                        result = result.getString("result"),
                        transcript = decodeTranscript(result.getString("transcript_json")),
                    ),
                )
            }
        }

    private fun decodeTranscript(raw: String): List<TranscriptTurnDto> =
        runCatching { json.decodeFromString<List<TranscriptTurnDto>>(raw) }
            .getOrElse { emptyList() }

    private fun jdbcUrl(): String = "jdbc:sqlite:$dbPath"

    private sealed interface BindArg {
        fun apply(statement: java.sql.PreparedStatement, index: Int)

        data class IntValue(val value: Int) : BindArg {
            override fun apply(statement: java.sql.PreparedStatement, index: Int) {
                statement.setInt(index, value)
            }
        }

        data class StringValue(val value: String) : BindArg {
            override fun apply(statement: java.sql.PreparedStatement, index: Int) {
                statement.setString(index, value)
            }
        }
    }

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
