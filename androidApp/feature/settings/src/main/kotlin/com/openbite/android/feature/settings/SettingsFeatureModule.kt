package com.openbite.android.feature.settings

import com.openbite.feature.settings.SettingsViewModel
import com.openbite.feature.settings.TasteViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsFeatureModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::TasteViewModel)
}
