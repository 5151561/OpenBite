package com.openbite.android.feature.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** 分享出去的文件都放在 cache/share/（FileProvider 只开放这个目录）。每次只留最新的几份。 */
internal object ShareFiles {
    private const val DIR = "share"

    suspend fun write(context: Context, fileName: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val safe = fileName.filterNot { it in "/\\" }.ifEmpty { "openbite" }
        val file = File(dir, safe)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    /** 系统分享面板。[mime] 是文件类型；只读权限交给接收方。 */
    fun share(context: Context, uri: Uri, mime: String, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /** Android 10+ 不用存储权限就能写进相册（Pictures/OpenBite）。更老的系统返回 false。 */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    suspend fun saveToGallery(context: Context, displayName: String, png: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!canSaveToGallery) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenBite")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val ok = runCatching { resolver.openOutputStream(uri)?.use { it.write(png) } != null }.getOrDefault(false)
        if (ok) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        ok
    }
}
