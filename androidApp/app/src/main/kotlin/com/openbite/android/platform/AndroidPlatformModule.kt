package com.openbite.android.platform

import android.content.Context
import androidx.room.RoomDatabase
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.IncomingFileSource
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.data.platform.TempFiles
import com.openbite.android.reminders.WorkManagerReminderScheduler
import com.openbite.core.data.platform.SeedSource
import com.openbite.core.network.createVisionHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.openBiteDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 共享内核需要的 Android 实现。 */
val androidPlatformModule = module {
    single<RoomDatabase.Builder<OpenBiteDatabase>> { openBiteDatabaseBuilder(androidContext()) }
    single<SeedSource> { AssetSeedSource(androidContext()) }
    single<HttpClient> { createVisionHttpClient(OkHttp.create()) }
    single<ImageProcessor> { AndroidImageProcessor(androidContext()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single<PhotoStore> { FilePhotoStore(androidContext()) }
    single<SecretStore> { KeystoreSecretStore(androidContext()) }
    single<PreferencesStore> { DataStorePreferencesStore(androidContext()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }
    single<IncomingFileSource> { ContentResolverFileSource(androidContext()) }
    single<TempFiles> { CacheTempFiles(androidContext()) }
}

private class AssetSeedSource(private val context: Context) : SeedSource {
    override suspend fun read(): String = withContext(Dispatchers.IO) {
        context.assets.open("seed/seed.json").bufferedReader().use { it.readText() }
    }
}
