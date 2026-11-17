package com.openbite.feature.place

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * place 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val placeFeatureModule = module {
    factoryOf(::PlacesViewModel)
    factory { (placeId: String) -> PlaceDetailViewModel(placeId, get(), get(), get(), get(), get()) }
    factory { (placeId: String, tab: MenuTab) -> PlaceMenuViewModel(placeId, tab, get(), get(), get()) }
}
