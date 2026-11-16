package com.openbite.android.feature.milestone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.MilestoneAvatarShape
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.SegmentedList
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.TrailingText
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.preview.PreviewData
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.text.monthDay
import com.openbite.android.core.designsystem.text.monthDayWeekday
import com.openbite.android.core.designsystem.text.previousCelebrationLabel
import com.openbite.android.core.designsystem.text.quoted
import com.openbite.android.core.designsystem.text.recurrenceLabel
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.android.core.designsystem.theme.emphasized
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.formatRating
import com.openbite.feature.milestone.MilestonesUiState
import com.openbite.feature.milestone.MilestonesViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.openbite.android.core.designsystem.R as DsR

/** 纪念日 Tab。店铺页、提醒设置和新建纪念日在后面的步骤才做，现在由 app 传空实现。 */
@Composable
fun MilestonesRoute(
    onOpenPlace: (placeId: String) -> Unit,
    onEditReminder: () -> Unit,
    onAddMilestone: () -> Unit,
    viewModel: MilestonesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    MilestonesScreen(
        state,
        onOpenPlace,
        onEditReminder,
        onAddMilestone,
        notificationsOn = reminders?.milestones ?: true,
        onNotifications = viewModel::setMilestoneReminders,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreen(
    state: MilestonesUiState,
    onOpenPlace: (String) -> Unit = {},
    onEditReminder: () -> Unit = {},
    onAddMilestone: () -> Unit = {},
    notificationsOn: Boolean = true,
    onNotifications: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.milestone_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMilestone) {
                Icon(OpenBiteIcons.Add, contentDescription = stringResource(R.string.milestone_add))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            val next = state.next
            if (next == null) {
                item {
                    Text(
                        stringResource(R.string.milestone_empty),
                        modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.l),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { NextMilestoneCard(next, onOpenPlace) }
            }
            if (state.later.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.milestone_later), Modifier.padding(top = Spacing.xs)) }
                itemsIndexed(state.later, key = { _, it -> it.upcoming.milestone.id }) { index, item ->
                    SegmentedListItem(
                        headline = milestoneTitle(item),
                        supporting = monthDay(item.upcoming.nextDate) + stringResource(DsR.string.ds_separator) +
                            recurrenceLabel(item.upcoming.milestone.recurrence),
                        shape = segmentShape(index, state.later.size),
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                        leading = { GlyphAvatar(item.glyph, shape = MilestoneAvatarShape) },
                        trailing = { TrailingText(stringResource(DsR.string.ds_days_count, item.upcoming.daysUntil)) },
                    )
                }
            }
            item { ReminderSection(state.remindDaysBefore, onEditReminder, notificationsOn, onNotifications) }
        }
    }
}

/** 下一个纪念日的大卡片，附上次为它吃的那顿饭。 */
@Composable
private fun NextMilestoneCard(highlight: MilestoneHighlight, onOpenPlace: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .background(scheme.tertiaryContainer, MaterialTheme.shapes.extraLarge)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.milestone_next), style = MaterialTheme.typography.labelLarge, color = scheme.onTertiaryContainer)
                Text(milestoneTitle(highlight), style = MaterialTheme.typography.headlineMedium.emphasized(), color = scheme.onTertiaryContainer)
                Text(
                    monthDayWeekday(highlight.upcoming.nextDate) + stringResource(DsR.string.ds_separator) +
                        recurrenceLabel(highlight.upcoming.milestone.recurrence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onTertiaryContainer,
                )
            }
            if (highlight.upcoming.daysUntil == 0) {
                Text(stringResource(DsR.string.ds_today), style = MaterialTheme.typography.displaySmall.emphasized(), color = scheme.onTertiaryContainer)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        highlight.upcoming.daysUntil.toString(),
                        style = MaterialTheme.typography.displayLarge.emphasized(),
                        color = scheme.onTertiaryContainer,
                    )
                    Text(
                        stringResource(DsR.string.ds_days_after),
                        modifier = Modifier.padding(bottom = Spacing.s),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onTertiaryContainer,
                    )
                }
            }
        }
        val previous = highlight.previous
        val previousLabel = previousCelebrationLabel(highlight)
        if (previous != null && previousLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(scheme.surfaceContainerLowest)
                    .clickable { onOpenPlace(previous.placeId) }
                    .padding(start = Spacing.l, top = Spacing.m, bottom = Spacing.m, end = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Column(Modifier.weight(1f)) {
                    val title = previous.rating?.let { stringResource(R.string.milestone_previous_rating, previousLabel, formatRating(it)) }
                        ?: previousLabel
                    Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    previous.note?.let {
                        Text(quoted(it), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = scheme.onSurface)
            }
        }
    }
}

@Composable
private fun ReminderSection(remindDaysBefore: Int, onEditReminder: () -> Unit, notify: Boolean, onNotify: (Boolean) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.milestone_reminders))
        SegmentedList {
            SegmentedListItem(
                headline = stringResource(R.string.milestone_remind_before),
                shape = segmentShape(0, 2),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrailingText(stringResource(DsR.string.ds_days_count, remindDaysBefore))
                        Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = onEditReminder,
            )
            SegmentedListItem(
                headline = stringResource(R.string.milestone_notifications),
                shape = segmentShape(1, 2),
                trailing = { Switch(checked = notify, onCheckedChange = onNotify) },
                onClick = { onNotify(!notify) },
            )
        }
        Text(
            stringResource(R.string.milestone_footer),
            modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MilestonesScreenPreview() {
    OpenBiteTheme(darkTheme = false) {
        MilestonesScreen(
            MilestonesUiState(isLoading = false, next = PreviewData.highlights.first(), later = PreviewData.highlights.drop(1)),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MilestonesScreenDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        MilestonesScreen(
            MilestonesUiState(isLoading = false, next = PreviewData.highlights.first(), later = PreviewData.highlights.drop(1)),
        )
    }
}
