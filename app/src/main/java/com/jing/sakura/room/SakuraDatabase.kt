package com.jing.sakura.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VideoHistoryEntity::class], version = 1, exportSchema = false)
abstract class SakuraDatabase : RoomDatabase() {

    abstract fun getVideoHistoryDao(): VideoHistoryDao
}