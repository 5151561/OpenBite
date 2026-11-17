package com.openbite.android.feature.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.OPENBITE_MIME
import com.openbite.core.domain.ShareOptions
import com.openbite.core.domain.placeGlyph
import com.openbite.feature.share.SharePlaceUiState
import com.openbite.feature.share.SharePlaceViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.openbite.android.core.designsystem.R as DsR

/** 分享店铺（设计稿 17）：生成 .openbite 文件，走系统分享面板。 */
@Composable
fun SharePlaceRoute(placeId: String, onBack: () -> Unit, viewModel: SharePlaceViewModel = koinViewModel(parameters = { parametersOf(placeId) })) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooser = stringResource(R.string.share_chooser)
    val failed = stringResource(R.string.share_place_failed)
    val text = state.place?.let { stringResource(R.string.share_place_text, it.name) }.orEmpty()
    LaunchedEffect(state.exported) {
        val file = state.exported ?: return@LaunchedEffect
        viewModel.exportHandled()
        val uri = ShareFiles.write(context, file.fileName, file.bytes)
        ShareFiles.share(context, uri, OPENBITE_MIME, text, chooser)
    }
    LaunchedEffect(state.failed) {
        if (state.failed) {
            Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            viewModel.failureShown()
        }
    }
    SharePlaceScreen(state, onBack, viewModel::setOptions, viewModel::export, viewModel::showPreview)
    state.preview?.let { ImportPreviewSheet(it, viewModel::hidePreview) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SharePlaceScreen(
    state: SharePlaceUiState,
    onBack: () -> Unit,
    onOptions: (ShareOptions) -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val place = state.place
    val o = state.options
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_place_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.share_back)) } },
            )
        },
        bottomBar = {
            Surface {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Button(onClick = onShare, enabled = place != null && !state.exporting, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (state.exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                        Text(stringResource(R.string.share_place_send), Modifier.padding(start = if (state.exporting) Spacing.s else 0.dp))
                    }
                    OutlinedButton(onClick = onPreview, enabled = place != null, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.share_place_preview))
                    }
                }
            }
        },
    ) { padding ->
        if (place == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { if (state.isLoading) CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // 文件卡片：文件名 + 店铺 + 会带上的东西。
            Column(
                Modifier.padding(horizontal = Spacing.l).fillMaxWidth().background(scheme.primaryContainer, MaterialTheme.shapes.extraLarge).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(state.fileName, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    GlyphAvatar(placeGlyph(place.name), size = 48.dp)
                    Column {
                        Text(place.name, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
                        state.area?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (o.menu && state.dishCount > 0) Pill(stringResource(R.string.share_place_menu_chip, state.dishCount))
                    if (o.location && place.lat != null) Pill(stringResource(R.string.share_place_location_chip))
                    if (o.tags) place.tags.forEach { Pill(it) }
                }
            }
            SectionHeader(stringResource(R.string.share_place_include), color = scheme.primary)
            Column(Modifier.padding(horizontal = Spacing.l).fillMaxWidth().background(scheme.surfaceContainer, MaterialTheme.shapes.large)) {
                OptionRow(
                    stringResource(R.string.share_place_menu),
                    stringResource(R.string.share_place_menu_hint, state.dishCount),
                    o.menu,
                    enabled = state.dishCount > 0,
                ) { onOptions(o.copy(menu = it)) }
                val hasLocation = place.lat != null && place.lon != null
                OptionRow(
                    stringResource(R.string.share_place_location),
                    if (hasLocation) stringResource(R.string.share_place_location_hint, state.area ?: place.name) else stringResource(R.string.share_place_no_location),
                    o.location && hasLocation,
                    enabled = hasLocation,
                ) { onOptions(o.copy(location = it)) }
                OptionRow(
                    stringResource(R.string.share_place_tags),
                    place.tags.takeIf { it.isNotEmpty() }?.joinToString(stringResource(DsR.string.ds_list_separator)) ?: stringResource(R.string.share_place_no_tags),
                    o.tags && place.tags.isNotEmpty(),
                    enabled = place.tags.isNotEmpty(),
                ) { onOptions(o.copy(tags = it)) }
                OptionRow(
                    stringResource(R.string.share_place_photos),
                    if (state.photoCount > 0) stringResource(R.string.share_place_photos_hint, state.photoCount) else stringResource(R.string.share_place_no_photos),
                    o.photos && state.photoCount > 0,
                    enabled = state.photoCount > 0,
                ) { onOptions(o.copy(photos = it)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_place_private), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.share_place_private_value), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.share_place_note),
                Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.m),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun OptionRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
