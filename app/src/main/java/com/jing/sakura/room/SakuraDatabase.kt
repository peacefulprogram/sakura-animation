package com.jing.sakura.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VideoHistoryEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SakuraDatabase : RoomDatabase() {

    abstract fun getVideoHistoryDao(): VideoHistoryDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                create table if not exists `search_history` (
                    `keyword` text not null,
                    `searchTime` integer not null,
                    primary key(`keyword`)
                )
            """.trimIndent()
                )
            }

        }
    }
}