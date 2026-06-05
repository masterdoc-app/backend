package pro.masterdoc.backend.detect

interface AssistantDetector {
    suspend fun detectAssistantName(
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): String?

    suspend fun detectAssistantNameStreaming(
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
        onLine: suspend (String) -> Unit,
    ): String? = detectAssistantName(imageBytes, fileName, contentType)
}
