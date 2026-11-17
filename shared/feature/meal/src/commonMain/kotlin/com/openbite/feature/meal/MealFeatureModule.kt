package com.openbite.feature.meal

import org.koin.dsl.module

/**
 * meal 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val mealFeatureModule = module {
    factory { (logId: String) -> MealViewModel(logId, get(), get()) }
    factory { (logId: String) -> MemoryEditViewModel(logId, get(), get(), get(), get(), get(), get(), get()) }
    factory { (logId: String) -> ReceiptArchiveViewModel(logId, get(), get(), get(), get(), get()) }
}
