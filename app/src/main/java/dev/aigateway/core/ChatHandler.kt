package dev.aigateway.core

import dev.aigateway.provider.Providers
import dev.aigateway.provider.WebViewPool
import org.json.JSONObject
import java.io.OutputStream

/** Routes OpenAI-compatible requests to the appropriate provider. */
object ChatHandler {

    fun handle(method: String, path: String, headers: Map<String, String>, body: String, out: OutputStream) {
        // Check API key
        val key = Settings.apiKey
        if (key.isNotEmpty()) {
            val auth = headers["authorization"] ?: ""
            val token = auth.removePrefix("Bearer ").trim()
            if (token != key) {
                HttpServer.respondJson(out, 401, OpenAi.error("invalid_api_key", "invalid_request_error", "Missing or invalid API key."))
                return
            }
        }

        when {
            method == "GET" && (path == "/" || path == "/health") -> {
                HttpServer.respondJson(out, 200, JSONObject().put("status", "ok").put("providers", Providers.ALL.size))
            }
            method == "GET" && path == "/v1/models" -> {
                HttpServer.respondJson(out, 200, OpenAi.modelList(Providers.allModels))
            }
            method == "POST" && path == "/v1/chat/completions" -> {
                handleChat(body, out)
            }
            method == "OPTIONS" -> {
                out.write("""HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Access-Control-Allow-Headers: *
Access-Control-Allow-Methods: POST, GET, OPTIONS
Connection: close

""".toByteArray())
            }
            else -> {
                HttpServer.respondJson(out, 404, OpenAi.error("not_found", "invalid_request_error", "Unknown path: $method $path"))
            }
        }
    }

    private fun handleChat(body: String, out: OutputStream) {
        try {
            val req = JSONObject(body)
            val messages = req.optJSONArray("messages")
                ?: run {
                    HttpServer.respondJson(out, 400, OpenAi.error("invalid_request", "invalid_request_error", "'messages' is required."))
                    return
                }

            val modelId = req.optString("model", "")
            if (modelId.isEmpty()) {
                HttpServer.respondJson(out, 400, OpenAi.error("invalid_request", "invalid_request_error", "'model' is required."))
                return
            }

            val providerId = Providers.allModels.find { it.first == modelId }?.second
                ?: run {
                    HttpServer.respondJson(out, 400, OpenAi.error("invalid_request", "invalid_request_error", "Unknown model '$modelId'."))
                    return
                }

            val def = Providers.byId[providerId]!!

            if (!WebViewPool.isLoggedIn(def)) {
                HttpServer.respondJson(out, 503, OpenAi.error("not_logged_in", "upstream_error", "Please log in to ${def.label} first."))
                return
            }

            val stream = req.optBoolean("stream", false)
            val prompt = OpenAi.flattenMessages(messages)
            val payload = buildPayload(providerId, modelId, prompt, messages)

            val id = OpenAi.newId()
            val created = System.currentTimeMillis() / 1000

            if (stream) {
                HttpServer.respondSseHeaders(out)
                HttpServer.respondSseChunk(out, OpenAi.chunkDelta(id, created, modelId, JSONObject().put("role", "assistant"), null))
            }

            val chunks = WebViewPool.sendRequest(def, payload.toString())
            val fullText = StringBuilder()
            var error: String? = null

            for (chunk in chunks) {
                when {
                    chunk == "__DONE__" -> {}
                    chunk.startsWith("__ERROR__:") -> { error = chunk.removePrefix("__ERROR__:"); break }
                    chunk.startsWith("__HTTP_ERROR__:") -> { error = chunk; break }
                    else -> {
                        try {
                            val obj = JSONObject(chunk)
                            val delta = obj.optString("delta", "")
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                if (stream) {
                                    HttpServer.respondSseChunk(out, OpenAi.chunkDelta(id, created, modelId, JSONObject().put("content", delta), null))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (error != null) {
                if (stream) {
                    HttpServer.respondSseChunk(out, OpenAi.chunkDelta(id, created, modelId, JSONObject().put("content", ""), "stop"))
                    HttpServer.respondSseDone(out)
                } else {
                    HttpServer.respondJson(out, 502, OpenAi.error("upstream_error", "upstream_error", error))
                }
                return
            }

            if (stream) {
                HttpServer.respondSseChunk(out, OpenAi.chunkDelta(id, created, modelId, JSONObject().put("content", ""), "stop"))
                HttpServer.respondSseDone(out)
            } else {
                HttpServer.respondJson(out, 200, OpenAi.fullResponse(id, created, modelId, fullText.toString()))
            }
        } catch (e: Exception) {
            Log.e("Chat error: ${e.message}")
            HttpServer.respondJson(out, 500, OpenAi.error("internal_error", "internal_error", e.message ?: "Unknown error"))
        }
    }

    /** Build provider-specific payload from OpenAI messages. */
    private fun buildPayload(providerId: String, modelId: String, prompt: String, messages: org.json.JSONArray): JSONObject {
        return when (providerId) {
            "doubao" -> JSONObject()
                .put("messages", org.json.JSONArray().put(JSONObject()
                    .put("content", JSONObject().put("text", prompt).toString())
                    .put("content_type", 2009)
                    .put("attachments", org.json.JSONArray())
                    .put("references", org.json.JSONArray())
                    .put("skill", JSONObject()
                        .put("skill_type", 3)
                        .put("skill_type_no_default", 3)
                        .put("skill_id", "3")
                        .put("skill_id_no_default", "3"))))
                .put("completion_option", JSONObject()
                    .put("is_regen", false)
                    .put("with_suggest", true)
                    .put("need_create_conversation", true)
                    .put("launch_stage", 1)
                    .put("memory_type", 2)
                    .put("use_deep_think", false)
                    .put("use_auto_cot", false))
                .put("evaluate_option", JSONObject().put("web_ab_params", ""))
                .put("local_conversation_id", java.util.UUID.randomUUID().toString())
                .put("local_message_id", java.util.UUID.randomUUID().toString())

            "deepseek" -> JSONObject()
                .put("model", modelId)
                .put("messages", messages)
                .put("stream", true)

            "chatgpt" -> JSONObject()
                .put("action", "next")
                .put("messages", org.json.JSONArray().put(JSONObject()
                    .put("id", java.util.UUID.randomUUID().toString())
                    .put("role", "user")
                    .put("content", JSONObject().put("content_type", "text").put("parts", org.json.JSONArray().put(prompt)))))
                .put("model", modelId)
                .put("parent_message_id", java.util.UUID.randomUUID().toString())
                .put("stream", true)

            "claude" -> JSONObject()
                .put("conversation_id", "")
                .put("message", JSONObject()
                    .put("role", "user")
                    .put("content", org.json.JSONArray().put(JSONObject()
                        .put("type", "text")
                        .put("text", prompt))))
                .put("model", modelId)
                .put("parent_message_uuid", "")
                .put("stream", true)

            else -> JSONObject()
                .put("model", modelId)
                .put("messages", org.json.JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("content", prompt)))
                .put("stream", true)
        }
    }
}
