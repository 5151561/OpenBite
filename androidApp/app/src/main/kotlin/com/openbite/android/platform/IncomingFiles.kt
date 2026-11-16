package com.openbite.android.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.openbite.core.data.platform.IncomingFile
import com.openbite.core.data.platform.IncomingFileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 读别的 App 交来的 content:// 文件：先看名字和类型，再最多读 maxBytes+1 字节，超了就不读了。 */
internal class ContentResolverFileSource(context: Context) : IncomingFileSource {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String, maxBytes: Long): IncomingFile = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        // 只收 content://；file:// 可能被用来指向我们自己的私有文件。
        require(parsed.scheme == "content") { "unsupported scheme" }
        val name = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: parsed.lastPathSegment
        val mime = resolver.getType(parsed)
        val out = ByteArrayOutputStream()
        var tooLarge = false
        (resolver.openInputStream(parsed) ?: error("cannot open")).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    tooLarge = true
                    break
                }
                out.write(buffer, 0, n)
            }
        }
        IncomingFile(name, mime, if (tooLarge) null else out.toByteArray(), tooLarge)
    }
}
