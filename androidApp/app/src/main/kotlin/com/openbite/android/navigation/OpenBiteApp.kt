package com.openbite.android.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openbite.android.R
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.android.feature.home.HomeRoute
import com.openbite.android.feature.memories.MemoriesRoute
import com.openbite.android.feature.milestone.MilestonesRoute
import com.openbite.android.feature.settings.SettingsRoute

private val tabs = TopLevelDestination.entries.toList()

/** App 外壳：底部导航 + 每个 Tab 自己的返回栈。 */
@Composable
fun OpenBiteApp() {
    val state = rememberNavigationState(tabs)
    val navigator = remember(state) { Navigator(state) }

    // 以下回调的目标页面在 U2–U4 才做，现在什么都不做。
    val notYet: () -> Unit = {}
    val notYetWithId: (String) -> Unit = {}

    val entryProvider = entryProvider {
        entry<HomeKey> {
            HomeRoute(
                onOpenSettings = { navigator.navigate(SettingsKey) },
                onOpenMilestones = { navigator.navigate(MilestonesKey) },
                onOpenMemories = { navigator.navigate(MemoriesKey) },
                onScanMenu = notYet, // TODO(U3): 识别菜单
                onPickPhoto = notYet, // TODO(U3): 相册选图后识别
                onOpenMemory = notYetWithId, // TODO(U4): 补记忆 / 看记录
            )
        }
        entry<MemoriesKey> {
            MemoriesRoute(
                onSearch = notYet, // TODO: 搜索记忆（设计稿未定）
                onScanMenu = notYet, // TODO(U3)
                onOpenMemory = notYetWithId, // TODO(U4)
            )
        }
        entry<PlacesKey> { PlacesPlaceholder() } // TODO(U2): 店铺列表
        entry<MilestonesKey> {
            MilestonesRoute(
                onOpenPlace = notYetWithId, // TODO(U2): 店铺详情
                onEditReminder = notYet, // TODO(U4): 提醒设置
                onAddMilestone = notYet, // TODO: 纪念日管理页（11/27）
            )
        }
        entry<SettingsKey> {
            SettingsRoute(
                onBack = navigator::goBack,
                onOpenTaste = notYet, // TODO(U3): 我的口味
                onChooseModel = notYet, // TODO(U3): 模型选择
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (state.isAtTabRoot) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = tab == state.currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigator.selectTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            entries = state.toDecoratedEntries(entryProvider),
            onBack = navigator::goBack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** 店铺 Tab 的占位页，U2 换成店铺列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesPlaceholder() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_places)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(R.string.places_placeholder),
                    modifier = Modifier.padding(horizontal = Spacing.xxxl),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
