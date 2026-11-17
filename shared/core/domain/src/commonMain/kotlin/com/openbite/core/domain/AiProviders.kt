package com.openbite.core.domain

import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.model.AiProvider
import com.openbite.core.model.AiSettings
import com.openbite.core.model.ModelCatalog
import com.openbite.core.model.ModelRef
import com.openbite.core.model.ModelUse
import com.openbite.core.model.ProviderModel
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.VisionSupport
import com.openbite.core.model.maskApiKey
import com.openbite.core.model.normalizeBaseUrl
import com.openbite.core.network.AiProviderClient
import com.openbite.core.network.ProviderEndpoint
import com.openbite.core.network.VisionException
import com.openbite.core.network.VisionModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** 一家服务商现在的样子。Key 只给打码后的。 */
data class ProviderStatus(
    val provider: AiProvider,
    val maskedKey: String?,
    /** 实际用的接口地址；自定义没填时为 null。 */
    val baseUrl: String?,
    /** 用户自己改过地址（代理）。 */
    val customBaseUrl: Boolean,
    val catalog: ModelCatalog?,
) {
    val connected: Boolean get() = maskedKey != null && baseUrl != null
}

/** 设置页、服务商列表、详情页都用这一份。 */
data class AiOverview(
    val providers: List<ProviderStatus>,
    val selected: ModelRef?,
    val demoMode: Boolean,
    private val visionChecks: Map<String, Boolean>,
) {
    val connectedCount: Int get() = providers.count { it.connected }

    fun status(provider: AiProvider): ProviderStatus = providers.first { it.provider == provider }

    /** 选的模型那家还连着（Key 没被清掉）。 */
    val selectedUsable: Boolean get() = selected?.let { status(it.provider).connected } == true

    fun displayName(ref: ModelRef): String =
        status(ref.provider).catalog?.models?.firstOrNull { it.id == ref.modelId }?.displayName ?: ref.modelId

    fun vision(ref: ModelRef): VisionSupport = AiSettings(visionChecks = visionChecks).visionOf(ref)
}

class ObserveAiOverviewUseCase(private val preferences: PreferencesStore, private val secrets: SecretStore) {
    operator fun invoke(): Flow<AiOverview> {
        val keys: Flow<Map<AiProvider, String?>> = combine(AiProvider.entries.map { p -> secrets.observe(SecretKeys.apiKey(p)) }) { values ->
            AiProvider.entries.zip(values.toList()).toMap()
        }
        return combine(preferences.observeAiSettings(), keys) { settings, keyMap ->
            AiOverview(
                providers = AiProvider.entries.map { p ->
                    ProviderStatus(
                        provider = p,
                        maskedKey = keyMap[p]?.takeIf { it.isNotBlank() }?.let(::maskApiKey),
                        baseUrl = settings.baseUrlOf(p),
                        customBaseUrl = settings.baseUrls.containsKey(p.name),
                        catalog = settings.catalogOf(p),
                    )
                },
                selected = settings.selectedModel,
                demoMode = settings.demoMode,
                visionChecks = settings.visionChecks,
            )
        }
    }
}

/** 连接、刷新、选模型的结果。 */
enum class AiActionResult {
    OK,

    /** Key 是空的或中间有空白。 */
    KEY_MALFORMED,

    /** 地址不是 https:// 开头，或者自定义没填地址。 */
    URL_INVALID,

    /** 服务商说 Key 不对（401 / 403）。 */
    KEY_REJECTED,

    /** 连上了但一个模型都没有。 */
    NO_MODELS,

    /** 选的模型试图没过：不能看图。 */
    NO_VISION,
    RATE_LIMITED,
    NETWORK,
    FAILED,
}

private fun VisionException.toActionResult(): AiActionResult = when (this) {
    is VisionException.Unauthorized -> AiActionResult.KEY_REJECTED
    is VisionException.RateLimited -> AiActionResult.RATE_LIMITED
    is VisionException.Timeout, is VisionException.Network -> AiActionResult.NETWORK
    // 地址填错（不是兼容接口）时多半是 404 或者读不出列表。
    is VisionException.ModelNotFound, is VisionException.BadRequest -> AiActionResult.URL_INVALID
    else -> AiActionResult.FAILED
}

private suspend fun guarded(block: suspend () -> AiActionResult): AiActionResult =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: VisionException) {
        e.toActionResult()
    } catch (e: Exception) {
        AiActionResult.FAILED
    }

/**
 * 连一家：先用这个 Key 拉一次模型列表，拉到了才保存 Key、地址和列表。
 * [rawBaseUrl] 为空用这家的默认地址（自定义必须填）。
 */
class ConnectProviderUseCase(
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val client: AiProviderClient,
    private val now: NowProvider,
) {
    suspend operator fun invoke(provider: AiProvider, rawKey: String, rawBaseUrl: String?): AiActionResult = guarded {
        val key = rawKey.trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) return@guarded AiActionResult.KEY_MALFORMED
        val typed = rawBaseUrl?.trim()?.takeIf { it.isNotEmpty() }
        val override = typed?.let { normalizeBaseUrl(it) ?: return@guarded AiActionResult.URL_INVALID }
        val baseUrl = override ?: provider.defaultBaseUrl ?: return@guarded AiActionResult.URL_INVALID
        val models = client.listModels(ProviderEndpoint(provider, baseUrl), key)
        if (models.isEmpty()) return@guarded AiActionResult.NO_MODELS
        secrets.put(SecretKeys.apiKey(provider), key)
        preferences.updateAiSettings { s ->
            s.copy(
                baseUrls = if (override != null && override != provider.defaultBaseUrl) s.baseUrls + (provider.name to override) else s.baseUrls - provider.name,
                catalogs = s.catalogs + (provider.name to ModelCatalog(models, now.nowMillis())),
            )
        }
        AiActionResult.OK
    }
}

/** 重新拉一次模型列表。选的模型不在新列表里也不动它，识别时报“下架了”再让用户换。 */
class RefreshModelsUseCase(
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val client: AiProviderClient,
    private val now: NowProvider,
) {
    suspend operator fun invoke(provider: AiProvider): AiActionResult = guarded {
        val key = secrets.get(SecretKeys.apiKey(provider))?.takeIf { it.isNotBlank() } ?: return@guarded AiActionResult.KEY_REJECTED
        val baseUrl = preferences.observeAiSettings().first().baseUrlOf(provider) ?: return@guarded AiActionResult.URL_INVALID
        val models = client.listModels(ProviderEndpoint(provider, baseUrl), key)
        if (models.isEmpty()) return@guarded AiActionResult.NO_MODELS
        preferences.updateAiSettings { it.copy(catalogs = it.catalogs + (provider.name to ModelCatalog(models, now.nowMillis()))) }
        AiActionResult.OK
    }
}

/** 断开一家：删 Key，忘掉它的地址、模型列表、试图结果；选的是它家的模型也清掉。 */
class DisconnectProviderUseCase(private val preferences: PreferencesStore, private val secrets: SecretStore) {
    suspend operator fun invoke(provider: AiProvider) {
        secrets.remove(SecretKeys.apiKey(provider))
        preferences.updateAiSettings { it.forget(provider) }
    }
}

/**
 * 选一个模型。试过能看图的直接选；试过不能的不让选；没试过的先发张小图试一次，记住结果。
 * 只有能看图才会被选上。
 */
class SelectModelUseCase(
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val client: AiProviderClient,
) {
    suspend operator fun invoke(ref: ModelRef): AiActionResult = guarded {
        val settings = preferences.observeAiSettings().first()
        when (settings.visionOf(ref)) {
            VisionSupport.NO -> return@guarded AiActionResult.NO_VISION
            VisionSupport.YES -> {
                preferences.updateAiSettings { it.copy(selected = ref.encode()) }
                return@guarded AiActionResult.OK
            }
            VisionSupport.UNKNOWN -> Unit
        }
        val key = secrets.get(SecretKeys.apiKey(ref.provider))?.takeIf { it.isNotBlank() } ?: return@guarded AiActionResult.KEY_REJECTED
        val baseUrl = settings.baseUrlOf(ref.provider) ?: return@guarded AiActionResult.URL_INVALID
        val ok = client.modelFor(ProviderEndpoint(ref.provider, baseUrl), ref.modelId).checkVision(key)
        preferences.updateAiSettings { s ->
            val checked = s.copy(visionChecks = s.visionChecks + (ref.encode() to ok))
            if (ok) checked.copy(selected = ref.encode()) else checked
        }
        if (ok) AiActionResult.OK else AiActionResult.NO_VISION
    }
}

class SetDemoModeUseCase(private val preferences: PreferencesStore) {
    suspend operator fun invoke(on: Boolean) = preferences.updateAiSettings { it.copy(demoMode = on) }
}

/** 识别时到底用哪个模型、带哪个 Key。演示模式开着用示例；没选模型或那家没 Key 抛 [ScanError.NO_MODEL]。 */
class ActiveVisionModel(
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val client: AiProviderClient,
) {
    class Resolved(val use: ModelUse, val model: VisionModel, val apiKey: String?)

    suspend fun resolve(): Resolved {
        val settings = preferences.observeAiSettings().first()
        if (settings.demoMode) return Resolved(ModelUse.Demo, client.sample, null)
        val ref = settings.selectedModel ?: throw ScanException(ScanError.NO_MODEL)
        val key = secrets.get(SecretKeys.apiKey(ref.provider))?.takeIf { it.isNotBlank() } ?: throw ScanException(ScanError.NO_MODEL)
        val baseUrl = settings.baseUrlOf(ref.provider) ?: throw ScanException(ScanError.NO_MODEL)
        return Resolved(ModelUse.Real(ref), client.modelFor(ProviderEndpoint(ref.provider, baseUrl), ref.modelId), key)
    }
}

/** 模型列表按搜索词过滤（id 和显示名，不分大小写）。 */
fun List<ProviderModel>.matching(query: String): List<ProviderModel> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.id.contains(q, ignoreCase = true) || it.displayName?.contains(q, ignoreCase = true) == true }
}
