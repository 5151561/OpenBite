package com.openbite.core.data.platform

import com.openbite.core.model.TasteProfile
import com.openbite.core.model.VisionModelChoice
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
    fun scheduleMilestoneCheck()
}

/** 两类提醒的开关。 */
data class ReminderSettings(val mealFollowUp: Boolean = true, val milestones: Boolean = true)

/** 照片文件存 App 私有目录：`photos/<id>.jpg`，返回相对路径。 */
interface PhotoStore {
    suspend fun save(id: String, jpegBytes: ByteArray): String
    suspend fun delete(relativePath: String)

    /** 读出照片文件；`seed/` 开头的读打包的示例图。读不到返回 null。 */
    suspend fun read(relativePath: String): ByteArray?

    /** 清空全部记忆时一起删掉。 */
    suspend fun deleteAll()
}

/** 普通设置（不含密钥）：Android 存 DataStore Preferences。 */
interface PreferencesStore {
    /** 用户选的识别模型；没选过发 null。 */
    fun observeVisionModel(): Flow<VisionModelChoice?>
    suspend fun setVisionModel(choice: VisionModelChoice)

    /** 我的口味；没设过或读不出来时发 [TasteProfile.EMPTY]。 */
    fun observeTasteProfile(): Flow<TasteProfile>
    suspend fun setTasteProfile(profile: TasteProfile)

    fun observeReminderSettings(): Flow<ReminderSettings>
    suspend fun setReminderSettings(settings: ReminderSettings)

    /** 已经发过的纪念日提醒，键是 "<milestoneId>:<年份>"。 */
    suspend fun sentMilestoneReminders(): Set<String>
    suspend fun markMilestoneRemindersSent(keys: Set<String>)
}

/** SecretStore 里的键名。 */
object SecretKeys {
    const val ANTHROPIC_API_KEY = "anthropic_api_key"
}
