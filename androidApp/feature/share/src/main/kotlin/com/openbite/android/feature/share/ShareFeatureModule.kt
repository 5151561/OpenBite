package com.openbite.android.feature.share

import com.openbite.feature.share.ImportViewModel
import com.openbite.feature.share.ShareMealViewModel
import com.openbite.feature.share.SharePlaceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shareFeatureModule = module {
    viewModel { (logId: String) -> ShareMealViewModel(logId, get()) }
    viewModel { (placeId: String) -> SharePlaceViewModel(placeId, get(), get()) }
    viewModel { (uri: String) -> ImportViewModel(uri, get(), get()) }
}
