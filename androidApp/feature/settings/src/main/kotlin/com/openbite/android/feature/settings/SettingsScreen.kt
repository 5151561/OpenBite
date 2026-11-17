package com.openbite.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import com.openbite.android.core.designsystem.text.visionModelLabel
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.SegmentedList
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.TrailingText
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.ReminderSettings
import com.openbite.core.domain.VisionSettings
import com.openbite.core.model.VisionModelChoice
import com.openbite.feature.settings.SettingsMessage
import com.openbite.feature.settings.SettingsUiState
import com.openbite.feature.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** 设置页：口味、识别模型和 Key、隐私、手机助手、提醒、示例数据。 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenTaste: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        keepLocation = state.keepLocation,
        onKeepLocation = viewModel::setKeepLocation,
        state = state,
        vision = state.vision,
        tasteSummary = state.tasteSummary,
        reminders = state.reminders,
        onReminders = viewModel::setReminders,
        onBack = onBack,
        onOpenTaste = onOpenTaste,
        onChooseModel = viewModel::chooseModel,
        onSaveKey = viewModel::saveKey,
        onClearKey = viewModel::clearKey,
        onLoadSample = viewModel::loadSample,
        onRequestClear = viewModel::requestClear,
        onConfirmClear = viewModel::confirmClear,
        onCancelClear = viewModel::cancelClear,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    vision: VisionSettings? = null,
    tasteSummary: String? = null,
    reminders: ReminderSettings? = null,
    keepLocation: Boolean? = null,
    onKeepLocation: (Boolean) -> Unit = {},
    onReminders: (meal: Boolean, milestones: Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onOpenTaste: () -> Unit = {},
    onChooseModel: (VisionModelChoice) -> Unit = {},
    onSaveKey: (String) -> Unit = {},
    onClearKey: () -> Unit = {},
    onLoadSample: () -> Unit = {},
    onRequestClear: () -> Unit = {},
    onConfirmClear: () -> Unit = {},
    onCancelClear: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            SettingsMessage.SAMPLE_LOADED -> R.string.settings_msg_sample_loaded
            SettingsMessage.SAMPLE_SKIPPED_NOT_EMPTY -> R.string.settings_msg_sample_skipped
            SettingsMessage.CLEARED -> R.string.settings_msg_cleared
            SettingsMessage.FAILED -> R.string.settings_msg_failed
            SettingsMessage.KEY_SAVED -> R.string.settings_msg_key_saved
            SettingsMessage.KEY_INVALID -> R.string.settings_msg_key_invalid
            SettingsMessage.KEY_CLEARED -> R.string.settings_msg_key_cleared
        }
        onMessageShown()
        snackbar.showSnackbar(resources.getString(text))
    }

    if (state.confirmingClear) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = onCancelClear) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fitInside(WindowInsetsRulers.Ime.current)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            SegmentedListItem(
                headline = stringResource(R.string.settings_taste),
                supporting = tasteSummary ?: stringResource(R.string.settings_taste_hint),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(horizontal = Spacing.l),
                leading = { Icon(OpenBiteIcons.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = onOpenTaste,
            )

            SectionHeader(stringResource(R.string.settings_model_section), color = MaterialTheme.colorScheme.primary)
            if (vision != null) VisionSection(vision, state.isWorking, onChooseModel, onSaveKey, onClearKey)

            SectionHeader(stringResource(R.string.settings_privacy_section), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                // 去 EXIF 是强制的，不给开关。
                SegmentedListItem(
                    headline = stringResource(R.string.settings_strip_exif),
                    supporting = stringResource(R.string.settings_strip_exif_why),
                    shape = segmentShape(0, 2),
                    trailing = { TrailingText(stringResource(R.string.settings_always_on)) },
                )
                val keep = keepLocation ?: true
                SegmentedListItem(
                    headline = stringResource(R.string.settings_keep_location),
                    supporting = stringResource(if (keep) R.string.settings_keep_location_on else R.string.settings_keep_location_off),
                    shape = segmentShape(1, 2),
                    trailing = { Switch(checked = keep, onCheckedChange = onKeepLocation, enabled = keepLocation != null) },
                    onClick = { onKeepLocation(!keep) },
                )
            }
            Note(stringResource(R.string.settings_privacy_note))

            SectionHeader(stringResource(R.string.settings_assistant_section), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                SegmentedListItem(
                    headline = stringResource(R.string.settings_appfunctions),
                    supporting = stringResource(R.string.settings_appfunctions_hint),
                    shape = segmentShape(0, 1),
                    trailing = { TrailingText(stringResource(R.string.settings_appfunctions_count)) },
                )
            }
            Note(stringResource(R.string.settings_assistant_note))

            SectionHeader(stringResource(R.string.settings_reminders), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                val meal = reminders?.mealFollowUp ?: true
                val milestones = reminders?.milestones ?: true
                SegmentedListItem(
                    headline = stringResource(R.string.settings_reminder_meal),
                    supporting = stringResource(R.string.settings_reminder_meal_hint),
                    shape = segmentShape(0, 2),
                    trailing = { Switch(checked = meal, onCheckedChange = { onReminders(it, milestones) }, enabled = reminders != null) },
                    onClick = { onReminders(!meal, milestones) },
                )
                SegmentedListItem(
                    headline = stringResource(R.string.settings_reminder_milestone),
                    supporting = stringResource(R.string.settings_reminder_milestone_hint),
                    shape = segmentShape(1, 2),
                    trailing = { Switch(checked = milestones, onCheckedChange = { onReminders(meal, it) }, enabled = reminders != null) },
                    onClick = { onReminders(meal, !milestones) },
                )
            }
            Note(stringResource(R.string.settings_reminders_hint))

            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedButton(onClick = onLoadSample, enabled = !state.isWorking) {
                    Text(stringResource(R.string.settings_load_sample))
                }
                TextButton(
                    onClick = onRequestClear,
                    enabled = !state.isWorking,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_clear_all)) }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = Spacing.xxl),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SettingsScreenPreview() {
    OpenBiteTheme(darkTheme = false) {
        SettingsScreen(SettingsUiState(), VisionSettings(VisionModelChoice.CLAUDE_HAIKU, VisionModelChoice.SAMPLE, null))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SettingsConfirmClearDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        SettingsScreen(
            SettingsUiState(confirmingClear = true),
            VisionSettings(VisionModelChoice.CLAUDE_HAIKU, VisionModelChoice.CLAUDE_HAIKU, "sk-ant-••••WXYZ"),
        )
    }
}

/** 识别模型：选模型 + API Key。没 Key 时需要 Key 的模型不能选，实际用示例菜单。 */
@Composable
private fun VisionSection(
    vision: VisionSettings,
    busy: Boolean,
    onChooseModel: (VisionModelChoice) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SegmentedList {
        Box {
            SegmentedListItem(
                headline = stringResource(R.string.settings_model),
                shape = segmentShape(0, 2),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrailingText(visionModelLabel(vision.effective))
                        Icon(OpenBiteIcons.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                VisionModelChoice.entries.forEach { choice ->
                    val enabled = !choice.needsApiKey || vision.hasApiKey
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(visionModelLabel(choice))
                                if (!enabled) {
                                    Text(
                                        stringResource(R.string.settings_model_needs_key),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        enabled = enabled,
                        leadingIcon = {
                            if (choice == vision.effective) Icon(OpenBiteIcons.Check, contentDescription = null)
                        },
                        onClick = {
                            onChooseModel(choice)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        val masked = vision.maskedKey
        if (masked != null) {
            SegmentedListItem(
                headline = stringResource(R.string.settings_api_key),
                supporting = masked,
                shape = segmentShape(1, 2),
                trailing = {
                    TextButton(onClick = onClearKey, enabled = !busy) { Text(stringResource(R.string.settings_api_key_clear)) }
                },
            )
        } else {
            ApiKeyField(segmentShape(1, 2), busy, onSaveKey)
        }
    }
    Note(stringResource(if (vision.hasApiKey) R.string.settings_model_note_key else R.string.settings_model_note))
}

@Composable
private fun ApiKeyField(shape: androidx.compose.ui.graphics.Shape, busy: Boolean, onSaveKey: (String) -> Unit) {
    // 输入框内容只在内存里，不进 saveable（避免进 Bundle）。
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.settings_api_key)) },
            placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (input.isNotBlank()) {
                    onSaveKey(input)
                    input = ""
                }
            }),
        )
        TextButton(
            onClick = {
                onSaveKey(input)
                input = ""
            },
            enabled = input.isNotBlank() && !busy,
        ) { Text(stringResource(R.string.settings_api_key_save)) }
    }
}
