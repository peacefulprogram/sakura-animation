package com.jing.sakura.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_history")
data class VideoHistoryEntity(
    @PrimaryKey val episodeId: String,
    val animeName: String,
    val animeId: String,
    val lastEpisodeName: String,
    val updateTime: Long,
    val lastPlayTime: Long = 0L,
    val videoDuration: Long = 0L,
    val coverUrl: String = "",
)