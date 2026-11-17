package com.openbite.core.data.platform

import com.openbite.core.model.ReminderSettings
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.AiSettings
import kotlinx.coroutines.flow.Flow

/**
 * 共享代码需要、但只能由各平台实现的能力。
 * Android 在 :androidApp:app 里实现并注入；iOS 以后在 Swift 里实现。
 */

/** 读取打包在 App 里的种子数据 JSON。 */
fun interface SeedSource {
    suspend fun read(): String
}

/** 处理菜单照片：先读 GPS，再按 EXIF 方向转正、缩到长边 [maxLongEdgePx]，重新编码成不带 EXIF 的 JPEG。读不出来时抛异常。 */
interface ImageProcessor {
    suspend fun process(sourceUri: String, maxLongEdgePx: Int = 1568): ProcessedImage

    /** 把外来的 JPEG 解码再重新编码（去掉一切元数据、缩到长边）。解不开就抛异常。导入店铺文件时用。 */
    suspend fun reencode(jpegBytes: ByteArray, maxLongEdgePx: Int = 1568): ByteArray
}

/** 别的 App 交给我们的文件（分享、“打开方式”、文件选择器）。 */
class IncomingFile(
    val displayName: String?,
    val mimeType: String?,
    /** 超过上限时为 null（[tooLarge] 为 true），不会把整个大文件读进内存。 */
    val bytes: ByteArray?,
    val tooLarge: Boolean,
)

/** 按 content:// 地址读文件，最多读 [maxBytes] 字节。读不了抛异常。 */
fun interface IncomingFileSource {
    suspend fun read(uri: String, maxBytes: Long): IncomingFile
}

class ProcessedImage(
    val jpegBytes: ByteArray,
    val lat: Double?,
    val lon: Double?,
)

/** 取一次当前定位，拿不到返回 null。 */
fun interface LocationProvider {
    suspend fun currentLocation(): Pair<Double, Double>?
}

/**
 * 经纬度 -> 国家/地区（ISO 3166 两位字母），用来推默认币种。
 * Android 用系统 Geocoder（可能要联网）；没网、没有地理编码服务或超时返回 null，之后用到时再查。
 */
fun interface CountryGeocoder {
    suspend fun countryCode(lat: Double, lon: Double): String?
}

/** API Key 存储：Android Keystore / iOS Keychain。不进数据库，不进日志。 */
interface SecretStore {
    /** 当前值；没有存过或解不开时发 null。 */
    fun observe(key: String): Flow<String?>
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

/** 本地提醒：饭后补记录、纪念日提前提醒。 */
interface ReminderScheduler {
    /** 同一条记录重复调用会替换掉之前排的提醒。 */
    fun scheduleMealFollowUp(logId: String, delayMinutes: Int = 90)

    /** 每天检查一次纪念日；已经排过就不重复排。 */
    /** 记录被删了：取消还没发的饭后提醒，已经显示的通知也收掉。 */
    fun cancelMealFollowUp(logId: String)

    fun scheduleMilestoneCheck()
}

/** 照片文件存 App 私有目录：`photos/<id>.jpg`，返回相对路径。 */
interface PhotoStore {
    suspend fun save(id: String, jpegBytes: ByteArray): String
    suspend fun delete(relativePath: String)

    /** 读出照片文件；`seed/` 开头的读打包的示例图。读不到返回 null。 */
    suspend fun read(relativePath: String): ByteArray?

    /** 清空全部记忆时一起删掉。 */
    suspend fun deleteAll()
}

/**
 * 拍照和分享留下的临时文件（Android 上是 cache/scan 和 cache/share）。
 * 系统相机写下的原图带完整 EXIF/GPS，处理完就该删。
 */
interface TempFiles {
    /** [sourceUri] 是 App 自己交给系统相机的临时文件时删掉；相册等别的地址不动。 */
    suspend fun discardCapture(sourceUri: String)

    /** 清空全部记忆时一起清掉。 */
    suspend fun clearAll()
}

/** 普通设置（不含密钥）：Android 存 DataStore Preferences。 */
interface PreferencesStore {
    /** 识别模型的设置：选的模型、演示模式、接口地址、模型列表、试图结果。没设过发默认值。 */
    fun observeAiSettings(): Flow<AiSettings>

    /** 原子地改一次（读-改-写在一个事务里）。 */
    suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings)

    /** 我的口味；没设过或读不出来时发 [TasteProfile.EMPTY]。 */
    fun observeTasteProfile(): Flow<TasteProfile>
    suspend fun setTasteProfile(profile: TasteProfile)

    fun observeReminderSettings(): Flow<ReminderSettings>
    suspend fun setReminderSettings(settings: ReminderSettings)

    /** 已经发过的纪念日提醒，键是 "<milestoneId>:<年份>"。 */
    suspend fun sentMilestoneReminders(): Set<String>
    suspend fun markMilestoneRemindersSent(keys: Set<String>)

    /** 纪念日改了日期或提醒天数、或者被删了：忘掉它“今年已经提醒过”。 */
    suspend fun clearMilestoneReminders(milestoneId: String)

    /** 隐私：识别新店时要不要用照片 GPS / 当前定位作为店铺位置。默认 true。 */
    fun observeKeepLocation(): Flow<Boolean>
    suspend fun setKeepLocation(keep: Boolean)

    /** 设置里的“备用币种”；null 表示跟随地区（手机地区的币种）。 */
    fun observeFallbackCurrency(): Flow<String?>
    suspend fun setFallbackCurrency(code: String?)

    /** 最近用过的币种，最近的在前，最多 [RECENT_CURRENCY_LIMIT] 个。 */
    fun observeRecentCurrencies(): Flow<List<String>>

    /** 记一次用过 [code]：挪到最前面，超出上限的丢掉。 */
    suspend fun addRecentCurrency(code: String)

    /** AI 翻好的小票句子表（JSON），按语言标签缓存；没缓存过返回 null。 */
    suspend fun cachedPhraseTable(languageTag: String): String?
    suspend fun cachePhraseTable(languageTag: String, json: String)

    /** 首次启动时已经自动导入过示例数据（不管导没导成，库里本来有数据也算）。清空记忆后不再自动导回来。 */
    suspend fun isSeedAutoImported(): Boolean
    suspend fun markSeedAutoImported()
}

/** “最近用过”的币种最多记几个。 */
const val RECENT_CURRENCY_LIMIT = 5

/** 把 [code] 放到最近用过的最前面。 */
fun List<String>.withRecentCurrency(code: String): List<String> =
    (listOf(code.uppercase()) + filterNot { it.equals(code, ignoreCase = true) }).take(RECENT_CURRENCY_LIMIT)

/** SecretStore 里的键名。 */
object SecretKeys {
    /** 每家一个键，见 [com.openbite.core.model.AiProvider.secretKey]。 */
    fun apiKey(provider: com.openbite.core.model.AiProvider): String = provider.secretKey
}
