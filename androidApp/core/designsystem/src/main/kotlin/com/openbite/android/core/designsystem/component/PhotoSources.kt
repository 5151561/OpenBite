package com.openbite.android.core.designsystem.component

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** 拍照（系统相机）和从相册选（Photo Picker）。选好后把 content:// 地址交给 onPicked。 */
class PhotoSources internal constructor(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
)

/**
 * 系统相机写进 cache/scan/（App 的 FileProvider，见 :androidApp:app 的 res/xml/file_paths.xml），
 * 没有相机应用时调 [onNoCamera]。不需要 CAMERA 权限。
 */
@Composable
fun rememberPhotoSources(onPicked: (String) -> Unit, onNoCamera: () -> Unit = {}): PhotoSources {
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) target?.let(onPicked)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(camera, picker) {
        PhotoSources(
            takePhoto = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newCaptureFile(context))
                target = uri.toString()
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    onNoCamera()
                }
            },
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, "scan").apply { mkdirs() }
    return File.createTempFile("photo-", ".jpg", dir)
}
