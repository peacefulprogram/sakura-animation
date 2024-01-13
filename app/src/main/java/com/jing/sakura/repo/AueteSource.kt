package com.jing.sakura.repo

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.getDocument
import com.jing.sakura.extend.getHtml
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.io.encoding.ExperimentalEncodingApi

class AueteSource(val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "auete"
    override val name: String
        get() = "Auete影视"
    override val pageSize: Int
        get() = 20

    override suspend fun fetchHomePageData(): HomePageData {
        val doc = okHttpClient.getDocument(BASE_URL)
        val series = doc.select(".container > .row > .main .card").map { area ->
            val areaName = area.firstElementChild()!!.selectFirst("a")!!.text().trim()
            val videos = area.select(".threadlist > li").map { it.parseAnime() }
            NamedValue(areaName, videos)
        }
        return HomePageData(sourceId = sourceId, series)
    }

    private fun Element.parseAnime(): AnimeData {
        val linkEl = selectFirst("a")!!
        val id = linkEl.attr("href").trim('/')
        val image = selectFirst("img")?.absUrl("src") ?: ""
        val episode = selectFirst(".hdtag")?.text()?.trim()
        val title = selectFirst(".title")!!.text().trim()
        return AnimeData(
            id = id,
            url = linkEl.absUrl("href"),
            title = title,
            currentEpisode = episode ?: "",
            imageUrl = image,
            sourceId = sourceId
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val doc = okHttpClient.getDocument("$BASE_URL/$animeId/")
        val container = doc.selectFirst(".main .card-thread > .card-body")!!
        val img = container.selectFirst(".cover img")?.absUrl("src") ?: ""
        val title =
            container.selectFirst(".media > .media-body > .title")!!.text().trim().let { str ->
                val idx = str.indexOf('《')
                if (idx == -1) {
                    str
                } else {
                    var end = str.length
                    for (i in (idx + 1) until str.length) {
                        if (str[i] == '》') {
                            end = i
                            break
                        }
                    }
                    str.substring(idx + 1, end)
                }
            }
        val infoList = mutableListOf<String>()
        val infoEls = container.select(".message > p")
        var desc = ""
        for ((index, p) in infoEls.withIndex()) {
            val text = p.text().trim()
            if (text.contains("简介")) {
                if (index + 1 < infoEls.size) {
                    desc = infoEls[index + 1].text().trim()
                }
                break
            }
            infoList.add(text)

        }

        val playlists = container.select("[id=player_list]").map { playlistContainer ->
            var name = playlistContainer.selectFirst(".title")!!.text().trim()
            name.indexOf('』').let {
                if (it >= 0) {
                    name = name.substring(it + 1)
                }
            }
            name.indexOf('：').let {
                if (it >= 0) {
                    name = name.substring(0, it)
                }
            }
            val episodes = playlistContainer.select("ul > li > a").map { epEl ->
                val id = epEl.attr("href").let {
                    it.substring(it.lastIndexOf('/') + 1, it.lastIndexOf('.'))
                }
                AnimePlayListEpisode(episode = epEl.text().trim(), episodeId = id)
            }
            AnimePlayList(name = name, episodeList = episodes)
        }
        return AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            imageUrl = img,
            playLists = playlists,
            description = desc,
            infoList = infoList
        )
    }

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val url = "$BASE_URL/auete3so.php?searchword=${keyword.encodeUrl()}"
        val doc = okHttpClient.getDocument(url)
        val videos = doc.getElementsByClass("threadlist").map { el ->
            val linkEl = el.selectFirst("a")!!
            AnimeData(
                id = linkEl.attr("href").trim('/'),
                url = linkEl.absUrl("href"),
                title = linkEl.text().trim(),
                sourceId = sourceId
            )
        }
        return AnimePageData(page, false, videos)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val html = okHttpClient.getHtml("$BASE_URL/$animeId/$episodeId.html")
        val idx = html.indexOf(" now")
        var start = -1
        if (idx > 0) {
            for (i in (idx + 4) until html.length) {
                val c = html[i]
                if (c == '"') {
                    if (start == -1) {
                        start = i
                    } else {
                        val videoUrl = if (html.substring(idx, start).contains("base64")) {
                            kotlin.io.encoding.Base64.decode(html.substring(start + 1, i))
                                .toString(Charsets.UTF_8)
                        } else {
                            html.substring(start + 1, i)
                        }
                        return Resource.Success(AnimationSource.VideoUrlResult(url = videoUrl))
                    }
                }
            }
        }
        return Resource.Error("未获取到视频链接")
    }

    override fun supportSearch(): Boolean = true

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        return listOf(VideoCategoryGroup.NormalCategoryGroup(
            name = "频道",
            key = "channel",
            defaultValue = "Movie",
            categories = listOf(
                VideoCategory(label = "电影", value = "Movie"),
                VideoCategory(label = "电视剧", value = "Tv"),
                VideoCategory(label = "综艺", value = "Zy"),
                VideoCategory(label = "动漫", value = "Dm"),
                VideoCategory(label = "其他", value = "qita")
            )
        ),
            VideoCategoryGroup.DynamicCategoryGroup(
                dependsOnKey = listOf("channel"),
                key = "type",
                name = "类型"
            ) {
                VideoCategoryGroup.NormalCategoryGroup(
                    name = "类型",
                    key = "type",
                    defaultValue = "",
                    categories = listOf(
                        VideoCategory(
                            label = "全部",
                            value = ""
                        )
                    ) + getVideoTypeCategoryGroup(it[0].value)
                )
            }
        )
    }

    private fun getVideoTypeCategoryGroup(channel: String): List<VideoCategory> {
        return when (channel) {
            "Movie" -> listOf(
                VideoCategory(label = "喜剧片", value = "xjp"),
                VideoCategory(label = "动作片", value = "dzp"),
                VideoCategory(label = "爱情片", value = "aqp"),
                VideoCategory(label = "科幻片", value = "khp"),
                VideoCategory(label = "恐怖片", value = "kbp"),
                VideoCategory(label = "惊悚片", value = "jsp"),
                VideoCategory(label = "战争片", value = "zzp"),
                VideoCategory(label = "剧情片", value = "jqp"),
            )

            "Tv" -> listOf(
                VideoCategory(label = "美剧", value = "oumei"),
                VideoCategory(label = "韩剧", value = "hanju"),
                VideoCategory(label = "日剧", value = "riju"),
                VideoCategory(label = "泰剧", value = "yataiju"),
                VideoCategory(label = "网剧", value = "wangju"),
                VideoCategory(label = "台剧", value = "taiju"),
                VideoCategory(label = "国产", value = "neidi"),
                VideoCategory(label = "港剧", value = "tvbgj"),
                VideoCategory(label = "英剧", value = "yingju"),
                VideoCategory(label = "外剧", value = "waiju"),
            )

            "Zy" -> listOf(
                VideoCategory(label = "国综", value = "guozong"),
                VideoCategory(label = "韩综", value = "hanzong"),
                VideoCategory(label = "美综", value = "meizong")
            )

            "Dm" -> listOf(
                VideoCategory(label = "动画", value = "donghua"),
                VideoCategory(label = "日漫", value = "riman"),
                VideoCategory(label = "国漫", value = "guoman"),
                VideoCategory(label = "美漫", value = "meiman")
            )

            "qita" -> listOf(
                VideoCategory(label = "记录片", value = "Jlp"),
                VideoCategory(label = "经典片", value = "Jdp"),
                VideoCategory(label = "经典剧", value = "Jdj"),
                VideoCategory(label = "网大电影", value = "wlp"),
                VideoCategory(label = "国产老电影", value = "laodianying")
            )

            else -> throw UnsupportedOperationException()
        }

    }
    
    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val map = categories.associateBy { it.name }
        val url = StringBuilder(BASE_URL)
            .append('/')
            .append(map["channel"]?.value)
        val type = map["type"]
        if (type?.value?.isNotBlank() == true) {
            url.append('/')
                .append(type.value)
        }
        url.append("/index")
        if (page > 1) {
            url.append(page)
        }
        url.append(".html")
        val doc = okHttpClient.getDocument(url.toString())
        val videos = doc.select(".threadlist > li").map { it.parseAnime() }
        return AnimePageData(
            page = page,
            hasNextPage = doc.haveNextPage(),
            animeList = videos
        )
    }

    private fun Document.haveNextPage(): Boolean {
        val pageItems = select(".pagination > li")
        if (pageItems.isEmpty()) {
            return false
        }
        return pageItems.indexOfLast { it.hasClass("active") } < pageItems.size - 3
    }

    companion object {
        private const val TAG = "AueteSource"
        const val BASE_URL = "https://auete.pro"
    }
}