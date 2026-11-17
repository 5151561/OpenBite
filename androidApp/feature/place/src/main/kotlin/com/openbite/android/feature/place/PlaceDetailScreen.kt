package com.openbite.android.feature.place

import com.openbite.core.model.PlaceDeletionSummary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import com.openbite.core.domain.normalizeTag
import com.openbite.core.domain.MAX_TAG_LENGTH
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.RatingDisplay
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.fullDate
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.text.quoted
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.DishStats
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceVisit
import com.openbite.core.model.formatMoney
import com.openbite.core.model.formatRating
import com.openbite.feature.place.PlaceDetailUiState
import com.openbite.feature.place.PlaceDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.openbite.android.core.designsystem.R as DsR

/** 店铺详情（设计稿 08）。加标签、删标签在这里直接做。 */
@Composable
fun PlaceDetailRoute(
    placeId: String,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onShare: () -> Unit,
    onScanMenu: () -> Unit,
    onOpenMeal: (logId: String, isDraft: Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onPickLocation: () -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: PlaceDetailViewModel = koinViewModel(parameters = { parametersOf(placeId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deleteFailed = stringResource(R.string.place_delete_failed)
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            Toast.makeText(context, deleteFailed, Toast.LENGTH_SHORT).show()
            viewModel.deleteErrorShown()
        }
    }
    state.confirmingDelete?.let { summary ->
        DeletePlaceDialog(summary, onConfirm = viewModel::confirmDelete, onDismiss = viewModel::cancelDelete)
    }
    PlaceDetailScreen(
        state = state,
        onBack = onBack,
        onOpenMenu = onOpenMenu,
        onShare = onShare,
        onAddTag = { tag -> viewModel.addTag(tag) },
        onRemoveTag = viewModel::removeTag,
        onScanMenu = onScanMenu,
        onOpenMeal = onOpenMeal,
        onOpenMap = onOpenMap,
        onPickLocation = onPickLocation,
        onDelete = viewModel::requestDelete,
    )
}

/** 删除确认：写清楚会一起删掉什么、不能撤销；删除按钮用 error 色。 */
@Composable
internal fun DeletePlaceDialog(summary: PlaceDeletionSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(OpenBiteIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.place_delete_title, summary.placeName)) },
        text = {
            Text(
                stringResource(R.string.place_delete_text, summary.dishCount, summary.logCount, summary.photoCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.place_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.place_tag_cancel)) } },
    )
}

@Composable
fun PlaceDetailScreen(
    state: PlaceDetailUiState,
    onBack: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onShare: () -> Unit = {},
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onScanMenu: () -> Unit = {},
    onOpenMeal: (String, Boolean) -> Unit = { _, _ -> },
    onOpenMap: () -> Unit = {},
    onPickLocation: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val detail = state.detail
    var addingTag by rememberSaveable { mutableStateOf(false) }
    if (addingTag && detail != null) {
        AddTagDialog(
            suggestions = state.tagSuggestions,
            onAdd = {
                onAddTag(it)
                addingTag = false
            },
            onDismiss = { addingTag = false },
        )
    }
    val tagActions = TagActions(onAdd = { addingTag = true }, onRemove = onRemoveTag, onOpenMap = onOpenMap, onPickLocation = onPickLocation)
    Scaffold(
        // 头部颜色铺到状态栏下面，所以这里只让出导航栏。
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            if (detail != null) {
                ExtendedFloatingActionButton(
                    onClick = onScanMenu,
                    icon = { Icon(OpenBiteIcons.PhotoCamera, contentDescription = null) },
                    text = { Text(stringResource(R.string.place_scan_again)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            detail == null -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.l)) {
                RoundIconButton(OpenBiteIcons.ArrowBack, stringResource(R.string.place_back), onBack)
                Text(
                    stringResource(R.string.place_missing),
                    modifier = Modifier.padding(Spacing.l),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> PlaceDetailContent(detail, padding, onBack, onOpenMenu, onShare, onDelete, tagActions, onOpenMeal)
        }
    }
}

@Composable
private fun PlaceDetailContent(
    detail: PlaceDetail,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    tagActions: TagActions,
    onOpenMeal: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item(key = "header") { Header(detail, onBack, onShare, onDelete, tagActions) }
        item(key = "history") { SectionHeader(stringResource(R.string.place_history), Modifier.padding(top = Spacing.xs)) }
        if (detail.visits.isEmpty()) {
            item(key = "no-visits") { Note(stringResource(R.string.place_no_visits)) }
        }
        itemsIndexed(detail.visits, key = { _, it -> it.memory.logId }) { index, visit ->
            VisitCard(visit, index, detail.visits.size) { onOpenMeal(visit.memory.logId, visit.memory.isDraft) }
        }
        item(key = "dishes") {
            SectionHeader(
                stringResource(R.string.place_ordered_dishes),
                action = { TextButton(onClick = onOpenMenu) { Text(stringResource(R.string.place_full_menu)) } },
            )
        }
        itemsIndexed(detail.orderedDishes, key = { _, it -> it.dishId ?: it.nameOriginal }) { index, dish ->
            SegmentedListItem(
                headline = dish.nameOriginal,
                supporting = dish.nameTranslated + stringResource(DsR.string.ds_separator) + timesOrdered(dish),
                shape = segmentShape(index, detail.orderedDishes.size),
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                trailing = { dish.averageRating?.let { RatingDisplay(formatRating(it)) } },
            )
        }
    }
}

@Composable
internal fun timesOrdered(stats: DishStats): String =
    pluralStringResource(if (stats.isDrink) R.plurals.place_cups_ordered else R.plurals.place_times_ordered, stats.timesOrdered, stats.timesOrdered)

/** 彩色头部：大号淡色单字、返回和分享、店名、来过几次、标签。 */
@Composable
private fun Header(detail: PlaceDetail, onBack: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, actions: TagActions) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer)
            .clipToBounds(),
    ) {
        Text(
            detail.glyph.char,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = 28.dp)
                .clearAndSetSemantics {},
            fontSize = 190.sp,
            lineHeight = 190.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
        )
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 204.dp)
                .padding(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.l),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth()) {
                RoundIconButton(OpenBiteIcons.ArrowBack, stringResource(R.string.place_back), onBack)
                Spacer(Modifier.weight(1f))
                RoundIconButton(OpenBiteIcons.Share, stringResource(R.string.place_share), onShare)
                Spacer(Modifier.width(Spacing.s))
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    RoundIconButton(OpenBiteIcons.MoreVert, stringResource(R.string.place_more)) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.place_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(OpenBiteIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(top = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(detail.place.name, style = MaterialTheme.typography.headlineLarge, color = scheme.onPrimaryContainer)
                val meta = listOfNotNull(pluralStringResource(R.plurals.place_visits, detail.visitCount, detail.visitCount), detail.area)
                    .joinToString(stringResource(DsR.string.ds_separator))
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
                val hasLocation = detail.place.lat != null && detail.place.lon != null
                AssistChip(
                    onClick = if (hasLocation) actions.onOpenMap else actions.onPickLocation,
                    label = { Text(stringResource(if (hasLocation) R.string.place_on_map else R.string.place_set_location)) },
                    leadingIcon = {
                        Icon(OpenBiteIcons.LocationOn, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize), tint = scheme.primary)
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = scheme.surfaceContainerLowest),
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.xs).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    detail.place.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { actions.onRemove(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    OpenBiteIcons.Close,
                                    contentDescription = stringResource(R.string.place_remove_tag, tag),
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                    AssistChip(
                        onClick = actions.onAdd,
                        label = { Text(stringResource(R.string.place_add_tag)) },
                        leadingIcon = { Icon(OpenBiteIcons.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }
    }
}

/** 头部标签和位置的操作。 */
private class TagActions(
    val onAdd: () -> Unit,
    val onRemove: (String) -> Unit,
    val onOpenMap: () -> Unit,
    val onPickLocation: () -> Unit,
)

/** 加标签：输入或点一个建议。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTagDialog(suggestions: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val valid = normalizeTag(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_add_tag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.place_tag_hint)) },
                    supportingText = { Text(stringResource(R.string.place_tag_limit, MAX_TAG_LENGTH)) },
                    isError = text.isNotBlank() && !valid,
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        suggestions.forEach { tag -> SuggestionChip(onClick = { onAdd(tag) }, label = { Text(tag) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = valid) { Text(stringResource(R.string.place_tag_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.place_tag_cancel)) } },
    )
}

@Composable
internal fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** 一次到店：日期、纪念日标签、评分、短评、“4 道菜 · ¥9,200 · 和老婆”。 */
@Composable
private fun VisitCard(visit: PlaceVisit, index: Int, count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val memory = visit.memory
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2)
            .fillMaxWidth()
            .clip(segmentShape(index, count))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(fullDate(memory.localDate), style = MaterialTheme.typography.titleMedium)
            visit.milestone?.let {
                Badge(milestoneTitle(it, visit.milestoneYears), scheme.tertiaryContainer, scheme.onTertiaryContainer)
            }
            Spacer(Modifier.weight(1f))
            if (memory.isDraft) {
                Text(stringResource(DsR.string.ds_memory_draft), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            } else {
                memory.rating?.let { RatingDisplay(formatRating(it), color = scheme.tertiary, starColor = scheme.tertiary) }
            }
        }
        memory.note?.let { Text(quoted(it), style = MaterialTheme.typography.bodyMedium) }
        val separator = stringResource(DsR.string.ds_separator)
        val listSeparator = stringResource(DsR.string.ds_list_separator)
        val meta = buildList {
            add(pluralStringResource(R.plurals.place_visit_dishes, memory.dishCount, memory.dishCount))
            memory.total?.let { add(formatMoney(it)) }
            if (memory.companions.isNotEmpty()) {
                add(stringResource(DsR.string.ds_memory_with, memory.companions.joinToString(listSeparator)))
            }
        }.joinToString(separator)
        Text(meta, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlaceDetailPreview() {
    OpenBiteTheme(darkTheme = false) {
        PlaceDetailScreen(PlaceDetailUiState(isLoading = false, detail = PlacePreviewData.detail, currentYear = 2026))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlaceDetailDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        PlaceDetailScreen(PlaceDetailUiState(isLoading = false, detail = PlacePreviewData.detail, currentYear = 2026))
    }
}
