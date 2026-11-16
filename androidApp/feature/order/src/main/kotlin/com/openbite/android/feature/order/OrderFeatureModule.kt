package com.openbite.android.feature.order

import com.openbite.feature.order.OrderViewModel
import com.openbite.feature.order.ReceiptViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val orderFeatureModule = module {
    // sessionId 和 placeId 都可能为 null，所以按位置取，不用解构。
    viewModel { params ->
        OrderViewModel(params.values.getOrNull(0) as String?, params.values.getOrNull(1) as String?, get(), get(), get())
    }
    viewModel { (sessionId: String) -> ReceiptViewModel(sessionId, get(), get(), get()) }
}
