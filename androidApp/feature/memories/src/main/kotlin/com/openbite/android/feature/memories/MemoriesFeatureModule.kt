package com.openbite.android.feature.memories

import com.openbite.feature.memories.MemoriesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val memoriesFeatureModule = module {
    viewModelOf(::MemoriesViewModel)
}
