package com.jing.sakura.data


data class UpdateTimeLine(
    val current: Int,
    val timeline: List<Pair<String, List<AnimeData>>>
)