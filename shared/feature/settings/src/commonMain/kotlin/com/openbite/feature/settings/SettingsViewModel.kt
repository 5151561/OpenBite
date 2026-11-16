package com.openbite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.LoadSampleDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 操作完成后给用户的一次性提示。 */
enum class SettingsMessage { SAMPLE_LOADED, SAMPLE_SKIPPED_NOT_EMPTY, CLEARED, FAILED }

data class SettingsUiState(
    val isWorking: Boolean = false,
    /** 正在等用户确认清空。 */
    val confirmingClear: Boolean = false,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val loadSampleData: LoadSampleDataUseCase,
    private val clearAllData: ClearAllDataUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

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
            val message = runCatching { block() }.getOrDefault(SettingsMessage.FAILED)
            state.update { it.copy(isWorking = false, message = message) }
        }
    }
}
