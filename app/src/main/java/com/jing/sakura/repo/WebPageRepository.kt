package com.jing.sakura.repo

import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import okhttp3.OkHttpClient

class WebPageRepository(
    okHttpClient: OkHttpClient
) {

    val animationSources =
        listOf(
            MxdmSource(okHttpClient),
            ChangZhangSource(okHttpClient),
            SakuraSource(okHttpClient),
            AueteSource(okHttpClient),
            WedmSource(okHttpClient),
            QukanbaSource(okHttpClient),
        )

    private val animationSourceMap = animationSources.associateBy { it.sourceId }

    fun requireAnimationSource(sourceId: String): AnimationSource =
        animationSourceMap[sourceId] ?: throw RuntimeException("数据源不存在:$sourceId")

    suspend fun fetchHomePage(sourceId: String): HomePageData =
        requireAnimationSource(sourceId).fetchHomePageData()

    suspend fun fetchDetailPage(animeId: String, sourceId: String): AnimeDetailPageData =
        requireAnimationSource(sourceId).fetchDetailPage(animeId)

    suspend fun searchAnimation(keyword: String, page: Int, sourceId: String): AnimePageData =
        requireAnimationSource(sourceId).searchAnimation(keyword, page)


    suspend fun fetchVideoUrl(
        episodeId: String,
        sourceId: String,
        animeId: String
    ): Resource<AnimationSource.VideoUrlResult> =
        requireAnimationSource(sourceId).fetchVideoUrl(animeId, episodeId)

    suspend fun fetchUpdateTimeline(sourceId: String): UpdateTimeLine =
        requireAnimationSource(sourceId).fetchUpdateTimeline()


    suspend fun getVideoCategories(sourceId: String): List<VideoCategoryGroup> =
        requireAnimationSource(sourceId).getVideoCategories()

    suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int,
        sourceId: String
    ): AnimePageData =
        requireAnimationSource(sourceId).queryByCategory(categories = categories, page = page)
}