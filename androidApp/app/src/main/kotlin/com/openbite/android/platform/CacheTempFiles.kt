package com.openbite.android.platform

import android.content.Context
import android.net.Uri
import com.openbite.core.data.platform.TempFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** cache/scan（系统相机写的原图）和 cache/share（分享出去的文件），见 res/xml/file_paths.xml。 */
internal class CacheTempFiles(private val context: Context) : TempFiles {
    private val authority = "${context.packageName}.fileprovider"

    override suspend fun discardCapture(sourceUri: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != "content" || uri.authority != authority) return@withContext
        val segments = uri.pathSegments
        // FileProvider 地址形如 content://<pkg>.fileprovider/scan/<文件名>，只认 scan 下面的直接文件。
        if (segments.size != 2 || segments[0] != SCAN_DIR) return@withContext
        val dir = File(context.cacheDir, SCAN_DIR).canonicalFile
        val file = File(dir, segments[1]).canonicalFile
        if (file.parentFile == dir) file.delete()
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        listOf(SCAN_DIR, SHARE_DIR).forEach { name -> File(context.cacheDir, name).listFiles()?.forEach { it.deleteRecursively() } }
    }

    private companion object {
        const val SCAN_DIR = "scan"
        const val SHARE_DIR = "share"
    }
}
