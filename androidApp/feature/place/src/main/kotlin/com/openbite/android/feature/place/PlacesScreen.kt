package com.openbite.android.feature.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.PlaceBadge
import com.openbite.core.model.PlaceSummary
import com.openbite.feature.place.PlacesUiState
import com.openbite.feature.place.PlacesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** 店铺 Tab（设计稿 12）。右上角菜单里可以导入朋友分享的店铺文件。 */
@Composable
fun PlacesRoute(
    onOpenPlace: (placeId: String) -> Unit,
    onSearch: () -> Unit,
    onOpenMap: () -> Unit,
    onScanMenu: () -> Unit,
    onImportFile: (uri: String) -> Unit,
    viewModel: PlacesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // “导入店铺”：用系统文件选择器（SAF）选 .openbite；类型很多 App 给不准，所以不限类型，交给导入页校验。
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportFile(it.toString()) }
    }
    PlacesScreen(state, onOpenPlace, onSearch, onOpenMap, onScanMenu, onImport = { picker.launch(arrayOf("*/*")) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    state: PlacesUiState,
    onOpenPlace: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onScanMenu: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(OpenBiteIcons.Search, contentDescription = stringResource(R.string.places_search))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(OpenBiteIcons.MoreVert, contentDescription = stringResource(R.string.places_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.places_import)) },
                                onClick = {
                                    menuOpen = false
                                    onImport()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanMenu,
                icon = { Icon(OpenBiteIcons.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.places_scan_menu)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Spacing.xxl, end = Spacing.l, bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Text(
                        stringResource(R.string.places_count, state.places.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssistChip(
                        onClick = onOpenMap,
                        label = { Text(stringResource(R.string.places_on_map)) },
                        leadingIcon = {
                            Icon(
                                OpenBiteIcons.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
            if (state.places.isEmpty()) {
                item(key = "empty") { Note(stringResource(R.string.places_empty)) }
            }
            itemsIndexed(state.places, key = { _, it -> it.place.id }) { index, summary ->
                PlaceRow(
                    summary = summary,
                    shape = segmentShape(index, state.places.size),
                    onClick = { onOpenPlace(summary.place.id) },
                )
            }
            if (state.places.isNotEmpty()) {
                item(key = "footer") { Note(stringResource(R.string.places_footer), Modifier.padding(top = Spacing.m)) }
            }
        }
    }
}

@Composable
private fun PlaceRow(summary: PlaceSummary, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    SegmentedListItem(
        headline = summary.place.name,
        supporting = stringResource(R.string.places_meta, summary.dishCount, summary.photoCount, summary.visitCount),
        shape = shape,
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
        leading = { GlyphAvatar(summary.glyph) },
        headlineTrailing = summary.badge?.let { badge -> { Badge(badgeLabel(badge)) } },
        trailing = { Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}

@Composable
private fun badgeLabel(badge: PlaceBadge): String = when (badge) {
    PlaceBadge.Tonight -> stringResource(R.string.places_badge_tonight)
    PlaceBadge.Milestone -> stringResource(R.string.places_badge_milestone)
    is PlaceBadge.Tag -> badge.tag
}

/** 店名旁的小标签（secondary-container）。 */
@Composable
internal fun Badge(
    text: String,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Text(
        text,
        modifier = Modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlacesScreenPreview() {
    OpenBiteTheme(darkTheme = false) { PlacesScreen(PlacesUiState(isLoading = false, places = PlacePreviewData.places)) }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlacesScreenDarkPreview() {
    OpenBiteTheme(darkTheme = true) { PlacesScreen(PlacesUiState(isLoading = false, places = PlacePreviewData.places)) }
}
