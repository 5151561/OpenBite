package com.openbite.android.feature.milestone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.fullDate
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.MilestoneDraft
import com.openbite.core.domain.MilestoneDraftError
import com.openbite.core.domain.REMIND_DAY_OPTIONS
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Recurrence
import com.openbite.feature.milestone.MilestoneEditUiState
import com.openbite.feature.milestone.MilestoneEditViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 纪念日管理：新建（[milestoneId] 为 null）或编辑。存好、删好都 [onDone]。 */
@Composable
fun MilestoneEditRoute(
    milestoneId: String?,
    onDone: () -> Unit,
    viewModel: MilestoneEditViewModel = koinViewModel(parameters = { parametersOf(milestoneId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    MilestoneEditScreen(
        state = state,
        onClose = onDone,
        onEdit = viewModel::edit,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = viewModel::addPerson,
        onSave = viewModel::save,
        onDelete = viewModel::requestDelete,
    )
    state.confirmingDeleteCount?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.edit_milestone_delete_title)) },
            text = {
                Text(
                    if (count > 0) stringResource(R.string.edit_milestone_delete_text, count)
                    else stringResource(R.string.edit_milestone_delete_text_none),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.edit_milestone_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.edit_milestone_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MilestoneEditScreen(
    state: MilestoneEditUiState,
    onClose: () -> Unit,
    onEdit: ((MilestoneDraft) -> MilestoneDraft) -> Unit,
    onTogglePerson: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val draft = state.draft
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.edit_milestone_new else R.string.edit_milestone_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(OpenBiteIcons.Close, contentDescription = stringResource(R.string.edit_milestone_close)) }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.saving && !state.isLoading && !state.missing) {
                        Text(stringResource(R.string.edit_milestone_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.missing -> Text(stringResource(R.string.edit_milestone_missing), Modifier.padding(padding).padding(Spacing.xxl))
            else -> Column(Modifier.fillMaxSize().padding(padding).fitInside(WindowInsetsRulers.Ime.current).verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
                val error = state.error
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { text -> onEdit { it.copy(title = text) } },
                    label = { Text(stringResource(R.string.edit_milestone_name)) },
                    placeholder = { Text(stringResource(R.string.edit_milestone_name_hint)) },
                    singleLine = true,
                    isError = error == MilestoneDraftError.TITLE_REQUIRED || error == MilestoneDraftError.TITLE_TOO_LONG,
                    supportingText = when (error) {
                        MilestoneDraftError.TITLE_REQUIRED -> ({ Text(stringResource(R.string.edit_milestone_error_title)) })
                        MilestoneDraftError.TITLE_TOO_LONG -> ({ Text(stringResource(R.string.edit_milestone_error_title_long)) })
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                )

                SectionHeader(stringResource(R.string.edit_milestone_type), color = MaterialTheme.colorScheme.primary)
                val typeNames = stringArrayResource(R.array.milestone_type_names)
                FlowRow(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MilestoneType.entries.forEach { type ->
                        FilterChip(selected = draft.type == type, onClick = { onEdit { it.copy(type = type) } }, label = { Text(typeNames[type.ordinal]) })
                    }
                }

                SectionHeader(stringResource(R.string.edit_milestone_date), color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.padding(horizontal = Spacing.l)) {
                    Text(draft.date?.let { fullDate(it) } ?: stringResource(R.string.edit_milestone_pick_date))
                }
                if (error == MilestoneDraftError.DATE_REQUIRED) {
                    Text(
                        stringResource(R.string.edit_milestone_error_date),
                        Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SectionHeader(stringResource(R.string.edit_milestone_repeat), color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
                    listOf(Recurrence.YEARLY to R.string.edit_milestone_yearly, Recurrence.NONE to R.string.edit_milestone_once).forEachIndexed { i, (rec, label) ->
                        SegmentedButton(
                            selected = draft.recurrence == rec,
                            onClick = { onEdit { it.copy(recurrence = rec) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }

                SectionHeader(stringResource(R.string.edit_milestone_remind), color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
                    REMIND_DAY_OPTIONS.forEachIndexed { i, days ->
                        SegmentedButton(
                            selected = draft.remindDaysBefore == days,
                            onClick = { onEdit { it.copy(remindDaysBefore = days) } },
                            shape = SegmentedButtonDefaults.itemShape(i, REMIND_DAY_OPTIONS.size),
                            label = {
                                Text(if (days == 0) stringResource(R.string.milestone_remind_days_none) else stringResource(R.string.edit_milestone_remind_days, days))
                            },
                        )
                    }
                }
                if (!state.isNew) {
                    Text(
                        stringResource(R.string.edit_milestone_reminder_note),
                        Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionHeader(stringResource(R.string.edit_milestone_people), color = MaterialTheme.colorScheme.primary)
                FlowRow(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    state.persons.forEach { person ->
                        FilterChip(selected = person.id in draft.personIds, onClick = { onTogglePerson(person.id) }, label = { Text(person.displayName) })
                    }
                    AssistChip(
                        onClick = { addingPerson = true },
                        label = { Text(stringResource(R.string.edit_milestone_add_person)) },
                        leadingIcon = { Icon(OpenBiteIcons.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }

                if (!state.isNew) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.l),
                    ) { Text(stringResource(R.string.edit_milestone_delete)) }
                }
            }
        }
    }
    if (pickingDate) DateDialog(draft.date, onPicked = { date -> onEdit { it.copy(date = date) }; pickingDate = false }, onDismiss = { pickingDate = false })
    if (addingPerson) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingPerson = false },
            title = { Text(stringResource(R.string.edit_milestone_add_person_title)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.edit_milestone_add_person_hint)) }) },
            confirmButton = {
                TextButton(onClick = { onAddPerson(name); addingPerson = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.edit_milestone_confirm)) }
            },
            dismissButton = { TextButton(onClick = { addingPerson = false }) { Text(stringResource(R.string.edit_milestone_cancel)) } },
        )
    }
}

/** Material 3 的日期选择器用 UTC 零点的毫秒，和当地日期互转都按 UTC。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun DateDialog(initial: LocalDate?, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let { onPicked(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.edit_milestone_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_milestone_cancel)) } },
    ) { DatePicker(state = picker) }
}
