package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayListEpisode

data class EpisodeUrlAndHistory(
    val videoUrl: String,
    val videoDuration: Long,
    val lastPlayPosition: Long,
    val headers: Map<String, String> = emptyMap(),
    val episode: AnimePlayListEpisode
)
