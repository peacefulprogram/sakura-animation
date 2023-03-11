package com.jing.sakura.data

import com.jing.sakura.room.VideoHistoryEntity

data class AnimeDetailPageData(
    val animeId: String,
    val animeName: String,
    val releaseDay: String,
    val region: NamedValue<String>,
    val tags: List<NamedValue<String>>,
    val description: String,
    val imageUrl: String,
    val defaultPlayListIndex: Int = 0,
    val playLists: List<AnimePlayList>,
    val animeAlias: String = "",
    val otherAnimeList: List<AnimeData> = listOf(),
    val latestEpisode: String = "",
    var videoHistory: VideoHistoryEntity? = null
)