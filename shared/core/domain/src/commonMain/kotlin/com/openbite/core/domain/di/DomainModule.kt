package com.openbite.core.domain.di

import com.openbite.core.data.di.dataModule
import com.openbite.core.domain.AddMealPhotoUseCase
import com.openbite.core.domain.AddPersonUseCase
import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.DiscardPhotosUseCase
import com.openbite.core.domain.MealFollowUpUseCase
import com.openbite.core.domain.MilestoneRemindersUseCase
import com.openbite.core.domain.ObserveMealUseCase
import com.openbite.core.domain.ObserveReminderSettingsUseCase
import com.openbite.core.domain.ReadReceiptUseCase
import com.openbite.core.domain.SaveMealUseCase
import com.openbite.core.domain.SaveReceiptUseCase
import com.openbite.core.domain.SetReminderSettingsUseCase
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
import com.openbite.core.domain.AppDispatchers
import com.openbite.core.domain.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/** 共享内核的全部 Koin 模块（数据层 + 领域层）。平台模块另外提供。 */
val sharedCoreModule: Module = module {
    includes(dataModule)
    single<TodayProvider> { SystemTodayProvider() }
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    factory { ObserveDiningLogsUseCase(get()) }
    factory { ObserveUpcomingMilestonesUseCase(get(), get()) }
    factory { ObserveMemoriesUseCase(get(), get()) }
    factory { ObserveMilestoneHighlightsUseCase(get(), get()) }
    factory { ObservePlacesUseCase(get(), get(), get()) }
    factory { ObservePlaceDetailUseCase(get(), get(), get(), get()) }
    factory { ObservePlaceMenuUseCase(get(), get()) }
    factory { LoadSampleDataUseCase(get()) }
    factory { ClearAllDataUseCase(get(), get(), get()) }
    single<IdGenerator> { UuidGenerator() }
    single<NowProvider> { SystemNowProvider() }
    factory { ScanMenuUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ObserveVisionSettingsUseCase(get(), get()) }
    factory { SetVisionModelUseCase(get()) }
    factory { SaveApiKeyUseCase(get()) }
    factory { ClearApiKeyUseCase(get()) }
    factory { RenamePlaceUseCase(get()) }
    single<TimeZoneProvider> { SystemTimeZoneProvider() }
    single { OrderSessions(get()) }
    factory { PlaceOrderUseCase(get(), get(), get(), get(), get()) }
    factory { ObserveMealUseCase(get(), get()) }
    factory { SaveMealUseCase(get(), get(), get()) }
    factory { AddPersonUseCase(get(), get()) }
    factory { AddMealPhotoUseCase(get(), get(), get(), get(), get()) }
    factory { DiscardPhotosUseCase(get(), get()) }
    factory { com.openbite.core.domain.DiscardCaptureUseCase(get(), get()) }
    factory { ReadReceiptUseCase(get(), get(), get()) }
    factory { SaveReceiptUseCase(get(), get(), get()) }
    factory { ObserveReminderSettingsUseCase(get()) }
    factory { SetReminderSettingsUseCase(get(), get()) }
    factory { MealFollowUpUseCase(get(), get()) }
    factory { MilestoneRemindersUseCase(get(), get()) }
    factory { com.openbite.core.domain.ObserveMapUseCase(get(), get(), get(), get(), get()) }
    factory { com.openbite.core.domain.SetPlaceLocationUseCase(get()) }
    factory { com.openbite.core.domain.AddPlaceTagUseCase(get()) }
    factory { com.openbite.core.domain.RemovePlaceTagUseCase(get()) }
    factory { com.openbite.core.domain.ObserveAllTagsUseCase(get()) }
    factory { com.openbite.core.domain.ExportPlaceUseCase(get(), get(), get()) }
    factory { com.openbite.core.domain.OpenSharedPlaceUseCase(get(), get()) }
    factory { com.openbite.core.domain.ImportPlaceUseCase(get(), get(), get(), get(), get()) }
    factory { com.openbite.core.domain.LoadMilestoneUseCase(get()) }
    factory { com.openbite.core.domain.ObservePersonsUseCase(get()) }
    factory { com.openbite.core.domain.TaggedMealCountUseCase(get()) }
    factory { com.openbite.core.domain.SaveMilestoneUseCase(get(), get(), get()) }
    factory { com.openbite.core.domain.DeleteMilestoneUseCase(get(), get()) }
    factory { com.openbite.core.domain.ObserveKeepLocationUseCase(get()) }
    factory { com.openbite.core.domain.SetKeepLocationUseCase(get()) }
    factory { com.openbite.core.domain.SearchMemoriesUseCase(get(), get()) }
    factory { com.openbite.core.domain.SearchPlacesUseCase(get(), get()) }
    factory { com.openbite.core.domain.UpdateDishUseCase(get()) }
    factory { com.openbite.core.domain.AssistantApi(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ObserveTasteProfileUseCase(get()) }
    factory { com.openbite.core.domain.ObserveAllMilestonesUseCase(get()) }
    factory { com.openbite.core.domain.ObservePlaceUseCase(get()) }
    factory { com.openbite.core.domain.ObserveSharePlaceUseCase(get()) }
    factory { SaveTasteProfileUseCase(get()) }
}
