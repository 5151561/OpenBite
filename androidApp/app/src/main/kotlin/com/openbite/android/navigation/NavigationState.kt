package com.openbite.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * 每个 Tab 一条返回栈（Navigation 3 “multiple back stacks” 写法）。
 * 返回栈和当前 Tab 都能撑过配置变更和进程被杀。
 */
class NavigationState(
    val tabs: List<TopLevelDestination>,
    private val selectedIndex: () -> Int,
    private val setSelectedIndex: (Int) -> Unit,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val startTab: TopLevelDestination get() = tabs.first()

    val currentTab: TopLevelDestination get() = tabs[selectedIndex()]

    val currentStack: NavBackStack<NavKey> get() = backStacks.getValue(currentTab.key)

    /** 底部弹窗下面露出来的那一页。 */
    private val visiblePage: NavKey? get() = currentStack.lastOrNull { it !is ImportPlaceKey }

    /** 当前页是不是某个 Tab 的根页面；只有根页面显示底部导航。 */
    val isAtTabRoot: Boolean get() = visiblePage == currentTab.key

    /** 底部导航：Tab 根页面和地图（设计稿 06 带底部导航）显示，其余页面藏起来。 */
    val showsBottomBar: Boolean get() = isAtTabRoot || visiblePage is MapKey

    fun selectTab(tab: TopLevelDestination) = setSelectedIndex(tabs.indexOf(tab))

    /** 退出路径固定是“当前 Tab → 今天 → 退出 App”。 */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
        val inUse = if (currentTab == startTab) listOf(startTab) else listOf(startTab, currentTab)
        return inUse.flatMap { decorated[it.key].orEmpty() }
    }
}

@Composable
fun rememberNavigationState(tabs: List<TopLevelDestination>): NavigationState {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val backStacks = tabs.associate { it.key to rememberNavBackStack(it.key) }
    return remember(tabs, backStacks) {
        NavigationState(tabs, { selected }, { selected = it }, backStacks)
    }
}

/** 处理前进和返回，只改 [NavigationState]。 */
class Navigator(private val state: NavigationState) {
    fun selectTab(tab: TopLevelDestination) = state.selectTab(tab)

    /**
     * 打开 [key]。它已经在当前栈里时退回到那一页，不再压第二份：
     * 同一个 key 的两个 entry 会共用 ViewModel 和保存的状态（比如店铺页 → 地图 → 同一家店）。
     */
    fun navigate(key: NavKey) {
        val tab = state.tabs.firstOrNull { it.key == key }
        if (tab != null) return state.selectTab(tab)
        val stack = state.currentStack
        val existing = stack.lastIndexOf(key)
        if (existing >= 0) {
            while (stack.size > existing + 1) stack.removeLastOrNull()
        } else {
            stack.add(key)
        }
    }

    /** 走完一段流程（比如点完菜）：清掉当前 Tab 上面的页面，回到 [tab] 的根页面。 */
    fun finishTo(tab: TopLevelDestination) {
        val stack = state.currentStack
        while (stack.size > 1) stack.removeLastOrNull()
        state.selectTab(tab)
    }

    /** 通知点进来：切到 [tab]，清掉它上面的页面，再打开 [key]（可以没有）。 */
    fun openFresh(tab: TopLevelDestination, key: NavKey?) {
        state.selectTab(tab)
        val stack = state.currentStack
        while (stack.size > 1) stack.removeLastOrNull()
        key?.let { stack.add(it) }
    }

    /**
     * 页面自己要求返回（点返回按钮、保存完自动返回）：只有 [key] 还是当前栈顶时才弹。
     * 用户先用手势返回、保存结果晚到时，不会把下面一页也弹掉。
     */
    fun pop(key: NavKey) {
        if (state.currentStack.lastOrNull() == key) goBack()
    }

    /** 系统返回（手势、返回键）：弹当前栈顶；在 Tab 根页面时回到“今天”。 */
    fun goBack() {
        val stack = state.currentStack
        if (stack.lastOrNull() == state.currentTab.key) {
            state.selectTab(state.startTab)
        } else {
            stack.removeLastOrNull()
        }
    }
}
