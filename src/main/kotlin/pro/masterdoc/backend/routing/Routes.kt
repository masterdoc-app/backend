package pro.masterdoc.backend.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import pro.masterdoc.backend.detect.AssistantDetector
import pro.masterdoc.backend.model.CreateCaseReportRequest
import pro.masterdoc.backend.model.CreateChatSessionRequest
import pro.masterdoc.backend.model.DetectAssistantResponse
import pro.masterdoc.backend.model.ErrorResponse
import pro.masterdoc.backend.model.SendChatMessageRequest
import pro.masterdoc.backend.onyx.OnyxClient
import pro.masterdoc.backend.onyx.OnyxException
import pro.masterdoc.backend.storage.CaseReportsRepository

fun Application.configureRoutes(
    onyx: OnyxClient,
    detector: AssistantDetector,
    caseReports: CaseReportsRepository,
) {
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/v1") {
            get("/assistants") {
                try {
                    call.respond(onyx.listAssistants())
                } catch (e: OnyxException) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(error = "Onyx error: ${e.status.value}"),
                    )
                }
            }

            post("/assistants/detect") {
                var imageBytes: ByteArray? = null
                var fileName = "image.jpg"
                var contentType = "image/jpeg"
                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.streamProvider().readBytes()
                            fileName = part.originalFileName?.takeIf { it.isNotBlank() } ?: fileName
                            contentType = part.contentType?.toString()?.takeIf { it.isNotBlank() }
                                ?: guessImageContentType(fileName)
                        }
                        else -> Unit
                    }
                    part.dispose()
                }
                val bytes = imageBytes
                if (bytes == null || bytes.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "image field required"))
                    return@post
                }
                if (bytes.size > DETECT_MAX_IMAGE_BYTES) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorResponse(
                            error = "Image too large (${bytes.size} bytes). Max ${DETECT_MAX_IMAGE_BYTES / (1024 * 1024)} MB.",
                        ),
                    )
                    return@post
                }
                try {
                    println("[masterdoc detect] image bytes=${bytes.size} file=$fileName type=$contentType")
                    val detected = detector.detectAssistantName(
                        imageBytes = bytes,
                        fileName = fileName,
                        contentType = contentType,
                    )
                    call.respond(DetectAssistantResponse(assistant = detected))
                } catch (e: OnyxException) {
                    println("[masterdoc detect] Onyx error ${e.status.value}: ${e.bodySnippet}")
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(error = "Onyx error: ${e.status.value}"),
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "bad request"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "detect failed"),
                    )
                }
            }

            post("/report") {
                val body = call.receive<CreateCaseReportRequest>()
                if (body.result.trim().length < 3) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error = "result must be at least 3 characters"),
                    )
                    return@post
                }
                try {
                    call.respond(caseReports.insert(body))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "failed to save report"),
                    )
                }
            }

            get("/report") {
                val assistantId = call.request.queryParameters["assistant_id"]?.toIntOrNull()
                if (assistantId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error = "assistant_id query parameter is required"),
                    )
                    return@get
                }
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                try {
                    call.respond(caseReports.list(assistantId = assistantId, page = page, size = size))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "failed to list reports"),
                    )
                }
            }

            post("/chat/sessions") {
                val body = call.receive<CreateChatSessionRequest>()
                try {
                    call.respond(onyx.createChatSession(body.personaId))
                } catch (e: OnyxException) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(error = "Onyx error: ${e.status.value}"),
                    )
                }
            }

            get("/chat/sessions/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "session id required"),
                )
                try {
                    call.respond(onyx.getChatSession(id))
                } catch (e: OnyxException) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(error = "Onyx error: ${e.status.value}"),
                    )
                }
            }

            post("/chat/sessions/{id}/messages") {
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "session id required"),
                )
                val body = call.receive<SendChatMessageRequest>()
                try {
                    if (body.stream) {
                        var lineCount = 0
                        call.respondTextWriter(ContentType.Text.Plain) {
                            onyx.streamChatMessage(id, body.message) { line ->
                                lineCount++
                                if (lineCount <= 5 || lineCount % 50 == 0) {
                                    println(
                                        "[masterdoc stream] session=$id line=$lineCount " +
                                            "preview=${line.take(120)}",
                                    )
                                }
                                write(line)
                                write("\n")
                                flush()
                            }
                            println("[masterdoc stream] session=$id total_lines=$lineCount")
                        }
                    } else {
                        call.respond(onyx.sendChatMessage(id, body.message))
                    }
                } catch (e: OnyxException) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(error = "Onyx error: ${e.status.value}"),
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "send failed"),
                    )
                }
            }
        }
    }
}

private const val DETECT_MAX_IMAGE_BYTES = 10 * 1024 * 1024

private fun guessImageContentType(fileName: String): String = when {
    fileName.endsWith(".png", ignoreCase = true) -> "image/png"
    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
    fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
    else -> "image/jpeg"
}
