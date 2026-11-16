package com.openbite.android.feature.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.MemoryListItem
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.preview.PreviewData
import com.openbite.android.core.designsystem.text.monthHeader
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.feature.memories.MemoriesUiState
import com.openbite.feature.memories.MemoriesViewModel
import com.openbite.feature.memories.MemoryFilter
import com.openbite.feature.memories.filterMemories
import com.openbite.feature.memories.groupByMonth
import org.koin.compose.viewmodel.koinViewModel

/** 记忆 Tab。搜索、拍菜单、点开记录的目标页面在后面的步骤才做，现在由 app 传空实现。 */
@Composable
fun MemoriesRoute(
    onSearch: () -> Unit,
    onScanMenu: () -> Unit,
    onOpenMemory: (logId: String, placeId: String) -> Unit,
    viewModel: MemoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MemoriesScreen(state, viewModel::selectFilter, onSearch, onScanMenu, onOpenMemory)
}

private val filters = listOf(
    MemoryFilter.ALL to R.string.memories_filter_all,
    MemoryFilter.MILESTONE to R.string.memories_filter_milestone,
    MemoryFilter.DRAFT to R.string.memories_filter_draft,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    state: MemoriesUiState,
    onSelectFilter: (MemoryFilter) -> Unit = {},
    onSearch: () -> Unit = {},
    onScanMenu: () -> Unit = {},
    onOpenMemory: (logId: String, placeId: String) -> Unit = { _, _ -> },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memories_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(OpenBiteIcons.Search, contentDescription = stringResource(R.string.memories_search))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanMenu,
                icon = { Icon(OpenBiteIcons.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.memories_scan_menu)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
            // 底部留出 FAB 的高度，最后一条不被挡住。
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item(key = "filters") {
                val groupLabel = stringResource(R.string.memories_filter_group)
                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.l)
                        .semantics { contentDescription = groupLabel },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (state.isEmpty) R.string.memories_empty else R.string.memories_empty_filtered),
                        modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.l),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "h-${group.year}-${group.month}") {
                    SectionHeader(monthHeader(group.year, group.month, state.currentYear))
                }
                itemsIndexed(group.items, key = { _, it -> it.logId }) { index, memory ->
                    MemoryListItem(
                        memory = memory,
                        currentYear = state.currentYear,
                        shape = segmentShape(index, group.items.size),
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                        onClick = { onOpenMemory(memory.logId, memory.placeId) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MemoriesScreenPreview() {
    OpenBiteTheme(darkTheme = false) {
        MemoriesScreen(
            MemoriesUiState(
                isLoading = false,
                currentYear = PreviewData.today.year,
                groups = groupByMonth(PreviewData.memories),
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MemoriesScreenDraftsDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        MemoriesScreen(
            MemoriesUiState(
                isLoading = false,
                filter = MemoryFilter.DRAFT,
                currentYear = PreviewData.today.year,
                groups = groupByMonth(filterMemories(PreviewData.memories, MemoryFilter.DRAFT)),
            ),
        )
    }
}
