package com.openbite.android.feature.search

import com.openbite.feature.search.SearchKind
import com.openbite.feature.search.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val searchFeatureModule = module {
    viewModel { (kind: SearchKind) -> SearchViewModel(kind, get(), get(), get()) }
}
