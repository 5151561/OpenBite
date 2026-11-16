package com.openbite.android

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import com.openbite.android.feature.home.homeFeatureModule
import com.openbite.android.feature.memories.memoriesFeatureModule
import com.openbite.android.feature.milestone.milestoneFeatureModule
import com.openbite.android.feature.place.placeFeatureModule
import com.openbite.android.feature.scan.scanFeatureModule
import com.openbite.android.feature.settings.settingsFeatureModule
import com.openbite.android.platform.androidPlatformModule
import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.domain.di.sharedCoreModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OpenBiteApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenBiteApplication)
            modules(
                androidPlatformModule,
                sharedCoreModule,
                homeFeatureModule,
                memoriesFeatureModule,
                milestoneFeatureModule,
                placeFeatureModule,
                scanFeatureModule,
                settingsFeatureModule,
            )
        }
        // 清掉一小时前系统相机留下的临时照片（处理后的照片已存进 filesDir/photos）。
        appScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
            File(cacheDir, "scan").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
        // 只在第一次启动时自动导入种子数据；用户在设置里清空后不再自动导回来。
        // 页面订阅的是 Flow，导入完会自动刷新。
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEED_DONE, false)) {
            appScope.launch {
                runCatching { get<SeedImporter>().importIfEmpty() }
                    .onSuccess { prefs.edit { putBoolean(KEY_SEED_DONE, true) } }
                    .onFailure { Log.e("OpenBite", "Seed import failed", it) }
            }
        }
    }

    private companion object {
        const val PREFS = "openbite_app"
        const val KEY_SEED_DONE = "seed_auto_import_done"
    }
}
