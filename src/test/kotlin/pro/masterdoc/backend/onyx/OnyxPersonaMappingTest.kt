package pro.masterdoc.backend.onyx

import kotlinx.serialization.json.Json
import pro.masterdoc.backend.model.AssistantDto
import pro.masterdoc.backend.model.OnyxPersonaSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class OnyxPersonaMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsPersonaListToAssistants() {
        val raw = """
            [
              {"id":1,"name":"Холодильники","description":"","tools":[]},
              {"id":2,"name":"Стиралки","description":"","tools":[]}
            ]
        """.trimIndent()
        val personas = json.decodeFromString<List<OnyxPersonaSnapshot>>(raw)
        val assistants = personas.map { AssistantDto(id = it.id, name = it.name) }
        assertEquals(
            listOf(AssistantDto(1, "Холодильники"), AssistantDto(2, "Стиралки")),
            assistants,
        )
    }
}
