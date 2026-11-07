package dev.lumen.slicer.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val sha256: String,
    val url: String,
    val notes: String = "",
)

/**
 * In-app update from the build host: fetch a manifest, download the APK, verify its
 * sha256 and hand it to the package installer. The host publishes `update/info.json` + `update/app.apk`
 * (see `server/`). No app store involved.
 */
object Updater {

    fun check(base: String, currentCode: Int): UpdateInfo {
        val b = base.trimEnd('/')
        val conn = (URL("$b/update/info.json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000
        }
        conn.inputStream.use { ins ->
            val o = JSONObject(ins.readBytes().decodeToString())
            return UpdateInfo(
                version = o.optString("version", "?"),
                versionCode = o.optInt("versionCode", 0),
                sha256 = o.optString("sha256", ""),
                url = o.optString("url", "$b/update/app.apk"),
                notes = o.optString("notes", ""),
            )
        }
    }

    fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit = {}): File {
        val f = File(ctx.getExternalFilesDir(null), "update/app-update.apk")
        f.parentFile?.mkdirs()
        val conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 120000
        }
        val total = conn.contentLength
        conn.inputStream.use { ins ->
            f.outputStream().use { outs ->
                val buf = ByteArray(1 shl 16); var read = 0L; var r: Int
                while (ins.read(buf).also { r = it } > 0) {
                    outs.write(buf, 0, r); read += r
                    if (total > 0) onProgress((100 * read / total).toInt())
                }
            }
        }
        return f
    }

    fun verifySha256(f: File, expected: String): Boolean {
        if (expected.isBlank()) return true
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val b = ByteArray(1 shl 20)
            while (true) { val r = ins.read(b); if (r <= 0) break; md.update(b, 0, r) }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        return got.equals(expected, ignoreCase = true)
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
