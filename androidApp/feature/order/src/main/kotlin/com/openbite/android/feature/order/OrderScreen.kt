package com.openbite.android.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.TasteEditor
import com.openbite.android.core.designsystem.component.mayContainLabel
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.MenuStatus
import com.openbite.core.domain.OrderDish
import com.openbite.core.domain.TastePhrases
import com.openbite.core.domain.dishGlyph
import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.Money
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.formatMoney
import com.openbite.feature.order.OrderItem
import com.openbite.feature.order.OrderUiState
import com.openbite.feature.order.OrderViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 点单（设计稿 03）。[sessionId] 来自识别页（边识别边点单）；否则用 [placeId] 店铺里存好的菜单。 */
@Composable
fun OrderRoute(
    sessionId: String?,
    placeId: String?,
    onBack: () -> Unit,
    onShowStaff: (sessionId: String) -> Unit,
    viewModel: OrderViewModel = koinViewModel(parameters = { parametersOf(sessionId, placeId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSession by viewModel.sessionId.collectAsStateWithLifecycle()
    OrderScreen(
        state = state,
        onBack = onBack,
        onSelectCategory = viewModel::selectCategory,
        onQuantity = viewModel::setQuantity,
        onTasteOverride = viewModel::setTasteOverride,
        onShowStaff = { currentSession?.let(onShowStaff) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    state: OrderUiState,
    onBack: () -> Unit = {},
    onSelectCategory: (String?) -> Unit = {},
    onQuantity: (key: String, qty: Int) -> Unit = { _, _ -> },
    onTasteOverride: (TasteProfile?) -> Unit = {},
    onShowStaff: () -> Unit = {},
) {
    var editingTaste by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.placeName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.order_back))
                    }
                },
            )
        },
        bottomBar = { if (!state.isLoading && !state.unavailable) TotalBar(state, onShowStaff) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.unavailable -> Text(
                stringResource(R.string.order_unavailable),
                modifier = Modifier.padding(padding).padding(Spacing.xxl),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> OrderList(state, padding, onSelectCategory, onQuantity) { editingTaste = true }
        }
    }
    if (editingTaste) {
        TasteSheet(
            initial = state.effectiveTaste,
            hasOverride = state.override != null,
            onApply = {
                onTasteOverride(it)
                editingTaste = false
            },
            onReset = {
                onTasteOverride(null)
                editingTaste = false
            },
            onDismiss = { editingTaste = false },
        )
    }
}

@Composable
private fun OrderList(
    state: OrderUiState,
    padding: PaddingValues,
    onSelectCategory: (String?) -> Unit,
    onQuantity: (String, Int) -> Unit,
    onEditTaste: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = Spacing.l),
    ) {
        item(key = "status") { MenuStatusRow(state) }
        if (state.categories.isNotEmpty()) {
            item(key = "categories") {
                val label = stringResource(R.string.order_category_group)
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m)
                        .semantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text(stringResource(R.string.order_category_all)) },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category) },
                        )
                    }
                }
            }
        }
        val visible = state.visibleItems
        if (visible.isEmpty() && state.status == MenuStatus.SCANNING) {
            item(key = "waiting") {
                Text(
                    stringResource(R.string.order_waiting_dishes),
                    modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.l),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(visible, key = { _, it -> it.dish.key }) { index, item ->
            // 头像颜色按整份菜单里的位置算，筛选时不变色。
            val menuIndex = state.items.indexOfFirst { it.dish.key == item.dish.key }
            DishRow(item, menuIndex, segmentShape(index, visible.size), onQuantity)
        }
        item(key = "taste") { TasteRow(state, onEditTaste) }
        item(key = "note") {
            Text(
                stringResource(R.string.order_allergen_note),
                modifier = Modifier.padding(horizontal = Spacing.xxxl, vertical = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuStatusRow(state: OrderUiState) {
    val text = when (state.status) {
        MenuStatus.SCANNING -> state.estimatedDishCount?.let { stringResource(R.string.order_scanning_estimated, state.dishCount, it) }
            ?: stringResource(R.string.order_scanning, state.dishCount)
        MenuStatus.SAVING -> stringResource(R.string.order_saving)
        MenuStatus.FAILED -> stringResource(R.string.order_scan_failed)
        MenuStatus.READY -> return
    }
    Column(Modifier.padding(start = Spacing.xxl, end = Spacing.l, bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.status == MenuStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.status != MenuStatus.FAILED) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun DishRow(item: OrderItem, menuIndex: Int, shape: androidx.compose.ui.graphics.Shape, onQuantity: (String, Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dish = item.dish
    val name = dish.nameTranslated.ifBlank { dish.nameOriginal }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2)
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .background(scheme.surfaceContainer, shape)
            .padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box {
            GlyphAvatar(dishGlyph(dish.nameOriginal, menuIndex), size = 48.dp)
            if (item.qty > 0) {
                Badge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) {
                    Text(stringResource(R.string.order_badge, item.qty))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(dish.nameOriginal, style = MaterialTheme.typography.titleMedium)
            if (dish.nameTranslated.isNotBlank()) {
                Text(dish.nameTranslated, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                dish.price?.let { Text(formatMoney(it), style = MaterialTheme.typography.bodyMedium) }
                if (item.warnings.isNotEmpty()) {
                    Text(
                        mayContainLabel(item.warnings),
                        modifier = Modifier
                            .background(scheme.tertiaryContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onTertiaryContainer,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { onQuantity(dish.key, item.qty - 1) }, enabled = item.qty > 0) {
                Icon(OpenBiteIcons.Remove, contentDescription = stringResource(R.string.order_decrease, name))
            }
            Text(
                item.qty.toString(),
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            FilledTonalIconButton(onClick = { onQuantity(dish.key, item.qty + 1) }) {
                Icon(OpenBiteIcons.Add, contentDescription = stringResource(R.string.order_increase, name))
            }
        }
    }
}

@Composable
private fun TasteRow(state: OrderUiState, onEdit: () -> Unit) {
    val summary = TastePhrases.summary(state.effectiveTaste)
    val headline = when {
        state.override != null -> stringResource(R.string.order_taste_overridden)
        summary != null -> stringResource(R.string.order_taste_applied)
        else -> stringResource(R.string.order_taste_none)
    }
    SegmentedListItem(
        headline = headline,
        supporting = summary ?: stringResource(R.string.order_taste_none_hint),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.order_taste_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onEdit,
    )
}

@Composable
private fun TotalBar(state: OrderUiState, onShowStaff: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.xxl, end = Spacing.l, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val total = state.total?.let(::formatMoney) ?: if (state.count == 0) formatMoney(Money(0, "JPY")) else stringResource(R.string.order_total_unknown)
                Text(total, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.order_count, state.count),
                    modifier = Modifier.padding(bottom = Spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onShowStaff, enabled = state.count > 0) {
                Text(stringResource(R.string.order_show_staff), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** “这次改一下”：只改这一单的口味。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasteSheet(
    initial: TasteProfile,
    hasOverride: Boolean,
    onApply: (TasteProfile) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
            Text(
                stringResource(R.string.order_sheet_title),
                modifier = Modifier.padding(horizontal = Spacing.xxl),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.order_sheet_hint),
                modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TasteEditor(
                profile = draft,
                onToggleAllergen = { draft = draft.toggle(it) },
                onToggleAvoid = { draft = draft.toggle(it) },
                onSpice = { draft = draft.copy(spice = if (draft.spice == it) null else it) },
            )
            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedButton(onClick = onReset, enabled = hasOverride, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.order_sheet_reset))
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.order_sheet_apply))
                }
            }
        }
    }
}

private fun previewDish(key: String, translated: String, category: String, price: Long, vararg allergens: String) =
    OrderDish(key, key, translated, category, Money(price, "JPY"), allergens.toList(), null)

private val previewProfile = TasteProfile(
    allergens = listOf(Allergen.PEANUT, Allergen.SHELLFISH),
    avoids = listOf(AvoidItem.GREEN_ONION, AvoidItem.CILANTRO),
    spice = SpiceLevel.MILD,
)

private val previewState = OrderUiState(
    isLoading = false,
    placeName = "炭火焼鳥 とり松",
    status = MenuStatus.SCANNING,
    estimatedDishCount = 6,
    categories = listOf("焼き鳥", "一品", "揚げ物", "飲み物"),
    items = listOf(
        OrderItem(previewDish("焼き鳥盛り合わせ", "烤鸡肉串拼盘", "焼き鳥", 980, "soy"), 1, emptyList()),
        OrderItem(previewDish("鯖の塩焼き", "盐烤青花鱼", "焼き物", 780, "fish"), 0, emptyList()),
        OrderItem(previewDish("だし巻き玉子", "高汤煎蛋卷", "一品", 580, "egg"), 1, emptyList()),
        OrderItem(previewDish("海老の天ぷら", "炸虾天妇罗", "揚げ物", 1200, "shellfish", "wheat"), 1, listOf(Allergen.SHELLFISH)),
        OrderItem(previewDish("生ビール", "生啤", "飲み物", 550, "wheat"), 2, emptyList()),
    ),
    profile = previewProfile,
    effectiveTaste = previewProfile,
    total = Money(3860, "JPY"),
    count = 5,
    dishCount = 5,
)

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun OrderScreenPreview() {
    OpenBiteTheme(darkTheme = false) { OrderScreen(previewState) }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun OrderScreenDarkPreview() {
    OpenBiteTheme(darkTheme = true) { OrderScreen(previewState.copy(status = MenuStatus.READY)) }
}
