package com.openbite.core.data.di

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.DraftRepository
import com.openbite.core.data.MealRepository
import com.openbite.core.data.PlaceImportRepository
import com.openbite.core.data.RoomPlaceImportRepository
import com.openbite.core.data.RoomMealRepository
import com.openbite.core.data.MenuScanRepository
import com.openbite.core.data.RoomDraftRepository
import com.openbite.core.data.RoomDiningLogRepository
import com.openbite.core.data.RoomMenuScanRepository
import com.openbite.core.data.RoomMilestoneRepository
import com.openbite.core.data.RoomPlaceRepository
import com.openbite.core.data.RoomSampleDataRepository
import com.openbite.core.data.SampleDataRepository
import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.buildOpenBite
import com.openbite.core.network.VisionModelRegistry
import io.ktor.client.HttpClient
import androidx.room.RoomDatabase
import com.openbite.core.data.InMemoryOrderSessionRepository
import com.openbite.core.data.OrderSessionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 数据层的 Koin 模块。平台需要另外提供：
 * `RoomDatabase.Builder<OpenBiteDatabase>`、`SeedSource`、`HttpClient`（识别用，见 createVisionHttpClient），
 * 以及 ImageProcessor、LocationProvider、PhotoStore、SecretStore、PreferencesStore。
 */
val dataModule: Module = module {
    single<OpenBiteDatabase> { get<RoomDatabase.Builder<OpenBiteDatabase>>().buildOpenBite() }
    single<DiningLogRepository> { RoomDiningLogRepository(get()) }
    single<PlaceRepository> { RoomPlaceRepository(get()) }
    single<MilestoneRepository> { RoomMilestoneRepository(get()) }
    single { SeedImporter(get(), get()) }
    single<OrderSessionRepository> { InMemoryOrderSessionRepository() }
    single<SampleDataRepository> { RoomSampleDataRepository(get(), get()) }
    single<MenuScanRepository> { RoomMenuScanRepository(get()) }
    single<DraftRepository> { RoomDraftRepository(get()) }
    single<MealRepository> { RoomMealRepository(get()) }
    single<PlaceImportRepository> { RoomPlaceImportRepository(get()) }
    single<com.openbite.core.data.PlaceRemovalRepository> { com.openbite.core.data.RoomPlaceRemovalRepository(get()) }
    single { VisionModelRegistry(get<HttpClient>()) }
}
