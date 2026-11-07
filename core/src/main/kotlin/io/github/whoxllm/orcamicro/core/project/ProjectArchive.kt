package io.github.whoxllm.orcamicro.core.project

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Project save/load (F): packs the app's working directory (persisted state, the normalized model,
 * and any sliced output) into a single `.osproj` ZIP, and unpacks one back. Project-local, no cloud.
 */
object ProjectArchive {

    const val EXTENSION = "osproj"

    /** Zips every file under [dir] (recursively) into [out]. Returns the file count written. */
    fun pack(dir: File, out: File): Int {
        var count = 0
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(dir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    /** Extracts [zip] into [dir] (created if needed). Rejects entries that escape [dir]. */
    fun unpack(zip: File, dir: File) {
        dir.mkdirs()
        val root = dir.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val target = File(root, e.name)
                if (!target.canonicalPath.startsWith(root.path + File.separator) && target.canonicalPath != root.path) {
                    throw IllegalArgumentException("Unsafe path in project: ${e.name}")
                }
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }
}
