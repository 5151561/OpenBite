package com.openbite.core.data.di

import android.content.Context
import androidx.room.RoomDatabase
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.openBiteDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android 上的数据库文件位置。App 只引入这个模块，不用直接依赖 :shared:core:database。 */
fun androidDataModule(context: Context): Module = module {
    single<RoomDatabase.Builder<OpenBiteDatabase>> { openBiteDatabaseBuilder(context.applicationContext) }
}
