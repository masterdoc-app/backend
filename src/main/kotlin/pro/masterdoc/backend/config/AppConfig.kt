package pro.masterdoc.backend.config

data class AppConfig(
    val port: Int,
    val onyxBaseUrl: String,
    val onyxPat: String,
    /** Onyx persona for /app?agentId=… routing (default agent 3). */
    val detectPersonaId: Int,
    /** When true, every chat message sends Onyx `forced_tool_id` for internal search. */
    val forceInternalSearch: Boolean = true,
    /** Optional override; when null, resolved from Onyx GET /tool (SearchTool). */
    val searchToolId: Int? = null,
) {
    companion object {
        fun fromEnv(): AppConfig {
            val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
            val onyxBaseUrl = System.getenv("ONYX_BASE_URL")?.trimEnd('/')
                ?: error("ONYX_BASE_URL is required")
            val onyxPat = System.getenv("ONYX_PAT")?.trim()
                ?: error("ONYX_PAT is required")
            val detectPersonaId = System.getenv("DETECT_PERSONA_ID")?.toIntOrNull() ?: 3
            val forceInternalSearch = parseBooleanEnv("ONYX_FORCE_INTERNAL_SEARCH", default = true)
            val searchToolId = System.getenv("ONYX_SEARCH_TOOL_ID")?.toIntOrNull()
            return AppConfig(
                port = port,
                onyxBaseUrl = onyxBaseUrl,
                onyxPat = onyxPat,
                detectPersonaId = detectPersonaId,
                forceInternalSearch = forceInternalSearch,
                searchToolId = searchToolId,
            )
        }

        private fun parseBooleanEnv(name: String, default: Boolean): Boolean {
            val raw = System.getenv(name)?.trim()?.lowercase() ?: return default
            return raw in setOf("true", "1", "yes")
        }
    }
}
