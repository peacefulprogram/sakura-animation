package com.jing.sakura.data

data class SearchPageData(
    val totalString: String,
    val page: Int,
    val hasNextPage: Boolean,
    val animeList: List<AnimeData>
)
