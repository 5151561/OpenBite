package com.openbite.android.feature.milestone

import com.openbite.feature.milestone.MilestonesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val milestoneFeatureModule = module {
    viewModelOf(::MilestonesViewModel)
}
