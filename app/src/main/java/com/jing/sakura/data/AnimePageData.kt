package com.jing.sakura.data

data class AnimePageData(
    val page: Int,
    val hasNextPage: Boolean,
    val animeList: List<AnimeData>
)
