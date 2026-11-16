package com.openbite.core.domain.di

import com.openbite.core.data.di.dataModule
import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.LoadSampleDataUseCase
import com.openbite.core.domain.ObserveDiningLogsUseCase
import com.openbite.core.domain.ObserveMemoriesUseCase
import com.openbite.core.domain.ObserveMilestoneHighlightsUseCase
import com.openbite.core.domain.ObservePlaceDetailUseCase
import com.openbite.core.domain.ObservePlaceMenuUseCase
import com.openbite.core.domain.ObservePlacesUseCase
import com.openbite.core.domain.ObserveUpcomingMilestonesUseCase
import com.openbite.core.domain.SystemTodayProvider
import com.openbite.core.domain.TodayProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/** 共享内核的全部 Koin 模块（数据层 + 领域层）。平台模块另外提供。 */
val sharedCoreModule: Module = module {
    includes(dataModule)
    single<TodayProvider> { SystemTodayProvider() }
    factory { ObserveDiningLogsUseCase(get()) }
    factory { ObserveUpcomingMilestonesUseCase(get(), get()) }
    factory { ObserveMemoriesUseCase(get(), get()) }
    factory { ObserveMilestoneHighlightsUseCase(get(), get()) }
    factory { ObservePlacesUseCase(get(), get(), get()) }
    factory { ObservePlaceDetailUseCase(get(), get(), get(), get()) }
    factory { ObservePlaceMenuUseCase(get(), get()) }
    factory { LoadSampleDataUseCase(get()) }
    factory { ClearAllDataUseCase(get()) }
}
