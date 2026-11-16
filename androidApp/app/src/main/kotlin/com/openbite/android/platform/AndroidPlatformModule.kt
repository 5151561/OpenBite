package com.openbite.android.platform

import android.content.Context
import androidx.room.RoomDatabase
import com.openbite.core.data.platform.SeedSource
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.openBiteDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 共享内核需要的 Android 实现。相机、定位、密钥、提醒在各自阶段加进来。 */
val androidPlatformModule = module {
    single<RoomDatabase.Builder<OpenBiteDatabase>> { openBiteDatabaseBuilder(androidContext()) }
    single<SeedSource> { AssetSeedSource(androidContext()) }
}

private class AssetSeedSource(private val context: Context) : SeedSource {
    override suspend fun read(): String = withContext(Dispatchers.IO) {
        context.assets.open("seed/seed.json").bufferedReader().use { it.readText() }
    }
}
