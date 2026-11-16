package com.openbite.android.feature.place

import com.openbite.feature.place.MenuTab
import com.openbite.feature.place.PlaceDetailViewModel
import com.openbite.feature.place.PlaceMenuViewModel
import com.openbite.feature.place.PlacesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val placeFeatureModule = module {
    viewModelOf(::PlacesViewModel)
    viewModel { (placeId: String) -> PlaceDetailViewModel(placeId, get(), get()) }
    viewModel { (placeId: String, tab: MenuTab) -> PlaceMenuViewModel(placeId, tab, get(), get()) }
}
