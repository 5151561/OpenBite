package com.openbite.android.feature.scan

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.modelUseLabel
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.ScanResult
import com.openbite.core.model.Money
import com.openbite.core.model.ScanError
import com.openbite.core.model.ModelRef
import com.openbite.core.model.AiProvider
import com.openbite.core.model.ModelUse
import com.openbite.core.model.formatMoney
import com.openbite.core.model.ScannedDish
import com.openbite.feature.scan.ScanPhase
import com.openbite.feature.scan.ScanUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen(
    state: ScanUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetake: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOrder: () -> Unit = {},
    onRename: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val sheet: @Composable () -> Unit = {
        Sheet(state, onBack, onRetry, onRetake, onOpenSettings, onOrder, onRename)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.inverseSurface)) {
        if (maxHeight < CompactHeight) {
            // 横屏或分屏：高度不够竖着排，照片放左边，识别面板放右边占满高度，底部按钮不会被挤掉。
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ScanTopBar(onBack)
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        PhotoCard(
                            state.photo,
                            sweeping = state.isWorking,
                            modifier = Modifier.fillMaxHeight(0.9f).aspectRatio(PhotoAspect),
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().statusBarsPadding().padding(top = Spacing.s),
                    color = scheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) { sheet() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ScanTopBar(onBack)
                Box(
                    modifier = Modifier.fillMaxWidth().height(272.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoCard(state.photo, sweeping = state.isWorking)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    color = scheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) { sheet() }
            }
        }
    }
}

/** 低于这个高度（横屏手机、分屏）改成左右两栏。 */
private val CompactHeight = 480.dp

private const val PhotoAspect = 280f / 252f

@Composable
private fun ScanTopBar(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.statusBarsPadding().padding(horizontal = Spacing.xs).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(onClick = onBack) {
            Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.scan_back), tint = scheme.inverseOnSurface)
        }
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge, color = scheme.inverseOnSurface)
    }
}

/** 斜放的照片 + 识别中左右扫动的竖条。 */
@Composable
private fun PhotoCard(
    jpeg: ByteArray?,
    sweeping: Boolean,
    modifier: Modifier = Modifier.size(width = 280.dp, height = 252.dp),
) {
    val bitmap by produceState<ImageBitmap?>(null, jpeg) {
        value = jpeg?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    val description = stringResource(R.string.scan_photo_description)
    BoxWithConstraints(
        modifier = modifier
            .rotate(-3f)
            .shadow(6.dp, MaterialTheme.shapes.extraSmall)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .semantics { contentDescription = description },
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (sweeping) {
            val transition = rememberInfiniteTransition(label = "sweep")
            val fraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "sweep-x",
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - 16.dp) * (1f - fraction) + 6.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun Sheet(
    state: ScanUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onOpenSettings: () -> Unit,
    onOrder: () -> Unit,
    onRename: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.l)) {
        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.l), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 32.dp, height = 4.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
        }
        StatusRow(state)
        Spacer(Modifier.height(Spacing.m))
        if (state.phase != ScanPhase.FAILED) {
            val progress = state.progress
            val label = stringResource(R.string.scan_progress_label)
            val modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
            if (progress == null) LinearProgressIndicator(modifier) else LinearProgressIndicator(progress = { progress }, modifier = modifier)
            Spacer(Modifier.height(Spacing.m))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            state.hasLocation?.let {
                InfoChip(stringResource(if (it) R.string.scan_chip_exif_location else R.string.scan_chip_exif), check = true)
            }
            state.model?.let { InfoChip(modelUseLabel(it), check = false) }
        }
        DishList(state, Modifier.weight(1f))
        Actions(state, onBack, onRetry, onRetake, onOpenSettings, onOrder, onRename)
    }
}

@Composable
private fun StatusRow(state: ScanUiState) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when (state.phase) {
                ScanPhase.DONE -> Icon(OpenBiteIcons.Check, contentDescription = null, tint = scheme.primary)
                ScanPhase.FAILED -> Icon(OpenBiteIcons.Close, contentDescription = null, tint = scheme.error)
                else -> CircularProgressIndicator(Modifier.size(32.dp))
            }
        }
        val title = when (state.phase) {
            ScanPhase.WAITING_PHOTO, ScanPhase.PREPARING -> R.string.scan_preparing
            ScanPhase.READING -> R.string.scan_reading
            ScanPhase.SAVING -> R.string.scan_saving
            ScanPhase.DONE -> R.string.scan_done
            ScanPhase.FAILED -> R.string.scan_failed
        }
        Text(stringResource(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (state.dishes.isNotEmpty() || state.estimatedDishCount != null) {
            val estimate = state.estimatedDishCount
            val count = if (estimate != null && state.phase != ScanPhase.DONE) {
                stringResource(R.string.scan_count_estimated, state.dishes.size, estimate)
            } else {
                pluralStringResource(R.plurals.scan_count, state.dishes.size, state.dishes.size)
            }
            Text(count, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** 只是说明，不可点。 */
@Composable
private fun InfoChip(text: String, check: Boolean) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (check) Icon(OpenBiteIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DishList(state: ScanUiState, modifier: Modifier) {
    val listState = rememberLazyListState()
    val count = state.dishes.size + if (state.pendingName != null) 1 else 0
    // 新菜出来时滚到底，跟着看。
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }
    LazyColumn(modifier = modifier.padding(top = Spacing.s), state = listState, contentPadding = PaddingValues(bottom = Spacing.s)) {
        itemsIndexed(state.dishes, key = { index, _ -> index }) { index, dish ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
            DishRow(dish)
        }
        state.pendingName?.let { name ->
            item(key = "pending") {
                if (state.dishes.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                PendingRow(name)
            }
        }
    }
}

@Composable
private fun DishRow(dish: ScannedDish) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.weight(1f)) {
            Text(dish.nameOriginal, style = MaterialTheme.typography.bodyLarge)
            if (dish.nameTranslated.isNotBlank()) {
                Text(dish.nameTranslated, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val price = dish.priceMinor?.let { amount -> dish.currency?.let { formatMoney(Money(amount, it)) } }
        price?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** 原名已经出来、译名和价格还在路上：灰条一闪一闪。 */
@Composable
private fun PendingRow(name: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse-alpha")
    val bar = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.size(96.dp, 12.dp).alpha(alpha).background(bar, CircleShape))
        }
        Box(Modifier.size(44.dp, 12.dp).alpha(alpha).background(bar, CircleShape))
    }
}

@Composable
private fun Actions(
    state: ScanUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onOpenSettings: () -> Unit,
    onOrder: () -> Unit,
    onRename: (String) -> Unit,
) {
    Column(
        modifier = Modifier.navigationBarsPadding().padding(top = Spacing.s, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        when (state.phase) {
            ScanPhase.DONE -> state.result?.let { result ->
                SavedNote(result, onRename)
                Text(
                    stringResource(R.string.scan_allergens_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrderButton(onOrder)
            }
            ScanPhase.READING, ScanPhase.SAVING -> OrderButton(onOrder)
            ScanPhase.FAILED -> {
                val error = state.error ?: ScanError.UNKNOWN
                Text(stringResource(error.message()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                val retry = R.string.scan_retry to onRetry
                val retake = R.string.scan_retake to onRetake
                // 去设置弄好再回来点重试：照片还在，不用重拍。
                val actions = when (error) {
                    ScanError.NO_MODEL -> listOf(R.string.scan_connect_ai to onOpenSettings, retry)
                    ScanError.INVALID_API_KEY -> listOf(R.string.scan_check_key to onOpenSettings, retry)
                    ScanError.MODEL_GONE -> listOf(R.string.scan_pick_model to onOpenSettings, retry)
                    ScanError.IMAGE_UNREADABLE, ScanError.NO_DISHES -> listOf(retake, retry)
                    ScanError.PLACE_MISSING -> listOf(R.string.scan_back to onBack)
                    else -> listOf(retry, retake)
                }
                if (state.dishes.isNotEmpty()) {
                    // 识别没完成也能用已经认出的菜点单，下单时只存名字和价格。
                    TextButton(onClick = onOrder) {
                        Text(pluralStringResource(R.plurals.scan_order_partial, state.dishes.size, state.dishes.size))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    actions.forEachIndexed { index, (label, action) ->
                        val modifier = Modifier.weight(1f).height(48.dp)
                        if (index == 0) {
                            Button(onClick = action, modifier = modifier) { Text(stringResource(label)) }
                        } else {
                            OutlinedButton(onClick = action, modifier = modifier) { Text(stringResource(label)) }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

/** 设计稿 02 的“边识别边点单”：识别中就能进点单页，菜会接着一道道出来。 */
@Composable
private fun OrderButton(onOrder: () -> Unit) {
    Button(onClick = onOrder, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.scan_order), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SavedNote(result: ScanResult, onRename: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val text = if (result.isNewPlace) {
            stringResource(R.string.scan_saved_new, result.placeName)
        } else {
            stringResource(R.string.scan_saved_existing, result.placeName, result.added, result.updated)
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (result.isNewPlace) {
            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.scan_rename)) }
        }
    }
    if (editing) {
        var name by rememberSaveable { mutableStateOf(result.placeName) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.scan_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(name)
                        editing = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.scan_rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.scan_cancel)) } },
        )
    }
}

private fun ScanError.message(): Int = when (this) {
    ScanError.NO_MODEL -> R.string.scan_error_no_model
    ScanError.INVALID_API_KEY -> R.string.scan_error_invalid_api_key
    ScanError.MODEL_GONE -> R.string.scan_error_model_gone
    ScanError.RATE_LIMITED -> R.string.scan_error_rate_limited
    ScanError.SERVICE_UNAVAILABLE -> R.string.scan_error_service_unavailable
    ScanError.TIMEOUT -> R.string.scan_error_timeout
    ScanError.NETWORK -> R.string.scan_error_network
    ScanError.INCOMPLETE -> R.string.scan_error_incomplete
    ScanError.IMAGE_UNREADABLE -> R.string.scan_error_image_unreadable
    ScanError.NO_DISHES -> R.string.scan_error_no_dishes
    ScanError.PLACE_MISSING -> R.string.scan_error_place_missing
    ScanError.UNKNOWN -> R.string.scan_error_unknown
}

private val previewDishes = listOf(
    ScannedDish("焼き鳥", "焼き鳥盛り合わせ", "烤鸡肉串拼盘", 980, "JPY"),
    ScannedDish("焼き物", "鯖の塩焼き", "盐烤青花鱼", 780, "JPY"),
    ScannedDish("一品", "だし巻き玉子", "高汤煎蛋卷", 580, "JPY"),
)

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ScanReadingPreview() {
    OpenBiteTheme(darkTheme = false) {
        ScanScreen(
            ScanUiState(
                phase = ScanPhase.READING,
                model = ModelUse.Real(ModelRef(AiProvider.ANTHROPIC, "claude-vision-model")),
                hasLocation = true,
                estimatedDishCount = 7,
                dishes = previewDishes,
                pendingName = "海老の天ぷら",
            ),
        )
    }
}

@Preview(widthDp = 892, heightDp = 412)
@Composable
private fun ScanReadingLandscapePreview() {
    OpenBiteTheme(darkTheme = false) {
        ScanScreen(
            ScanUiState(
                phase = ScanPhase.READING,
                model = ModelUse.Real(ModelRef(AiProvider.GEMINI, "gemini-vision-model")),
                hasLocation = true,
                estimatedDishCount = 7,
                dishes = previewDishes,
            ),
        )
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ScanDoneDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        ScanScreen(
            ScanUiState(
                phase = ScanPhase.DONE,
                model = ModelUse.Demo,
                hasLocation = false,
                dishes = previewDishes,
                result = ScanResult("p", "新店铺 11月17日", isNewPlace = true, scanId = "s", dishCount = 3, added = 3, updated = 0),
            ),
        )
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ScanNoModelPreview() {
    OpenBiteTheme(darkTheme = false) {
        ScanScreen(ScanUiState(phase = ScanPhase.FAILED, error = ScanError.NO_MODEL))
    }
}
