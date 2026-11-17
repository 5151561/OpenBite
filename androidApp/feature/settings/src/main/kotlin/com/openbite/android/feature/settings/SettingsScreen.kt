package com.openbite.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import com.openbite.android.core.designsystem.text.providerLabel
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
import com.openbite.core.domain.AiOverview
import com.openbite.feature.settings.SettingsMessage
import com.openbite.feature.settings.SettingsUiState
import com.openbite.feature.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** 设置页：口味、识别模型（入口到 AI 服务商）、演示模式、隐私、手机助手、提醒、示例数据。 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenTaste: () -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        keepLocation = state.keepLocation,
        onKeepLocation = viewModel::setKeepLocation,
        state = state,
        ai = state.ai,
        tasteSummary = state.tasteSummary,
        reminders = state.reminders,
        onReminders = viewModel::setReminders,
        onBack = onBack,
        onOpenTaste = onOpenTaste,
        onOpenProviders = onOpenProviders,
        onDemoMode = viewModel::setDemoMode,
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
    ai: AiOverview? = null,
    tasteSummary: String? = null,
    reminders: ReminderSettings? = null,
    keepLocation: Boolean? = null,
    onKeepLocation: (Boolean) -> Unit = {},
    onReminders: (meal: Boolean, milestones: Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onOpenTaste: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onDemoMode: (Boolean) -> Unit = {},
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
            if (ai != null) ModelSection(ai, onOpenProviders, onDemoMode)

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
        SettingsScreen(SettingsUiState(), previewOverview(selected = null, demo = false))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SettingsConfirmClearDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        SettingsScreen(
            SettingsUiState(confirmingClear = true),
            previewOverview(selected = com.openbite.core.model.ModelRef(com.openbite.core.model.AiProvider.OPENAI, "vision-model-1"), demo = true),
        )
    }
}

/** 识别模型：正在用哪个、连了几家（点进去管服务商）、演示模式开关。 */
@Composable
private fun ModelSection(ai: AiOverview, onOpenProviders: () -> Unit, onDemoMode: (Boolean) -> Unit) {
    val chevron: @Composable () -> Unit = {
        Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SegmentedList {
        val selected = ai.selected
        val modelText = when {
            selected == null -> stringResource(R.string.settings_model_none)
            !ai.selectedUsable -> stringResource(R.string.settings_model_unusable, ai.displayName(selected))
            else -> "${ai.displayName(selected)} · ${providerLabel(selected.provider)}"
        }
        SegmentedListItem(
            headline = stringResource(R.string.settings_model_in_use),
            supporting = modelText,
            shape = segmentShape(0, 2),
            trailing = chevron,
            onClick = onOpenProviders,
        )
        SegmentedListItem(
            headline = stringResource(R.string.settings_providers),
            supporting = if (ai.connectedCount == 0) {
                stringResource(R.string.settings_providers_none)
            } else {
                stringResource(R.string.settings_providers_count, ai.connectedCount)
            },
            shape = segmentShape(1, 2),
            trailing = chevron,
            onClick = onOpenProviders,
        )
    }
    Note(stringResource(R.string.settings_model_note))

    SectionHeader(stringResource(R.string.settings_demo_section), color = MaterialTheme.colorScheme.primary)
    SegmentedList {
        SegmentedListItem(
            headline = stringResource(R.string.settings_demo),
            supporting = stringResource(R.string.settings_demo_hint),
            shape = segmentShape(0, 1),
            trailing = { Switch(checked = ai.demoMode, onCheckedChange = onDemoMode) },
            onClick = { onDemoMode(!ai.demoMode) },
        )
    }
    if (ai.demoMode) Note(stringResource(R.string.settings_demo_on_note))
}

/** 预览用：OpenAI 已连接、其他没连。 */
internal fun previewOverview(selected: com.openbite.core.model.ModelRef?, demo: Boolean): AiOverview = AiOverview(
    providers = com.openbite.core.model.AiProvider.entries.map { p ->
        com.openbite.core.domain.ProviderStatus(
            provider = p,
            maskedKey = if (p == com.openbite.core.model.AiProvider.OPENAI) "sk-proj-••••WXYZ" else null,
            baseUrl = p.defaultBaseUrl,
            customBaseUrl = false,
            catalog = null,
        )
    },
    selected = selected,
    demoMode = demo,
    visionChecks = emptyMap(),
)
