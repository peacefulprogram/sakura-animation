package com.jing.sakura.repo

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.bodyString
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.getDocument
import com.jing.sakura.extend.newGetRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class XiGuaCartoonSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "xgcartoon"
    override val name: String
        get() = "西瓜卡通"
    override val pageSize: Int
        get() = 36

    private val gson = Gson()

    private val categoryLock = Mutex()

    @Volatile
    private var categoryGroups: List<VideoCategoryGroup>? = null

    override suspend fun fetchHomePageData(): HomePageData {
        val doc = okHttpClient.getDocument(BASE_URL)
        val series = mutableListOf<NamedValue<List<AnimeData>>>()
        doc.select("#index .index-hot").forEach { hot ->
            val title = hot.child(0).text()
            val videos = hot.select(".index-hot-item > .box").map { box ->
                val linkEl = box.selectFirst(".title")!!
                val url = linkEl.absUrl("href")
                val name = linkEl.text().trim()
                val id = url.extractIdFromUrl()
                AnimeData(
                    id = id,
                    url = url,
                    title = name,
                    sourceId = sourceId,
                    currentEpisode = box.selectFirst(".info")?.text() ?: "",
                    imageUrl = imageUrlOfVideoId(id)
                )
            }
            if (videos.isNotEmpty()) {
                series.add(NamedValue(title, videos))
            }
        }

        doc.select("#index > .index-category > .catelog").forEach { category ->
            val videos = mutableListOf<AnimeData>()
            val title = category.selectFirst(".head")!!.text()
            category.selectFirst(".top-item")?.let { item ->
                val linkEl = item.selectFirst("a")!!
                val url = linkEl.absUrl("href")
                val name = item.selectFirst(".info > .title")!!.text()
                val id = url.extractIdFromUrl()
                AnimeData(
                    id = id,
                    sourceId = sourceId,
                    url = url,
                    title = name,
                    currentEpisode = item.selectFirst(".info > .author")?.text() ?: "",
                    imageUrl = imageUrlOfVideoId(id)
                ).run {
                    videos.add(this)
                }
            }
            category.select(".list > a").forEach { linkEl ->
                val url = linkEl.absUrl("href")
                val infoEls = linkEl.selectFirst(".topic-info")!!.children()
                val id = url.extractIdFromUrl()
                AnimeData(
                    id = id,
                    sourceId = sourceId,
                    url = url,
                    title = infoEls[0].text().trim(),
                    currentEpisode = infoEls.getOrNull(1)?.text() ?: "",
                    imageUrl = imageUrlOfVideoId(id)
                ).run {
                    videos.add(this)
                }
            }
            series.add(NamedValue(title, videos))
        }
        return HomePageData(sourceId = sourceId, series)
    }

    private fun String.extractIdFromUrl() = this.substring(this.lastIndexOf('/') + 1)


    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val doc = okHttpClient.getDocument("$BASE_URL/detail/$animeId")
        val detailContainer = doc.selectFirst(".detail-right")!!
        val name = detailContainer.selectFirst(".detail-right__title")!!.text()
        val tags = detailContainer.select(".detail-right__tags .tag").map { it.text() }
        val desc =
            detailContainer.selectFirst(".detail-right__desc")?.children()?.last()?.text()
        val playlists = mutableListOf<AnimePlayList>()
        var currentEpisodes = arrayListOf<AnimePlayListEpisode>()
        detailContainer.selectFirst(".detail-right__volumes")?.lastElementChild()?.children()
            ?.forEach { element ->
                if (element.hasClass("volume-title")) {
                    currentEpisodes = arrayListOf()
                    playlists.add(
                        AnimePlayList(
                            name = element.text(),
                            episodeList = currentEpisodes
                        )
                    )
                } else {
                    val linkEl = element.selectFirst("a")!!
                    currentEpisodes.add(
                        AnimePlayListEpisode(
                            episodeId = linkEl.attr("href").let {
                                it.substring(it.lastIndexOf('=') + 1)
                            },
                            episode = linkEl.text().substringBefore(' ')
                        )
                    )
                }
            }
        val otherList = detailContainer.select(".row > .index-hot-item").map { box ->
            val linkEl = box.selectFirst(".title")!!
            val url = linkEl.absUrl("href")
            val videoName = linkEl.text().trim()
            val id = url.extractIdFromUrl()
            AnimeData(
                id = id,
                url = url,
                title = videoName,
                sourceId = sourceId,
                currentEpisode = box.selectFirst(".info")?.text() ?: "",
                imageUrl = imageUrlOfVideoId(id)
            )
        }
        var infoEl = doc.selectFirst("#layout > div.detail.container  div.detail-sider-btns")
            ?.nextElementSibling()
        val infoList = mutableListOf<String>()
        if (tags.isNotEmpty()) {
            infoList.add(tags.joinToString(separator = " "))
        }
        while (infoEl != null && infoEl.classNames().isEmpty()) {
            val info = infoEl.text().trim()
            if (info.isNotEmpty()) {
                infoList.add(info)
            }
            infoEl = infoEl.nextElementSibling()
        }
        return AnimeDetailPageData(
            animeId = animeId,
            imageUrl = imageUrlOfVideoId(animeId),
            description = desc ?: "",
            animeName = name,
            playLists = playlists,
            infoList = infoList,
            otherAnimeList = otherList
        )
    }

    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val doc = okHttpClient.getDocument("$BASE_URL/video/$animeId/$episodeId.html")
        val iframe =
            doc.selectFirst("#video_content iframe")
                ?: throw RuntimeException("未获取到播放器")
        val iframeSrc = iframe.attr("src")
        if (iframeSrc.isEmpty()) {
            throw RuntimeException("未获取到播放器链接")
        }
        val vid = iframeSrc.toHttpUrlOrNull()?.queryParameter("vid")
        val videoUrl = if (vid != null) {
            "https://xgct-video.vzcdn.net/$vid/playlist.m3u8"
        } else {
            okHttpClient.getDocument(iframeSrc)
                .getElementsByTag("source")
                .firstOrNull()
                ?.attr("src")
                ?.takeIf { it.isNotEmpty() }
                ?: throw RuntimeException("未获取到video source")
        }
        return Resource.Success(data = AnimationSource.VideoUrlResult(url = videoUrl))
    }

    override fun supportSearch(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val doc = okHttpClient.getDocument("$BASE_URL/search?q=${keyword.encodeUrl()}")
        val videos =
            doc.select("#layout > .container.search > .topic-list > .topic-list-box").map { box ->
                val url = box.selectFirst("a")!!.absUrl("href")
                val id = url.extractIdFromUrl()
                val tags =
                    box.select(".topic-tag .tag").asSequence().map { it.text() }.toMutableList()
                val infoList = box.selectFirst(".topic-list-item__info")!!.children()
                val name = infoList.last()!!.text()
                if (infoList.size > 1) {
                    tags.add(infoList[0].text())
                }
                AnimeData(
                    id = id,
                    sourceId = sourceId,
                    url = url,
                    title = name,
                    currentEpisode = tags.joinToString(" "),
                    imageUrl = imageUrlOfVideoId(id)
                )
            }
        return AnimePageData(page = page, hasNextPage = false, animeList = videos)
    }

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        if (categoryGroups == null) {
            categoryLock.withLock {
                if (categoryGroups == null) {
                    categoryGroups = requestVideoCategories()
                }
            }
        }
        return categoryGroups!!
    }

    private suspend fun requestVideoCategories(): List<VideoCategoryGroup> {
        val doc = okHttpClient.getDocument("$BASE_URL/classify")
        return doc.select("#classify .row .filter").map { container ->
            val groupName =
                container.selectFirst(".filter-head")!!.children().last()!!.text().trim()
            val items = container.select(".filter-item > a").map { linkEl ->
                val url = linkEl.absUrl("href")
                linkEl.text().trim() to url.toHttpUrl()
            }
            val url1 = items[0].second
            val url2 = items[1].second
            val key = url1.queryParameterNames.find { name ->
                url1.queryParameter(name) != url2.queryParameter(name)
            }!!
            val categories = items.map { (name, url) ->
                VideoCategory(label = name, value = url.queryParameter(key)!!)
            }
            VideoCategoryGroup.NormalCategoryGroup(
                name = groupName,
                key = key,
                defaultValue = categories.first().value,
                categories = categories
            )
        }
    }

    private fun imageUrlOfVideoId(id: String): String =
        "https://static-a.xgcartoon.com/cover/$id.jpg"

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val url = buildString {
            append(BASE_URL)
            append("/api/amp_query_cartoon_list?")
            categories.forEach { (name, value) ->
                append(name)
                append('=')
                append(value)
                append('&')
            }
            append("page=")
            append(page)
            append("&limit=36&language=cn&__amp_source_origin=")
            append(BASE_URL)
        }
        val json = okHttpClient.newGetRequest {
            url(url)
            header("referer", "$BASE_URL/")
        }.bodyString()
        val resp = gson.fromJson(json, VideoResponse::class.java)
        return AnimePageData(
            page = page,
            hasNextPage = resp.next?.isNotEmpty() == true && resp.items?.isNotEmpty() == true,
            animeList = resp.items?.map { item ->
                AnimeData(
                    id = item.id,
                    url = "$BASE_URL/detail/${item.id}",
                    title = item.name,
                    currentEpisode = item.tags?.joinToString(separator = " ") ?: "",
                    imageUrl = imageUrlOfVideoId(item.id),
                    sourceId = sourceId
                )
            } ?: emptyList()
        )
    }


    @Keep
    data class VideoResponse(
        val items: List<VideoData>?,
        val next: String?
    )

    @Keep
    data class VideoData(
        @SerializedName("cartoon_id")
        val id: String,
        val name: String,
        @SerializedName("type_names")
        val tags: List<String>?
    )

    companion object {
        const val BASE_URL = "https://cn.xgcartoon.com"
    }
}