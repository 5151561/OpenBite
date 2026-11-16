package com.openbite.core.data.platform

/**
 * 共享代码需要、但只能由各平台实现的能力。
 * Android 在 :androidApp:app 里实现并注入；iOS 以后在 Swift 里实现。
 */

/** 读取打包在 App 里的种子数据 JSON。 */
fun interface SeedSource {
    suspend fun read(): String
}

/** 处理菜单照片：先读 GPS，再重新编码去掉全部 EXIF。 */
interface ImageProcessor {
    suspend fun process(sourceUri: String, maxLongEdgePx: Int = 1568): ProcessedImage
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
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

/** 本地提醒：饭后补记录、纪念日提前提醒。 */
interface ReminderScheduler {
    fun scheduleMealFollowUp(logId: String, delayMinutes: Int = 90)
    fun scheduleMilestoneCheck()
}

/** 照片文件存 App 私有目录，返回相对路径。 */
interface PhotoStore {
    suspend fun save(jpegBytes: ByteArray): String
    suspend fun delete(relativePath: String)
}
