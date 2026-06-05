package pro.masterdoc.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class TranscribeVoiceResponse(
    val text: String,
)
