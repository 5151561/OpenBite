package com.openbite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.AiActionResult
import com.openbite.core.domain.AiOverview
import com.openbite.core.domain.ConnectProviderUseCase
import com.openbite.core.domain.DisconnectProviderUseCase
import com.openbite.core.domain.ObserveAiOverviewUseCase
import com.openbite.core.domain.ProviderStatus
import com.openbite.core.domain.RefreshModelsUseCase
import com.openbite.core.domain.SelectModelUseCase
import com.openbite.core.domain.matching
import com.openbite.core.model.AiProvider
import com.openbite.core.model.ModelRef
import com.openbite.core.model.ProviderModel
import com.openbite.core.model.VisionSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AI 服务商列表页：每家连没连、选的是哪个。加载完之前为 null。 */
class AiProvidersViewModel(observeAi: ObserveAiOverviewUseCase) : ViewModel() {
    val uiState: StateFlow<AiOverview?> = observeAi().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 详情页给用户的一次性提示。 */
enum class ProviderMessage {
    CONNECTED, REFRESHED, SELECTED, DISCONNECTED,
    KEY_MALFORMED, URL_INVALID, KEY_REJECTED, NO_MODELS, NO_VISION, RATE_LIMITED, NETWORK, FAILED,
}

/** 正在做的事。一次只做一件。 */
enum class ProviderBusy { CONNECTING, REFRESHING, CHECKING }

data class ModelRow(val model: ProviderModel, val vision: VisionSupport, val selected: Boolean) {
    val label: String get() = model.displayName ?: model.id
}

data class ProviderDetailUiState(
    val provider: AiProvider,
    /** 加载完之前为 null。 */
    val status: ProviderStatus? = null,
    val query: String = "",
    /** 按搜索词过滤后的模型。 */
    val models: List<ModelRow> = emptyList(),
    /** 这家一共有几个模型（不管搜索）。 */
    val totalModels: Int = 0,
    val busy: ProviderBusy? = null,
    /** 正在试图的那个模型。 */
    val checkingModelId: String? = null,
    val message: ProviderMessage? = null,
) {
    val connected: Boolean get() = status?.connected == true
}

private data class DetailLocal(
    val query: String = "",
    val busy: ProviderBusy? = null,
    val checkingModelId: String? = null,
    val message: ProviderMessage? = null,
)

/** 一家服务商的详情：填 Key 连接、拉模型列表、搜索、选模型（先试图）、断开。 */
class ProviderDetailViewModel(
    private val provider: AiProvider,
    observeAi: ObserveAiOverviewUseCase,
    private val connect: ConnectProviderUseCase,
    private val refresh: RefreshModelsUseCase,
    private val select: SelectModelUseCase,
    private val disconnect: DisconnectProviderUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(DetailLocal())

    val uiState: StateFlow<ProviderDetailUiState> = combine(local, observeAi()) { l, ai ->
        val status = ai.status(provider)
        val all = status.catalog?.models.orEmpty()
        ProviderDetailUiState(
            provider = provider,
            status = status,
            query = l.query,
            models = all.matching(l.query).map { m ->
                val ref = ModelRef(provider, m.id)
                ModelRow(m, ai.vision(ref), selected = ai.selected == ref)
            },
            totalModels = all.size,
            busy = l.busy,
            checkingModelId = l.checkingModelId,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState(provider))

    fun setQuery(query: String) = local.update { it.copy(query = query) }

    /** Key 只在这里经过，不回显、不记日志。[baseUrl] 为空用默认地址。 */
    fun connect(key: String, baseUrl: String?) = run(ProviderBusy.CONNECTING, ProviderMessage.CONNECTED) { connect(provider, key, baseUrl) }

    fun refresh() = run(ProviderBusy.REFRESHING, ProviderMessage.REFRESHED) { refresh(provider) }

    fun select(modelId: String) {
        if (local.value.busy != null) return
        local.update { it.copy(checkingModelId = modelId) }
        run(ProviderBusy.CHECKING, ProviderMessage.SELECTED) { select(ModelRef(provider, modelId)) }
    }

    fun disconnect() {
        if (local.value.busy != null) return
        viewModelScope.launch {
            disconnect(provider)
            local.update { it.copy(query = "", message = ProviderMessage.DISCONNECTED) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun run(busy: ProviderBusy, success: ProviderMessage, block: suspend () -> AiActionResult) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = busy, message = null) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiActionResult.FAILED
            }
            local.update { it.copy(busy = null, checkingModelId = null, message = result.toMessage(success)) }
        }
    }
}

private fun AiActionResult.toMessage(success: ProviderMessage): ProviderMessage = when (this) {
    AiActionResult.OK -> success
    AiActionResult.KEY_MALFORMED -> ProviderMessage.KEY_MALFORMED
    AiActionResult.URL_INVALID -> ProviderMessage.URL_INVALID
    AiActionResult.KEY_REJECTED -> ProviderMessage.KEY_REJECTED
    AiActionResult.NO_MODELS -> ProviderMessage.NO_MODELS
    AiActionResult.NO_VISION -> ProviderMessage.NO_VISION
    AiActionResult.RATE_LIMITED -> ProviderMessage.RATE_LIMITED
    AiActionResult.NETWORK -> ProviderMessage.NETWORK
    AiActionResult.FAILED -> ProviderMessage.FAILED
}
