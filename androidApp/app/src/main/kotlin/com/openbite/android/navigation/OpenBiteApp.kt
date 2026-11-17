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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openbite.android.R
import com.openbite.android.feature.home.HomeRoute
import com.openbite.android.feature.map.MapRoute
import com.openbite.android.feature.map.PickLocationRoute
import com.openbite.android.feature.meal.MealRoute
import com.openbite.android.feature.meal.MemoryEditRoute
import com.openbite.android.feature.meal.ReceiptArchiveRoute
import com.openbite.android.feature.memories.MemoriesRoute
import com.openbite.android.feature.milestone.MilestoneEditRoute
import com.openbite.android.feature.milestone.MilestonesRoute
import com.openbite.android.feature.order.OrderRoute
import com.openbite.android.feature.order.ReceiptRoute
import com.openbite.android.feature.place.PlaceDetailRoute
import com.openbite.android.feature.place.PlaceMenuRoute
import com.openbite.android.feature.place.PlacesRoute
import com.openbite.android.feature.scan.ScanRoute
import com.openbite.android.feature.search.SearchRoute
import com.openbite.android.feature.settings.SettingsRoute
import com.openbite.android.feature.share.ImportSheetRoute
import com.openbite.android.feature.share.ShareMealRoute
import com.openbite.android.feature.share.SharePlaceRoute
import com.openbite.android.feature.settings.TasteRoute

private val tabs = TopLevelDestination.entries.toList()

/** App 外壳：底部导航 + 每个 Tab 自己的返回栈。 */
@Composable
fun OpenBiteApp(deepLink: DeepLink? = null, onDeepLinkHandled: () -> Unit = {}) {
    val state = rememberNavigationState(tabs)
    val navigator = remember(state) { Navigator(state) }

    // 从通知点进来：饭后提醒 -> 今天 Tab 上开补记忆；纪念日 -> 纪念日 Tab（有去年的记录就打开它）。
    // 正在导入的店铺文件；导入页盖在店铺列表上（设计稿 18）。
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLink) {
        when (deepLink) {
            is DeepLink.ImportPlace -> {
                navigator.openFresh(TopLevelDestination.PLACES, null)
                importUri = deepLink.uri
            }
            is DeepLink.EditMeal -> navigator.openFresh(TopLevelDestination.TODAY, MemoryEditKey(deepLink.logId))
            is DeepLink.Milestones -> navigator.openFresh(TopLevelDestination.MILESTONES, deepLink.logId?.let { MealKey(it) })
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }

    val openPlace: (String) -> Unit = { navigator.navigate(PlaceKey(it)) }

    /** 草稿直接进补记忆；已完成的先看只读页。 */
    val openMemory: (String, Boolean) -> Unit = { logId, isDraft ->
        navigator.navigate(if (isDraft) MemoryEditKey(logId) else MealKey(logId))
    }

    val entryProvider = entryProvider {
        entry<HomeKey> {
            HomeRoute(
                onOpenSettings = { navigator.navigate(SettingsKey) },
                onOpenMilestones = { navigator.navigate(MilestonesKey) },
                onOpenMemories = { navigator.navigate(MemoriesKey) },
                onScanMenu = { navigator.navigate(ScanKey()) },
                onPickPhoto = { navigator.navigate(ScanKey(fromGallery = true)) },
                onOpenMemory = openMemory,
            )
        }
        entry<MemoriesKey> {
            MemoriesRoute(
                onSearch = { navigator.navigate(SearchKey(places = false)) },
                onScanMenu = { navigator.navigate(ScanKey()) },
                onOpenMemory = openMemory,
            )
        }
        entry<PlacesKey> {
            PlacesRoute(
                onOpenPlace = openPlace,
                onSearch = { navigator.navigate(SearchKey(places = true)) },
                onOpenMap = { navigator.navigate(MapKey()) },
                onScanMenu = { navigator.navigate(ScanKey()) },
                onImportFile = { importUri = it },
            )
        }
        entry<MilestonesKey> {
            MilestonesRoute(
                onOpenPlace = openPlace,
                onEditMilestone = { navigator.navigate(MilestoneEditKey(it)) },
                onAddMilestone = { navigator.navigate(MilestoneEditKey()) },
            )
        }
        entry<SettingsKey> { key ->
            SettingsRoute(
                onBack = { navigator.pop(key) },
                onOpenTaste = { navigator.navigate(TasteKey) },
            )
        }
        entry<ScanKey> { key ->
            ScanRoute(
                placeId = key.placeId,
                fromGallery = key.fromGallery,
                onBack = { navigator.pop(key) },
                onOrder = { sessionId -> navigator.navigate(OrderKey(sessionId = sessionId)) },
                onOpenSettings = { navigator.navigate(SettingsKey) },
            )
        }
        entry<OrderKey> { key ->
            OrderRoute(
                sessionId = key.sessionId,
                placeId = key.placeId,
                onBack = { navigator.pop(key) },
                onShowStaff = { navigator.navigate(ReceiptKey(it)) },
            )
        }
        entry<ReceiptKey> { key ->
            ReceiptRoute(
                sessionId = key.sessionId,
                onBack = { navigator.pop(key) },
                // 草稿出现在“今天”的最近记忆里，点它进补记忆。
                onPlaced = { navigator.finishTo(TopLevelDestination.TODAY) },
            )
        }
        entry<TasteKey> { key -> TasteRoute(onBack = { navigator.pop(key) }) }
        entry<MemoryEditKey> { key ->
            MemoryEditRoute(
                logId = key.logId,
                onDone = { navigator.pop(key) },
                onOpenReceipt = { navigator.navigate(ReceiptArchiveKey(key.logId)) },
                onShare = { navigator.navigate(ShareMealKey(key.logId)) },
            )
        }
        entry<MealKey> { key ->
            MealRoute(
                logId = key.logId,
                onBack = { navigator.pop(key) },
                onEdit = { navigator.navigate(MemoryEditKey(key.logId)) },
                onShare = { navigator.navigate(ShareMealKey(key.logId)) },
            )
        }
        entry<SearchKey> { key ->
            SearchRoute(places = key.places, onBack = { navigator.pop(key) }, onOpenMemory = openMemory, onOpenPlace = openPlace)
        }
        entry<MilestoneEditKey> { key -> MilestoneEditRoute(milestoneId = key.milestoneId, onDone = { navigator.pop(key) }) }
        entry<ShareMealKey> { key -> ShareMealRoute(logId = key.logId, onBack = { navigator.pop(key) }) }
        entry<SharePlaceKey> { key -> SharePlaceRoute(placeId = key.placeId, onBack = { navigator.pop(key) }) }
        entry<MapKey> { key ->
            MapRoute(
                focusPlaceId = key.focusPlaceId,
                onBackToList = { navigator.pop(key) },
                onOpenPlace = openPlace,
                onOpenMeal = openMemory,
            )
        }
        entry<PickLocationKey> { key -> PickLocationRoute(placeId = key.placeId, onDone = { navigator.pop(key) }) }
        entry<ReceiptArchiveKey> { key -> ReceiptArchiveRoute(logId = key.logId, onBack = { navigator.pop(key) }) }
        entry<PlaceKey> { key ->
            PlaceDetailRoute(
                placeId = key.placeId,
                onBack = { navigator.pop(key) },
                onOpenMenu = { navigator.navigate(PlaceMenuKey(key.placeId)) },
                onShare = { navigator.navigate(SharePlaceKey(key.placeId)) },
                onScanMenu = { navigator.navigate(ScanKey(placeId = key.placeId)) },
                onOpenMeal = { logId, isDraft -> openMemory(logId, isDraft) },
                onOpenMap = { navigator.navigate(MapKey(focusPlaceId = key.placeId)) },
                onPickLocation = { navigator.navigate(PickLocationKey(key.placeId)) },
            )
        }
        entry<PlaceMenuKey> { key ->
            PlaceMenuRoute(
                placeId = key.placeId,
                showPhotos = key.showPhotos,
                onBack = { navigator.pop(key) },
                onOrder = { navigator.navigate(OrderKey(placeId = key.placeId)) },
                onRescan = { navigator.navigate(ScanKey(placeId = key.placeId)) },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (state.showsBottomBar) {
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

    importUri?.let { uri ->
        ImportSheetRoute(
            uri = uri,
            onDismiss = { importUri = null },
            onImported = { placeId ->
                importUri = null
                navigator.openFresh(TopLevelDestination.PLACES, PlaceKey(placeId))
            },
        )
    }
}
