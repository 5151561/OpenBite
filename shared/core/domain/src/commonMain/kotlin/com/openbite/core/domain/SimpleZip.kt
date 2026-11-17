package com.openbite.core.domain

/**
 * 不依赖 java.util.zip 的 zip 读写，给没有现成 zip 库的平台（iOS）用；Android 仍用 java.util.zip。
 * 只支持 .openbite 用得到的部分：写出时全部 STORED（照片本来就是 JPEG）；读入支持 STORED 和 DEFLATED，
 * 解压交给平台（[RawInflate]）。不支持 zip64、加密、分卷，遇到就当作“不是 zip”。
 *
 * 安全规则和 Android 实现一致：条目名先过 [checkEntryName]；重复条目、目录条目拒绝；
 * 不信 zip 里写的大小，按实际解出来的字节数卡单个条目和总量上限；CRC 不对也拒绝。
 */
internal object SimpleZip {
    /**
     * 把 raw deflate 数据（没有 zlib 头）解压出来，最多输出 [maxBytes] + 1 字节就停：
     * 调用方看到超出上限就拒绝，不会被 zip 炸弹撑爆内存。数据坏了抛异常。
     */
    fun interface RawInflate {
        fun inflate(data: ByteArray, maxBytes: Long): ByteArray
    }

    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val END_SIG = 0x06054b50
    private const val STORED = 0
    private const val DEFLATED = 8
    private const val FLAG_UTF8 = 0x0800
    private const val DOS_DATE_1980_01_01 = 0x21

    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = Bytes()
        val central = Bytes()
        entries.forEach { (name, data) ->
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.int(LOCAL_SIG); out.short(20); out.short(FLAG_UTF8); out.short(STORED); out.short(0); out.short(DOS_DATE_1980_01_01)
            out.int(crc); out.int(data.size); out.int(data.size); out.short(nameBytes.size); out.short(0)
            out.bytes(nameBytes); out.bytes(data)

            central.int(CENTRAL_SIG); central.short(20); central.short(20); central.short(FLAG_UTF8); central.short(STORED)
            central.short(0); central.short(DOS_DATE_1980_01_01); central.int(crc); central.int(data.size); central.int(data.size)
            central.short(nameBytes.size); central.short(0); central.short(0); central.short(0); central.short(0); central.int(0)
            central.int(offset); central.bytes(nameBytes)
        }
        val centralOffset = out.size
        out.bytes(central.toByteArray())
        out.int(END_SIG); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
        out.int(central.size); out.int(centralOffset); out.short(0)
        return out.toByteArray()
    }

    fun read(bytes: ByteArray, limits: OpenBiteLimits, inflate: RawInflate): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            val end = findEnd(bytes) ?: throw ImportException(ImportError.NOT_ZIP)
            val count = u16(bytes, end + 10)
            var p = u32(bytes, end + 16).toIntExact()
            repeat(count) {
                if (u32(bytes, p) != CENTRAL_SIG.toLong()) throw ImportException(ImportError.NOT_ZIP)
                val flags = u16(bytes, p + 8)
                val method = u16(bytes, p + 10)
                val crc = u32(bytes, p + 16)
                val compressedSize = u32(bytes, p + 20).toIntExact()
                val nameLen = u16(bytes, p + 28)
                val extraLen = u16(bytes, p + 30)
                val commentLen = u16(bytes, p + 32)
                val localOffset = u32(bytes, p + 42).toIntExact()
                val name = bytes.copyOfRange(p + 46, p + 46 + nameLen).decodeToString()
                p += 46 + nameLen + extraLen + commentLen

                if (result.size >= limits.maxEntries) throw ImportException(ImportError.TOO_MANY_ENTRIES)
                checkEntryName(name)
                if (name.endsWith("/") || name in result) throw ImportException(ImportError.UNEXPECTED_ENTRY, name)
                if (flags and 1 != 0) throw ImportException(ImportError.NOT_ZIP, "encrypted")

                if (u32(bytes, localOffset) != LOCAL_SIG.toLong()) throw ImportException(ImportError.NOT_ZIP)
                val dataStart = localOffset + 30 + u16(bytes, localOffset + 26) + u16(bytes, localOffset + 28)
                if (dataStart < 0 || dataStart + compressedSize > bytes.size) throw ImportException(ImportError.NOT_ZIP)
                val raw = bytes.copyOfRange(dataStart, dataStart + compressedSize)

                val cap = if (name == OPENBITE_MANIFEST) limits.maxManifestBytes else limits.maxPhotoBytes
                val budget = minOf(cap, limits.maxTotalBytes - total)
                val data = when (method) {
                    STORED -> raw
                    DEFLATED -> inflate.inflate(raw, budget.coerceAtLeast(0))
                    else -> throw ImportException(ImportError.NOT_ZIP, "method $method")
                }
                total += data.size
                if (data.size > cap || total > limits.maxTotalBytes) throw ImportException(ImportError.TOO_LARGE, name)
                if (crc32(data) != crc) throw ImportException(ImportError.NOT_ZIP, "crc")
                result[name] = data
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException(ImportError.NOT_ZIP, e.message)
        }
        if (result.isEmpty()) throw ImportException(ImportError.NOT_ZIP)
        return result
    }

    /** 从文件尾往前找“中央目录结束”记录（后面最多跟 65535 字节注释）。 */
    private fun findEnd(bytes: ByteArray): Int? {
        val last = bytes.size - 22
        val first = maxOf(0, last - 0xFFFF)
        for (i in last downTo first) if (u32(bytes, i) == END_SIG.toLong()) return i
        return null
    }

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long = u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)

    private fun Long.toIntExact(): Int {
        if (this < 0 || this > Int.MAX_VALUE) throw ImportException(ImportError.NOT_ZIP, "zip64")
        return toInt()
    }

    private val crcTable = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }

    fun crc32(data: ByteArray): Long {
        var c = 0xFFFFFFFFL
        for (b in data) c = crcTable[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
        return c xor 0xFFFFFFFFL
    }

    private class Bytes {
        private var buf = ByteArray(1024)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }

        fun short(v: Int) {
            ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte()
        }

        fun int(v: Long) {
            short((v and 0xFFFF).toInt()); short(((v ushr 16) and 0xFFFF).toInt())
        }

        fun int(v: Int) = int(v.toLong() and 0xFFFFFFFFL)

        fun bytes(b: ByteArray) {
            ensure(b.size); b.copyInto(buf, size); size += b.size
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
