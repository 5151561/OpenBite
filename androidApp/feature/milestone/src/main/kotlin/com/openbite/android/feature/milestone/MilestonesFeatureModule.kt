package com.openbite.android.feature.milestone

import com.openbite.feature.milestone.MilestonesViewModel
import com.openbite.feature.milestone.MilestoneEditViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val milestoneFeatureModule = module {
    viewModelOf(::MilestonesViewModel)
    viewModel { params -> MilestoneEditViewModel(params.getOrNull<String>(), get(), get(), get(), get(), get(), get()) }
}
