package com.jing.sakura.data

data class SearchPageData(
    val page: Int,
    val hasNextPage: Boolean,
    val animeList: List<AnimeData>
)
