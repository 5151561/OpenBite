package com.openbite.core.domain

import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.PlaceImportRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.IncomingFileSource
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.model.Dish
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * .openbite 店铺文件：一个 zip，里面是 manifest.json 和（可选的）photos/N.jpg。
 * 只有店铺和菜单，永远不带用餐记录、评分、短评、同行人、小票和纪念日。
 * 收到的文件是不可信的输入：这里逐项校验，任何一项不过就整份拒绝，什么都不写。
 */

const val OPENBITE_FORMAT_VERSION = 1
const val OPENBITE_MIME = "application/vnd.openbite+zip"
const val OPENBITE_EXTENSION = ".openbite"
const val OPENBITE_MANIFEST = "manifest.json"
private val PHOTO_ENTRY = Regex("photos/[0-9]{1,2}\\.jpg")

@Serializable
data class OpenBiteManifest(
    val formatVersion: Int,
    val place: SharedPlace,
    /** 菜单原文语言（BCP 47），导入后小票用它。 */
    val menuLanguage: String? = null,
    val dishes: List<SharedDish> = emptyList(),
    /** 附带的菜单原照片，zip 里的条目名，如 "photos/1.jpg"。 */
    val photos: List<String> = emptyList(),
)

@Serializable
data class SharedPlace(
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class SharedDish(
    val category: String = "",
    val nameOriginal: String,
    val nameTranslated: String = "",
    val priceMinor: Long? = null,
    val currency: String? = null,
    val allergens: List<String> = emptyList(),
)

class SharedPhoto(val name: String, val bytes: ByteArray)

class OpenBitePackage(val manifest: OpenBiteManifest, val photos: List<SharedPhoto>)

/** 各项上限。默认值就是 App 用的；测试里可以调小。 */
data class OpenBiteLimits(
    val maxFileBytes: Long = 20L * 1024 * 1024,
    /** 解压后所有条目加起来（防 zip 炸弹）。 */
    val maxTotalBytes: Long = 20L * 1024 * 1024,
    val maxEntries: Int = 1 + 10,
    val maxManifestBytes: Long = 1024 * 1024,
    val maxPhotoBytes: Long = 8L * 1024 * 1024,
    val maxPhotos: Int = 10,
    val maxDishes: Int = 500,
    val maxTags: Int = 20,
    val maxAllergens: Int = 20,
    val maxPlaceName: Int = 80,
    val maxAddress: Int = 200,
    val maxDishName: Int = 120,
    val maxCategory: Int = 60,
    val maxPriceMinor: Long = 10_000_000_000L,
)

enum class ImportError {
    /** 不是 .openbite 文件（扩展名或类型不对）。 */
    NOT_OPENBITE,
    TOO_LARGE,
    NOT_ZIP,
    TOO_MANY_ENTRIES,
    /** 条目名想跳出目录（../、绝对路径等）。 */
    UNSAFE_PATH,
    /** 有不该有的文件。 */
    UNEXPECTED_ENTRY,
    MISSING_MANIFEST,
    BAD_MANIFEST,
    /** 文件来自更新版本的 OpenBite。 */
    UNSUPPORTED_VERSION,
    /** 某个字段超出范围（名字太长、经纬度不对、价格离谱……）。 */
    INVALID_FIELD,
    /** 照片不是 JPEG，或者解不开。 */
    BAD_PHOTO,
    /** 读文件或写数据库失败。 */
    READ_FAILED,
    SAVE_FAILED,
}

class ImportException(val error: ImportError, detail: String? = null) : Exception(detail ?: error.name)

/**
 * 能不能当作店铺文件收下：类型是 [OPENBITE_MIME] 就收；
 * 聊天软件常把类型丢成 application/zip、application/octet-stream 或干脆没有，这时看文件名是不是 .openbite。
 */
fun acceptsIncoming(mimeType: String?, displayName: String?): Boolean {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    if (mime == OPENBITE_MIME) return true
    val named = displayName?.trim()?.lowercase()?.endsWith(OPENBITE_EXTENSION) == true
    return named && (mime == null || mime == "application/zip" || mime == "application/octet-stream" || mime == "application/x-zip-compressed")
}

/** 条目名只能是 manifest.json 或 photos/N.jpg。路径跳转、绝对路径、反斜杠、盘符一律拒绝。 */
fun checkEntryName(name: String) {
    val unsafe = name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || ".." in name ||
        '\\' in name || ':' in name || name.any { it.code < 0x20 }
    if (unsafe) throw ImportException(ImportError.UNSAFE_PATH, name)
    if (name != OPENBITE_MANIFEST && !PHOTO_ENTRY.matches(name)) throw ImportException(ImportError.UNEXPECTED_ENTRY, name)
}

fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

/** 解压，边解边数字节（单个条目和总量都有上限）。各平台实现。 */
internal expect fun readZipEntries(bytes: ByteArray, limits: OpenBiteLimits): Map<String, ByteArray>

internal expect fun writeZipEntries(entries: List<Pair<String, ByteArray>>): ByteArray

private val manifestJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private val CURRENCY = Regex("[A-Z]{3}")
private val ALLERGEN_CODE = Regex("[a-z_]{1,30}")
private val LANGUAGE_TAG = Regex("[A-Za-z0-9-]{1,35}")

private fun invalid(field: String): Nothing = throw ImportException(ImportError.INVALID_FIELD, field)

private fun checkText(value: String, max: Int, field: String, allowEmpty: Boolean = false) {
    if (!allowEmpty && value.isBlank()) invalid(field)
    if (value.length > max) invalid(field)
    if (value.any { it.code < 0x20 || it.code == 0x7F }) invalid(field)
}

/** 校验 manifest 的每个字段。不过就抛 [ImportException]。 */
fun validateManifest(manifest: OpenBiteManifest, limits: OpenBiteLimits = OpenBiteLimits()) {
    when {
        manifest.formatVersion > OPENBITE_FORMAT_VERSION -> throw ImportException(ImportError.UNSUPPORTED_VERSION)
        manifest.formatVersion < 1 -> throw ImportException(ImportError.BAD_MANIFEST, "formatVersion")
    }
    val place = manifest.place
    checkText(place.name, limits.maxPlaceName, "place.name")
    place.address?.let { checkText(it, limits.maxAddress, "place.address", allowEmpty = true) }
    if ((place.lat == null) != (place.lon == null)) invalid("place.latlon")
    place.lat?.let { if (!it.isFinite() || it !in -90.0..90.0) invalid("place.lat") }
    place.lon?.let { if (!it.isFinite() || it !in -180.0..180.0) invalid("place.lon") }
    if (place.tags.size > limits.maxTags) invalid("place.tags")
    place.tags.forEach { if (normalizeTag(it) != it) invalid("place.tags") }
    manifest.menuLanguage?.let { if (!LANGUAGE_TAG.matches(it)) invalid("menuLanguage") }
    if (manifest.dishes.size > limits.maxDishes) invalid("dishes")
    manifest.dishes.forEachIndexed { i, dish ->
        checkText(dish.nameOriginal, limits.maxDishName, "dishes[$i].nameOriginal")
        checkText(dish.nameTranslated, limits.maxDishName, "dishes[$i].nameTranslated", allowEmpty = true)
        checkText(dish.category, limits.maxCategory, "dishes[$i].category", allowEmpty = true)
        dish.priceMinor?.let { if (it !in 0..limits.maxPriceMinor) invalid("dishes[$i].priceMinor") }
        dish.currency?.let { if (!CURRENCY.matches(it)) invalid("dishes[$i].currency") }
        if (dish.priceMinor != null && dish.currency == null) invalid("dishes[$i].currency")
        if (dish.allergens.size > limits.maxAllergens || dish.allergens.any { !ALLERGEN_CODE.matches(it) }) invalid("dishes[$i].allergens")
    }
    if (manifest.photos.size > limits.maxPhotos) invalid("photos")
    if (manifest.photos.toSet().size != manifest.photos.size) invalid("photos")
    manifest.photos.forEach {
        if (!PHOTO_ENTRY.matches(it)) throw ImportException(ImportError.UNEXPECTED_ENTRY, it)
    }
}

object OpenBiteCodec {
    fun encode(pkg: OpenBitePackage): ByteArray = writeZipEntries(
        listOf(OPENBITE_MANIFEST to manifestJson.encodeToString(OpenBiteManifest.serializer(), pkg.manifest).encodeToByteArray()) +
            pkg.photos.map { it.name to it.bytes },
    )

    /** 解析并校验一份收到的文件。照片只查了是不是 JPEG，导入时还会重新编码。 */
    fun decode(bytes: ByteArray, limits: OpenBiteLimits = OpenBiteLimits()): OpenBitePackage {
        if (bytes.size > limits.maxFileBytes) throw ImportException(ImportError.TOO_LARGE)
        val zipMagic = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()
        if (!zipMagic) throw ImportException(ImportError.NOT_ZIP)
        val entries = readZipEntries(bytes, limits)
        val raw = entries[OPENBITE_MANIFEST] ?: throw ImportException(ImportError.MISSING_MANIFEST)
        val manifest = try {
            manifestJson.decodeFromString(OpenBiteManifest.serializer(), raw.decodeToString(throwOnInvalidSequence = true))
        } catch (e: SerializationException) {
            throw ImportException(ImportError.BAD_MANIFEST, e.message)
        } catch (e: IllegalArgumentException) {
            throw ImportException(ImportError.BAD_MANIFEST, e.message)
        } catch (e: CharacterCodingException) {
            throw ImportException(ImportError.BAD_MANIFEST, e.message)
        }
        validateManifest(manifest, limits)
        val extra = entries.keys - OPENBITE_MANIFEST - manifest.photos.toSet()
        if (extra.isNotEmpty()) throw ImportException(ImportError.UNEXPECTED_ENTRY, extra.first())
        val photos = manifest.photos.map { name ->
            val data = entries[name] ?: throw ImportException(ImportError.BAD_PHOTO, name)
            if (!isJpeg(data)) throw ImportException(ImportError.BAD_PHOTO, name)
            SharedPhoto(name, data)
        }
        return OpenBitePackage(manifest, photos)
    }
}

/** 分享店铺时“带上什么”（设计稿 17）。 */
data class ShareOptions(
    val menu: Boolean = true,
    val location: Boolean = true,
    val tags: Boolean = true,
    val photos: Boolean = false,
)

/** 用店铺和菜单拼 manifest。照片只给名字，字节另外读。 */
fun buildManifest(place: Place, menu: List<Dish>, scans: List<MenuScan>, options: ShareOptions, limits: OpenBiteLimits = OpenBiteLimits()): OpenBiteManifest {
    val photoCount = if (options.photos) scans.size.coerceAtMost(limits.maxPhotos) else 0
    return OpenBiteManifest(
        formatVersion = OPENBITE_FORMAT_VERSION,
        place = SharedPlace(
            name = place.name.take(limits.maxPlaceName),
            address = if (options.location) place.address?.take(limits.maxAddress) else null,
            lat = if (options.location && place.lon != null) place.lat else null,
            lon = if (options.location && place.lat != null) place.lon else null,
            tags = if (options.tags) place.tags.mapNotNull(::normalizeTag).distinct().take(limits.maxTags) else emptyList(),
        ),
        menuLanguage = scans.maxByOrNull { it.takenAt }?.sourceLang?.takeIf { LANGUAGE_TAG.matches(it) },
        dishes = if (!options.menu) emptyList() else menu.take(limits.maxDishes).map { dish ->
            SharedDish(
                category = dish.category.take(limits.maxCategory),
                nameOriginal = dish.nameOriginal.take(limits.maxDishName),
                nameTranslated = dish.nameTranslated.take(limits.maxDishName),
                priceMinor = dish.price?.amountMinor?.takeIf { it in 0..limits.maxPriceMinor },
                currency = dish.price?.takeIf { it.amountMinor in 0..limits.maxPriceMinor }?.currency?.takeIf { CURRENCY.matches(it) },
                allergens = dish.allergens.filter { ALLERGEN_CODE.matches(it) }.take(limits.maxAllergens),
            )
        },
        photos = (1..photoCount).map { "photos/$it.jpg" },
    )
}

/** 文件名：店名最后一段（“割烹 みなと” -> “みなと.openbite”），去掉文件系统不认的字符。 */
fun openBiteFileName(placeName: String): String {
    val last = placeName.trim().split(Regex("\\s+")).lastOrNull().orEmpty()
    val clean = last.filterNot { it in "/\\:*?\"<>|" || it.code < 0x20 }.take(40).trim('.', ' ')
    return (clean.ifEmpty { "openbite-place" }) + OPENBITE_EXTENSION
}

/** 导入页（设计稿 18）上要显示的东西。 */
data class ImportPreview(
    val name: String,
    val area: String?,
    val tags: List<String>,
    val dishCount: Int,
    val hasLocation: Boolean,
    val photoCount: Int,
)

fun importPreview(manifest: OpenBiteManifest) = ImportPreview(
    name = manifest.place.name,
    area = shortArea(manifest.place.address),
    tags = manifest.place.tags,
    dishCount = manifest.dishes.size,
    hasLocation = manifest.place.lat != null && manifest.place.lon != null,
    photoCount = manifest.photos.size,
)

class ExportedFile(val fileName: String, val bytes: ByteArray, val manifest: OpenBiteManifest)

/** 生成要分享出去的店铺文件。照片用已经去掉 EXIF 的那份；读不出来或不是 JPEG 的跳过。 */
class ExportPlaceUseCase(
    private val places: PlaceRepository,
    private val photos: PhotoStore,
    private val dispatchers: AppDispatchers = AppDispatchers(),
) {
    suspend fun manifest(placeId: String, options: ShareOptions): OpenBiteManifest? {
        val place = places.observePlace(placeId).first() ?: return null
        return buildManifest(place, places.observeMenu(placeId).first(), places.observeScans(placeId).first(), options)
    }

    suspend operator fun invoke(placeId: String, options: ShareOptions, limits: OpenBiteLimits = OpenBiteLimits()): ExportedFile? {
        val place = places.observePlace(placeId).first() ?: return null
        val scans = places.observeScans(placeId).first()
        val photoBytes = if (!options.photos) {
            emptyList()
        } else {
            var total = 0L
            scans.sortedBy { it.takenAt }.mapNotNull { scan -> photos.read(scan.photoPath)?.takeIf(::isJpeg) }
                .takeWhile { bytes -> (total + bytes.size <= limits.maxTotalBytes - limits.maxManifestBytes).also { if (it) total += bytes.size } }
                .take(limits.maxPhotos)
        }
        val base = buildManifest(place, places.observeMenu(placeId).first(), scans, options.copy(photos = false), limits)
        val manifest = base.copy(photos = photoBytes.indices.map { "photos/${it + 1}.jpg" })
        val pkg = OpenBitePackage(manifest, photoBytes.mapIndexed { i, bytes -> SharedPhoto(manifest.photos[i], bytes) })
        val bytes = withContext(dispatchers.default) { OpenBiteCodec.encode(pkg) }
        return ExportedFile(openBiteFileName(place.name), bytes, manifest)
    }
}

/** 读一个收到的文件并校验。 */
class OpenSharedPlaceUseCase(
    private val files: IncomingFileSource,
    private val dispatchers: AppDispatchers = AppDispatchers(),
) {
    suspend operator fun invoke(uri: String, limits: OpenBiteLimits = OpenBiteLimits()): OpenBitePackage {
        val file = try {
            files.read(uri, limits.maxFileBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ImportException(ImportError.READ_FAILED, e.message)
        }
        if (!acceptsIncoming(file.mimeType, file.displayName)) throw ImportException(ImportError.NOT_OPENBITE)
        if (file.tooLarge) throw ImportException(ImportError.TOO_LARGE)
        val bytes = file.bytes ?: throw ImportException(ImportError.READ_FAILED)
        return withContext(dispatchers.default) { OpenBiteCodec.decode(bytes, limits) }
    }
}

/**
 * 导入：永远建一家全新的店（设计稿 18），不合并。照片先全部重新编码（有一张解不开就整份拒绝），
 * 再存文件、写数据库；写库失败就把刚存的文件删掉。
 */
class ImportPlaceUseCase(
    private val images: ImageProcessor,
    private val photos: PhotoStore,
    private val repository: PlaceImportRepository,
    private val ids: IdGenerator,
    private val now: NowProvider,
) {
    suspend operator fun invoke(pkg: OpenBitePackage): String {
        val manifest = pkg.manifest
        validateManifest(manifest)
        val encoded = pkg.photos.map { photo ->
            if (!isJpeg(photo.bytes)) throw ImportException(ImportError.BAD_PHOTO, photo.name)
            try {
                images.reencode(photo.bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ImportException(ImportError.BAD_PHOTO, photo.name)
            }
        }
        val placeId = ids.newId()
        val at = now.nowMillis()
        val saved = mutableListOf<String>()
        try {
            val scans = encoded.mapIndexed { i, bytes ->
                val scanId = ids.newId()
                val path = photos.save(scanId, bytes).also(saved::add)
                NewMenuScan(scanId, placeId, path, IMPORT_MODEL, manifest.menuLanguage, "zh-CN", "{}", at + i)
            }
            val dishes = manifest.dishes.map {
                Dish(
                    id = ids.newId(),
                    placeId = placeId,
                    category = it.category,
                    nameOriginal = it.nameOriginal,
                    nameTranslated = it.nameTranslated,
                    price = it.priceMinor?.let { amount -> it.currency?.let { c -> Money(amount, c) } },
                    allergens = it.allergens,
                )
            }
            val p = manifest.place
            repository.importPlace(Place(placeId, p.name, p.lat, p.lon, p.address, p.tags.distinct()), scans, dishes, at)
        } catch (e: Exception) {
            saved.forEach { runCatching { photos.delete(it) } }
            throw if (e is CancellationException) e else ImportException(ImportError.SAVE_FAILED, e.message)
        }
        return placeId
    }

    companion object {
        /** 导入的菜单写进 menu_scan.model 的值。 */
        const val IMPORT_MODEL = "import"
    }
}
