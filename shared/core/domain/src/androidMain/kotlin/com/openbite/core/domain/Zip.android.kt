package com.openbite.core.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 解压 .openbite：条目名先过 [checkEntryName]（防 zip slip），
 * 不信 zip 里写的大小，按实际解出来的字节数卡上限（防 zip 炸弹）。
 */
internal actual fun readZipEntries(bytes: ByteArray, limits: OpenBiteLimits): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    var total = 0L
    try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (result.size >= limits.maxEntries) throw ImportException(ImportError.TOO_MANY_ENTRIES)
                val name = entry.name
                checkEntryName(name)
                if (entry.isDirectory || name in result) throw ImportException(ImportError.UNEXPECTED_ENTRY, name)
                val cap = if (name == OPENBITE_MANIFEST) limits.maxManifestBytes else limits.maxPhotoBytes
                val out = ByteArrayOutputStream()
                var size = 0L
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    size += n
                    total += n
                    if (size > cap || total > limits.maxTotalBytes) throw ImportException(ImportError.TOO_LARGE, name)
                    out.write(buffer, 0, n)
                }
                result[name] = out.toByteArray()
            }
        }
    } catch (e: ImportException) {
        throw e
    } catch (e: Exception) {
        throw ImportException(ImportError.NOT_ZIP, e.message)
    }
    if (result.isEmpty()) throw ImportException(ImportError.NOT_ZIP)
    return result
}

internal actual fun writeZipEntries(entries: List<Pair<String, ByteArray>>): ByteArray {
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
