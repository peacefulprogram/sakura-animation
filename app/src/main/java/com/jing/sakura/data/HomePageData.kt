package com.jing.sakura.data

data class HomePageData(
    val sourceId: String,
    val seriesList: List<NamedValue<List<AnimeData>>>
)