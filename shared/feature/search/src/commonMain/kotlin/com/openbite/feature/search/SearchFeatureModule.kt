package com.openbite.feature.search

import org.koin.dsl.module

/**
 * search 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val searchFeatureModule = module {
    factory { (kind: SearchKind) -> SearchViewModel(kind, get(), get(), get()) }
}
