package pro.masterdoc.backend.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptTurnDto(
    val ask: String,
    val answer: String,
)

@Serializable
data class CreateCaseReportRequest(
    @SerialName("assistant_id") val assistantId: Int,
    @SerialName("conversation_id") val conversationId: String? = null,
    val result: String,
    val transcript: List<TranscriptTurnDto> = emptyList(),
)

@Serializable
data class CaseReportDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("assistant_id") val assistantId: Int,
    @SerialName("conversation_id") val conversationId: String? = null,
    val result: String,
    val transcript: List<TranscriptTurnDto>,
)

@Serializable
data class PaginatedCaseReportsResponse(
    val items: List<CaseReportDto>,
    val page: Int,
    val size: Int,
    val total: Int,
    @SerialName("has_more") val hasMore: Boolean,
)
