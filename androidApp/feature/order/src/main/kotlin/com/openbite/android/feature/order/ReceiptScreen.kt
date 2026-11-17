package com.openbite.android.feature.order

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.MenuStatus
import com.openbite.core.domain.Phrase
import com.openbite.core.domain.Receipt
import com.openbite.core.domain.ReceiptLine
import com.openbite.core.domain.buildReceipt
import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import com.openbite.feature.order.PlacePhase
import com.openbite.feature.order.ReceiptUiState
import com.openbite.feature.order.ReceiptViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 给店员看（设计稿 04）：全屏、大字、屏幕常亮，深浅色都看得清。
 * 点“已下单”才生成草稿记录，成功后交给 [onPlaced]。
 */
@Composable
fun ReceiptRoute(
    sessionId: String,
    onBack: () -> Unit,
    onPlaced: (logId: String) -> Unit,
    viewModel: ReceiptViewModel = koinViewModel(parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.placedLogId) { state.placedLogId?.let(onPlaced) }
    // 进程被杀过、会话没了：不停在空页，直接退回上一页。
    LaunchedEffect(state.sessionLost) { if (state.sessionLost) onBack() }
    val context = LocalContext.current
    // 通知权限只在第一次“已下单”时要（饭后补记忆的提醒要用）；不管给不给都接着下单。
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.placeOrder()
    }
    val place = {
        val prefs = context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        if (needsAsk) {
            prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.placeOrder()
        }
    }
    ReceiptScreen(
        state = state,
        onBack = onBack,
        onPlace = place,
        onRetrySave = viewModel::retrySave,
        onPlaceWithoutMenu = viewModel::placeWithoutMenu,
        onDismissDecision = viewModel::dismissDecision,
    )
}

@Composable
fun ReceiptScreen(
    state: ReceiptUiState,
    onBack: () -> Unit = {},
    onPlace: () -> Unit = {},
    onRetrySave: () -> Unit = {},
    onPlaceWithoutMenu: () -> Unit = {},
    onDismissDecision: () -> Unit = {},
) {
    KeepScreenOnFullScreen()
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.primaryContainer)
            .safeDrawingPadding()
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(
            modifier = Modifier.height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerLowest,
                    contentColor = scheme.onSurface,
                ),
            ) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.receipt_back)) }
            Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
        }
        val receipt = state.receipt
        when {
            state.unavailable -> Text(
                stringResource(R.string.order_unavailable),
                modifier = Modifier.padding(Spacing.l),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onPrimaryContainer,
            )
            receipt == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ReceiptCard(receipt) }
                Footer(state, onPlace, onBack)
            }
        }
    }
    if (state.phase == PlacePhase.NEEDS_DECISION) {
        AlertDialog(
            onDismissRequest = onDismissDecision,
            title = { Text(stringResource(R.string.receipt_decision_title)) },
            text = { Text(stringResource(R.string.receipt_decision_text)) },
            confirmButton = { TextButton(onClick = onRetrySave) { Text(stringResource(R.string.receipt_retry)) } },
            dismissButton = { TextButton(onClick = onPlaceWithoutMenu) { Text(stringResource(R.string.receipt_without_menu)) } },
        )
    }
}

/** 小票卡片：日文（菜单语言）大字，中文小字在下。 */
@Composable
private fun ReceiptCard(receipt: Receipt) {
    val scheme = MaterialTheme.colorScheme
    val big = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLowest, MaterialTheme.shapes.extraLarge)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PhraseText(receipt.header, MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            receipt.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Column(Modifier.weight(1f)) {
                        Text(line.nameOriginal, style = big, color = scheme.onSurface)
                        line.nameTranslated?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.receipt_qty, line.qty), style = big, color = scheme.onSurface)
                }
            }
        }
        TicketDivider()
        if (receipt.notes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.errorContainer, MaterialTheme.shapes.large)
                    .padding(horizontal = Spacing.l, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                receipt.notes.forEach { note ->
                    PhraseText(
                        note.phrase,
                        if (note.isAllergy) big else MaterialTheme.typography.titleLarge,
                        color = scheme.onErrorContainer,
                        secondaryColor = scheme.onErrorContainer,
                    )
                }
            }
        }
        PhraseText(
            receipt.disclaimer,
            MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            secondaryColor = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhraseText(
    phrase: Phrase,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column {
        Text(phrase.primary, style = style, color = color)
        phrase.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryColor) }
    }
}

/** 虚线 + 两边的半圆缺口，像撕下来的小票。 */
@Composable
private fun TicketDivider() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-32).dp).size(24.dp).background(scheme.primaryContainer, CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 32.dp).size(24.dp).background(scheme.primaryContainer, CircleShape))
    }
}

@Composable
private fun Footer(state: ReceiptUiState, onPlace: () -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val busy = state.phase == PlacePhase.WAITING_FOR_MENU || state.phase == PlacePhase.PLACING || state.phase == PlacePhase.PLACED
    Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val hint = when (state.phase) {
            PlacePhase.WAITING_FOR_MENU -> R.string.receipt_waiting
            PlacePhase.PLACING -> R.string.receipt_placing
            PlacePhase.FAILED -> R.string.receipt_failed
            else -> null
        }
        hint?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
        }
        Button(
            onClick = onPlace,
            enabled = !busy && state.receipt?.lines?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = scheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.size(Spacing.s))
            }
            Text(stringResource(R.string.receipt_placed), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.onPrimaryContainer),
        ) { Text(stringResource(R.string.receipt_edit)) }
    }
}

/** 给店员看的时候：屏幕常亮，藏起状态栏和导航栏（从边缘滑一下可以叫出来）。离开时恢复。 */
@Composable
private fun KeepScreenOnFullScreen() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(view, activity) {
        view.keepScreenOn = true
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private const val PERMISSION_PREFS = "openbite_permissions"
private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val previewReceipt = buildReceipt(
    listOf(
        ReceiptLine("焼き鳥盛り合わせ", "烤鸡肉串拼盘", 1),
        ReceiptLine("だし巻き玉子", "高汤煎蛋卷", 1),
        ReceiptLine("海老の天ぷら", "炸虾天妇罗", 1),
        ReceiptLine("生ビール", "生啤", 2),
    ),
    TasteProfile(listOf(Allergen.PEANUT), listOf(AvoidItem.GREEN_ONION, AvoidItem.CILANTRO), SpiceLevel.MILD),
    "ja",
)

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ReceiptPreview() {
    OpenBiteTheme(darkTheme = false) { ReceiptScreen(ReceiptUiState(isLoading = false, receipt = previewReceipt)) }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ReceiptDarkWaitingPreview() {
    OpenBiteTheme(darkTheme = true) {
        ReceiptScreen(ReceiptUiState(isLoading = false, receipt = previewReceipt, menuStatus = MenuStatus.SAVING, phase = PlacePhase.WAITING_FOR_MENU))
    }
}
