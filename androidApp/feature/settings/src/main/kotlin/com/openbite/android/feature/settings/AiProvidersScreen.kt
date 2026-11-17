package com.openbite.android.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.SegmentedList
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.providerLabel
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.AiOverview
import com.openbite.core.model.AiProvider
import com.openbite.core.model.ModelRef
import com.openbite.core.model.ProviderModel
import com.openbite.core.model.VisionSupport
import com.openbite.feature.settings.AiProvidersViewModel
import com.openbite.feature.settings.ModelRow
import com.openbite.feature.settings.ProviderBusy
import com.openbite.feature.settings.ProviderDetailUiState
import com.openbite.feature.settings.ProviderDetailViewModel
import com.openbite.feature.settings.ProviderMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** AI 服务商列表：每家连没连，点进去连接、选模型。 */
@Composable
fun AiProvidersRoute(
    onBack: () -> Unit,
    onOpenProvider: (AiProvider) -> Unit,
    viewModel: AiProvidersViewModel = koinViewModel(),
) {
    val overview by viewModel.uiState.collectAsStateWithLifecycle()
    AiProvidersScreen(overview, onBack, onOpenProvider)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(
    overview: AiOverview?,
    onBack: () -> Unit = {},
    onOpenProvider: (AiProvider) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.providers_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            if (overview == null) return@Column
            val count = AiProvider.entries.size
            SegmentedList {
                AiProvider.entries.forEachIndexed { index, provider ->
                    val status = overview.status(provider)
                    val selected = overview.selected?.takeIf { it.provider == provider }
                    SegmentedListItem(
                        headline = providerLabel(provider),
                        supporting = when {
                            selected != null && status.connected -> stringResource(R.string.providers_in_use, overview.displayName(selected))
                            provider == AiProvider.CUSTOM -> stringResource(R.string.providers_custom_hint)
                            else -> null
                        },
                        shape = segmentShape(index, count),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                if (status.connected) {
                                    Tag(stringResource(R.string.providers_connected), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                } else {
                                    Tag(stringResource(R.string.providers_not_set), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = { onOpenProvider(provider) },
                    )
                }
            }
            Note(stringResource(R.string.providers_note))
        }
    }
}

/** 一家服务商：连接、模型列表、选模型、断开。 */
@Composable
fun ProviderDetailRoute(
    provider: AiProvider,
    onBack: () -> Unit,
    viewModel: ProviderDetailViewModel = koinViewModel(key = "provider-${provider.name}", parameters = { parametersOf(provider) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderDetailScreen(
        state = state,
        onBack = onBack,
        onConnect = viewModel::connect,
        onRefresh = viewModel::refresh,
        onQuery = viewModel::setQuery,
        onSelect = viewModel::select,
        onDisconnect = viewModel::disconnect,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    state: ProviderDetailUiState,
    onBack: () -> Unit = {},
    onConnect: (key: String, baseUrl: String?) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onQuery: (String) -> Unit = {},
    onSelect: (String) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        onMessageShown()
        snackbar.showSnackbar(resources.getString(message.text()))
    }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val name = providerLabel(state.provider)
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.provider_disconnect_title, name)) },
            text = { Text(stringResource(R.string.provider_disconnect_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        onDisconnect()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.provider_disconnect)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.provider_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.provider_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val status = state.status
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fitInside(WindowInsetsRulers.Ime.current),
        ) {
            if (status == null) return@LazyColumn
            if (!state.connected) {
                item("connect") { ConnectForm(state.provider, state.busy == ProviderBusy.CONNECTING, onConnect) }
                item("empty-header") { SectionHeader(stringResource(R.string.provider_models, 0), color = MaterialTheme.colorScheme.primary) }
                item("empty") { Note(stringResource(R.string.provider_models_empty)) }
                return@LazyColumn
            }

            item("key") {
                val showUrl = status.customBaseUrl || state.provider == AiProvider.CUSTOM
                SegmentedList {
                    SegmentedListItem(
                        headline = stringResource(R.string.provider_key),
                        supporting = status.maskedKey,
                        shape = segmentShape(0, if (showUrl) 2 else 1),
                        trailing = {
                            TextButton(
                                onClick = { confirmDisconnect = true },
                                enabled = state.busy == null,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.provider_disconnect)) }
                        },
                    )
                    if (showUrl) {
                        SegmentedListItem(
                            headline = stringResource(R.string.provider_base_url),
                            supporting = status.baseUrl,
                            shape = segmentShape(1, 2),
                        )
                    }
                }
            }
            item("models-header") {
                SectionHeader(
                    stringResource(R.string.provider_models, state.totalModels),
                    color = MaterialTheme.colorScheme.primary,
                    action = {
                        if (state.busy == ProviderBusy.REFRESHING) {
                            CircularProgressIndicator(Modifier.padding(Spacing.m).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = onRefresh, enabled = state.busy == null) {
                                Icon(OpenBiteIcons.Refresh, contentDescription = stringResource(R.string.provider_refresh))
                            }
                        }
                    },
                )
            }
            item("search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l),
                    placeholder = { Text(stringResource(R.string.provider_search)) },
                    leadingIcon = { Icon(OpenBiteIcons.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                )
            }
            item("fetched") {
                val fetched = status.catalog?.fetchedAtMillis
                if (fetched != null) {
                    val ago = DateUtils.getRelativeTimeSpanString(fetched, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    Note(stringResource(R.string.provider_fetched_at, ago), Modifier.padding(top = Spacing.s))
                }
            }
            if (state.models.isEmpty() && state.query.isNotBlank()) {
                item("no-match") { Note(stringResource(R.string.provider_models_no_match, state.query.trim())) }
            }
            val count = state.models.size
            itemsIndexed(state.models, key = { _, row -> "model-" + row.model.id }) { index, row ->
                ModelItem(
                    row = row,
                    shape = segmentShape(index, count),
                    checking = state.checkingModelId == row.model.id,
                    enabled = state.busy == null && row.vision != VisionSupport.NO,
                    onClick = { onSelect(row.model.id) },
                )
            }
            item("check-note") { Note(stringResource(R.string.provider_check_note), Modifier.padding(top = Spacing.s, bottom = Spacing.xxl)) }
        }
    }
}

@Composable
private fun ModelItem(
    row: ModelRow,
    shape: androidx.compose.ui.graphics.Shape,
    checking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        headline = row.label,
        supporting = row.model.id.takeIf { row.model.displayName != null && it != row.model.displayName },
        shape = shape,
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = 1.dp)
            .alpha(if (row.vision == VisionSupport.NO) 0.45f else 1f),
        leading = { RadioButton(selected = row.selected, onClick = null) },
        trailing = {
            when {
                checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Tag(stringResource(R.string.provider_vision_checking), scheme.surfaceVariant, scheme.onSurfaceVariant)
                }
                row.vision == VisionSupport.YES -> Tag(stringResource(R.string.provider_vision_yes), scheme.secondaryContainer, scheme.onSecondaryContainer)
                row.vision == VisionSupport.NO -> Tag(stringResource(R.string.provider_vision_no), scheme.errorContainer, scheme.onErrorContainer)
                else -> Tag(stringResource(R.string.provider_vision_unknown), scheme.surfaceVariant, scheme.onSurfaceVariant)
            }
        },
        onClick = if (enabled && !row.selected) onClick else null,
    )
}

/** 没连上时：Key 输入框（不回显），自定义必填地址，其他几家可以展开改地址（代理）。 */
@Composable
private fun ConnectForm(provider: AiProvider, connecting: Boolean, onConnect: (String, String?) -> Unit) {
    // 输入框内容只在内存里，不进 saveable（避免进 Bundle）。
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val urlRequired = provider.defaultBaseUrl == null
    var showUrl by remember { mutableStateOf(urlRequired) }
    val canConnect = !connecting && key.isNotBlank() && (!urlRequired || url.isNotBlank())
    val submit = {
        if (canConnect) {
            onConnect(key, url.takeIf { showUrl && it.isNotBlank() })
            key = ""
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.provider_key)) },
            placeholder = { Text(stringResource(R.string.provider_key_placeholder)) },
            singleLine = true,
            enabled = !connecting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = if (showUrl) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (showUrl) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(if (urlRequired) R.string.provider_base_url_required else R.string.provider_base_url_optional))
                },
                placeholder = { provider.defaultBaseUrl?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                singleLine = true,
                enabled = !connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!urlRequired && !showUrl) {
                TextButton(onClick = { showUrl = true }, enabled = !connecting) { Text(stringResource(R.string.provider_base_url_optional)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = submit, enabled = canConnect) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(Spacing.s))
                    Text(stringResource(R.string.provider_connecting))
                } else {
                    Text(stringResource(R.string.provider_connect))
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ProviderMessage.text(): Int = when (this) {
    ProviderMessage.CONNECTED -> R.string.provider_msg_connected
    ProviderMessage.REFRESHED -> R.string.provider_msg_refreshed
    ProviderMessage.SELECTED -> R.string.provider_msg_selected
    ProviderMessage.DISCONNECTED -> R.string.provider_msg_disconnected
    ProviderMessage.KEY_MALFORMED -> R.string.provider_msg_key_malformed
    ProviderMessage.URL_INVALID -> R.string.provider_msg_url_invalid
    ProviderMessage.KEY_REJECTED -> R.string.provider_msg_key_rejected
    ProviderMessage.NO_MODELS -> R.string.provider_msg_no_models
    ProviderMessage.NO_VISION -> R.string.provider_msg_no_vision
    ProviderMessage.RATE_LIMITED -> R.string.provider_msg_rate_limited
    ProviderMessage.NETWORK -> R.string.provider_msg_network
    ProviderMessage.FAILED -> R.string.provider_msg_failed
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun AiProvidersPreview() {
    OpenBiteTheme(darkTheme = false) {
        AiProvidersScreen(previewOverview(ModelRef(AiProvider.OPENAI, "vision-model-1"), demo = false))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ProviderNotConnectedPreview() {
    OpenBiteTheme(darkTheme = false) {
        ProviderDetailScreen(ProviderDetailUiState(AiProvider.CUSTOM, status = previewOverview(null, false).status(AiProvider.CUSTOM)))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ProviderConnectedDarkPreview() {
    val status = previewOverview(null, false).status(AiProvider.OPENAI)
        .copy(catalog = com.openbite.core.model.ModelCatalog(emptyList(), System.currentTimeMillis() - 3 * DateUtils.DAY_IN_MILLIS))
    val rows = listOf(
        ModelRow(ProviderModel("vision-model-1"), VisionSupport.YES, selected = true),
        ModelRow(ProviderModel("vision-model-2"), VisionSupport.UNKNOWN, selected = false),
        ModelRow(ProviderModel("text-embedding-x"), VisionSupport.NO, selected = false),
    )
    OpenBiteTheme(darkTheme = true) {
        ProviderDetailScreen(
            ProviderDetailUiState(AiProvider.OPENAI, status = status, models = rows, totalModels = 38, checkingModelId = "vision-model-2", busy = ProviderBusy.CHECKING),
        )
    }
}
