package com.openbite.android.feature.meal

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.rememberPhotoSources
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.MealDetail
import com.openbite.core.domain.placeGlyph
import com.openbite.core.domain.shortArea
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import com.openbite.core.model.formatMoney
import com.openbite.feature.meal.EditItem
import com.openbite.feature.meal.MealEditor
import com.openbite.feature.meal.MemoryEditUiState
import com.openbite.feature.meal.MemoryEditViewModel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.openbite.android.core.designsystem.R as DsR

/** 补记忆（设计稿 05）。存入记忆 / 稍后都会 [onDone]；关闭时有未存的改动先确认。 */
@Composable
fun MemoryEditRoute(
    logId: String,
    onDone: () -> Unit,
    onOpenReceipt: () -> Unit,
    onShare: () -> Unit,
    viewModel: MemoryEditViewModel = koinViewModel(parameters = { parametersOf(logId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoFailed = stringResource(R.string.edit_photo_failed)
    val noCamera = stringResource(R.string.edit_no_camera)
    val sources = rememberPhotoSources(onPicked = viewModel::addPhoto, onNoCamera = {
        Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
    })
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved != null) onDone() }
    LaunchedEffect(state.photoError) {
        if (state.photoError != null) {
            Toast.makeText(context, photoFailed, Toast.LENGTH_SHORT).show()
            viewModel.photoErrorShown()
        }
    }
    val close = {
        if (state.editor?.dirty == true) confirmDiscard = true else onDone()
    }
    BackHandler(onBack = close)

    MemoryEditScreen(
        state = state,
        onClose = close,
        onSave = viewModel::save,
        onEdit = viewModel::edit,
        onTakePhoto = sources.takePhoto,
        onPickPhoto = sources.pickPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onAddCompanion = viewModel::addCompanion,
        onOpenReceipt = onOpenReceipt,
        onShare = onShare,
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                    onDone()
                }) { Text(stringResource(R.string.edit_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.edit_keep)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryEditScreen(
    state: MemoryEditUiState,
    onClose: () -> Unit = {},
    onSave: (done: Boolean) -> Unit = {},
    onEdit: ((MealEditor) -> MealEditor) -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: (String) -> Unit = {},
    onAddCompanion: (String) -> Unit = {},
    onOpenReceipt: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val detail = state.detail
    val editor = state.editor
    val isDraft = detail?.log?.status != DiningStatus.DONE
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isDraft) R.string.edit_title else R.string.edit_title_done)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(OpenBiteIcons.Close, contentDescription = stringResource(R.string.edit_close)) }
                },
                actions = {
                    if (isDraft && editor != null) {
                        TextButton(onClick = { onSave(false) }, enabled = !state.saving) { Text(stringResource(R.string.edit_later)) }
                    }
                },
            )
        },
        bottomBar = {
            if (editor != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Button(
                            onClick = { onSave(true) },
                            enabled = !state.saving,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(Spacing.s))
                            }
                            Text(stringResource(if (isDraft) R.string.edit_save_done else R.string.edit_save), style = MaterialTheme.typography.titleMedium)
                        }
                        // 分享用的是已经存下的内容（设计稿 05 右下角）。
                        FilledTonalIconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                            Icon(OpenBiteIcons.Share, contentDescription = stringResource(R.string.edit_share))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            editor == null || detail == null -> Text(
                stringResource(R.string.edit_missing),
                modifier = Modifier.padding(padding).padding(Spacing.xxl),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                HeaderCard(detail, editor, state.currentYear, onEdit)
                ItemsCard(editor, onEdit)
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = { text -> onEdit { it.setNote(text) } },
                    label = { Text(stringResource(R.string.edit_note)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                    minLines = 2,
                )
                PhotoRow(detail, editor, state.addingPhoto, onTakePhoto, onPickPhoto, onRemovePhoto, onOpenReceipt)
                TagsCard(detail, editor, onEdit, onAddCompanion)
            }
        }
    }
}

/** 店名、时间、金额、总评分。 */
@Composable
private fun HeaderCard(detail: MealDetail, editor: MealEditor, currentYear: Int, onEdit: ((MealEditor) -> MealEditor) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val log = detail.log
    Column(
        Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .background(scheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            GlyphAvatar(placeGlyph(log.place.name), size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(log.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val total = log.paidTotal ?: editor.orderedTotal
                val meta = listOfNotNull(mealTimeLabel(log, currentYear), shortArea(log.place.address), total?.let(::formatMoney))
                    .joinToString(stringResource(DsR.string.ds_separator))
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (log.status == DiningStatus.DRAFT && !editor.dirty) {
                Text(
                    stringResource(R.string.edit_prefilled),
                    modifier = Modifier.background(scheme.secondaryContainer, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
        StarRow(editor.rating, onPick = { n -> onEdit { it.setRating(n) } }, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** 每道菜：原名 + 译名、份数、点开打分写短评。 */
@Composable
private fun ItemsCard(editor: MealEditor, onEdit: ((MealEditor) -> MealEditor) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large),
    ) {
        editor.items.forEach { item ->
            ItemRow(item, expanded == item.id, onToggle = { expanded = if (expanded == item.id) null else item.id }, onEdit = onEdit)
        }
    }
}

@Composable
private fun ItemRow(item: EditItem, expanded: Boolean, onToggle: () -> Unit, onEdit: ((MealEditor) -> MealEditor) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var menu by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                val qty = if (item.qty > 1) " " + stringResource(R.string.edit_qty, item.qty) else ""
                Text(item.name + qty, style = MaterialTheme.typography.bodyLarge)
                item.nameTranslated?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            }
            val rating = item.rating
            if (rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rating.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.tertiary)
                    Icon(OpenBiteIcons.Star, contentDescription = stringResource(R.string.edit_star, rating), tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(stringResource(R.string.edit_tap_to_rate), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(OpenBiteIcons.Edit, contentDescription = stringResource(R.string.edit_remove_item)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_increase)) },
                        leadingIcon = { Icon(OpenBiteIcons.Add, contentDescription = null) },
                        onClick = { onEdit { it.setQty(item.id, item.qty + 1) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_decrease)) },
                        leadingIcon = { Icon(OpenBiteIcons.Remove, contentDescription = null) },
                        enabled = item.qty > 1,
                        onClick = { onEdit { it.setQty(item.id, item.qty - 1) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_remove_item), color = scheme.error) },
                        leadingIcon = { Icon(OpenBiteIcons.Close, contentDescription = null, tint = scheme.error) },
                        onClick = {
                            menu = false
                            onEdit { it.removeItem(item.id) }
                        },
                    )
                }
            }
        }
        if (expanded) {
            StarRow(item.rating, onPick = { n -> onEdit { it.setItemRating(item.id, n) } }, size = 28.dp)
            OutlinedTextField(
                value = item.comment,
                onValueChange = { text -> onEdit { it.setItemComment(item.id, text) } },
                label = { Text(stringResource(R.string.edit_item_comment)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun PhotoRow(
    detail: MealDetail,
    editor: MealEditor,
    adding: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onOpenReceipt: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box {
            Tile(onClick = { menu = true }) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(OpenBiteIcons.Add, contentDescription = null)
                }
                Text(stringResource(R.string.edit_add_photo), style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit_take_photo)) }, onClick = { menu = false; onTakePhoto() })
                DropdownMenuItem(text = { Text(stringResource(R.string.edit_pick_photo)) }, onClick = { menu = false; onPickPhoto() })
            }
        }
        (editor.photos.map { it.id to it.path } + editor.addedPhotos.map { it.id to it.path }).forEach { (id, path) ->
            Box {
                PhotoThumb(path)
                IconButton(
                    onClick = { onRemovePhoto(id) },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Icon(
                        OpenBiteIcons.Close,
                        contentDescription = stringResource(R.string.edit_remove_photo),
                        tint = scheme.inverseOnSurface,
                        modifier = Modifier.background(scheme.inverseSurface.copy(alpha = 0.6f), MaterialTheme.shapes.extraLarge).padding(2.dp),
                    )
                }
            }
        }
        Tile(onClick = onOpenReceipt, highlighted = true) {
            Icon(OpenBiteIcons.Receipt, contentDescription = null)
            val paid = detail.log.paidTotal
            Text(
                if (paid != null) stringResource(R.string.edit_receipt_saved, formatMoney(paid)) else stringResource(R.string.edit_receipt),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Tile(onClick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .size(80.dp)
            .background(if (highlighted) scheme.tertiaryContainer else scheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/** 纪念日和同行人。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(detail: MealDetail, editor: MealEditor, onEdit: ((MealEditor) -> MealEditor) -> Unit, onAddCompanion: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(bottom = Spacing.m),
    ) {
        if (detail.allMilestones.isNotEmpty()) {
            SectionHeader(stringResource(R.string.edit_milestone), Modifier.padding(start = 0.dp))
            FlowRow(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                detail.allMilestones.forEach { milestone ->
                    val years = detail.log.localDate.year - milestone.date.year
                    FilterChip(
                        selected = editor.milestoneId == milestone.id,
                        onClick = { onEdit { it.toggleMilestone(milestone.id) } },
                        label = { Text(milestoneTitle(milestone, years)) },
                    )
                }
            }
        }
        SectionHeader(stringResource(R.string.edit_companions))
        FlowRow(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            detail.allPersons.forEach { person ->
                FilterChip(
                    selected = person.id in editor.personIds,
                    onClick = { onEdit { it.togglePerson(person.id) } },
                    label = { Text(person.displayName) },
                )
            }
            AssistChip(
                onClick = { adding = true },
                label = { Text(stringResource(R.string.edit_add_person)) },
                leadingIcon = { Icon(OpenBiteIcons.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
    if (adding) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.edit_add_person_title)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.edit_add_person_hint)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddCompanion(name)
                    adding = false
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.edit_add_person_confirm)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.edit_cancel)) } },
        )
    }
}

internal val previewLog = DiningLog(
    id = "l",
    place = Place("p", "炭火焼鳥 とり松", null, null, "京都市中京区先斗町通"),
    visitedAt = 1_795_000_000_000,
    timezone = "Asia/Tokyo",
    localDate = LocalDate(2026, 11, 17),
    status = DiningStatus.DRAFT,
    milestoneId = "love",
    note = "炭火味很足，她把蛋卷全吃了",
    source = LogSource.USER,
    items = listOf(
        DiningLogItem("a", null, "焼き鳥盛り合わせ", Money(980, "JPY"), 1, 5, null, "烤鸡肉串拼盘"),
        DiningLogItem("b", null, "だし巻き玉子", Money(580, "JPY"), 1, 5, null, "高汤煎蛋卷"),
        DiningLogItem("c", null, "海老の天ぷら", Money(1200, "JPY"), 1, 4, null, "炸虾天妇罗"),
        DiningLogItem("d", null, "生ビール", Money(550, "JPY"), 2, null, null, "生啤"),
    ),
    companions = listOf(Person("wife", "老婆")),
    rating = 5,
)

internal val previewDetail = MealDetail(
    log = previewLog,
    milestone = null,
    milestoneYears = 0,
    allMilestones = listOf(Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY)),
    allPersons = listOf(Person("wife", "老婆"), Person("roommates", "大学室友")),
)

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MemoryEditPreview() {
    OpenBiteTheme(darkTheme = false) {
        MemoryEditScreen(MemoryEditUiState(isLoading = false, detail = previewDetail, editor = MealEditor.from(previewLog), currentYear = 2026))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MemoryEditDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        MemoryEditScreen(MemoryEditUiState(isLoading = false, detail = previewDetail, editor = MealEditor.from(previewLog), currentYear = 2026))
    }
}
