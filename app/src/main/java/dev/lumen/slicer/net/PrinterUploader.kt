package dev.lumen.slicer.net

import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A network printer target (WiFi). `kind` selects the upload protocol. */
enum class PrinterKind { MOONRAKER, OCTOPRINT, PRUSALINK }

data class NetworkPrinter(
    val name: String,
    val kind: PrinterKind,
    /** Base URL including scheme + port, e.g. "http://192.168.0.50" or "http://printer.local:7125". */
    val baseUrl: String,
    /** API key, if the printer requires one (OctoPrint, Moonraker). */
    val apiKey: String? = null,
)

/**
 * Uploads a sliced file (G-code for FDM, or a resin archive) to a network printer over WiFi.
 * First cut: **Moonraker/Klipper** and **OctoPrint** (multipart upload) + **PrusaLink**.
 * No external deps — `HttpURLConnection`.
 */
object PrinterUploader {

    sealed class Result {
        data class Ok(val message: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun upload(file: File, printer: NetworkPrinter, startPrint: Boolean = false): Result = try {
        val base = printer.baseUrl.trimEnd('/')
        when (printer.kind) {
            PrinterKind.MOONRAKER -> moonraker(file, base, printer.apiKey, startPrint)
            PrinterKind.OCTOPRINT -> octoprint(file, base, printer.apiKey, startPrint)
            PrinterKind.PRUSALINK -> prusalink(file, base, printer.apiKey)
        }
    } catch (t: Throwable) {
        Result.Failed("Upload failed: ${t.message ?: t.javaClass.simpleName}")
    }

    // Moonraker: POST multipart to /server/files/upload (field "file", root=gcodes).
    private fun moonraker(file: File, base: String, key: String?, start: Boolean): Result {
        val boundary = "----mslice${UUID.randomUUID()}"
        val conn = (URL("$base/server/files/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 8000; readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            key?.let { setRequestProperty("X-Api-Key", it) }
        }
        conn.outputStream.use { out ->
            writeField(out, boundary, "root", "gcodes")
            if (start) writeField(out, boundary, "print", "true")
            writeFile(out, boundary, "file", file)
            out.write("--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        return if (code in 200..299) Result.Ok("Sent to ${base}") else Result.Failed("Moonraker HTTP $code")
    }

    // OctoPrint: POST multipart to /api/files/local (field "file", select/print).
    private fun octoprint(file: File, base: String, key: String?, start: Boolean): Result {
        val boundary = "----mslice${UUID.randomUUID()}"
        val conn = (URL("$base/api/files/local").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 8000; readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            key?.let { setRequestProperty("X-Api-Key", it) }
        }
        conn.outputStream.use { out ->
            writeFile(out, boundary, "file", file)
            writeField(out, boundary, "print", if (start) "true" else "false")
            out.write("--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        return if (code in 200..299) Result.Ok("Sent to ${base}") else Result.Failed("OctoPrint HTTP $code")
    }

    // PrusaLink: PUT the raw file to /api/v1/files/usb/<name>.
    private fun prusalink(file: File, base: String, key: String?): Result {
        val conn = (URL("$base/api/v1/files/usb/${file.name}").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 8000; readTimeout = 60000
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Length", file.length().toString())
            key?.let { setRequestProperty("X-Api-Key", it) }
        }
        conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
        val code = conn.responseCode
        return if (code in 200..299) Result.Ok("Sent to ${base}") else Result.Failed("PrusaLink HTTP $code")
    }

    private fun writeField(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
    }

    private fun writeFile(out: OutputStream, boundary: String, name: String, file: File) {
        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray())
        out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write("\r\n".toByteArray())
    }
}
