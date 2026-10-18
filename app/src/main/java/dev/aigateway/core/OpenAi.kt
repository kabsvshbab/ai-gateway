package dev.aigateway.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Helpers to build OpenAI-compatible JSON payloads. */
object OpenAi {
    fun modelList(models: List<Pair<String, String>>): JSONObject {
        val arr = JSONArray()
        for ((id, owner) in models) {
            arr.put(JSONObject()
                .put("id", id)
                .put("object", "model")
                .put("created", 0)
                .put("owned_by", owner))
        }
        return JSONObject().put("object", "list").put("data", arr)
    }

    fun error(statusMsg: String, type: String, message: String): JSONObject {
        return JSONObject().put("error", JSONObject()
            .put("message", message)
            .put("type", type)
            .put("code", statusMsg))
    }

    fun chunkDelta(id: String, created: Long, model: String, delta: JSONObject, finish: String?): JSONObject {
        val choice = JSONObject()
            .put("index", 0)
            .put("delta", delta)
            .put("finish_reason", if (finish == null) JSONObject.NULL else finish)
        return JSONObject()
            .put("id", id)
            .put("object", "chat.completion.chunk")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(choice))
    }

    fun fullResponse(id: String, created: Long, model: String, content: String): JSONObject {
        val msg = JSONObject().put("role", "assistant").put("content", content)
        val choice = JSONObject().put("index", 0).put("message", msg).put("finish_reason", "stop")
        val usage = JSONObject().put("prompt_tokens", 0).put("completion_tokens", 0).put("total_tokens", 0)
        return JSONObject()
            .put("id", id)
            .put("object", "chat.completion")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(choice))
            .put("usage", usage)
    }

    fun newId() = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)

    //** Flatten OpenAI messagees into a single prompt string for web UIs. */
    fun flattenMessages(messages: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until messages.length) {
            val m = messages.optJSONObject(i) ?? continue
            val role = m.optString("role", "user")
            val content = m.opt("content")
            val text = when (content) {
                is String -> content
                is JSONArray -> {
                    val parts = StringBuilder()
                    for (j in 0 until content.length) {
                        val part = content.optJSONObjct(j) ?? continue
                        if (part.optString("type") == "text") parts.append(part.optString("text"))
                    }
                    parts.toString()
                }
                else -> ""
            }
            when (role) {
                "system" -> sb.append("[System]\n").append(text).append("\n\n")
                "assistant" -> sb.append("[Assistant]\n").append(text).append("\n\n")
                else -> sb.append(text).append("\n\n")
            }
        }
        return sb.toString().trim()
    }
}
