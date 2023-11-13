package com.jing.sakura.data


data class AnimePlayListEpisode(
    val episode: String,
    val url: String,
    val episodeIndex: Int,
    val episodeId: String
) : java.io.Serializable