package com.openbite.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.colors
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.feature.map.PickLocationViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** 给没有位置的店铺选位置：十字准星对准后点“设为这里”，或者长按地图把那个点挪到中间。 */
@Composable
fun PickLocationRoute(
    placeId: String,
    onDone: () -> Unit,
    viewModel: PickLocationViewModel = koinViewModel(parameters = { parametersOf(placeId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    var savedCamera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var camera by remember { mutableStateOf<CameraCommand?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    // 和地图页一样：首次镜头放好之前不存镜头，否则默认的全球视角会顶掉首次定位。
    var cameraPlaced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && !cameraPlaced) {
            cameraPlaced = true
            camera = CameraCommand.Fit(state.otherPins.map { LatLng(it.lat, it.lon) })
        }
    }

    Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHigh)) {
        MapLibreView(
            pins = state.otherPins.map {
                val (c, t) = it.glyph.tone.colors()
                PinSpec(it.place.id, it.lat, it.lon, it.glyph.char, c.toArgb(), t.toArgb(), selected = false)
            },
            borderColor = scheme.surfaceContainerLowest.toArgb(),
            camera = camera,
            savedCamera = savedCamera,
            onCameraSaved = { if (cameraPlaced) savedCamera = it },
            onPinClick = {},
            attributionBottom = 140.dp,
            onLongPress = { camera = CameraCommand.MoveTo(it) },
            onMapReady = { map = it },
            modifier = Modifier.fillMaxSize(),
        )
        // 十字准星：地图正中。
        Box(Modifier.align(Alignment.Center)) {
            Box(Modifier.width(2.dp).height(36.dp).background(scheme.primary).align(Alignment.Center))
            Box(Modifier.width(36.dp).height(2.dp).background(scheme.primary).align(Alignment.Center))
            Box(Modifier.size(12.dp).background(scheme.primary, MaterialTheme.shapes.extraLarge).align(Alignment.Center))
        }
        Column(Modifier.statusBarsPadding().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilledIconButton(
                onClick = onDone,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.surfaceContainerLowest, contentColor = scheme.onSurface),
            ) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.pick_back)) }
            Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, shadowElevation = 3.dp) {
                Column(Modifier.padding(Spacing.l)) {
                    Text(stringResource(R.string.pick_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.pick_hint, state.place?.name.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = { map?.cameraPosition?.target?.let { viewModel.confirm(it.latitude, it.longitude) } },
            enabled = map != null && !state.saving && state.place != null,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(Spacing.l).height(56.dp),
        ) {
            if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
            Text(stringResource(R.string.pick_confirm), Modifier.padding(start = Spacing.s), style = MaterialTheme.typography.titleMedium)
        }
    }
}
