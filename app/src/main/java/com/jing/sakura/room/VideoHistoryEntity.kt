package com.jing.sakura.room

import androidx.room.Entity

@Entity(tableName = "video_history", primaryKeys = ["animeId", "episodeId", "sourceId"])
data class VideoHistoryEntity(
    val episodeId: String,
    val sourceId: String,
    val animeName: String,
    val animeId: String,
    val lastEpisodeName: String,
    val updateTime: Long,
    val lastPlayTime: Long = 0L,
    val videoDuration: Long = 0L,
    val coverUrl: String = "",
)