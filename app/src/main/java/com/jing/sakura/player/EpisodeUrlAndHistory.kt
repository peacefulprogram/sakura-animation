package com.jing.sakura.player

data class EpisodeUrlAndHistory(
    val videoUrl: String,
    val videoDuration: Long,
    val lastPlayPosition: Long,
    val headers: Map<String, String> = emptyMap()
)
