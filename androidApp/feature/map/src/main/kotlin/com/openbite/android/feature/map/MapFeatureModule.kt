package com.openbite.android.feature.map

import com.openbite.feature.map.MapViewModel
import com.openbite.feature.map.PickLocationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapFeatureModule = module {
    viewModel { params -> MapViewModel(params.getOrNull<String>(), get()) }
    viewModel { (placeId: String) -> PickLocationViewModel(placeId, get(), get(), get()) }
}
