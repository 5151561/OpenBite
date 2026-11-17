package com.openbite.core.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * ViewModel 测试：viewModelScope 跑在 Dispatchers.Main 上，这里把 Main 换成测试调度器
 * （StandardTestDispatcher，协程要 runCurrent / advanceUntilIdle 才往前走，方便卡在中间状态）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}

/** 一直订阅 [flow]（WhileSubscribed 的 stateIn 才会开始算），测试结束自动取消。 */
fun <T> TestScope.keepCollecting(flow: StateFlow<T>) {
    backgroundScope.launch { flow.collect {} }
}

/** 模拟页面出栈：ViewModelStore 清掉时会调 onCleared。 */
fun ViewModel.clearForTest() {
    ViewModelStore().apply { put("vm", this@clearForTest) }.clear()
}

/** 应用级作用域：直接用 TestScope，advanceUntilIdle 会把里面的活（比如删照片）跑完。 */
val TestScope.appScope get() = testAppScope(this as CoroutineScope)

val kyotoPlace = Place("place-minato", "割烹 みなと", 35.0, 135.77, "京都市東山区")

/** 一条用餐记录，默认是草稿、两道菜。 */
fun diningLog(
    id: String = "log-1",
    place: Place = kyotoPlace,
    status: DiningStatus = DiningStatus.DRAFT,
    milestoneId: String? = null,
    items: List<DiningLogItem> = listOf(
        DiningLogItem("$id-i1", "dish-uni", "うに茶碗蒸し", Money(2800, "JPY"), 1, null, null, "海胆茶碗蒸"),
        DiningLogItem("$id-i2", "dish-ale", "生ビール", Money(550, "JPY"), 2, null, null, "生啤"),
    ),
    date: LocalDate = LocalDate(2026, 11, 17),
) = DiningLog(
    id = id,
    place = place,
    visitedAt = 1_794_913_200_000,
    timezone = "Asia/Tokyo",
    localDate = date,
    status = status,
    milestoneId = milestoneId,
    note = null,
    source = LogSource.USER,
    items = items,
)
