package com.openbite.android.feature.meal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.rememberPhotoSources
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.modelUseLabel
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.Money
import com.openbite.core.model.formatMoney
import com.openbite.core.model.currencySymbol
import com.openbite.android.core.designsystem.component.CurrencyField
import com.openbite.android.core.designsystem.component.CurrencyPickerDialog
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import com.openbite.core.domain.sampleReceipt
import com.openbite.feature.meal.ReceiptArchiveUiState
import com.openbite.feature.meal.ReceiptArchiveViewModel
import com.openbite.feature.meal.ReceiptPhase
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/** 小票存档（设计稿 19）：店家给的纸质小票。存好或返回都回补记忆。 */
@Composable
fun ReceiptArchiveRoute(
    logId: String,
    onBack: () -> Unit,
    viewModel: ReceiptArchiveViewModel = koinViewModel(parameters = { parametersOf(logId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noCamera = stringResource(R.string.edit_no_camera)
    val lang = remember { Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() && it != "und" } ?: "zh-CN" }
    val sources = rememberPhotoSources(
        onPicked = { viewModel.onPhoto(it, lang) },
        onNoCamera = { Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show() },
    )
    // 返回交给 Navigation 3（带预测性返回动画）；没存的新照片由 ViewModel.onCleared 删。
    LaunchedEffect(state.phase) { if (state.phase == ReceiptPhase.SAVED) onBack() }
    ReceiptArchiveScreen(
        state = state,
        onBack = onBack,
        onTakePhoto = sources.takePhoto,
        onPickPhoto = sources.pickPhoto,
        onRetry = viewModel::retryReading,
        onTotal = viewModel::setTotalText,
        onCurrency = viewModel::setCurrency,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptArchiveScreen(
    state: ReceiptArchiveUiState,
    onBack: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onRetry: () -> Unit = {},
    onTotal: (String) -> Unit = {},
    onCurrency: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }
    if (pickingCurrency) {
        CurrencyPickerDialog(
            selected = state.currency,
            recent = state.recentCurrencies,
            onPick = { code ->
                code?.let(onCurrency)
                pickingCurrency = false
            },
            onDismiss = { pickingCurrency = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.receipt_back)) }
                },
            )
        },
        bottomBar = {
            if (!state.isLoading && !state.missing) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = onSave,
                        enabled = !state.totalInvalid && state.phase !in setOf(ReceiptPhase.PROCESSING, ReceiptPhase.READING, ReceiptPhase.SAVING),
                        // 跟着键盘上移：键盘收起时让开导航栏，弹出时贴在键盘上方。Scaffold 把底栏高度算进 padding，
                        // 内容区随之变矮，输入框的 fitInside(Ime) 不会再多让一次。
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(Spacing.l)
                            .height(48.dp),
                    ) { Text(stringResource(R.string.receipt_save), style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.missing -> Text(stringResource(R.string.receipt_missing), Modifier.padding(padding).padding(Spacing.xxl))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).fitInside(WindowInsetsRulers.Ime.current).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                PhotoPanel(state, onTakePhoto, onPickPhoto)
                Status(state, onRetry)
                state.reading?.let { Lines(state) }
                Amounts(state, onTotal, onPickCurrency = { pickingCurrency = true })
                Text(
                    stringResource(R.string.receipt_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoPanel(state: ReceiptArchiveUiState, onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val path = state.newPhoto?.path ?: state.savedPhotoPath
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer, MaterialTheme.shapes.extraLarge).padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (path != null) {
            PhotoThumb(path, size = 240.dp, description = stringResource(R.string.receipt_photo_description))
        } else {
            Icon(OpenBiteIcons.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = scheme.onSurfaceVariant)
            Text(stringResource(R.string.receipt_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(onClick = onTakePhoto) {
                Icon(OpenBiteIcons.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (path != null) R.string.receipt_retake else R.string.receipt_take), Modifier.padding(start = Spacing.s))
            }
            TextButton(onClick = onPickPhoto) { Text(stringResource(R.string.receipt_pick)) }
        }
    }
}

@Composable
private fun Status(state: ReceiptArchiveUiState, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (state.phase) {
        ReceiptPhase.PROCESSING, ReceiptPhase.READING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(if (state.phase == ReceiptPhase.PROCESSING) R.string.receipt_processing else R.string.receipt_reading))
        }
        ReceiptPhase.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.receipt_read_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            if (state.newPhoto != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.receipt_retry)) }
        }
        ReceiptPhase.READ -> state.model?.let {
            Text(stringResource(R.string.receipt_read_by, modelUseLabel(it)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        else -> Unit
    }
}

/** 读出来的明细、小计、税、合计。 */
@Composable
private fun Lines(state: ReceiptArchiveUiState) {
    val receipt = state.reading ?: return
    val scheme = MaterialTheme.colorScheme
    fun money(minor: Long?) = minor?.let { formatMoney(Money(it, state.currency)) }.orEmpty()
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(stringResource(R.string.receipt_lines), style = MaterialTheme.typography.titleSmall, color = scheme.primary)
        receipt.items.forEach { line ->
            Row {
                Column(Modifier.weight(1f)) {
                    Text(line.name + if (line.qty > 1) " ×${line.qty}" else "", style = MaterialTheme.typography.bodyMedium)
                    line.nameTranslated?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                }
                Text(money(line.amountMinor), style = MaterialTheme.typography.bodyMedium)
            }
        }
        receipt.subtotalMinor?.let { Row { Text(stringResource(R.string.receipt_subtotal), Modifier.weight(1f)); Text(money(it)) } }
        receipt.taxMinor?.let { Row { Text(stringResource(R.string.receipt_tax), Modifier.weight(1f)); Text(money(it)) } }
        receipt.totalMinor?.let {
            Row {
                Text(stringResource(R.string.receipt_total), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(money(it), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Amounts(state: ReceiptArchiveUiState, onTotal: (String) -> Unit, onPickCurrency: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    CurrencyField(state.currency, state.currencySource, onClick = onPickCurrency, enabled = state.phase != ReceiptPhase.SAVING)
    OutlinedTextField(
        value = state.totalText,
        onValueChange = onTotal,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.receipt_paid)) },
        placeholder = { Text(stringResource(R.string.receipt_paid_hint, state.currency)) },
        prefix = { Text(currencySymbol(state.currency, ComposeLocale.current.toLanguageTag())) },
        singleLine = true,
        isError = state.totalInvalid,
        supportingText = if (state.totalInvalid) ({ Text(stringResource(R.string.receipt_paid_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    state.extra?.let { extra ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.receipt_extra), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            val value = if (extra.amountMinor > 0) {
                stringResource(R.string.receipt_extra_value, formatMoney(extra))
            } else {
                stringResource(R.string.receipt_less_value, formatMoney(extra.copy(amountMinor = -extra.amountMinor)))
            }
            Text(value, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ReceiptArchivePreview() {
    OpenBiteTheme(darkTheme = false) {
        ReceiptArchiveScreen(
            ReceiptArchiveUiState(
                isLoading = false,
                phase = ReceiptPhase.READ,
                reading = sampleReceipt(),
                totalText = "4246",
                orderedTotal = Money(3860, "JPY"),
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ReceiptArchiveEmptyDarkPreview() {
    OpenBiteTheme(darkTheme = true) { ReceiptArchiveScreen(ReceiptArchiveUiState(isLoading = false)) }
}
