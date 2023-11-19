package com.jing.sakura.repo

import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine

interface AnimationSource {

    val sourceId: String

    val name: String

    suspend fun fetchHomePageData(): HomePageData

    suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData

    suspend fun searchAnimation(keyword: String, page: Int): AnimePageData

    suspend fun fetchVideoUrl(episodeId: String): Resource<VideoUrlResult>

    suspend fun fetchUpdateTimeline(): UpdateTimeLine

    suspend fun getVideoCategories(): List<VideoCategoryGroup> = emptyList()

    suspend fun queryByCategory(categories: List<NamedValue<String>>, page: Int): AnimePageData =
        throw UnsupportedOperationException()

    fun supportTimeline(): Boolean = true

    fun supportSearch(): Boolean = true

    fun supportSearchByCategory(): Boolean = false

    data class VideoUrlResult(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    )
}