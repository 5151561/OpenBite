package com.openbite.android.feature.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.PhotoLoad
import com.openbite.android.core.designsystem.component.rememberLocalPhoto
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.hhmm
import com.openbite.android.core.designsystem.text.monthDay
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.MenuEntry
import com.openbite.core.model.MenuPhoto
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.formatMoney
import com.openbite.core.model.formatRating
import com.openbite.feature.place.MenuTab
import com.openbite.feature.place.PlaceMenuUiState
import com.openbite.feature.place.PlaceMenuViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 电子菜单与原照片（设计稿 13 / 14）。改译名或价格还没做（app 传空实现）。 */
@Composable
fun PlaceMenuRoute(
    placeId: String,
    showPhotos: Boolean,
    onBack: () -> Unit,
    onOrder: () -> Unit,
    onRescan: () -> Unit,
    onEditDish: () -> Unit,
    viewModel: PlaceMenuViewModel = koinViewModel(
        parameters = { parametersOf(placeId, if (showPhotos) MenuTab.PHOTO else MenuTab.DIGITAL) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaceMenuScreen(state, viewModel::selectTab, viewModel::selectPhoto, onBack, onOrder, onRescan, onEditDish)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceMenuScreen(
    state: PlaceMenuUiState,
    onSelectTab: (MenuTab) -> Unit = {},
    onSelectPhoto: (Int) -> Unit = {},
    onBack: () -> Unit = {},
    onOrder: () -> Unit = {},
    onRescan: () -> Unit = {},
    onEditDish: () -> Unit = {},
) {
    val menu = state.menu
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(menu?.place?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.menu_back))
                    }
                },
            )
        },
        bottomBar = {
            if (menu != null && menu.entries.isNotEmpty()) {
                Button(
                    onClick = onOrder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.l, vertical = Spacing.l)
                        .height(48.dp),
                ) { Text(stringResource(R.string.menu_order), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (menu == null) {
                Text(stringResource(R.string.place_missing), Modifier.padding(Spacing.xxl), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            val tabs = listOf(MenuTab.DIGITAL to R.string.menu_tab_digital, MenuTab.PHOTO to R.string.menu_tab_photo)
            PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == state.tab }) {
                tabs.forEach { (tab, label) ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(stringResource(label)) },
                    )
                }
            }
            when (state.tab) {
                MenuTab.DIGITAL -> DigitalMenu(menu, state.currentYear, onRescan, onEditDish)
                MenuTab.PHOTO -> PhotoMenu(menu, state.photoIndex, state.currentYear, onSelectPhoto, onRescan)
            }
        }
    }
}

@Composable
private fun DigitalMenu(menu: PlaceMenu, currentYear: Int, onRescan: () -> Unit, onEditDish: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.l)) {
        item(key = "header") {
            val scanned = menu.lastScannedAt
            val header = if (scanned != null) {
                stringResource(R.string.menu_header_scanned, monthDay(scanned.date, currentYear), menu.entries.size)
            } else {
                stringResource(R.string.menu_header, menu.entries.size)
            }
            Text(
                header,
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (menu.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.menu_empty),
                    modifier = Modifier.padding(Spacing.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(menu.entries, key = { _, it -> it.dish.id }) { index, entry ->
            MenuDishRow(entry, index, menu.entries.size)
        }
        item(key = "actions") {
            Column(
                modifier = Modifier.padding(top = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                SegmentedListItem(
                    headline = stringResource(R.string.menu_rescan),
                    shape = segmentShape(0, 2),
                    leading = { Icon(OpenBiteIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = onRescan,
                )
                SegmentedListItem(
                    headline = stringResource(R.string.menu_edit_dish),
                    shape = segmentShape(1, 2),
                    leading = { Icon(OpenBiteIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = onEditDish,
                )
            }
        }
    }
}

/** 电子菜单的一行：头像、原名 + 译名、价格 + 点过几次。 */
@Composable
private fun MenuDishRow(entry: MenuEntry, index: Int, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(vertical = Spacing.xxs / 2)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(scheme.surfaceContainer, segmentShape(index, count))
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        GlyphAvatar(entry.glyph)
        Column(Modifier.weight(1f)) {
            Text(entry.dish.nameOriginal, style = MaterialTheme.typography.titleMedium)
            Text(entry.dish.nameTranslated, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            entry.dish.price?.let { Text(formatMoney(it), style = MaterialTheme.typography.bodyLarge) }
            val stats = entry.stats
            val note = when {
                stats == null -> stringResource(R.string.menu_not_ordered)
                stats.averageRating != null ->
                    stringResource(R.string.menu_rating_and_times, formatRating(stats.averageRating ?: 0.0), timesOrdered(stats))
                else -> timesOrdered(stats)
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoMenu(menu: PlaceMenu, index: Int, currentYear: Int, onSelectPhoto: (Int) -> Unit, onRescan: () -> Unit) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val photo = menu.photos.getOrNull(index)
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (photo == null) {
                NoPhotoPlaceholder()
            } else {
                PhotoContent(photo, index, ContentScale.Crop, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopEnd).padding(Spacing.m)) {
                    RoundIconButton(OpenBiteIcons.Fullscreen, stringResource(R.string.menu_photo_fullscreen)) { fullscreen = true }
                }
            }
        }
        photo?.let {
            Text(
                stringResource(R.string.menu_photo_caption, monthDay(it.takenAtLocal.date, currentYear), it.takenAtLocal.time.hhmm()),
                modifier = Modifier.padding(horizontal = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            menu.photos.indices.forEach { i -> PageThumb(i, selected = i == index) { onSelectPhoto(i) } }
            AddPageThumb(onRescan)
        }
    }
    if (fullscreen && photo != null) {
        FullscreenPhoto(photo, index) { fullscreen = false }
    }
}

@Composable
private fun PhotoContent(photo: MenuPhoto, index: Int, scale: ContentScale, modifier: Modifier) {
    val load by rememberLocalPhoto(photo.scan.photoPath)
    when (val current = load) {
        PhotoLoad.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.inverseOnSurface)
        PhotoLoad.Failed -> Text(
            stringResource(R.string.menu_photo_unreadable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        is PhotoLoad.Loaded -> Image(
            bitmap = current.bitmap,
            contentDescription = stringResource(R.string.menu_photo_description, index + 1),
            modifier = modifier,
            contentScale = scale,
        )
    }
}

@Composable
private fun NoPhotoPlaceholder() {
    Column(
        modifier = Modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(OpenBiteIcons.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
        Text(stringResource(R.string.menu_photo_none), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
        Text(
            stringResource(R.string.menu_photo_none_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageThumb(index: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border = if (selected) BorderStroke(3.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant)
    val description = stringResource(R.string.menu_photo_page, index + 1)
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 76.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerLowest)
            .border(border, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    }
}

@Composable
private fun AddPageThumb(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .size(width = 60.dp, height = 76.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = size.copy(width = size.width - stroke, height = size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(OpenBiteIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.menu_photo_add), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 全屏看原照片，双指缩放和拖动。 */
@Composable
private fun FullscreenPhoto(photo: MenuPhoto, index: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            pan = if (zoom == 1f) Offset.Zero else pan + panChange
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                PhotoContent(
                    photo,
                    index,
                    ContentScale.Fit,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                )
                Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.m)) {
                    RoundIconButton(OpenBiteIcons.Close, stringResource(R.string.menu_photo_close), onDismiss)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlaceMenuDigitalPreview() {
    OpenBiteTheme(darkTheme = false) {
        PlaceMenuScreen(PlaceMenuUiState(isLoading = false, menu = PlacePreviewData.menu, currentYear = 2026))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlaceMenuPhotoDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        PlaceMenuScreen(PlaceMenuUiState(isLoading = false, menu = PlacePreviewData.menu.copy(photos = emptyList()), tab = MenuTab.PHOTO, currentYear = 2026))
    }
}
