package pro.masterdoc.backend.config

data class AppConfig(
    val port: Int,
    val onyxBaseUrl: String,
    val onyxPat: String,
) {
    companion object {
        fun fromEnv(): AppConfig {
            val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
            val onyxBaseUrl = System.getenv("ONYX_BASE_URL")?.trimEnd('/')
                ?: error("ONYX_BASE_URL is required")
            val onyxPat = System.getenv("ONYX_PAT")?.trim()
                ?: error("ONYX_PAT is required")
            return AppConfig(
                port = port,
                onyxBaseUrl = onyxBaseUrl,
                onyxPat = onyxPat,
            )
        }
    }
}
