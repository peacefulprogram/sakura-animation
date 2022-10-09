package com.jing.sakura.data

import com.jing.sakura.data.AnimeData

data class HomePageData(
    val timeLineList: List<NamedValue<List<AnimeData>>>,
    val seriesList: List<NamedValue<List<AnimeData>>>
)