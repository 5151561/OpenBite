package com.openbite.android.feature.meal

import com.openbite.feature.meal.MealViewModel
import com.openbite.feature.meal.MemoryEditViewModel
import com.openbite.feature.meal.ReceiptArchiveViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mealFeatureModule = module {
    viewModel { (logId: String) -> MealViewModel(logId, get(), get()) }
    viewModel { (logId: String) -> MemoryEditViewModel(logId, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (logId: String) -> ReceiptArchiveViewModel(logId, get(), get(), get(), get(), get()) }
}
