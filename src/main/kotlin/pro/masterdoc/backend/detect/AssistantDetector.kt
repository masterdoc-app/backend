package pro.masterdoc.backend.detect

interface AssistantDetector {
    suspend fun detectAssistantId(imageBytes: ByteArray): Int
}

class StubAssistantDetector : AssistantDetector {
    override suspend fun detectAssistantId(imageBytes: ByteArray): Int {
        throw UnsupportedOperationException("not_implemented")
    }
}
