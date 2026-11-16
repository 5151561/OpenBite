package com.openbite.android.feature.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.text.quoted
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.domain.MealDetail
import com.openbite.core.domain.mealRating
import com.openbite.core.domain.placeGlyph
import com.openbite.core.domain.totalOf
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.formatMoney
import com.openbite.core.model.formatRating
import com.openbite.feature.meal.MealUiState
import com.openbite.feature.meal.MealViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.openbite.android.core.designsystem.R as DsR

/** 一餐（只读）：已经存入记忆的记录。右上角编辑进补记忆。 */
@Composable
fun MealRoute(
    logId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    viewModel: MealViewModel = koinViewModel(parameters = { parametersOf(logId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MealScreen(state, onBack, onEdit, onShare)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen(state: MealUiState, onBack: () -> Unit = {}, onEdit: () -> Unit = {}, onShare: () -> Unit = {}) {
    val detail = state.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.log?.place?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.meal_back)) }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = onShare) { Icon(OpenBiteIcons.Share, contentDescription = stringResource(R.string.meal_share)) }
                        IconButton(onClick = onEdit) { Icon(OpenBiteIcons.Edit, contentDescription = stringResource(R.string.meal_edit)) }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            detail == null -> Text(stringResource(R.string.edit_missing), Modifier.padding(padding).padding(Spacing.xxl))
            else -> MealContent(detail, state.currentYear, Modifier.padding(padding))
        }
    }
}

@Composable
private fun MealContent(detail: MealDetail, currentYear: Int, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val log = detail.log
    val separator = stringResource(DsR.string.ds_separator)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(
            Modifier
                .padding(horizontal = Spacing.l)
                .fillMaxWidth()
                .background(scheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                GlyphAvatar(placeGlyph(log.place.name), size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(mealTimeLabel(log, currentYear), style = MaterialTheme.typography.titleMedium)
                    val people = log.companions.takeIf { it.isNotEmpty() }?.let { list ->
                        stringResource(R.string.meal_with, list.joinToString(stringResource(DsR.string.ds_list_separator)) { it.displayName })
                    }
                    val milestone = detail.milestone?.let { milestoneTitle(it, detail.milestoneYears) }
                    listOfNotNull(milestone, people).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(separator), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            val rating = mealRating(log)
            if (rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StarRow(log.rating ?: rating.toInt(), onPick = null, size = 24.dp)
                    Text(formatRating(rating), style = MaterialTheme.typography.titleMedium, color = scheme.tertiary)
                }
            } else {
                Text(stringResource(R.string.meal_no_rating), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            log.note?.let { Text(quoted(it), style = MaterialTheme.typography.bodyLarge) }
            val money = listOfNotNull(
                log.paidTotal?.let { stringResource(R.string.meal_paid, formatMoney(it)) },
                totalOf(log)?.let { stringResource(R.string.meal_ordered, formatMoney(it)) },
            )
            if (money.isNotEmpty()) Text(money.joinToString(separator), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (log.status == DiningStatus.DRAFT) {
                Text(stringResource(R.string.meal_draft_hint), style = MaterialTheme.typography.bodySmall, color = scheme.tertiary)
            }
        }
        val photos = log.photos
        if (photos.isNotEmpty()) {
            SectionHeader(stringResource(R.string.meal_photos))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                photos.forEach { photo ->
                    PhotoThumb(photo.path, size = 120.dp, description = if (photo.kind == PhotoKind.RECEIPT) stringResource(R.string.receipt_photo_description) else null)
                }
            }
        }
        SectionHeader(stringResource(R.string.meal_items))
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            log.items.forEachIndexed { index, item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer, segmentShape(index, log.items.size))
                        .padding(horizontal = Spacing.l, vertical = Spacing.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val qty = if (item.qty > 1) " " + stringResource(R.string.edit_qty, item.qty) else ""
                            Text(item.name + qty, style = MaterialTheme.typography.bodyLarge)
                            item.nameTranslated?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                        }
                        item.rating?.let {
                            Text(it.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.tertiary)
                            Icon(OpenBiteIcons.Star, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                    item.comment?.let { Text(quoted(it), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MealPreview() {
    OpenBiteTheme(darkTheme = false) {
        MealScreen(MealUiState(false, previewDetail.copy(log = previewLog.copy(status = DiningStatus.DONE)), 2026))
    }
}
