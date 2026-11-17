package com.openbite.android.core.designsystem.component

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.openbite.android.core.designsystem.photo.PhotoFiles

/** 读原照片的结果。 */
sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data class Loaded(val bitmap: ImageBitmap) : PhotoLoad
    data object Failed : PhotoLoad
}

/**
 * 在 IO 线程解码 App 里的照片（菜单原照片、饭菜照片、小票）。图片已在拍摄时压到 1568px 长边以内。
 * 缩略图传 [targetSize]（显示时的边长，按裁剪铺满算）：按它采样解码，80dp 的缩略图不再占一整张原图的内存。
 * 不传就按原尺寸解码（大图、全屏）。
 */
@Composable
fun rememberLocalPhoto(photoPath: String, targetSize: Dp? = null): State<PhotoLoad> {
    val context = LocalContext.current.applicationContext
    val targetPx = targetSize?.let { with(LocalDensity.current) { it.roundToPx() } } ?: 0
    return produceState<PhotoLoad>(PhotoLoad.Loading, photoPath, targetPx) {
        value = withContext(Dispatchers.IO) { decode(context, photoPath, targetPx) }
    }
}

/** 路径规则和 PhotoStore 共用（[PhotoFiles]）：越出 photos/ 目录的路径打不开。 */
private fun openLocalPhoto(context: Context, photoPath: String) =
    PhotoFiles.open(context, photoPath) ?: throw java.io.FileNotFoundException(photoPath)

/** 读照片的原始字节（分享卡片要自己解码）。读不到返回 null。在 IO 线程调用。 */
fun readLocalPhotoBytes(context: Context, photoPath: String): ByteArray? =
    runCatching { openLocalPhoto(context, photoPath).use { it.readBytes() } }.getOrNull()

private fun decode(context: Context, photoPath: String, targetPx: Int): PhotoLoad = runCatching {
    val options = BitmapFactory.Options()
    if (targetPx > 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openLocalPhoto(context, photoPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    openLocalPhoto(context, photoPath).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()?.let { PhotoLoad.Loaded(it) } ?: PhotoLoad.Failed

/** 最大的 2 的幂，让短边采样后仍不小于 [targetPx]（裁剪铺满时不发糊）。 */
internal fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    val shortSide = minOf(width, height)
    if (shortSide <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (shortSide / (sample * 2) >= targetPx) sample *= 2
    return sample
}
