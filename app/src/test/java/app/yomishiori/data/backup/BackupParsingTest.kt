package app.yomishiori.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupParsingTest {
    @Test fun `rejects newer format versions`() {
        val json = """{"formatVersion":99,"exportedAt":0,"categories":[],"entries":[],"sources":[],"memberships":[],"history":[]}"""
        val e = assertThrows(IOException::class.java) { BackupManager.parse(json) }
        assertTrue(e.message!!.contains("newer"))
    }

    @Test fun `rejects non backup json`() {
        assertThrows(IOException::class.java) { BackupManager.parse("""{"hello":1}""") }
        assertThrows(IOException::class.java) { BackupManager.parse("not json") }
    }

    @Test fun `parses a minimal backup and ignores unknown fields`() {
        val json = """{"formatVersion":1,"exportedAt":5,"future":true,"categories":[{"id":1,"name":"Fav"}],
            "entries":[{"id":7,"title":"T","contentType":"EPUB","readingStatus":"READING","dateAdded":1}],
            "sources":[{"id":3,"entryId":7,"sourceType":"WEB_URL","name":"site","lastUrl":"https://a.com/2"}],
            "memberships":[{"entryId":7,"categoryId":1}],"history":[]}"""
        val backup = BackupManager.parse(json)
        assertEquals("T", backup.entries.single().title)
        assertEquals("https://a.com/2", backup.sources.single().lastUrl)
        assertEquals("Fav", backup.categories.single().name)
    }

    @Test fun `zip slip entries are rejected`() {
        val target = Files.createTempDirectory("restore").toFile()
        val zip = zipOf("../evil.txt" to "x")
        assertThrows(IOException::class.java) { BackupManager.extractZip(ByteArrayInputStream(zip), target) }
        assertFalse(File(target.parentFile, "evil.txt").exists())
    }

    @Test fun `only backup files are extracted`() {
        val target = Files.createTempDirectory("restore").toFile()
        val zip = zipOf("backup.json" to "{}", "epubs/a.epub" to "e", "covers/c.png" to "c", "other/x.so" to "x")
        BackupManager.extractZip(ByteArrayInputStream(zip), target)
        assertTrue(File(target, "backup.json").exists())
        assertTrue(File(target, "epubs/a.epub").exists())
        assertTrue(File(target, "covers/c.png").exists())
        assertFalse(File(target, "other/x.so").exists())
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
