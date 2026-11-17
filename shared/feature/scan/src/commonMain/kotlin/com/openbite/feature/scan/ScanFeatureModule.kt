package com.openbite.feature.scan

import org.koin.dsl.module

/**
 * scan 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val scanFeatureModule = module {
    factory { params -> ScanViewModel(params.getOrNull<String>(), get(), get(), get(), get()) }
}
