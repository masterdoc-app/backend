package pro.masterdoc.backend.detect

import pro.masterdoc.backend.config.AppConfig
import pro.masterdoc.backend.onyx.OnyxClient

class OnyxAssistantDetector(
    private val onyx: OnyxClient,
    private val detectPersonaId: Int,
) : AssistantDetector {
    constructor(onyx: OnyxClient, config: AppConfig) : this(
        onyx = onyx,
        detectPersonaId = config.detectPersonaId,
    )

    override suspend fun detectAssistantName(
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): String? {
        val assistants = onyx.listAssistants()
        if (assistants.isEmpty()) {
            return null
        }
        val prompt = DetectPrompt.build(assistants)
        val uploaded = onyx.uploadUserChatFile(
            bytes = imageBytes,
            fileName = fileName,
            contentType = contentType,
        )
        val answer = onyx.sendDetectMessage(
            message = prompt,
            uploadedFile = uploaded,
            personaId = detectPersonaId,
        )
        return DetectResponseParser.parse(answer, assistants.map { it.name })
    }
}
