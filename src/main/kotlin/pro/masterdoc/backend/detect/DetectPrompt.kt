package pro.masterdoc.backend.detect

import pro.masterdoc.backend.model.AssistantDto

object DetectPrompt {
    fun build(assistants: List<AssistantDto>): String {
        require(assistants.isNotEmpty()) { "assistants must not be empty" }
        val count = assistants.size
        val chatWord = if (count == 1) "чат" else "чата"
        val names = assistants.joinToString(separator = " и ") { it.name }
        return "Мы имеем $count $chatWord: $names. По фото определи, к какому чату относится снимок. " +
            "Ответь только одним названием из списка, без поиска в базе."
    }
}
