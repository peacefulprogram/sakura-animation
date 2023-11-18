package com.jing.sakura.repo

import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.SearchPageData
import com.jing.sakura.data.UpdateTimeLine

interface AnimationSource {

    val sourceId: String

//    @get:DrawableRes
//    val icon: Int

    val name: String

    suspend fun fetchHomePageData(): HomePageData

    suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData

    suspend fun searchAnimation(keyword: String, page: Int): SearchPageData

    suspend fun fetchVideoUrl(episodeId: String): Resource<String>

    suspend fun fetchUpdateTimeline(): UpdateTimeLine

    fun supportTimeline(): Boolean = true

    fun supportSearch(): Boolean = true
}