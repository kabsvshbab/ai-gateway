package dev.aigateway.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Minimal HTTP/1.1 server for local OpenAI-compatible API. */
class HttpServer(
    private val port: Int,
    private val handler: (method: String, path: String, headers: Map<String, String>, body: String, out: OutputStream) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private val executor: ExecutorService = Executors.newFixedThreadPool(8)

    fun start() {
        running = true
        Thread({
            try {
                serverSocket = ServerSocket(port)
                Log.i("HTTP server started on 127.0.0.1:" + port)
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    executor.submit { handleConnection(socket) }
                }
            } catch (e: Exception) {
                Log.e("HTTP server error: " + e.message)
            }
        }, "http-server").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdown()
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) return
            val method = parts[0]
            val path = parts[1].split("?")[0]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            handler(method, path, headers, body, out)
            out.flush()
        } catch (e: Exception) {
            Log.e("Connection error: " + e.message)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val CRLF = "\r\n"

        fun respondJson(out: OutputStream, status: Int, json: org.json.JSONObject) {
            val body = json.toString().toByteArray()
            val statusText = when (status) {
                200 -> "OK"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                502 -> "Bad Gateway"
                504 -> "Gateway Timeout"
                else -> "Unknown"
            }
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(status).append(" ").append(statusText).append(CRLF)
            sb.append("Content-Type: application/json").append(CRLF)
            sb.append("Content-Length: ").append(body.size).append(CRLF)
            sb.append("Access-Control-Allow-Origin: *").append(CRLF)
            sb.append("Access-Control-Allow-Headers: *").append(CRLF)
            sb.append("Access-Control-Allow-Methods: *").append(CRLF)
            sb.append("Connection: close").append(CRLF)
            sb.append(CRLF)
            out.write(sb.toString().toByteArray())
            out.write(body)
        }

        fun respondSseHeaders(out: OutputStream) {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 200 OK").append(CRLF)
            sb.append("Content-Type: text/event-stream").append(CRLF)
            sb.append("Cache-Control: no-cache").append(CRLF)
            sb.append("Connection: close").append(CRLF)
            sb.append("Access-Control-Allow-Origin: *").append(CRLF)
            sb.append(CRLF)
            out.write(sb.toString().toByteArray())
            out.flush()
        }

        fun respondSseChunk(out: OutputStream, json: org.json.JSONObject) {
            val data = "data: " + json.toString() + "\n\n"
            out.write(data.toByteArray())
            out.flush()
        }

        fun respondSseDone(out: OutputStream) {
            out.write("data: [DONE]\n\n".toByteArray())
            out.flush()
        }
    }
}
