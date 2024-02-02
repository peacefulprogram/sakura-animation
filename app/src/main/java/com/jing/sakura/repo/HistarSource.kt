package com.jing.sakura.repo

import androidx.core.text.isDigitsOnly
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.jsonBody
import com.jing.sakura.extend.newGetRequest
import com.jing.sakura.extend.newRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class HistarSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "histar"
    override val name: String
        get() = "星视界"
    override val pageSize: Int
        get() = 48

    @Volatile
    private var categoryGroups: List<VideoCategoryGroup>? = null

    private val categoryLock = Mutex()

    override suspend fun fetchHomePageData(): HomePageData {
        return coroutineScope {
            val series = (1..6).map { page ->
                async {
                    okHttpClient.newGetRequest { url("$BASE_URL/v3/api/dianbo/pages/$page") }
                        .jsonBody<HiStarResponse<HiStarHomePageData>>()
                        .data
                        ?.cards
                        ?.asSequence()
                        ?.filter {
                            it.card?.isNotEmpty() == true
                        }
                        ?.map {
                            val videos = it.card!!.map { card ->
                                AnimeData(
                                    id = card.id.toString(),
                                    url = "",
                                    title = card.name,
                                    imageUrl = card.img,
                                    tags = card.labels.joinToString(" "),
                                    sourceId = sourceId,
                                    currentEpisode = card.countStr
                                )
                            }
                            NamedValue(it.name, videos)
                        }
                        ?.toList()
                        ?: emptyList()
                }
            }.awaitAll()
            HomePageData(sourceId = sourceId, seriesList = series.flatten())
        }
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        return coroutineScope {
            val recommend = async {
                okHttpClient.newGetRequest {
                    url("$BASE_URL/v3/api/dianbo/recommend/$animeId")
                }.jsonBody<HiStarResponse<HiStarVideoDetailRecommend>>()
                    .data
                    ?.cards
                    ?.map {
                        AnimeData(
                            id = it.id.toString(),
                            url = "",
                            title = it.name,
                            imageUrl = it.img,
                            sourceId = sourceId
                        )
                    }
                    ?: emptyList()
            }
            val detail = fetchDetail(animeId)
            AnimeDetailPageData(
                animeId = animeId,
                animeName = detail.name,
                description = detail.desc,
                imageUrl = detail.picurl,
                infoList = sequenceOf(
                    "演员: " to detail.actor,
                    "地区: " to detail.country,
                    "年份: " to detail.time
                ).filter {
                    it.second.isNotEmpty()
                }
                    .map { it.first + it.second }
                    .toList(),
                playLists = listOf(
                    AnimePlayList(
                        "播放列表",
                        episodeList = detail.videos.map {
                            AnimePlayListEpisode(
                                episodeId = it.vid.toString(),
                                episode = it.displayEpName
                            )
                        })
                ),
                otherAnimeList = recommend.await()
            )
        }
    }

    private suspend fun fetchDetail(animeId: String): HiStarVideoDetail {
        return okHttpClient.newGetRequest {
            url("$BASE_URL/v3/api/dianbo/collection/$animeId")
        }.jsonBody<HiStarResponse<HiStarVideoDetail>>()
            .data ?: throw RuntimeException("未获取到视频详情")
    }

    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val url = fetchDetail(animeId).videos.find {
            it.vid.toString() == episodeId
        }?.purl ?: throw RuntimeException("未获取到视频链接")
        return Resource.Success(AnimationSource.VideoUrlResult(url = url))
    }

    override fun supportSearch(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val list = okHttpClient.newGetRequest {
            url("$BASE_URL/v3/api/home/searchWord?word=${keyword.encodeUrl()}")
        }.jsonBody<HiStarResponse<HiStarResponse<List<HiStarSearchResult>>>>()
            .data
            ?.data
            ?.asSequence()
            ?.filter {
                it.type != 0
            }
            ?.map {
                AnimeData(
                    id = it.id.toString(),
                    url = "",
                    title = it.name,
                    imageUrl = it.img,
                    sourceId = sourceId,
                    currentEpisode = it.countStr
                )
            }
            ?.toList()
            ?: emptyList()
        return AnimePageData(page = page, hasNextPage = false, animeList = list)
    }

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        if (categoryGroups == null) {
            categoryLock.withLock {
                if (categoryGroups == null) {
                    categoryGroups = buildCategoryGroups()
                }
            }
        }
        return categoryGroups!!
    }

    private suspend fun buildCategoryGroups(): List<VideoCategoryGroup> {
        val channels = listOf("电影", "电视剧", "动漫", "纪录片", "综艺")
        val map = coroutineScope {
            channels
                .map { chName ->
                    async {
                        chName to okHttpClient.newGetRequest {
                            url("$BASE_URL/v3/api/discover/getFilterCondition?chName=${chName.encodeUrl()}")
                        }.jsonBody<HiStarResponse<HiStarFilterCondition>>()
                            .data!!
                    }
                }.awaitAll()
                .toMap()
        }
        val result = mutableListOf<VideoCategoryGroup>()
        result.add(
            VideoCategoryGroup.NormalCategoryGroup(
                name = "频道",
                key = "chName",
                defaultValue = "电影",
                categories = channels.map { VideoCategory(label = it, value = it) })
        )
        listOf(
            HiStarFilterCondition::label to "分类",
            HiStarFilterCondition::country to "地区",
            HiStarFilterCondition::time to "时间",
            HiStarFilterCondition::status to "状态",
            HiStarFilterCondition::sort to "排序",
        ).forEach { (prop, name) ->
            result.add(VideoCategoryGroup.DynamicCategoryGroup(
                name = name,
                key = prop.name,
                dependsOnKey = listOf("chName"),
                categoriesProvider = { chName ->
                    val conditions = prop.get(map[chName[0].value]!!)
                    VideoCategoryGroup.NormalCategoryGroup(
                        name = name,
                        key = prop.name,
                        defaultValue = conditions.firstOrNull() ?: "",
                        categories = conditions.map { VideoCategory(label = it, value = it) }
                    )
                }
            ))
        }
        return result
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val valueMap = categories.associate { it.name to it.value }
        val param = HiStarFilterRequestData(
            chName = valueMap["chName"] ?: "电影",
            label = valueMap["label"] ?: "全部",
            country = valueMap["country"] ?: "全部",
            time = valueMap["time"] ?: "全部",
            status = valueMap["status"] ?: "",
            sort = valueMap["sort"] ?: "最热",
            page = page,
            pageSize = pageSize
        )
        val resp = okHttpClient.newRequest {
            url("$BASE_URL/filter/v1/filter")
            post(
                com.jing.sakura.extend.json.encodeToString(param)
                    .toRequestBody("application/json".toMediaType())
            )
        }.jsonBody<HiStarResponse<HiStarFilterResultPage>>()
        return AnimePageData(
            page = page,
            hasNextPage = resp.data?.haveNextPage() ?: false,
            animeList = resp.data?.list?.map {
                AnimeData(
                    id = it.id.toString(),
                    url = "",
                    title = it.name,
                    imageUrl = it.img,
                    currentEpisode = it.countStr,
                    sourceId = sourceId
                )
            } ?: emptyList()
        )

    }

    @Serializable
    data class HiStarResponse<T>(
        val code: Int = 0,
        val data: T? = null
    )


    @Serializable
    data class HiStarHomePageData(
        val cards: List<HomePageCardList> = emptyList()
    )

    @Serializable
    data class HomePageCardList(
        val id: Int,
        val type: Int,
        val name: String,
        val card: List<HomePageCard>? = emptyList()
    )

    @Serializable
    data class HomePageCard(
        val id: Long,
        val name: String,
        val img: String,
        val country: String = "",
        val time: String = "",
        val labels: List<String> = emptyList(),
        val countStr: String = ""
    )

    @Serializable
    data class HiStarVideoDetail(
        val id: Long,
        val name: String,
        val picurl: String,
        val videos: List<HiStarVideoDetailVideo> = emptyList(),
        val actor: String = "",
        val country: String = "",
        val desc: String = "",
        val time: String = ""
    )

    @Serializable
    data class HiStarVideoDetailVideo(
        val vid: Long,
        val epOrder: Int = 0,
        val name: String,
        val purl: String,
        val epInfo: String = ""
    ) {
        val displayEpName: String
            get() {
                if (epInfo.isEmpty()) {
                    return name
                }
                if (epInfo.isDigitsOnly()) {
                    return "第${epInfo}集"
                }
                return epInfo
            }
    }

    @Serializable
    data class HiStarVideoDetailRecommend(
        val cards: List<VideoDetailRecommendCard>? = null
    )

    @Serializable
    data class VideoDetailRecommendCard(
        val id: Long,
        val name: String,
        val img: String
    )

    @Serializable
    data class HiStarSearchResult(
        val id: Long,
        val name: String,
        val img: String,
        val type: Int,
        val countStr: String = ""
    )

    @Serializable
    data class HiStarFilterCondition(
        val tabName: List<String>,
        val label: List<String> = emptyList(),
        val country: List<String> = emptyList(),
        val time: List<String> = emptyList(),
        val status: List<String> = emptyList(),
        val sort: List<String> = emptyList()
    )

    @Serializable
    data class HiStarFilterRequestData(
        val chName: String,
        val label: String,
        val country: String,
        val time: String,
        val status: String,
        val sort: String,
        val page: Int,
        val pageSize: Int
    )

    @Serializable
    data class HiStarFilterResultPage(
        val list: List<HiStarFilterResult> = emptyList(),
        val total: Int,
        val page: Int,
        val pageSize: Int
    ) {
        fun haveNextPage(): Boolean = total.toDouble().div(pageSize) > page
    }

    @Serializable
    data class HiStarFilterResult(
        val id: Long,
        val name: String,
        val img: String,
        val countStr: String = ""
    )

    companion object {
        const val BASE_URL = "https://aws.ulivetv.net"
    }
}