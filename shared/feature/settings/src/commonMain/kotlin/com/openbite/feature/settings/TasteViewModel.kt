package com.openbite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.PhraseLanguage
import com.openbite.core.domain.SaveTasteProfileUseCase
import com.openbite.core.domain.TasteNote
import com.openbite.core.domain.tasteNotes
import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasteUiState(
    val isLoading: Boolean = true,
    val profile: TasteProfile = TasteProfile.EMPTY,
    /** “小票上会这样写”：用日文预览（种子数据都是日本的店）。 */
    val preview: List<TasteNote> = emptyList(),
)

/** 我的口味（设计稿 15）。每次改动立刻存。 */
class TasteViewModel(
    observeTaste: ObserveTasteProfileUseCase,
    private val saveTaste: SaveTasteProfileUseCase,
) : ViewModel() {
    private val profile = observeTaste()

    val uiState: StateFlow<TasteUiState> = profile
        .map { TasteUiState(isLoading = false, profile = it, preview = tasteNotes(it, PhraseLanguage.JA)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasteUiState())

    fun toggle(allergen: Allergen) = edit { it.toggle(allergen) }

    fun toggle(avoid: AvoidItem) = edit { it.toggle(avoid) }

    /** 再点一次已选中的辣度就是“不要求”。 */
    fun setSpice(level: SpiceLevel) = edit { it.copy(spice = if (it.spice == level) null else level) }

    fun setAutoApply(enabled: Boolean) = edit { it.copy(autoApply = enabled) }

    private fun edit(transform: (TasteProfile) -> TasteProfile) {
        viewModelScope.launch { saveTaste(transform(profile.first())) }
    }
}
