package com.openbite.android.feature.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** 底图：OpenFreeMap 的 liberty 样式（颜色最接近设计稿：浅绿的公园、蓝色的河）。免费、不需要 Key。 */
object MapStyle {
    const val URL = "https://tiles.openfreemap.org/styles/liberty"

    /** 没有任何图钉时的镜头：京都市中心。 */
    val DEFAULT_CENTER = LatLng(35.0116, 135.7681)
    const val DEFAULT_ZOOM = 12.0
    const val PIN_ZOOM = 15.0
}

/** 一个图钉要画成什么样。颜色是 ARGB。 */
data class PinSpec(
    val id: String,
    val lat: Double,
    val lon: Double,
    val char: String,
    val container: Int,
    val content: Int,
    val selected: Boolean,
)

/** 地图上要做的镜头动作。每次给一个新对象就执行一次。 */
sealed interface CameraCommand {
    /** 框住这些点（只有一个点时放大到它）。 */
    class Fit(val points: List<LatLng>, val focus: LatLng? = null) : CameraCommand

    class MoveTo(val point: LatLng, val zoom: Double? = null) : CameraCommand
}

/** 镜头位置，转屏时存起来：lat, lon, zoom。 */
typealias SavedCamera = DoubleArray

/**
 * MapLibre 的 MapView 放进 Compose：
 * - 生命周期跟着 LocalLifecycleOwner 转发（onStart/onResume/onPause/onStop），离开组合时 onDestroy；
 * - 内存紧张时转发 onLowMemory；
 * - 图钉是一个 GeoJSON 源 + SymbolLayer，图标是按单字头像画好的位图。
 */
@Composable
fun MapLibreView(
    pins: List<PinSpec>,
    borderColor: Int,
    camera: CameraCommand?,
    savedCamera: SavedCamera?,
    onCameraSaved: (SavedCamera) -> Unit,
    onPinClick: (String) -> Unit,
    attributionBottom: Dp,
    modifier: Modifier = Modifier,
    onLongPress: ((LatLng) -> Unit)? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapView = rememberMapViewWithLifecycle(context)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentPinClick by rememberUpdatedState(onPinClick)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentSave by rememberUpdatedState(onCameraSaved)

    DisposableEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            savedCamera?.takeIf { it.size == 3 }?.let {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it[0], it[1]), it[2]))
            }
            m.addOnMapClickListener { latLng ->
                val point: PointF = m.projection.toScreenLocation(latLng)
                val hit = m.queryRenderedFeatures(point, PIN_LAYER).firstOrNull()
                val id = hit?.getStringProperty(PROP_ID)
                if (id != null) currentPinClick(id)
                id != null
            }
            m.addOnMapLongClickListener { latLng ->
                val handler = currentLongPress ?: return@addOnMapLongClickListener false
                handler(latLng)
                true
            }
            m.addOnCameraIdleListener {
                val target = m.cameraPosition.target ?: return@addOnCameraIdleListener
                currentSave(doubleArrayOf(target.latitude, target.longitude, m.cameraPosition.zoom))
            }
            m.setStyle(Style.Builder().fromUri(MapStyle.URL)) { loaded ->
                loaded.addSource(GeoJsonSource(PIN_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
                loaded.addLayer(
                    SymbolLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                        PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.symbolSortKey(Expression.get(PROP_SORT)),
                    ),
                )
                style = loaded
                onMapReady(m)
            }
        }
        onDispose {
            map = null
            style = null
        }
    }

    // 图钉：缺的图标补画，再整批换掉 GeoJSON。
    LaunchedEffect(style, pins, borderColor) {
        val s = style ?: return@LaunchedEffect
        val features = pins.map { pin ->
            val icon = iconName(pin, borderColor)
            if (s.getImage(icon) == null) s.addImage(icon, drawPin(pin, borderColor, density.density))
            Feature.fromGeometry(Point.fromLngLat(pin.lon, pin.lat)).apply {
                addStringProperty(PROP_ID, pin.id)
                addStringProperty(PROP_ICON, icon)
                addNumberProperty(PROP_SORT, if (pin.selected) 1 else 0)
            }
        }
        s.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(map, camera) {
        val m = map ?: return@LaunchedEffect
        when (val c = camera) {
            is CameraCommand.Fit -> {
                val px = { dp: Float -> (dp * density.density).toInt() }
                when {
                    c.points.isEmpty() -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(MapStyle.DEFAULT_CENTER, MapStyle.DEFAULT_ZOOM))
                    c.points.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(c.points.first(), MapStyle.PIN_ZOOM))
                    else -> {
                        val bounds = LatLngBounds.Builder().includes(c.points).build()
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, px(56f), px(180f), px(56f), px(260f)))
                        if (m.cameraPosition.zoom > MapStyle.PIN_ZOOM) m.moveCamera(CameraUpdateFactory.zoomTo(MapStyle.PIN_ZOOM))
                    }
                }
                c.focus?.let { m.moveCamera(CameraUpdateFactory.newLatLng(it)) }
            }
            is CameraCommand.MoveTo -> m.easeCamera(
                if (c.zoom != null) CameraUpdateFactory.newLatLngZoom(c.point, c.zoom) else CameraUpdateFactory.newLatLng(c.point),
            )
            null -> Unit
        }
    }

    // 版权信息放在底部卡片上面，别被挡住（OpenFreeMap / OpenStreetMap 要求可见）。
    LaunchedEffect(map, attributionBottom) {
        val m = map ?: return@LaunchedEffect
        val bottom = (attributionBottom.value * density.density).toInt()
        val side = (8 * density.density).toInt()
        m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
        m.uiSettings.logoGravity = Gravity.BOTTOM or Gravity.START
        m.uiSettings.setLogoMargins(side, 0, 0, bottom)
        m.uiSettings.setAttributionMargins(side * 12, 0, 0, bottom)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun rememberMapViewWithLifecycle(context: Context): MapView {
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()

            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        context.registerComponentCallbacks(memory)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            // 离开页面（导航走了）：按顺序收尾，释放原生资源，不留 MapView。
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private const val PIN_SOURCE = "openbite-pins"
private const val PIN_LAYER = "openbite-pins-layer"
private const val PROP_ID = "id"
private const val PROP_ICON = "icon"
private const val PROP_SORT = "sort"

private fun iconName(pin: PinSpec, border: Int) =
    "pin-${pin.char}-${pin.container}-${pin.content}-$border-${if (pin.selected) "sel" else "n"}"

/** 单字圆形图钉：普通 48dp、3dp 白边；选中 60dp、4dp 白边（设计稿 06）。 */
private fun drawPin(pin: PinSpec, border: Int, density: Float): Bitmap {
    val sizeDp = if (pin.selected) 60f else 48f
    val borderDp = if (pin.selected) 4f else 3f
    val shadowDp = 3f
    val size = ((sizeDp + shadowDp * 2) * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val radius = sizeDp * density / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x40000000
    canvas.drawCircle(cx, cx + density, radius + density, paint)
    paint.color = border
    canvas.drawCircle(cx, cx, radius, paint)
    paint.color = pin.container
    canvas.drawCircle(cx, cx, radius - borderDp * density, paint)
    paint.color = pin.content
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = (if (pin.selected) 24f else 18f) * density
    val metrics = paint.fontMetrics
    canvas.drawText(pin.char, cx, cx - (metrics.ascent + metrics.descent) / 2f, paint)
    return bitmap
}
