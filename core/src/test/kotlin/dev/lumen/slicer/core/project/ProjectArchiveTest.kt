package dev.lumen.slicer.core.project

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectArchiveTest {

    @Test
    fun `pack then unpack round-trips the tree`() {
        val src = createTempDir(prefix = "proj-src")
        val dst = createTempDir(prefix = "proj-dst")
        val zip = File.createTempFile("proj", ".osproj")
        try {
            File(src, "state.json").writeText("{\"ok\":true}")
            File(src, "sub").mkdirs()
            File(src, "sub/model.stl").writeText("solid x")

            val n = ProjectArchive.pack(src, zip)
            assertEquals(2, n)

            ProjectArchive.unpack(zip, dst)
            assertEquals("{\"ok\":true}", File(dst, "state.json").readText())
            assertEquals("solid x", File(dst, "sub/model.stl").readText())
        } finally {
            src.deleteRecursively(); dst.deleteRecursively(); zip.delete()
        }
    }

    @Test
    fun `unpack rejects path traversal`() {
        val zip = File.createTempFile("evil", ".osproj")
        val dst = createTempDir(prefix = "proj-safe")
        try {
            java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
                z.write("bad".toByteArray())
                z.closeEntry()
            }
            var threw = false
            try { ProjectArchive.unpack(zip, dst) } catch (e: IllegalArgumentException) { threw = true }
            assertTrue(threw, "expected traversal to be rejected")
        } finally {
            dst.deleteRecursively(); zip.delete()
        }
    }
}
