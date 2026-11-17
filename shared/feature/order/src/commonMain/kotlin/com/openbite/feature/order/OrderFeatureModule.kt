package com.openbite.feature.order

import org.koin.dsl.module

/**
 * order 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val orderFeatureModule = module {
    // sessionId 和 placeId 都可能为 null，包成一个参数对象传，不靠位置和强转区分。
    factory { (args: OrderArgs) -> OrderViewModel(args.sessionId, args.placeId, get(), get(), get()) }
    factory { (sessionId: String) -> ReceiptViewModel(sessionId, get(), get(), get()) }
}
