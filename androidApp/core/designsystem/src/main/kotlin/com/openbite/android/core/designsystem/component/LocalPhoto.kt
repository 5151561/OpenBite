package com.openbite.android.core.designsystem.component

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 读原照片的结果。 */
sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data class Loaded(val bitmap: ImageBitmap) : PhotoLoad
    data object Failed : PhotoLoad
}

/** `seed/` 开头的示例图打包在 assets 里；其余是 App 私有目录（filesDir）下的相对路径。 */
private const val SEED_PREFIX = "seed/"

/** 在 IO 线程解码 App 里的照片（菜单原照片、饭菜照片、小票）。图片已在拍摄时压到 1568px 长边以内，这里不再缩放。 */
@Composable
fun rememberLocalPhoto(photoPath: String): State<PhotoLoad> {
    val context = LocalContext.current.applicationContext
    return produceState<PhotoLoad>(PhotoLoad.Loading, photoPath) {
        value = withContext(Dispatchers.IO) { decode(context, photoPath) }
    }
}

private fun decode(context: Context, photoPath: String): PhotoLoad = runCatching {
    val stream = if (photoPath.startsWith(SEED_PREFIX)) {
        context.assets.open(photoPath)
    } else {
        File(context.filesDir, photoPath).inputStream()
    }
    stream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
}.getOrNull()?.let { PhotoLoad.Loaded(it) } ?: PhotoLoad.Failed
