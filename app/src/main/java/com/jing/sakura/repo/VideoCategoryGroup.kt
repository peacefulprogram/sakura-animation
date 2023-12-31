package com.jing.sakura.repo

import com.jing.sakura.data.NamedValue


sealed abstract class VideoCategoryGroup(
    open val name: String,
    open val key: String,
) {

    class NormalCategoryGroup(
        name: String,
        key: String,
        val defaultValue: String,
        val categories: List<VideoCategory>
    ) : VideoCategoryGroup(name, key)

    class DynamicCategoryGroup(
        name: String,
        key: String,
        val dependsOnKey: List<String>,
        val categoriesProvider: (List<NamedValue<String>>) -> NormalCategoryGroup
    ) : VideoCategoryGroup(name, key)
}

data class VideoCategoryValue(
    val key: String,
    val value: String
)

data class VideoCategory(
    val label: String,
    val value: String
)