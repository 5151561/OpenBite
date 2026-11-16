package com.openbite.android.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.openbite.core.data.platform.ReminderSettings
import androidx.datastore.preferences.preferencesDataStore
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.VisionModelChoice
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/** 普通设置：识别模型。 */
internal class DataStorePreferencesStore(context: Context) : PreferencesStore {
    private val store = context.applicationContext.settingsDataStore
    private val visionModel = stringPreferencesKey("vision_model")

    override fun observeVisionModel(): Flow<VisionModelChoice?> = store.data.map { prefs ->
        prefs[visionModel]?.let { name -> VisionModelChoice.entries.firstOrNull { it.name == name } }
    }

    override suspend fun setVisionModel(choice: VisionModelChoice) {
        store.edit { it[visionModel] = choice.name }
    }

    override fun observeTasteProfile(): Flow<TasteProfile> = store.data.map { prefs ->
        prefs[tasteProfile]?.let { runCatching { json.decodeFromString<TasteProfile>(it) }.getOrNull() } ?: TasteProfile.EMPTY
    }

    override suspend fun setTasteProfile(profile: TasteProfile) {
        store.edit { it[tasteProfile] = json.encodeToString(profile) }
    }

    override fun observeReminderSettings(): Flow<ReminderSettings> = store.data.map { prefs ->
        ReminderSettings(mealFollowUp = prefs[mealReminders] ?: true, milestones = prefs[milestoneReminders] ?: true)
    }

    override suspend fun setReminderSettings(settings: ReminderSettings) {
        store.edit {
            it[mealReminders] = settings.mealFollowUp
            it[milestoneReminders] = settings.milestones
        }
    }

    override suspend fun sentMilestoneReminders(): Set<String> = store.data.first()[sentMilestones].orEmpty()

    override suspend fun markMilestoneRemindersSent(keys: Set<String>) {
        store.edit { it[sentMilestones] = it[sentMilestones].orEmpty() + keys }
    }

    private companion object {
        val mealReminders = booleanPreferencesKey("reminder_meal")
        val milestoneReminders = booleanPreferencesKey("reminder_milestone")
        val sentMilestones = stringSetPreferencesKey("milestone_reminders_sent")
        val tasteProfile = stringPreferencesKey("taste_profile")
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * 密钥：用 Android Keystore 里不可导出的 AES-256 密钥做 AES/GCM 加密，密文（IV + 密文）存 DataStore。
 * 明文只在内存里；解不开（比如 Keystore 被系统清掉）就当没存过。不打日志。
 */
internal class KeystoreSecretStore(context: Context) : SecretStore {
    private val store = context.applicationContext.secretsDataStore

    override fun observe(key: String): Flow<String?> =
        store.data.map { prefs -> prefs[prefKey(key)]?.let { decrypt(key, it) } }.flowOn(Dispatchers.Default)

    override suspend fun get(key: String): String? = observe(key).first()

    override suspend fun put(key: String, value: String) {
        val encrypted = withContext(Dispatchers.Default) { encrypt(key, value) }
        store.edit { it[prefKey(key)] = encrypted }
    }

    override suspend fun remove(key: String) {
        store.edit { it.remove(prefKey(key)) }
    }

    private fun prefKey(key: String) = stringPreferencesKey("enc_$key")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(name: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        // 键名当附加数据，密文不能被挪到别的键下面用。
        cipher.updateAAD(name.toByteArray())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(name: String, encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.updateAAD(name.toByteArray())
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "openbite_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** 菜单原照片存在 filesDir/photos/<id>.jpg，数据库只存相对路径。 */
internal class FilePhotoStore(context: Context) : PhotoStore {
    private val root = context.applicationContext.filesDir

    override suspend fun save(id: String, jpegBytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "bad photo id" }
        val relative = "$DIR/$id.jpg"
        val target = File(root, relative)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$id.jpg.tmp")
        tmp.writeBytes(jpegBytes)
        check(tmp.renameTo(target)) { "rename failed" }
        relative
    }

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) {
            val file = File(root, relativePath).canonicalFile
            if (file.path.startsWith(File(root, DIR).canonicalPath)) file.delete()
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { File(root, DIR).deleteRecursively() }
    }

    private companion object {
        const val DIR = "photos"
    }
}

/** 取一次当前位置（粗略即可），没有权限、没开定位或 10 秒内拿不到就返回 null。不依赖 Play 服务。 */
internal class AndroidLocationProvider(context: Context) : LocationProvider {
    private val context = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Pair<Double, Double>? {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        val location = fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        return location?.let { it.latitude to it.longitude }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
