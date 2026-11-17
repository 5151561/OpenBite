package com.openbite.android.feature.scan

import com.openbite.feature.scan.ScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val scanFeatureModule = module {
    viewModel { params -> ScanViewModel(params.getOrNull<String>(), get(), get(), get(), get()) }
}
