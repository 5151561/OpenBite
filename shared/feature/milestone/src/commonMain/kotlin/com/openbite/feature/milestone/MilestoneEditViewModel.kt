package com.openbite.feature.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.AddPersonUseCase
import com.openbite.core.domain.DeleteMilestoneUseCase
import com.openbite.core.domain.LoadMilestoneUseCase
import com.openbite.core.domain.MilestoneDraft
import com.openbite.core.domain.MilestoneDraftError
import com.openbite.core.domain.ObservePersonsUseCase
import com.openbite.core.domain.SaveMilestoneResult
import com.openbite.core.domain.SaveMilestoneUseCase
import com.openbite.core.domain.TaggedMealCountUseCase
import com.openbite.core.domain.validateMilestone
import com.openbite.core.model.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MilestoneEditUiState(
    val isLoading: Boolean = true,
    /** 要改的纪念日已经被删了。 */
    val missing: Boolean = false,
    val draft: MilestoneDraft = MilestoneDraft(),
    val persons: List<Person> = emptyList(),
    /** 点过“保存”之后才显示校验错误。 */
    val error: MilestoneDraftError? = null,
    val saving: Boolean = false,
    /** 删除确认框：标了这个纪念日的记录数；null 表示没在确认。 */
    val confirmingDeleteCount: Int? = null,
    /** 存好或删好，界面返回。 */
    val done: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
}

/** 纪念日管理：[milestoneId] 为 null 是新建。 */
class MilestoneEditViewModel(
    private val milestoneId: String?,
    private val load: LoadMilestoneUseCase,
    observePersons: ObservePersonsUseCase,
    private val addPerson: AddPersonUseCase,
    private val save: SaveMilestoneUseCase,
    private val delete: DeleteMilestoneUseCase,
    private val taggedCount: TaggedMealCountUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(MilestoneEditUiState(isLoading = milestoneId != null))
    val uiState: StateFlow<MilestoneEditUiState> = state.asStateFlow()
    private var showErrors = false

    init {
        if (milestoneId != null) {
            viewModelScope.launch {
                val existing = load(milestoneId)
                state.update {
                    if (existing == null) it.copy(isLoading = false, missing = true) else it.copy(isLoading = false, draft = MilestoneDraft.from(existing))
                }
            }
        }
        viewModelScope.launch { observePersons().collect { list -> state.update { it.copy(persons = list) } } }
    }

    fun edit(transform: (MilestoneDraft) -> MilestoneDraft) = state.update {
        val draft = transform(it.draft)
        it.copy(draft = draft, error = if (showErrors) validateMilestone(draft) else null)
    }

    fun togglePerson(id: String) = edit { it.copy(personIds = if (id in it.personIds) it.personIds - id else it.personIds + id) }

    fun addPerson(name: String) {
        viewModelScope.launch {
            val person = addPerson.invoke(name) ?: return@launch
            edit { it.copy(personIds = it.personIds + person.id) }
        }
    }

    fun save() {
        if (state.value.saving) return
        showErrors = true
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = save(state.value.draft)) {
                is SaveMilestoneResult.Invalid -> state.update { it.copy(saving = false, error = result.error) }
                is SaveMilestoneResult.Saved -> state.update { it.copy(saving = false, done = true) }
            }
        }
    }

    fun requestDelete() {
        val id = state.value.draft.id ?: return
        viewModelScope.launch {
            val count = taggedCount(id)
            state.update { it.copy(confirmingDeleteCount = count) }
        }
    }

    fun cancelDelete() = state.update { it.copy(confirmingDeleteCount = null) }

    fun confirmDelete() {
        val id = state.value.draft.id ?: return
        state.update { it.copy(confirmingDeleteCount = null, saving = true) }
        viewModelScope.launch {
            delete(id)
            state.update { it.copy(saving = false, done = true) }
        }
    }
}
