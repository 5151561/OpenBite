package com.openbite.core.domain

import com.openbite.core.data.MealRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Person
import com.openbite.core.model.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** 提前几天提醒，只给这几个选项。 */
val REMIND_DAY_OPTIONS = listOf(0, 1, 3, 7, 14)
const val DEFAULT_REMIND_DAYS = 7
const val MAX_MILESTONE_TITLE = 30

/** 纪念日编辑页正在改的内容。[id] 为 null 是新建。 */
data class MilestoneDraft(
    val id: String? = null,
    val title: String = "",
    val type: MilestoneType = MilestoneType.ANNIVERSARY,
    val date: LocalDate? = null,
    val recurrence: Recurrence = Recurrence.YEARLY,
    val remindDaysBefore: Int = DEFAULT_REMIND_DAYS,
    val personIds: Set<String> = emptySet(),
) {
    companion object {
        fun from(existing: MilestoneWithPeople) = MilestoneDraft(
            id = existing.milestone.id,
            title = existing.milestone.title,
            type = existing.milestone.type,
            date = existing.milestone.date,
            recurrence = existing.milestone.recurrence,
            remindDaysBefore = existing.milestone.remindDaysBefore,
            personIds = existing.personIds.toSet(),
        )
    }
}

enum class MilestoneDraftError { TITLE_REQUIRED, TITLE_TOO_LONG, DATE_REQUIRED, BAD_REMIND_DAYS }

fun validateMilestone(draft: MilestoneDraft): MilestoneDraftError? = when {
    draft.title.isBlank() -> MilestoneDraftError.TITLE_REQUIRED
    draft.title.trim().length > MAX_MILESTONE_TITLE -> MilestoneDraftError.TITLE_TOO_LONG
    draft.date == null -> MilestoneDraftError.DATE_REQUIRED
    draft.remindDaysBefore !in REMIND_DAY_OPTIONS -> MilestoneDraftError.BAD_REMIND_DAYS
    else -> null
}

/** 改了哪些会影响提醒：日期、重复方式、提前天数。 */
fun affectsReminders(before: Milestone, after: Milestone): Boolean =
    before.date != after.date || before.recurrence != after.recurrence || before.remindDaysBefore != after.remindDaysBefore

class LoadMilestoneUseCase(private val milestones: MilestoneRepository) {
    suspend operator fun invoke(id: String): MilestoneWithPeople? = milestones.milestone(id)
}

class ObservePersonsUseCase(private val meals: MealRepository) {
    operator fun invoke(): Flow<List<Person>> = meals.observePersons()
}

class TaggedMealCountUseCase(private val milestones: MilestoneRepository) {
    suspend operator fun invoke(id: String): Int = milestones.taggedMealCount(id)
}

sealed interface SaveMilestoneResult {
    data class Saved(val id: String) : SaveMilestoneResult
    data class Invalid(val error: MilestoneDraftError) : SaveMilestoneResult
}

/**
 * 存纪念日。日期、重复方式或提前天数变了，就忘掉“今年已经提醒过”，让每天的检查按新设置重新提醒。
 * 所有页面（纪念日列表、首页卡片、地图时间胶囊、提醒 Worker）都观察同一张表，存完自动更新。
 */
class SaveMilestoneUseCase(
    private val milestones: MilestoneRepository,
    private val preferences: PreferencesStore,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(draft: MilestoneDraft): SaveMilestoneResult {
        validateMilestone(draft)?.let { return SaveMilestoneResult.Invalid(it) }
        val before = draft.id?.let { milestones.milestone(it)?.milestone }
        val milestone = Milestone(
            id = draft.id ?: ids.newId(),
            title = draft.title.trim(),
            type = draft.type,
            date = draft.date!!,
            recurrence = draft.recurrence,
            remindDaysBefore = draft.remindDaysBefore,
        )
        milestones.save(milestone, draft.personIds.toList())
        if (before != null && affectsReminders(before, milestone)) preferences.clearMilestoneReminders(milestone.id)
        return SaveMilestoneResult.Saved(milestone.id)
    }
}

class DeleteMilestoneUseCase(private val milestones: MilestoneRepository, private val preferences: PreferencesStore) {
    suspend operator fun invoke(id: String) {
        milestones.delete(id)
        preferences.clearMilestoneReminders(id)
    }
}
