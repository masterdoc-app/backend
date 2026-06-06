package pro.masterdoc.backend.detect

import pro.masterdoc.backend.model.AssistantDto
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectPromptTest {
    @Test
    fun buildsRussianPromptForTwoChats() {
        val prompt = DetectPrompt.build(
            listOf(
                AssistantDto(1, "Атлант-стиралки"),
                AssistantDto(2, "Атлант-холодильники"),
            ),
        )
        assertEquals(
            "Мы имеем 2 чата: Атлант-стиралки и Атлант-холодильники. " +
                "По фото определи, к какому чату относится снимок. " +
                "Ответь только одним названием из списка, без поиска в базе.",
            prompt,
        )
    }

    @Test
    fun usesSingularChatWordForOneAssistant() {
        val prompt = DetectPrompt.build(listOf(AssistantDto(1, "Атлант-холодильники")))
        assertEquals(
            "Мы имеем 1 чат: Атлант-холодильники. " +
                "По фото определи, к какому чату относится снимок. " +
                "Ответь только одним названием из списка, без поиска в базе.",
            prompt,
        )
    }
}
