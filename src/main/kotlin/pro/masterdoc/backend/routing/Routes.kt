package pro.masterdoc.backend.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import pro.masterdoc.backend.detect.AssistantDetector
import pro.masterdoc.backend.model.CreateChatSessionRequest
import pro.masterdoc.backend.model.ErrorResponse
import pro.masterdoc.backend.model.SendChatMessageRequest
import pro.masterdoc.backend.onyx.OnyxClient
import pro.masterdoc.backend.onyx.OnyxException

fun Application.configureRoutes(
    onyx: OnyxClient,
    detector: AssistantDetector,
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
                var hasImage = false
                call.receiveMultipart().forEachPart { part ->
                    if (part.name == "image") {
                        hasImage = true
                    }
                    part.dispose()
                }
                if (!hasImage) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "image field required"))
                    return@post
                }
                call.respond(HttpStatusCode.NotImplemented, ErrorResponse(error = "not_implemented"))
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
