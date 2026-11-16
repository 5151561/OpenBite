package com.openbite.core.domain.di

import com.openbite.core.data.di.dataModule
import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.OrderSessions
import com.openbite.core.domain.PlaceOrderUseCase
import com.openbite.core.domain.SaveTasteProfileUseCase
import com.openbite.core.domain.SystemTimeZoneProvider
import com.openbite.core.domain.TimeZoneProvider
import com.openbite.core.domain.ClearApiKeyUseCase
import com.openbite.core.domain.IdGenerator
import com.openbite.core.domain.NowProvider
import com.openbite.core.domain.ObserveVisionSettingsUseCase
import com.openbite.core.domain.RenamePlaceUseCase
import com.openbite.core.domain.SaveApiKeyUseCase
import com.openbite.core.domain.ScanMenuUseCase
import com.openbite.core.domain.SetVisionModelUseCase
import com.openbite.core.domain.SystemNowProvider
import com.openbite.core.domain.UuidGenerator
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
    factory { ClearAllDataUseCase(get(), get()) }
    single<IdGenerator> { UuidGenerator() }
    single<NowProvider> { SystemNowProvider() }
    factory { ScanMenuUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ObserveVisionSettingsUseCase(get(), get()) }
    factory { SetVisionModelUseCase(get()) }
    factory { SaveApiKeyUseCase(get()) }
    factory { ClearApiKeyUseCase(get()) }
    factory { RenamePlaceUseCase(get()) }
    single<TimeZoneProvider> { SystemTimeZoneProvider() }
    single { OrderSessions(get()) }
    factory { PlaceOrderUseCase(get(), get(), get(), get()) }
    factory { ObserveTasteProfileUseCase(get()) }
    factory { SaveTasteProfileUseCase(get()) }
}
