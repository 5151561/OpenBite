package com.openbite.feature.settings

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * settings 的 ViewModel。Android 和 iOS 共用，App 启动时在 startKoin 里引入。
 * 用 factory 注册：Compose 的 koinViewModel() 按类型取，实例由 NavEntry 的 ViewModelStore 持有。
 */
val settingsFeatureModule = module {
    factoryOf(::SettingsViewModel)
    factoryOf(::TasteViewModel)
    factoryOf(::AiProvidersViewModel)
    factory { params -> ProviderDetailViewModel(params.get<com.openbite.core.model.AiProvider>(), get(), get(), get(), get(), get()) }
}
