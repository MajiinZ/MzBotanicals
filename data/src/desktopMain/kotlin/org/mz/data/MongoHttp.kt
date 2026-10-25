package org.mz.data

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

actual fun mongoBaseUrl(): String = (System.getProperty("mz.api.url") ?: System.getenv("MZ_API_URL") ?: MongoApiConfig.DEFAULT_ORIGIN).trimEnd('/')

actual suspend fun mongoHttp(method: String, path: String, token: String, data: ByteArray?): ByteArray = withContext(Dispatchers.IO) {
    val base = URI(mongoBaseUrl())
    require(base.userInfo == null && base.query == null && base.fragment == null && base.path.orEmpty() in listOf("", "/")) { "Use an API origin without credentials or a path" }
    require(base.scheme == "https" || (base.scheme == "http" && base.host in listOf("127.0.0.1", "localhost"))) { "Remote APIs require HTTPS. Local Android uses adb reverse tcp:8765 tcp:8765." }
    require(path.startsWith("/") && !path.startsWith("//"))
    val connection = URI(mongoBaseUrl() + path).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 10000; connection.readTimeout = 60000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (data != null) {
            require(data.size <= 16 * 1024 * 1024)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", if (path == "/media") "application/octet-stream" else "application/json")
            connection.setFixedLengthStreamingMode(data.size)
            connection.outputStream.use { it.write(data) }
        }
        val status = connection.responseCode
        val input = if (status in 200..299) connection.inputStream else connection.errorStream
        val bytes = input?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer); if (count < 0) break
                require(out.size() + count <= 16 * 1024 * 1024) { "API response too large" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: ByteArray(0)
        check(status in 200..299) {
            runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject["detail"]?.jsonPrimitive?.content }.getOrNull()
                ?: "Data service request failed (HTTP $status)."
        }
        bytes
    } catch (error: java.io.IOException) {
        throw IllegalStateException("The data service is unavailable. Check your connection and try again.", error)
    } finally { connection.disconnect() }
}
