package com.jing.sakura.repo

data class VideoCategoryGroup(
    val name: String,
    val key: String,
    val defaultValue: String,
    val categories: List<VideoCategory>
)

data class VideoCategory(
    val label: String,
    val value: String
)