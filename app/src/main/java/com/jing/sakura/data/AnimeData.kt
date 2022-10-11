package com.jing.sakura.data

data class AnimeData(
    val url: String,
    val title: String,
    val currentEpisode: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val tags: String = ""
)
