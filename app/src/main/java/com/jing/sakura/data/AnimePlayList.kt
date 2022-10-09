package com.jing.sakura.data

data class AnimePlayList(
    val name:String,
    val episodeList:List<AnimePlayListEpisode>,
    val defaultPlayList: Boolean = false
)
