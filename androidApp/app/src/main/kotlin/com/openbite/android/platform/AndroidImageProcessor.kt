package com.openbite.android.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.ProcessedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * 菜单照片处理：先从原图 EXIF 读 GPS 和方向，再解码、转正、缩到长边 1568px，重新编码成 JPEG。
 * Bitmap.compress 不写任何 EXIF，所以发给模型和存进 filesDir 的图片都不含元数据。
 */
internal class AndroidImageProcessor(private val context: Context) : ImageProcessor {

    override suspend fun process(sourceUri: String, maxLongEdgePx: Int): ProcessedImage = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val exif = readExif(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdgePx) sample *= 2
        val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("decode failed")

        val scale = maxLongEdgePx.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            exif.applyOrientation(this)
        }
        val output = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        output.recycle()
        ProcessedImage(bytes, exif.lat, exif.lon)
    }

    /**
     * 外来的 JPEG（导入的店铺文件）：只认 JPEG，尺寸离谱的拒绝（防“小文件大图片”），
     * 解码后缩到长边再重新编码，原文件里的任何元数据都不会留下。
     */
    override suspend fun reencode(jpegBytes: ByteArray, maxLongEdgePx: Int): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        require(bounds.outMimeType == "image/jpeg") { "not a jpeg" }
        require(bounds.outWidth in 1..MAX_SIDE && bounds.outHeight in 1..MAX_SIDE) { "bad size" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdgePx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("decode failed")
        val scale = maxLongEdgePx.toFloat() / max(decoded.width, decoded.height)
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            output.recycle()
            stream.toByteArray()
        }
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("cannot open $uri")

    private class ExifInfo(val lat: Double?, val lon: Double?, val orientation: Int) {
        fun applyOrientation(matrix: Matrix) {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
        }
    }

    /**
     * 相册里的照片：Android 10+ 默认把位置抹掉，只有拿到 ACCESS_MEDIA_LOCATION 并用 setRequireOriginal 打开才读得到。
     * 打不开原图（比如 Photo Picker 的地址不支持）就退回普通读取，位置为空。
     */
    private fun readExif(uri: Uri): ExifInfo = runCatching {
        val stream = originalOrNull(uri) ?: open(uri)
        stream.use {
            val exif = ExifInterface(it)
            val latLong = exif.latLong
            ExifInfo(
                lat = latLong?.getOrNull(0)?.takeIf { lat -> lat in -90.0..90.0 && lat != 0.0 },
                lon = latLong?.getOrNull(1)?.takeIf { lon -> lon in -180.0..180.0 && lon != 0.0 },
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
        }
    }.getOrDefault(ExifInfo(null, null, ExifInterface.ORIENTATION_NORMAL))

    private fun originalOrNull(uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.authority != MediaStore.AUTHORITY) return null
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return runCatching { context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()
    }

    private companion object {
        const val JPEG_QUALITY = 85

        /** 导入的照片单边最多这么多像素。 */
        const val MAX_SIDE = 12_000
    }
}
