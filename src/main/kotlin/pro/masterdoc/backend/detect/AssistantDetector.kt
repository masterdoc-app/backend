package pro.masterdoc.backend.detect

interface AssistantDetector {
    suspend fun detectAssistantName(
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): String?
}
