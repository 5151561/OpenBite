package com.openbite.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.MemoryListItem
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.preview.PreviewData
import com.openbite.android.core.designsystem.text.monthDayWeekday
import com.openbite.android.core.designsystem.text.milestoneTitle
import com.openbite.android.core.designsystem.text.previousCelebrationLabel
import com.openbite.android.core.designsystem.theme.DecorativeShapes
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.android.core.designsystem.theme.emphasized
import com.openbite.core.model.MilestoneHighlight
import com.openbite.feature.home.HomeUiState
import com.openbite.feature.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.openbite.android.core.designsystem.R as DsR

/**
 * 首页（今天）。拍菜单、从相册选、点开记忆的目标页面在 U3/U4 才做，现在由 app 传空实现。
 */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenMilestones: () -> Unit,
    onOpenMemories: () -> Unit,
    onScanMenu: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenMemory: (logId: String, placeId: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, onOpenSettings, onOpenMilestones, onOpenMemories, onScanMenu, onPickPhoto, onOpenMemory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit = {},
    onOpenMilestones: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onScanMenu: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onOpenMemory: (logId: String, placeId: String) -> Unit = { _, _ -> },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
                        state.today?.let {
                            Text(
                                monthDayWeekday(it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(OpenBiteIcons.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val currentYear = state.today?.year ?: 0
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.l),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    HeroCard(onScanMenu, onPickPhoto)
                    state.nextMilestone?.let { MilestoneCard(it, onOpenMilestones) }
                }
            }
            item {
                SectionHeader(
                    text = stringResource(R.string.home_recent),
                    action = { TextButton(onClick = onOpenMemories) { Text(stringResource(R.string.home_see_all)) } },
                )
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_empty),
                        modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.s),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(state.recent, key = { _, it -> it.logId }) { index, memory ->
                MemoryListItem(
                    memory = memory,
                    currentYear = currentYear,
                    shape = segmentShape(index, state.recent.size),
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                    onClick = { onOpenMemory(memory.logId, memory.placeId) },
                )
            }
        }
    }
}

/** 顶部大卡片：三个带字的装饰形状 + 拍菜单入口。 */
@Composable
private fun HeroCard(onScanMenu: () -> Unit, onPickPhoto: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.primaryContainer),
    ) {
        Decoration(DecorativeShapes.SoftBurst, 96.dp, "焼", scheme.primary, scheme.onPrimary, 8f,
            MaterialTheme.typography.headlineLarge.emphasized(), Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-18).dp))
        Decoration(DecorativeShapes.Cookie7, 64.dp, "鯖", scheme.tertiaryContainer, scheme.onTertiaryContainer, -10f,
            MaterialTheme.typography.titleLarge.emphasized(), Modifier.align(Alignment.TopEnd).offset(x = (-70).dp, y = 44.dp))
        Decoration(DecorativeShapes.Clover4, 56.dp, "酒", scheme.surfaceContainerLowest, scheme.onSurface, 6f,
            MaterialTheme.typography.titleLarge.emphasized(), Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 86.dp))
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.home_hero_label), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
            Text(
                stringResource(R.string.home_hero_title),
                modifier = Modifier.widthIn(max = 224.dp),
                style = MaterialTheme.typography.headlineMedium.emphasized(),
                color = scheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Button(onClick = onScanMenu, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(OpenBiteIcons.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.home_scan_menu), modifier = Modifier.padding(start = Spacing.s))
                }
                TextButton(onClick = onPickPhoto, colors = ButtonDefaults.textButtonColors(contentColor = scheme.onPrimaryContainer)) {
                    Text(stringResource(R.string.home_pick_photo))
                }
            }
        }
    }
}

@Composable
private fun Decoration(
    shape: Shape,
    size: Dp,
    char: String,
    container: Color,
    content: Color,
    degrees: Float,
    style: TextStyle,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .rotate(degrees)
            .background(container, shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(char, style = style, color = content)
    }
}

/** 最近的纪念日：心形 + 标题 + 倒数天数，点开去纪念日 Tab。 */
@Composable
private fun MilestoneCard(highlight: MilestoneHighlight, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Box(Modifier.size(48.dp).background(scheme.tertiaryContainer, DecorativeShapes.Heart))
            Column(Modifier.weight(1f)) {
                Text(milestoneTitle(highlight), style = MaterialTheme.typography.titleMedium)
                val subtitle = listOfNotNull(monthDayWeekday(highlight.upcoming.nextDate), previousCelebrationLabel(highlight))
                    .joinToString(stringResource(DsR.string.ds_separator))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Countdown(highlight.upcoming.daysUntil)
        }
    }
}

@Composable
private fun Countdown(daysUntil: Int) {
    val scheme = MaterialTheme.colorScheme
    if (daysUntil == 0) {
        Text(stringResource(DsR.string.ds_today), style = MaterialTheme.typography.headlineSmall, color = scheme.tertiary)
        return
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(daysUntil.toString(), style = MaterialTheme.typography.displaySmall, color = scheme.tertiary)
        Text(
            stringResource(DsR.string.ds_days_after),
            modifier = Modifier.padding(bottom = Spacing.s),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun HomeScreenPreview() {
    OpenBiteTheme(darkTheme = false) {
        HomeScreen(
            HomeUiState(
                isLoading = false,
                today = PreviewData.today,
                nextMilestone = PreviewData.highlights.first(),
                recent = PreviewData.memories.take(3),
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun HomeScreenDarkEmptyPreview() {
    OpenBiteTheme(darkTheme = true) {
        HomeScreen(HomeUiState(isLoading = false, today = PreviewData.today))
    }
}
