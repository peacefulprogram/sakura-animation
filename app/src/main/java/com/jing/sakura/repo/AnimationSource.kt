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

    fun fetchHomePageData(): HomePageData

    fun fetchDetailPage(animeId: String): AnimeDetailPageData

    fun searchAnimation(keyword: String, page: Int): SearchPageData

    fun fetchVideoUrl(episodeId: String): Resource<String>

    fun fetchUpdateTimeline(): UpdateTimeLine
}