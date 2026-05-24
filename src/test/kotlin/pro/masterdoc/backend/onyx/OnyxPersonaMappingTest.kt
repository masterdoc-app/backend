package pro.masterdoc.backend.onyx

import kotlinx.serialization.json.Json
import pro.masterdoc.backend.model.AssistantDto
import pro.masterdoc.backend.model.OnyxPersonaSnapshot
import pro.masterdoc.backend.model.chatAssistantDisplayName
import pro.masterdoc.backend.model.isChatAssistant
import kotlin.test.Test
import kotlin.test.assertEquals

class OnyxPersonaMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsPersonaListToAssistants() {
        val raw = """
            [
              {"id":1,"name":"Атлант холодильники доки","description":"","tools":[]},
              {"id":2,"name":"Атлант-стиралки-chat","description":"","tools":[]},
              {"id":3,"name":"Атлант-холодильники-chat","description":"","tools":[]}
            ]
        """.trimIndent()
        val personas = json.decodeFromString<List<OnyxPersonaSnapshot>>(raw)
        val assistants = personas
            .filter { it.isChatAssistant() }
            .map { AssistantDto(id = it.id, name = it.chatAssistantDisplayName()) }
        assertEquals(
            listOf(
                AssistantDto(2, "Атлант-стиралки"),
                AssistantDto(3, "Атлант-холодильники"),
            ),
            assistants,
        )
    }
}
