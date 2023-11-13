package com.jing.sakura.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jing.sakura.repo.SakuraSource

@Database(
    entities = [VideoHistoryEntity::class, SearchHistoryEntity::class],
    version = 3,
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE `video_history_temp` (
                    `episodeId` TEXT NOT NULL, 
                    `sourceId` TEXT NOT NULL, 
                    `animeName` TEXT NOT NULL, 
                    `animeId` TEXT NOT NULL, 
                    `lastEpisodeName` TEXT NOT NULL, 
                    `updateTime` INTEGER NOT NULL, 
                    `lastPlayTime` INTEGER NOT NULL, 
                    `videoDuration` INTEGER NOT NULL, 
                    `coverUrl` TEXT NOT NULL, 
                    PRIMARY KEY(`episodeId`, `sourceId`)
                    )
                """.trimIndent()
                )
                database.execSQL(
                    """
                    insert into video_history_temp(`episodeId`,
                                                    `sourceId`,
                                                    `animeName`,
                                                    `animeId`,
                                                    `lastEpisodeName`,
                                                    `updateTime`,
                                                    `lastPlayTime`,
                                                    `videoDuration`,
                                                    `coverUrl`)
                    select `episodeId`,
                            ?,
                            `animeName`,
                            `animeId`,
                            `lastEpisodeName`,
                            `updateTime`,
                            `lastPlayTime`,
                            `videoDuration`,
                            `coverUrl`
                    from video_history
                """.trimIndent(), arrayOf(SakuraSource.SOURCE_ID)
                )
                database.execSQL("drop table video_history")
                database.execSQL("alter table video_history_temp rename to video_history")
            }

        }
    }
}