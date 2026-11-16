package com.openbite.android

import android.app.Application
import android.util.Log
import com.openbite.android.feature.journal.journalFeatureModule
import com.openbite.android.platform.androidPlatformModule
import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.domain.di.sharedCoreModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OpenBiteApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenBiteApplication)
            modules(androidPlatformModule, sharedCoreModule, journalFeatureModule)
        }
        // 首次启动导入种子数据；页面订阅的是 Flow，导入完会自动刷新。
        appScope.launch {
            runCatching { get<SeedImporter>().importIfEmpty() }
                .onFailure { Log.e("OpenBite", "Seed import failed", it) }
        }
    }
}
