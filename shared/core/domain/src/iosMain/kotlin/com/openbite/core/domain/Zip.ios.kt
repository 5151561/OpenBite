package com.openbite.core.domain

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/** iOS：zip 结构用纯 Kotlin 的 [SimpleZip]，解压用系统自带的 zlib。规则和 Android 实现一致。 */
internal actual fun readZipEntries(bytes: ByteArray, limits: OpenBiteLimits): Map<String, ByteArray> =
    SimpleZip.read(bytes, limits, SimpleZip.RawInflate(::zlibInflateRaw))

internal actual fun writeZipEntries(entries: List<Pair<String, ByteArray>>): ByteArray = SimpleZip.write(entries)

/** raw deflate（windowBits = -15，没有 zlib 头）。最多输出 [maxBytes] + 1 字节就停。 */
@OptIn(ExperimentalForeignApi::class)
private fun zlibInflateRaw(data: ByteArray, maxBytes: Long): ByteArray {
    val limit = (maxBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val chunk = ByteArray(64 * 1024)
    val parts = ArrayList<ByteArray>()
    var size = 0
    if (data.isEmpty()) return ByteArray(0)
    memScoped {
        val stream = alloc<z_stream>()
        check(inflateInit2_(stream.ptr, -15, zlibVersion(), sizeOf<z_stream>().toInt()) == Z_OK) { "inflateInit2 failed" }
        try {
            data.usePinned { input ->
                stream.next_in = input.addressOf(0).reinterpret()
                stream.avail_in = data.size.toUInt()
                while (size < limit) {
                    val produced = chunk.usePinned { buf ->
                        stream.next_out = buf.addressOf(0).reinterpret()
                        stream.avail_out = chunk.size.toUInt()
                        val status = inflate(stream.ptr, Z_NO_FLUSH)
                        if (status != Z_OK && status != Z_STREAM_END && status != Z_BUF_ERROR) error("inflate failed: $status")
                        val n = chunk.size - stream.avail_out.toInt()
                        if (n > 0) {
                            val part = chunk.copyOf(minOf(n, limit - size))
                            parts += part
                            size += part.size
                        }
                        if (status == Z_STREAM_END) -1 else n
                    }
                    if (produced <= 0) break
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    val out = ByteArray(size)
    var at = 0
    parts.forEach { it.copyInto(out, at); at += it.size }
    return out
}
