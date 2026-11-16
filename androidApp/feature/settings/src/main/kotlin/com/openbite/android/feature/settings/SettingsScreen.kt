package com.openbite.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import com.openbite.feature.settings.SettingsMessage
import com.openbite.feature.settings.SettingsUiState
import com.openbite.feature.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 设置页。U1 只做静态部分和两个数据操作：
 * 口味、模型选择、API Key 在 U3 做；隐私开关现在固定为开。
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenTaste: () -> Unit,
    onChooseModel: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenTaste = onOpenTaste,
        onChooseModel = onChooseModel,
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
    onBack: () -> Unit = {},
    onOpenTaste: () -> Unit = {},
    onChooseModel: () -> Unit = {},
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            // TODO(U3): 口味档案页（15 我的口味）。
            SegmentedListItem(
                headline = stringResource(R.string.settings_taste),
                supporting = stringResource(R.string.settings_taste_hint),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(horizontal = Spacing.l),
                leading = { Icon(OpenBiteIcons.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = onOpenTaste,
            )

            // TODO(U3): 模型选择和 API Key（Keystore）。
            SectionHeader(stringResource(R.string.settings_model_section), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                SegmentedListItem(
                    headline = stringResource(R.string.settings_model),
                    shape = segmentShape(0, 1),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrailingText(stringResource(R.string.settings_model_default))
                            Icon(OpenBiteIcons.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = onChooseModel,
                )
            }
            Note(stringResource(R.string.settings_model_note))

            // 去 EXIF 是强制的；保留经纬度的开关在 U3 接 DataStore，现在固定为开。
            SectionHeader(stringResource(R.string.settings_privacy_section), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                SegmentedListItem(
                    headline = stringResource(R.string.settings_strip_exif),
                    shape = segmentShape(0, 2),
                    trailing = { Switch(checked = true, onCheckedChange = null, enabled = false) },
                )
                SegmentedListItem(
                    headline = stringResource(R.string.settings_keep_location),
                    shape = segmentShape(1, 2),
                    trailing = { Switch(checked = true, onCheckedChange = null, enabled = false) },
                )
            }
            Note(stringResource(R.string.settings_privacy_note))

            SectionHeader(stringResource(R.string.settings_assistant_section), color = MaterialTheme.colorScheme.primary)
            SegmentedList {
                SegmentedListItem(
                    headline = stringResource(R.string.settings_appfunctions),
                    supporting = stringResource(R.string.settings_appfunctions_hint),
                    shape = segmentShape(0, 2),
                    trailing = { TrailingText(stringResource(R.string.settings_appfunctions_count)) },
                )
                SegmentedListItem(
                    headline = stringResource(R.string.settings_reminders),
                    supporting = stringResource(R.string.settings_reminders_hint),
                    shape = segmentShape(1, 2),
                )
            }
            Note(stringResource(R.string.settings_assistant_note))

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
    OpenBiteTheme(darkTheme = false) { SettingsScreen(SettingsUiState()) }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SettingsConfirmClearDarkPreview() {
    OpenBiteTheme(darkTheme = true) { SettingsScreen(SettingsUiState(confirmingClear = true)) }
}
