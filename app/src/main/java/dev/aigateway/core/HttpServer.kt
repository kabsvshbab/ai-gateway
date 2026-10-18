package dev.aigateway.core

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Minimal HTTP/1.1 server for the local OpenAI-compatible API. */
class HttpServer(
    private val port: Int,
    private val handler: (method: String, path: String, headers: Map<String, String>, body: String, out: OutputStream) -> Unit
) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private val executor: ExecutorService = Executors.newFixedThreadPool(8)

    fun start() {
        running = true
        val t = Thread({ serve() }, "http-server")
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdown()
    }

    private fun serve() {
        try {
            // Bind only to the configured address (default 127.0.0.1), not to all interfaces.
            val host = Settings.bindAddress
            val ss = ServerSocket(port, 50, InetAddress.getByName(host))
            serverSocket = ss
            Log.i("HTTP server started on " + host + ":" + port)
            while (running) {
                val socket = ss.accept()
                executor.execute { handleConnection(socket) }
            }
        } catch (e: Exception) {
            if (running) {
                Log.e("HTTP server error: " + e.message)
            }
        }
    }

    /** Reads one header line as bytes (LF = 10, CR = 13), so no escape sequences are needed. */
    private fun readHttpLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            }
            if (b == 10) break
            if (b != 13) buf.write(b)
        }
        return buf.toString("ISO-8859-1")
    }

    private fun handleConnection(socket: Socket) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val out = socket.getOutputStream()

            val requestLine = readHttpLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) return
            val method = parts[0]
            val path = parts[1].split("?")[0]

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readHttpLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            // Content-Length is in bytes, so read bytes and decode as UTF-8 (needed for Chinese text).
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body: String
            if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(buf, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                body = String(buf, 0, read, Charsets.UTF_8)
            } else {
                body = ""
            }

            handler(method, path, headers, body, out)
            out.flush()
        } catch (e: Exception) {
            Log.e("Connection error: " + e.message)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        // CR (13) + LF (10), built from char codes so the source has no escape sequences.
        private val CRLF: String = String(charArrayOf(13.toChar(), 10.toChar()))

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
                503 -> "Service Unavailable"
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

        fun respondOptions(out: OutputStream) {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 200 OK").append(CRLF)
            sb.append("Access-Control-Allow-Origin: *").append(CRLF)
            sb.append("Access-Control-Allow-Headers: *").append(CRLF)
            sb.append("Access-Control-Allow-Methods: POST, GET, OPTIONS").append(CRLF)
            sb.append("Content-Length: 0").append(CRLF)
            sb.append("Connection: close").append(CRLF)
            sb.append(CRLF)
            out.write(sb.toString().toByteArray())
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
            val sb = StringBuilder()
            sb.append("data: ").append(json.toString()).appendLine().appendLine()
            out.write(sb.toString().toByteArray())
            out.flush()
        }

        fun respondSseDone(out: OutputStream) {
            val sb = StringBuilder()
            sb.append("data: [DONE]").appendLine().appendLine()
            out.write(sb.toString().toByteArray())
            out.flush()
        }
    }
}
