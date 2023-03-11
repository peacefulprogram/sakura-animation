package com.jing.sakura.data


data class AnimePlayListEpisode(
    val episode: String,
    val url: String,
    val episodeIndex: Int
) : java.io.Serializable {
    val episodeId by lazy {
        url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))
    }
}