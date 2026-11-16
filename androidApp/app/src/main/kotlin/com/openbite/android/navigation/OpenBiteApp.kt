package com.openbite.android.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openbite.android.R
import com.openbite.android.feature.home.HomeRoute
import com.openbite.android.feature.memories.MemoriesRoute
import com.openbite.android.feature.milestone.MilestonesRoute
import com.openbite.android.feature.place.PlaceDetailRoute
import com.openbite.android.feature.place.PlaceMenuRoute
import com.openbite.android.feature.place.PlacesRoute
import com.openbite.android.feature.settings.SettingsRoute

private val tabs = TopLevelDestination.entries.toList()

/** App 外壳：底部导航 + 每个 Tab 自己的返回栈。 */
@Composable
fun OpenBiteApp() {
    val state = rememberNavigationState(tabs)
    val navigator = remember(state) { Navigator(state) }

    // 以下回调的目标页面在 U3–U6 才做，现在什么都不做。
    val notYet: () -> Unit = {}

    val openPlace: (String) -> Unit = { navigator.navigate(PlaceKey(it)) }

    val entryProvider = entryProvider {
        entry<HomeKey> {
            HomeRoute(
                onOpenSettings = { navigator.navigate(SettingsKey) },
                onOpenMilestones = { navigator.navigate(MilestonesKey) },
                onOpenMemories = { navigator.navigate(MemoriesKey) },
                onScanMenu = notYet, // TODO(U3): 识别菜单
                onPickPhoto = notYet, // TODO(U3): 相册选图后识别
                // TODO(U4): 改成打开这条记录（补记忆 / 看记录）；在那之前先进店铺页。
                onOpenMemory = { _, placeId -> openPlace(placeId) },
            )
        }
        entry<MemoriesKey> {
            MemoriesRoute(
                onSearch = notYet, // TODO: 搜索记忆（设计稿未定）
                onScanMenu = notYet, // TODO(U3)
                // TODO(U4): 改成打开这条记录；在那之前先进店铺页。
                onOpenMemory = { _, placeId -> openPlace(placeId) },
            )
        }
        entry<PlacesKey> {
            PlacesRoute(
                onOpenPlace = openPlace,
                onSearch = notYet, // TODO: 搜索店铺（设计稿未定）
                onOpenMap = notYet, // TODO(U5): 地图
                onScanMenu = notYet, // TODO(U3)
            )
        }
        entry<MilestonesKey> {
            MilestonesRoute(
                onOpenPlace = openPlace,
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
        entry<PlaceKey> { key ->
            PlaceDetailRoute(
                placeId = key.placeId,
                onBack = navigator::goBack,
                onOpenMenu = { navigator.navigate(PlaceMenuKey(key.placeId)) },
                onShare = notYet, // TODO(U6): 分享店铺
                onAddTag = notYet, // TODO(U5): 标签编辑
                onScanMenu = notYet, // TODO(U3)
            )
        }
        entry<PlaceMenuKey> { key ->
            PlaceMenuRoute(
                placeId = key.placeId,
                showPhotos = key.showPhotos,
                onBack = navigator::goBack,
                onOrder = notYet, // TODO(U3): 用这份菜单点单
                onRescan = notYet, // TODO(U3): 补拍一页
                onEditDish = notYet, // TODO(U3): 改译名或价格
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
