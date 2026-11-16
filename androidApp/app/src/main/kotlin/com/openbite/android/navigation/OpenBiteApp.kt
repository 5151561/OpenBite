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
import com.openbite.android.feature.order.OrderRoute
import com.openbite.android.feature.order.ReceiptRoute
import com.openbite.android.feature.place.PlaceDetailRoute
import com.openbite.android.feature.place.PlaceMenuRoute
import com.openbite.android.feature.place.PlacesRoute
import com.openbite.android.feature.scan.ScanRoute
import com.openbite.android.feature.settings.SettingsRoute
import com.openbite.android.feature.settings.TasteRoute

private val tabs = TopLevelDestination.entries.toList()

/** App 外壳：底部导航 + 每个 Tab 自己的返回栈。 */
@Composable
fun OpenBiteApp() {
    val state = rememberNavigationState(tabs)
    val navigator = remember(state) { Navigator(state) }

    // 以下回调的目标页面在 U4–U6 才做，现在什么都不做。
    val notYet: () -> Unit = {}

    val openPlace: (String) -> Unit = { navigator.navigate(PlaceKey(it)) }

    val entryProvider = entryProvider {
        entry<HomeKey> {
            HomeRoute(
                onOpenSettings = { navigator.navigate(SettingsKey) },
                onOpenMilestones = { navigator.navigate(MilestonesKey) },
                onOpenMemories = { navigator.navigate(MemoriesKey) },
                onScanMenu = { navigator.navigate(ScanKey()) },
                onPickPhoto = { navigator.navigate(ScanKey(fromGallery = true)) },
                // TODO(U4): 改成打开这条记录（补记忆 / 看记录）；在那之前先进店铺页。
                onOpenMemory = { _, placeId -> openPlace(placeId) },
            )
        }
        entry<MemoriesKey> {
            MemoriesRoute(
                onSearch = notYet, // TODO: 搜索记忆（设计稿未定）
                onScanMenu = { navigator.navigate(ScanKey()) },
                // TODO(U4): 改成打开这条记录；在那之前先进店铺页。
                onOpenMemory = { _, placeId -> openPlace(placeId) },
            )
        }
        entry<PlacesKey> {
            PlacesRoute(
                onOpenPlace = openPlace,
                onSearch = notYet, // TODO: 搜索店铺（设计稿未定）
                onOpenMap = notYet, // TODO(U5): 地图
                onScanMenu = { navigator.navigate(ScanKey()) },
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
                onOpenTaste = { navigator.navigate(TasteKey) },
            )
        }
        entry<ScanKey> { key ->
            ScanRoute(
                placeId = key.placeId,
                fromGallery = key.fromGallery,
                onBack = navigator::goBack,
                onOrder = { sessionId -> navigator.navigate(OrderKey(sessionId = sessionId)) },
                onOpenSettings = { navigator.navigate(SettingsKey) },
            )
        }
        entry<OrderKey> { key ->
            OrderRoute(
                sessionId = key.sessionId,
                placeId = key.placeId,
                onBack = navigator::goBack,
                onShowStaff = { navigator.navigate(ReceiptKey(it)) },
            )
        }
        entry<ReceiptKey> { key ->
            ReceiptRoute(
                sessionId = key.sessionId,
                onBack = navigator::goBack,
                // 草稿出现在“今天”的最近记忆里，补记忆（U4）从那里进。
                onPlaced = { navigator.finishTo(TopLevelDestination.TODAY) },
            )
        }
        entry<TasteKey> { TasteRoute(onBack = navigator::goBack) }
        entry<PlaceKey> { key ->
            PlaceDetailRoute(
                placeId = key.placeId,
                onBack = navigator::goBack,
                onOpenMenu = { navigator.navigate(PlaceMenuKey(key.placeId)) },
                onShare = notYet, // TODO(U6): 分享店铺
                onAddTag = notYet, // TODO(U5): 标签编辑
                onScanMenu = { navigator.navigate(ScanKey(placeId = key.placeId)) },
            )
        }
        entry<PlaceMenuKey> { key ->
            PlaceMenuRoute(
                placeId = key.placeId,
                showPhotos = key.showPhotos,
                onBack = navigator::goBack,
                onOrder = { navigator.navigate(OrderKey(placeId = key.placeId)) },
                onRescan = { navigator.navigate(ScanKey(placeId = key.placeId)) },
                onEditDish = notYet, // TODO(U4): 改译名或价格
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
