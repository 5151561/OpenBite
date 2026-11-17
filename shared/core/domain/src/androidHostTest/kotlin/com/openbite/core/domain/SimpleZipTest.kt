package com.openbite.core.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** iOS 用的纯 Kotlin zip（[SimpleZip]）和 java.util.zip 互相读得通；解压用 JVM 的 Inflater 代替 iOS 的 zlib。 */
class SimpleZipTest {
    private val jvmInflate = SimpleZip.RawInflate { data, maxBytes ->
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inflater.finished() && out.size() <= maxBytes) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        out.toByteArray()
    }

    private val manifest = """{"v":1}""".encodeToByteArray()
    private val photo = ByteArray(5000) { (it % 251).toByte() }

    private fun javaZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun readsDeflatedZipFromJava() {
        val read = SimpleZip.read(javaZip(OPENBITE_MANIFEST to manifest, "photos/1.jpg" to photo), OpenBiteLimits(), jvmInflate)
        assertContentEquals(manifest, read[OPENBITE_MANIFEST])
        assertContentEquals(photo, read["photos/1.jpg"])
    }

    @Test
    fun javaReadsWhatWeWrite() {
        val bytes = SimpleZip.write(listOf(OPENBITE_MANIFEST to manifest, "photos/1.jpg" to photo))
        val read = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                read[e.name] = zip.readBytes()
            }
        }
        assertEquals(listOf(OPENBITE_MANIFEST, "photos/1.jpg"), read.keys.toList())
        assertContentEquals(photo, read["photos/1.jpg"])
        // 自己写的也能自己读回来。
        assertContentEquals(photo, SimpleZip.read(bytes, OpenBiteLimits(), jvmInflate)["photos/1.jpg"])
    }

    @Test
    fun sameRulesAsAndroid() {
        fun error(bytes: ByteArray, limits: OpenBiteLimits = OpenBiteLimits()) =
            assertFailsWith<ImportException> { SimpleZip.read(bytes, limits, jvmInflate) }.error
        assertEquals(ImportError.UNSAFE_PATH, error(SimpleZip.write(listOf("../x" to manifest))))
        assertEquals(ImportError.UNEXPECTED_ENTRY, error(SimpleZip.write(listOf(OPENBITE_MANIFEST to manifest, OPENBITE_MANIFEST to manifest))))
        assertEquals(ImportError.TOO_LARGE, error(javaZip("photos/1.jpg" to ByteArray(4096)), OpenBiteLimits(maxPhotoBytes = 1024)))
        assertEquals(
            ImportError.TOO_LARGE,
            error(javaZip("photos/1.jpg" to photo, "photos/2.jpg" to photo), OpenBiteLimits(maxTotalBytes = 8000)),
        )
        assertEquals(ImportError.NOT_ZIP, error(byteArrayOf(1, 2, 3)))
        assertEquals(java.util.zip.CRC32().apply { update(photo) }.value, SimpleZip.crc32(photo))
    }
}
