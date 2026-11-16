package com.openbite.core.domain

import com.openbite.core.data.MenuScanRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.model.Place
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.VisionModelChoice
import com.openbite.core.model.effectiveVisionModel
import com.openbite.core.model.maskApiKey
import com.openbite.core.network.MenuScanRequest
import com.openbite.core.network.ScanEvent
import com.openbite.core.network.ScannedDish
import com.openbite.core.network.VisionException
import com.openbite.core.network.VisionModelRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 生成主键。测试里换成固定序列。 */
fun interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

/** 当前 UTC 毫秒。测试里换成固定值。 */
fun interface NowProvider {
    fun nowMillis(): Long
}

@OptIn(ExperimentalTime::class)
class SystemNowProvider : NowProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

data class ScanMenuRequest(
    /** 照片的 content:// 或 file:// 地址。 */
    val sourceUri: String,
    /** 从店铺页发起时挂到这家店；null 表示新建店铺。 */
    val placeId: String?,
    /** 菜单抬头认不出店名时用的名字，用户之后可以改。 */
    val defaultPlaceName: String,
    val targetLang: String = "zh-CN",
    /** “换成示例菜单重试”时指定；null 表示按设置。 */
    val modelOverride: VisionModelChoice? = null,
)

data class ScanResult(
    val placeId: String,
    val placeName: String,
    val isNewPlace: Boolean,
    val scanId: String,
    val dishCount: Int,
    val added: Int,
    val updated: Int,
    /** 原名（[normalizeDishName]）-> 存进数据库的菜品 id，点单页用它把内存里的菜对上数据库。 */
    val dishIds: Map<String, String> = emptyMap(),
)

sealed interface ScanUpdate {
    /** [placeName]：从店铺页发起时是这家店的名字，新店为 null。 */
    data class Started(val model: VisionModelChoice, val placeName: String? = null) : ScanUpdate

    /** 处理好的照片（已去 EXIF、压缩），界面直接显示它，和发给模型的是同一张。 */
    class PhotoReady(val jpegBytes: ByteArray) : ScanUpdate

    /** 照片已去 EXIF、压缩好。[hasLocation]：店铺有没有位置（照片 GPS、当前定位或已有店铺的位置）。 */
    data class Prepared(val location: Pair<Double, Double>?) : ScanUpdate {
        val hasLocation: Boolean get() = location != null
    }
    data class Header(
        val placeName: String?,
        val estimatedDishCount: Int?,
        val currency: String? = null,
        val sourceLang: String? = null,
    ) : ScanUpdate
    data class DishStarted(val nameOriginal: String) : ScanUpdate
    data class DishFound(val dish: ScannedDish) : ScanUpdate
    data object Saving : ScanUpdate
    data class Saved(val result: ScanResult) : ScanUpdate
}

/**
 * 识别一张菜单：处理照片 → （新店时）定位 → 流式识别 → 存照片、menu_scan 和合并后的菜品。
 * 失败抛 [ScanException]。识别中途出错不保存任何东西。
 */
class ScanMenuUseCase(
    private val imageProcessor: ImageProcessor,
    private val locationProvider: LocationProvider,
    private val photoStore: PhotoStore,
    private val scans: MenuScanRepository,
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val models: VisionModelRegistry,
    private val ids: IdGenerator,
    private val now: NowProvider,
) {
    /**
     * [ensureLocationPermission]：只有照片没有 GPS、又要新建店铺时才调用，界面在这时弹定位权限。
     * 返回 false 就不定位，店铺没有位置。
     */
    operator fun invoke(
        request: ScanMenuRequest,
        ensureLocationPermission: suspend () -> Boolean,
    ): Flow<ScanUpdate> = flow {
        val apiKey = secrets.get(SecretKeys.ANTHROPIC_API_KEY)?.takeIf { it.isNotBlank() }
        val choice = request.modelOverride
            ?: effectiveVisionModel(preferences.observeVisionModel().first() ?: VisionModelChoice.DEFAULT, apiKey != null)
        if (choice.needsApiKey && apiKey == null) throw ScanException(ScanError.NO_API_KEY)
        val existingPlace = request.placeId?.let { scans.place(it) ?: throw ScanException(ScanError.PLACE_MISSING) }
        emit(ScanUpdate.Started(choice, existingPlace?.name))

        val image = try {
            imageProcessor.process(request.sourceUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ScanException(ScanError.IMAGE_UNREADABLE, e)
        }

        emit(ScanUpdate.PhotoReady(image.jpegBytes))
        val photoLat = image.lat
        val photoLon = image.lon
        val location: Pair<Double, Double>? = when {
            existingPlace != null -> existingPlace.lat?.let { lat -> existingPlace.lon?.let { lat to it } }
            photoLat != null && photoLon != null -> photoLat to photoLon
            ensureLocationPermission() -> runCatching { locationProvider.currentLocation() }.getOrNull()
            else -> null
        }
        emit(ScanUpdate.Prepared(location))

        val model = models.modelFor(choice)
        val dishes = mutableListOf<ScannedDish>()
        var header: ScanEvent.Header? = null
        var raw = ""
        try {
            model.scanMenu(MenuScanRequest(image.jpegBytes, request.targetLang, apiKey)).collect { event ->
                when (event) {
                    is ScanEvent.Header -> {
                        header = event
                        emit(ScanUpdate.Header(event.placeName, event.estimatedDishCount, event.currency, event.sourceLang))
                    }
                    is ScanEvent.DishStarted -> emit(ScanUpdate.DishStarted(event.nameOriginal))
                    is ScanEvent.DishScanned -> {
                        dishes += event.dish
                        emit(ScanUpdate.DishFound(event.dish))
                    }
                    is ScanEvent.Finished -> raw = event.rawOutput
                }
            }
        } catch (e: VisionException) {
            throw ScanException(e.toScanError(), e)
        }
        if (dishes.isEmpty()) throw ScanException(ScanError.NO_DISHES)

        emit(ScanUpdate.Saving)
        val scanId = ids.newId()
        val createdAt = now.nowMillis()
        val newPlace = if (existingPlace == null) {
            Place(
                id = ids.newId(),
                name = header?.placeName ?: request.defaultPlaceName,
                lat = location?.first,
                lon = location?.second,
                address = null,
            )
        } else {
            null
        }
        val place = existingPlace ?: newPlace!!
        val merge = mergeMenu(
            placeId = place.id,
            scanId = scanId,
            existing = existingPlace?.let { scans.menuOf(it.id) }.orEmpty(),
            scanned = dishes,
            defaultCurrency = header?.currency,
            newId = ids::newId,
        )
        val photoPath = photoStore.save(scanId, image.jpegBytes)
        try {
            scans.save(
                newPlace = newPlace,
                scan = NewMenuScan(
                    id = scanId,
                    placeId = place.id,
                    photoPath = photoPath,
                    model = model.id,
                    sourceLang = header?.sourceLang,
                    targetLang = request.targetLang,
                    rawOutput = raw,
                    createdAt = createdAt,
                ),
                dishes = merge.upserts,
            )
        } catch (e: Exception) {
            runCatching { photoStore.delete(photoPath) }
            throw if (e is CancellationException) e else ScanException(ScanError.UNKNOWN, e)
        }
        emit(
            ScanUpdate.Saved(
                ScanResult(
                    placeId = place.id,
                    placeName = place.name,
                    isNewPlace = newPlace != null,
                    scanId = scanId,
                    dishCount = merge.upserts.size,
                    added = merge.added,
                    updated = merge.updated,
                    dishIds = merge.upserts.associate { normalizeDishName(it.nameOriginal) to it.id },
                ),
            ),
        )
    }
}

internal fun VisionException.toScanError(): ScanError = when (this) {
    is VisionException.Unauthorized -> ScanError.INVALID_API_KEY
    is VisionException.RateLimited -> ScanError.RATE_LIMITED
    is VisionException.Unavailable -> ScanError.SERVICE_UNAVAILABLE
    is VisionException.Timeout -> ScanError.TIMEOUT
    is VisionException.Network -> ScanError.NETWORK
    is VisionException.BadRequest, is VisionException.StreamError -> ScanError.UNKNOWN
}

/** 设置页“识别模型”部分要显示的东西。Key 本身不出这一层，只给打码后的样子。 */
data class VisionSettings(
    val choice: VisionModelChoice,
    /** 实际会用的模型：没 Key 时是示例菜单。 */
    val effective: VisionModelChoice,
    val maskedKey: String?,
) {
    val hasApiKey: Boolean get() = maskedKey != null
}

class ObserveVisionSettingsUseCase(private val preferences: PreferencesStore, private val secrets: SecretStore) {
    operator fun invoke(): Flow<VisionSettings> =
        combine(
            preferences.observeVisionModel(),
            secrets.observe(SecretKeys.ANTHROPIC_API_KEY),
        ) { stored, key ->
            val choice = stored ?: VisionModelChoice.DEFAULT
            val present = key?.takeIf { it.isNotBlank() }
            VisionSettings(choice, effectiveVisionModel(choice, present != null), present?.let(::maskApiKey))
        }
}

class SetVisionModelUseCase(private val preferences: PreferencesStore) {
    suspend operator fun invoke(choice: VisionModelChoice) = preferences.setVisionModel(choice)
}

class SaveApiKeyUseCase(private val secrets: SecretStore) {
    /** 去掉首尾空白后保存；空的或中间有空白的不保存，返回 false。 */
    suspend operator fun invoke(rawKey: String): Boolean {
        val key = rawKey.trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) return false
        secrets.put(SecretKeys.ANTHROPIC_API_KEY, key)
        return true
    }
}

class ClearApiKeyUseCase(private val secrets: SecretStore) {
    suspend operator fun invoke() = secrets.remove(SecretKeys.ANTHROPIC_API_KEY)
}

class RenamePlaceUseCase(private val places: PlaceRepository) {
    /** 空名字不改，返回 false。 */
    suspend operator fun invoke(placeId: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        places.rename(placeId, trimmed)
        return true
    }
}
