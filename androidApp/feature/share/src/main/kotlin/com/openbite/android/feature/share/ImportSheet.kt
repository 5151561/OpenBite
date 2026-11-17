package com.openbite.android.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.ImportError
import com.openbite.core.domain.ImportPreview
import com.openbite.core.domain.placeGlyph
import com.openbite.feature.share.ImportPhase
import com.openbite.feature.share.ImportViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.openbite.android.core.designsystem.R as DsR

/**
 * 导入店铺（设计稿 18），盖在店铺列表上。[uri] 是收到或选中的文件。导入成功后交出新店铺 id。
 * 外层的 ModalBottomSheet 由 App 的 bottom sheet scene 提供，ViewModel 跟着这个返回栈 entry 走，
 * 关掉就清理，同一个文件再导入一次会重新读。
 */
@Composable
fun ImportSheetRoute(
    uri: String,
    onDismiss: () -> Unit,
    onImported: (placeId: String) -> Unit,
    viewModel: ImportViewModel = koinViewModel(parameters = { parametersOf(uri) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.placeId) {
        val placeId = state.placeId ?: return@LaunchedEffect
        viewModel.importHandled()
        onImported(placeId)
    }
    when (state.phase) {
        ImportPhase.READING -> Box(Modifier.fillMaxWidth().padding(Spacing.xxxl), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.import_reading))
            }
        }
        ImportPhase.FAILED -> ImportFailed(state.error ?: ImportError.READ_FAILED, onDismiss)
        else -> state.preview?.let {
            ImportSheetContent(
                preview = it,
                title = stringResource(R.string.import_title),
                busy = state.phase == ImportPhase.IMPORTING || state.phase == ImportPhase.DONE,
                onCancel = onDismiss,
                onConfirm = viewModel::confirm,
            )
        }
    }
}

/** “看看对方收到的样子”：同一张卡片，导入按钮不可点。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(preview: ImportPreview, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImportSheetContent(preview, stringResource(R.string.import_preview_title), busy = false, onCancel = onDismiss, onConfirm = null)
    }
}

@Composable
private fun ImportFailed(error: ImportError, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.import_failed_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(error.message()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.import_close)) }
    }
}

private fun ImportError.message(): Int = when (this) {
    ImportError.NOT_OPENBITE -> R.string.import_error_not_openbite
    ImportError.TOO_LARGE -> R.string.import_error_too_large
    ImportError.NOT_ZIP, ImportError.MISSING_MANIFEST, ImportError.BAD_MANIFEST, ImportError.TOO_MANY_ENTRIES -> R.string.import_error_damaged
    ImportError.UNSAFE_PATH, ImportError.UNEXPECTED_ENTRY -> R.string.import_error_unsafe
    ImportError.UNSUPPORTED_VERSION -> R.string.import_error_version
    ImportError.INVALID_FIELD -> R.string.import_error_invalid
    ImportError.BAD_PHOTO -> R.string.import_error_photo
    ImportError.READ_FAILED -> R.string.import_error_read
    ImportError.SAVE_FAILED -> R.string.import_error_save
}

@Composable
internal fun ImportSheetContent(preview: ImportPreview, title: String, busy: Boolean, onCancel: () -> Unit, onConfirm: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val separator = stringResource(DsR.string.ds_separator)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            GlyphAvatar(placeGlyph(preview.name), size = 56.dp)
            Column {
                Text(preview.name, style = MaterialTheme.typography.headlineSmall)
                val meta = listOfNotNull(preview.area, preview.tags.firstOrNull()).joinToString(separator)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth().background(scheme.surfaceContainer, MaterialTheme.shapes.large).padding(vertical = Spacing.s)) {
            IncludeRow(stringResource(R.string.import_menu), pluralStringResource(R.plurals.import_menu_count, preview.dishCount, preview.dishCount), included = preview.dishCount > 0)
            val hasTags = preview.tags.isNotEmpty()
            IncludeRow(
                stringResource(R.string.import_location),
                when {
                    preview.hasLocation -> stringResource(R.string.import_location_yes)
                    hasTags -> stringResource(R.string.import_location_tags_only)
                    else -> stringResource(R.string.import_photos_none)
                },
                included = preview.hasLocation || hasTags,
            )
            IncludeRow(
                stringResource(R.string.import_photos),
                if (preview.photoCount > 0) pluralStringResource(R.plurals.import_photos_count, preview.photoCount, preview.photoCount) else stringResource(R.string.import_photos_none),
                included = preview.photoCount > 0,
            )
        }
        Text(stringResource(R.string.import_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(if (onConfirm == null) R.string.import_close else R.string.import_cancel)) }
            Button(onClick = { onConfirm?.invoke() }, enabled = onConfirm != null && !busy, modifier = Modifier.height(48.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                Text(stringResource(R.string.import_confirm), Modifier.padding(start = if (busy) Spacing.s else 0.dp))
            }
        }
    }
}

@Composable
private fun IncludeRow(title: String, detail: String, included: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(if (included) R.string.import_includes else R.string.import_excludes)
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Icon(
            if (included) OpenBiteIcons.Check else OpenBiteIcons.Close,
            contentDescription = label,
            tint = if (included) scheme.primary else scheme.outline,
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (included) scheme.onSurface else scheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}
