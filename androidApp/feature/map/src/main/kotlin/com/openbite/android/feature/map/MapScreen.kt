package com.openbite.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.RatingDisplay
import com.openbite.android.core.designsystem.component.colors
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.fullDate
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.text.quoted
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.CapsuleKind
import com.openbite.core.domain.MapFilter
import com.openbite.core.domain.MapPin
import com.openbite.core.domain.TimeCapsule
import com.openbite.core.model.formatMoney
import com.openbite.core.model.formatRating
import com.openbite.feature.map.MapUiState
import com.openbite.feature.map.MapViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.android.geometry.LatLng
import com.openbite.android.core.designsystem.R as DsR

/**
 * 地图与时间胶囊（设计稿 06）。压在当前 Tab 的返回栈上，底部导航照常显示。
 * [focusPlaceId]：从店铺页“在地图上看”进来时先选中这家店。
 */
@Composable
fun MapRoute(
    focusPlaceId: String?,
    onBackToList: () -> Unit,
    onOpenPlace: (placeId: String) -> Unit,
    onOpenMeal: (logId: String, isDraft: Boolean) -> Unit,
    viewModel: MapViewModel = koinViewModel(parameters = { parametersOf(focusPlaceId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MapScreen(state, focusPlaceId, viewModel::selectFilter, viewModel::setQuery, viewModel::selectPlace, onBackToList, onOpenPlace, onOpenMeal)
}

@Composable
fun MapScreen(
    state: MapUiState,
    focusPlaceId: String?,
    onFilter: (MapFilter) -> Unit,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenMeal: (String, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var savedCamera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var cardHeight by rememberSaveable { mutableStateOf(0) }
    var camera by remember { mutableStateOf<CameraCommand?>(null) }
    // 首次镜头放好之前不存镜头：MapLibre 一加载完就会以默认的全球视角报一次“镜头停下”，
    // 那时数据可能还没到，存下来就会让下面的首次定位被跳过（地图停在全世界、看不到图钉）。
    var cameraPlaced by rememberSaveable { mutableStateOf(false) }

    // 第一次打开：框住全部图钉，或对准选中的店。转屏、返回时用存下来的镜头。
    LaunchedEffect(state.isLoading) {
        if (state.isLoading || cameraPlaced) return@LaunchedEffect
        cameraPlaced = true
        val focus = state.allPins.firstOrNull { it.place.id == (focusPlaceId ?: state.selectedPlaceId) }
        camera = if (focusPlaceId != null && focus != null) {
            CameraCommand.MoveTo(LatLng(focus.lat, focus.lon), MapStyle.PIN_ZOOM)
        } else {
            CameraCommand.Fit(state.allPins.map { LatLng(it.lat, it.lon) }, focus?.let { LatLng(it.lat, it.lon) })
        }
    }

    val selectedColors = scheme.primary.toArgb() to scheme.onPrimary.toArgb()
    val pins = state.pins.map { pin ->
        val selected = pin.place.id == state.selectedPlaceId
        val (container, content) = if (selected) selectedColors else pin.glyph.tone.colors().let { it.first.toArgb() to it.second.toArgb() }
        PinSpec(pin.place.id, pin.lat, pin.lon, pin.glyph.char, container, content, selected)
    }

    val description = pinsDescription(state.pins)
    Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHigh)) {
        MapLibreView(
            pins = pins,
            borderColor = scheme.surfaceContainerLowest.toArgb(),
            camera = camera,
            savedCamera = savedCamera,
            onCameraSaved = { if (cameraPlaced) savedCamera = it },
            onPinClick = { id ->
                onSelect(id)
                state.pins.firstOrNull { it.place.id == id }?.let { camera = CameraCommand.MoveTo(LatLng(it.lat, it.lon)) }
            },
            attributionBottom = with(density) { cardHeight.toDp() } + 24.dp,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
        )
        Column(Modifier.statusBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SearchBar(state.query, onQuery, onBackToList)
            FilterRow(state, onFilter)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(Spacing.l).onSizeChanged { cardHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.placesWithoutLocation > 0 && !state.isLoading) {
                Text(
                    stringResource(R.string.map_missing_locations, state.placesWithoutLocation),
                    modifier = Modifier.background(scheme.surfaceContainerLow, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            when {
                state.isLoading -> Unit
                state.allPins.isEmpty() -> InfoCard(stringResource(R.string.map_no_pins))
                state.capsule == null -> InfoCard(stringResource(R.string.map_no_capsule))
                else -> CapsuleCard(state.capsule!!, onOpenPlace, onOpenMeal)
            }
        }
    }
}

@Composable
private fun pinsDescription(pins: List<MapPin>): String = pins.joinToString(stringResource(DsR.string.ds_list_separator)) { it.place.name }

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onBackToList: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(OpenBiteIcons.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = Spacing.l)) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.map_search_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(OpenBiteIcons.Close, contentDescription = stringResource(R.string.map_clear_search)) }
            }
            IconButton(onClick = onBackToList) { Icon(OpenBiteIcons.ListIcon, contentDescription = stringResource(R.string.map_list)) }
        }
    }
}

@Composable
private fun FilterRow(state: MapUiState, onFilter: (MapFilter) -> Unit) {
    val label = stringResource(R.string.map_filter_group)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        MapFilter.entries.forEach { filter ->
            val count = state.counts[filter] ?: 0
            val text = when (filter) {
                MapFilter.ALL -> R.string.map_filter_all
                MapFilter.MILESTONE -> R.string.map_filter_milestone
                MapFilter.DATE -> R.string.map_filter_date
                MapFilter.LATE_NIGHT -> R.string.map_filter_late_night
            }
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(text, count)) },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = FilterChipDefaults.filterChipElevation(elevation = 1.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 3.dp) {
        Text(text, Modifier.fillMaxWidth().padding(Spacing.xl), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 时间胶囊卡片。 */
@Composable
private fun CapsuleCard(capsule: TimeCapsule, onOpenPlace: (String) -> Unit, onOpenMeal: (String, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val memory = capsule.memory
    val separator = stringResource(DsR.string.ds_separator)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerLow, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(OpenBiteIcons.Favorite, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                val label = when (capsule.kind) {
                    CapsuleKind.UPCOMING_MILESTONE -> stringResource(
                        R.string.map_capsule_upcoming,
                        if (capsule.yearsAgo == 1) stringResource(R.string.map_capsule_one_year) else stringResource(R.string.map_capsule_years, capsule.yearsAgo),
                    )
                    CapsuleKind.PLACE_MILESTONE -> stringResource(R.string.map_capsule_place_milestone)
                    CapsuleKind.PLACE_RECENT -> stringResource(R.string.map_capsule_recent)
                }
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                Text(stringResource(R.string.map_visits, capsule.visitCount), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Text(
                capsule.placeName,
                modifier = Modifier.padding(top = Spacing.xs).clickable { onOpenPlace(capsule.placeId) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val companions = memory.companions.takeIf { it.isNotEmpty() }?.let {
                stringResource(DsR.string.ds_memory_with, it.joinToString(stringResource(DsR.string.ds_list_separator)))
            }
            val meta = listOfNotNull(fullDate(memory.localDate), capsule.milestone?.let { milestoneTitle(it, capsule.milestoneYears) }, companions)
            Text(meta.joinToString(separator), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            memory.note?.let { Text(quoted(it), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                when {
                    memory.isDraft -> Text(stringResource(R.string.map_draft), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    memory.rating != null -> RatingDisplay(formatRating(memory.rating!!), color = scheme.tertiary, starColor = scheme.tertiary)
                }
                val money = listOfNotNull(stringResource(R.string.map_dishes, memory.dishCount), memory.total?.let(::formatMoney))
                Text(money.joinToString(separator), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                FilledTonalButton(onClick = { onOpenMeal(memory.logId, memory.isDraft) }, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.map_open_meal))
                }
            }
        }
    }
}
