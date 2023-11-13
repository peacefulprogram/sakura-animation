package com.jing.sakura.repo

import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.SearchPageData
import com.jing.sakura.data.UpdateTimeLine
import okhttp3.OkHttpClient

class WebPageRepository(
    okHttpClient: OkHttpClient
) {

    val animationSources = listOf(SakuraSource(okHttpClient), MxdmSource(okHttpClient))

    val animationSourceMap = animationSources.associateBy { it.sourceId }

    fun requireAnimationSource(sourceId: String): AnimationSource =
        animationSourceMap[sourceId] ?: throw RuntimeException("数据源不存在:$sourceId")

    fun fetchHomePage(sourceId: String): HomePageData =
        requireAnimationSource(sourceId).fetchHomePageData()

    fun fetchDetailPage(animeId: String, sourceId: String): AnimeDetailPageData =
        requireAnimationSource(sourceId).fetchDetailPage(animeId)

    fun searchAnimation(keyword: String, page: Int, sourceId: String): SearchPageData =
        requireAnimationSource(sourceId).searchAnimation(keyword, page)


    fun fetchVideoUrl(episodeId: String, sourceId: String): Resource<String> =
        requireAnimationSource(sourceId).fetchVideoUrl(episodeId)

    fun fetchUpdateTimeline(sourceId: String): UpdateTimeLine =
        requireAnimationSource(sourceId).fetchUpdateTimeline()
}