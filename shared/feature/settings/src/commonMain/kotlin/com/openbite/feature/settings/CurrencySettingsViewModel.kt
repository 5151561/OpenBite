package com.openbite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.CurrencyPreferencesUseCase
import com.openbite.core.domain.DefaultCurrencyUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CurrencySettingsUiState(
    val isLoading: Boolean = true,
    /** 用户选的备用币种；null 表示跟随地区。 */
    val fallback: String? = null,
    /** 手机地区的币种（“跟随地区 · CNY”）。 */
    val regionCurrency: String = "",
    val recent: List<String> = emptyList(),
)

/** 设置页“备用币种”那一行：店铺、菜单都推不出币种时用它。 */
class CurrencySettingsViewModel(
    private val currencyPrefs: CurrencyPreferencesUseCase,
    defaultCurrency: DefaultCurrencyUseCase,
) : ViewModel() {
    private val region = defaultCurrency.regionChoice().code

    val uiState: StateFlow<CurrencySettingsUiState> = combine(currencyPrefs.fallback(), currencyPrefs.recent()) { fallback, recent ->
        CurrencySettingsUiState(isLoading = false, fallback = fallback, regionCurrency = region, recent = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencySettingsUiState(regionCurrency = region))

    /** null：跟随地区。 */
    fun setFallback(code: String?) {
        viewModelScope.launch {
            currencyPrefs.setFallback(code)
            code?.let { currencyPrefs.remember(it) }
        }
    }
}
