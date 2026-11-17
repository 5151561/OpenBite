package com.openbite.android.feature.share

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.PhotoLoad
import com.openbite.android.core.designsystem.component.readLocalPhotoBytes
import com.openbite.android.core.designsystem.component.rememberLocalPhoto
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.feature.share.CardColor
import com.openbite.feature.share.ShareMealUiState
import com.openbite.feature.share.ShareMealViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 分享这一餐（设计稿 16）：选照片、底色、显示哪些，存为图片或发出去。 */
@Composable
fun ShareMealRoute(logId: String, onBack: () -> Unit, viewModel: ShareMealViewModel = koinViewModel(parameters = { parametersOf(logId) })) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShareMealScreen(state, onBack, viewModel::selectPhoto, viewModel::selectColor, viewModel::setShowPlace, viewModel::setShowAmount)
}

@Composable
private fun palette(color: CardColor): CardPalette {
    val s = MaterialTheme.colorScheme
    val (bg, fg) = when (color) {
        CardColor.PRIMARY -> s.primary to s.onPrimary
        CardColor.PRIMARY_CONTAINER -> s.primaryContainer to s.onPrimaryContainer
        CardColor.TERTIARY_CONTAINER -> s.tertiaryContainer to s.onTertiaryContainer
        CardColor.NEUTRAL -> s.surfaceContainerHighest to s.onSurface
    }
    return CardPalette(bg.toArgb(), fg.toArgb(), s.surfaceVariant.toArgb())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMealScreen(
    state: ShareMealUiState,
    onBack: () -> Unit,
    onPhoto: (String?) -> Unit,
    onColor: (CardColor) -> Unit,
    onShowPlace: (Boolean) -> Unit,
    onShowAmount: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal = palette(state.color)
    val card = state.card
    val photoPath = state.photoPath
    // 预览和发出去的是同一张位图（预览用小一点的倍率）。
    val preview by produceState<android.graphics.Bitmap?>(null, card, photoPath, pal) {
        value = card?.let {
            withContext(Dispatchers.Default) {
                val bytes = photoPath?.let { p -> withContext(Dispatchers.IO) { readLocalPhotoBytes(context, p) } }
                MealCardRenderer.render(context.resources, it, bytes, pal, scale = 2f)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.share_saved)
    val saveFailed = stringResource(R.string.share_save_failed)
    val chooser = stringResource(R.string.share_chooser)
    val text = card?.placeName?.let { stringResource(R.string.share_meal_text, it) } ?: stringResource(R.string.share_meal_text_no_place)

    suspend fun renderPng(): ByteArray? {
        val c = card ?: return null
        return withContext(Dispatchers.Default) {
            val bytes = photoPath?.let { withContext(Dispatchers.IO) { readLocalPhotoBytes(context, it) } }
            val bitmap = MealCardRenderer.render(context.resources, c, bytes, pal)
            ShareFiles.png(bitmap).also { bitmap.recycle() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_meal_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.share_back)) } },
            )
        },
        bottomBar = {
            Surface {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (ShareFiles.canSaveToGallery) {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val png = renderPng()
                                    val ok = png != null && ShareFiles.saveToGallery(context, "openbite-${System.currentTimeMillis()}.png", png)
                                    busy = false
                                    Toast.makeText(context, if (ok) saved else saveFailed, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = card != null && !busy,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(stringResource(R.string.share_save_image)) }
                    }
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val png = renderPng()
                                busy = false
                                if (png != null) {
                                    val uri = ShareFiles.write(context, "openbite-meal.png", png)
                                    ShareFiles.share(context, uri, "image/png", text, chooser)
                                }
                            }
                        },
                        enabled = card != null && !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text(stringResource(R.string.share_send)) }
                }
            }
        },
    ) { padding ->
        if (state.isLoading || card == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { if (state.isLoading) CircularProgressIndicator() }
            return@Scaffold
        }
        val description = stringResource(R.string.share_preview_description)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(300.dp).aspectRatio(MealCardRenderer.WIDTH_DP / MealCardRenderer.HEIGHT_DP)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = preview
                    if (bitmap == null) CircularProgressIndicator() else Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Text(stringResource(R.string.share_photo), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (state.photos.isEmpty()) {
                Text(stringResource(R.string.share_photo_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = state.photoId == null, onClick = { onPhoto(null) }, label = { Text(stringResource(R.string.share_photo_none)) })
                    state.photos.forEach { photo -> PhotoChoice(photo.path, selected = photo.id == state.photoId) { onPhoto(photo.id) } }
                }
            }
            Text(stringResource(R.string.share_color), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val names = stringArrayResource(R.array.share_color_names)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                CardColor.entries.forEach { color ->
                    val p = palette(color)
                    val selected = color == state.color
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(p.background))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onColor(color) }
                            .semantics { contentDescription = names[color.ordinal] },
                    )
                }
            }
            ToggleRow(stringResource(R.string.share_show_place), state.showPlace, onShowPlace)
            ToggleRow(stringResource(R.string.share_show_amount), state.showAmount, onShowAmount)
        }
    }
}

private val PhotoChoiceSize = 64.dp

@Composable
private fun PhotoChoice(path: String, selected: Boolean, onClick: () -> Unit) {
    val load by rememberLocalPhoto(path, targetSize = PhotoChoiceSize)
    Box(
        Modifier.size(PhotoChoiceSize).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        (load as? PhotoLoad.Loaded)?.let {
            Image(it.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onChange(!checked) }.padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
