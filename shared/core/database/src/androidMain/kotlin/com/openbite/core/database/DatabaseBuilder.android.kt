package com.openbite.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun openBiteDatabaseBuilder(context: Context): RoomDatabase.Builder<OpenBiteDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<OpenBiteDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_NAME).absolutePath,
    )
}
