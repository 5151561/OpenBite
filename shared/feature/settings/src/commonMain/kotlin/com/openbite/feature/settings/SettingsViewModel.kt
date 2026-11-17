package com.openbite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.AiOverview
import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.ObserveAiOverviewUseCase
import com.openbite.core.domain.SetDemoModeUseCase
import com.openbite.core.model.ReminderSettings
import com.openbite.core.domain.ObserveKeepLocationUseCase
import com.openbite.core.domain.ObserveReminderSettingsUseCase
import com.openbite.core.domain.SetKeepLocationUseCase
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.SetReminderSettingsUseCase
import com.openbite.core.domain.TastePhrases
import com.openbite.core.domain.LoadSampleDataUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 操作完成后给用户的一次性提示。 */
enum class SettingsMessage { SAMPLE_LOADED, SAMPLE_SKIPPED_NOT_EMPTY, CLEARED, FAILED }

data class SettingsUiState(
    val isWorking: Boolean = false,
    /** 正在等用户确认清空。 */
    val confirmingClear: Boolean = false,
    val message: SettingsMessage? = null,
    /** “保留经纬度作为店铺位置”；加载完之前为 null。 */
    val keepLocation: Boolean? = null,
    /** 两个提醒开关；加载完之前为 null。 */
    val reminders: ReminderSettings? = null,
    /** “我的口味”那一行的摘要；没设过为 null。加载完之前也是 null。 */
    val tasteSummary: String? = null,
    /** 识别模型：选的哪个、连了几家、演示模式。加载完之前为 null。 */
    val ai: AiOverview? = null,
)

/** 页面自己的临时状态（忙不忙、确认框、提示）。 */
private data class SettingsLocal(
    val isWorking: Boolean = false,
    val confirmingClear: Boolean = false,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val loadSampleData: LoadSampleDataUseCase,
    private val clearAllData: ClearAllDataUseCase,
    observeAi: ObserveAiOverviewUseCase,
    private val setDemoMode: SetDemoModeUseCase,
    observeTaste: ObserveTasteProfileUseCase,
    observeReminders: ObserveReminderSettingsUseCase,
    private val setReminderSettings: SetReminderSettingsUseCase,
    observeKeepLocation: ObserveKeepLocationUseCase,
    private val setKeepLocation: SetKeepLocationUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsLocal())

    /** 整页一个不可变状态。 */
    val uiState: StateFlow<SettingsUiState> = combine(
        state,
        observeKeepLocation(),
        observeReminders(),
        observeTaste().map { TastePhrases.summary(it) },
        observeAi(),
    ) { local, keep, reminders, taste, ai ->
        SettingsUiState(local.isWorking, local.confirmingClear, local.message, keep, reminders, taste, ai)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setKeepLocation(keep: Boolean) {
        viewModelScope.launch { setKeepLocation.invoke(keep) }
    }

    fun setReminders(meal: Boolean, milestones: Boolean) {
        viewModelScope.launch { setReminderSettings(ReminderSettings(meal, milestones)) }
    }

    fun setDemoMode(on: Boolean) {
        viewModelScope.launch { setDemoMode.invoke(on) }
    }

    fun loadSample() = launchAction {
        val imported = loadSampleData()
        if (imported) SettingsMessage.SAMPLE_LOADED else SettingsMessage.SAMPLE_SKIPPED_NOT_EMPTY
    }

    /** 第一步：弹确认框。 */
    fun requestClear() = state.update { it.copy(confirmingClear = true) }

    fun cancelClear() = state.update { it.copy(confirmingClear = false) }

    /** 第二步：用户确认后才真的删。 */
    fun confirmClear() {
        state.update { it.copy(confirmingClear = false) }
        launchAction {
            clearAllData()
            SettingsMessage.CLEARED
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }

    private fun launchAction(block: suspend () -> SettingsMessage) {
        if (state.value.isWorking) return
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            // 页面关掉时的取消要继续往外抛，不能当成“失败”。
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsMessage.FAILED
            }
            state.update { it.copy(isWorking = false, message = message) }
        }
    }
}
