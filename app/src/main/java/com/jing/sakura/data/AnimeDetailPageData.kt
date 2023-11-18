package com.jing.sakura.data

data class AnimeDetailPageData(
    val animeId: String,
    val animeName: String,
    val description: String,
    val imageUrl: String,
    val defaultPlayListIndex: Int = 0,
    val playLists: List<AnimePlayList>,
    val otherAnimeList: List<AnimeData> = listOf(),
    val infoList: List<String> = emptyList(),
    val lastPlayEpisodePosition: Pair<Int, Int> = Pair(0, 0)
)