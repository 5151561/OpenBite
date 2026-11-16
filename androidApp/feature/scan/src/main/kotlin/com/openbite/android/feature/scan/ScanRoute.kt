package com.openbite.android.feature.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.text.monthDay
import com.openbite.feature.scan.ScanViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 识别菜单（设计稿 02）。进来先拍照（系统相机）或从相册选（Photo Picker），然后交给 ViewModel 流式识别。
 * 权限都在用到时才要：从相册选之前要 ACCESS_MEDIA_LOCATION（读照片位置）；
 * 新建店铺且照片没有位置时要粗略定位。拒绝了也能继续，只是店铺没有位置。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun ScanRoute(
    placeId: String?,
    fromGallery: Boolean,
    onBack: () -> Unit,
    onOpenMenu: (placeId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ScanViewModel = koinViewModel(parameters = { parametersOf(placeId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 这些都存进 saveable：转屏或进程被杀后能接着识别同一张照片。
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var launched by rememberSaveable { mutableStateOf(false) }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val defaultName = stringResource(R.string.scan_default_place_name, monthDay(today))
    val noCamera = stringResource(R.string.scan_no_camera)

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) photoUri = cameraTarget else if (photoUri == null) onBack()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString() else if (photoUri == null) onBack()
    }
    val galleryRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val mediaLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // 不管给不给都继续选图；不给只是读不到照片位置。
        picker.launch(galleryRequest)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }

    fun launchCamera() {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newCaptureFile(context))
        cameraTarget = uri.toString()
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    fun launchGallery() {
        val needsMediaLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !context.isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION)
        if (needsMediaLocation) mediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) else picker.launch(galleryRequest)
    }

    fun launchSource() = if (fromGallery) launchGallery() else launchCamera()

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            launchSource()
        }
    }
    // ViewModel 只在等照片时接受；进程重建后这里会把同一张照片再交一次。
    LaunchedEffect(photoUri) { photoUri?.let { viewModel.onPhoto(it, defaultName) } }
    LaunchedEffect(state.askingLocationPermission) {
        if (!state.askingLocationPermission) return@LaunchedEffect
        if (context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) || context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            viewModel.onLocationPermissionResult(true)
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    ScanScreen(
        state = state,
        onBack = onBack,
        onRetry = { viewModel.retry() },
        onUseSample = { viewModel.retry(useSample = true) },
        onRetake = {
            viewModel.reset()
            photoUri = null
            launchSource()
        },
        onOpenSettings = onOpenSettings,
        onOpenMenu = { state.result?.let { onOpenMenu(it.placeId) } },
        onRename = viewModel::rename,
    )
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** 系统相机写入的临时文件，在 cache/scan/ 下（见 res/xml/file_paths.xml）。 */
private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, "scan").apply { mkdirs() }
    return File.createTempFile("menu-", ".jpg", dir)
}
