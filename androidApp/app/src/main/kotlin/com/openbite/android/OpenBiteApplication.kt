package com.openbite.android

import android.app.Application
import android.util.Log
import com.openbite.feature.home.homeFeatureModule
import com.openbite.feature.map.mapFeatureModule
import com.openbite.feature.meal.mealFeatureModule
import com.openbite.feature.memories.memoriesFeatureModule
import com.openbite.feature.milestone.milestoneFeatureModule
import com.openbite.feature.order.orderFeatureModule
import com.openbite.feature.place.placeFeatureModule
import com.openbite.feature.scan.scanFeatureModule
import com.openbite.feature.search.searchFeatureModule
import com.openbite.feature.settings.settingsFeatureModule
import com.openbite.feature.share.shareFeatureModule
import com.openbite.android.platform.androidPlatformModule
import com.openbite.android.reminders.Notifications
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.domain.FirstLaunchSeedUseCase
import com.openbite.core.domain.di.sharedCoreModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.openbite.core.data.di.androidDataModule

class OpenBiteApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenBiteApplication)
            modules(
                androidDataModule(this@OpenBiteApplication),
                androidPlatformModule,
                sharedCoreModule,
                homeFeatureModule,
                memoriesFeatureModule,
                mealFeatureModule,
                mapFeatureModule,
                milestoneFeatureModule,
                orderFeatureModule,
                placeFeatureModule,
                scanFeatureModule,
                settingsFeatureModule,
                shareFeatureModule,
                searchFeatureModule,
            )
        }
        // 通知渠道随时可建；纪念日每天检查一次（已经排过就不重复排，开关在 Worker 里判断）。
        Notifications.createChannels(this)
        get<ReminderScheduler>().scheduleMilestoneCheck()
        // 清掉一小时前系统相机留下的临时照片（处理后的照片已存进 filesDir/photos）。
        appScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
            File(cacheDir, "scan").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
        // 只在第一次启动时自动导入种子数据；用户在设置里清空后不再自动导回来。
        // 页面订阅的是 Flow，导入完会自动刷新。
        // 标志存在 DataStore；老版本存在 SharedPreferences 的标志也认。
        appScope.launch {
            runCatching { get<FirstLaunchSeedUseCase>()() }
                .onFailure { Log.e("OpenBite", "Seed import failed", it) }
        }
    }
}
