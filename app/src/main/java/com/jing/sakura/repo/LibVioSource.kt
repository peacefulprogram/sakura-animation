package com.jing.sakura.repo

import com.google.gson.Gson
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.FunCDNHelper
import com.jing.sakura.extend.asDocument
import com.jing.sakura.extend.bodyString
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.newRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class LibVioSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "libvio"
    override val name: String
        get() = "LIBVIO"
    override val pageSize: Int
        get() = 12

    @Volatile
    private var videoCategoryGroups: List<VideoCategoryGroup>? = null

    private val videoCategoryLock = Mutex()

    override suspend fun fetchHomePageData(): HomePageData {
        val containers = getRequest("/").asDocument().getElementsByClass("stui-vodlist")
        val groups = mutableListOf<NamedValue<List<AnimeData>>>()
        for (container in containers) {
            val videoEls = container.select("li > .stui-vodlist__box")
            if (videoEls.isEmpty()) {
                break
            }
            val groupName =
                container.previousElementSibling()?.takeIf { it.hasClass("stui-vodlist__head") }
                    ?.selectFirst("h3")?.text()?.trim() ?: "推荐视频"
            groups.add(NamedValue(groupName, videoEls.map { it.parseAnime() }))
        }
        return HomePageData(groups)
    }

    private fun Element.parseAnime(): AnimeData {
        val linkEl = selectFirst(".stui-vodlist__thumb") ?: throw RuntimeException("未找到视频图片")
        val image = linkEl.dataset()["original"] ?: ""
        val url = linkEl.absUrl("href")
        val episode = linkEl.selectFirst(".pic-text")?.text() ?: ""
        val title =
            selectFirst(".stui-vodlist__detail > .title")?.text()?.trim() ?: throw RuntimeException(
                "未找到视频标题"
            )
        return AnimeData(
            id = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.')),
            url = url,
            title = title,
            currentEpisode = episode,
            imageUrl = image,
            sourceId = sourceId
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val doc = getRequest("/detail/$animeId.html").asDocument()
        val playlists = mutableListOf<AnimePlayList>()
        for (playlistEl in doc.select(".stui-vodlist__head > .stui-content__playlist")) {
            val name =
                playlistEl.previousElementSibling()?.takeIf { it.hasClass("stui-pannel__head") }
                    ?.text()?.trim() ?: continue
            if (name.contains("下载") || name.contains("网盘")) {
                continue
            }
            val episodes = playlistEl.select("a").map { el ->
                AnimePlayListEpisode(
                    episode = el.text(),
                    episodeId = el.attr("href")
                        .let { it.substring(it.lastIndexOf('/') + 1, it.lastIndexOf('.')) }
                )
            }
            if (episodes.isEmpty()) {
                continue
            }
            playlists.add(AnimePlayList(name = name, episodeList = episodes))
        }
        val otherVideos =
            doc.select(".stui-vodlist > li > .stui-vodlist__box").map { it.parseAnime() }

        val detailContainer =
            doc.selectFirst(".stui-content") ?: throw RuntimeException("未找到视频详情")
        val img = detailContainer.selectFirst("img")?.dataset()?.get("original") ?: ""
        val title = detailContainer.selectFirst(".stui-content__detail > .title")!!.text()
        val infos = detailContainer.select(".stui-content__detail > .data").map { it.text() }
        val desc =
            detailContainer.selectFirst(".stui-content__detail > .desc > .detail-content")?.text()
                ?: ""
        return AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            description = desc,
            imageUrl = img,
            playLists = playlists,
            otherAnimeList = otherVideos,
            infoList = infos
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val html = getRequest("/play/$episodeId.html").bodyString()
        val keyword = "player_aaaa"
        val startIndex = html.indexOf(keyword)
        if (startIndex == -1) {
            throw RuntimeException("未获取到视频播放信息")
        }
        val configStartIndex = html.indexOf('{', startIndex = startIndex + keyword.length)
        val configJson =
            html.substring(
                configStartIndex,
                html.indexOf('}', startIndex = configStartIndex + 1) + 1
            )
        val cfg = Gson().fromJson<Map<String, Any>>(configJson, Map::class.java)
        val encryptMode = cfg["encrypt"]?.toString() ?: ""
        val url = cfg["url"]?.toString()?.takeIf { it.isNotEmpty() }
            ?: throw RuntimeException("播放信息中无url")
        val nextLink = cfg["link_next"]?.toString()?.encodeUrl() ?: ""
        val data = when (encryptMode) {
            "1" -> URLDecoder.decode(url, "utf-8")
            "2" -> URLDecoder.decode(Base64.decode(url).toString(Charsets.UTF_8), "utf-8")
            else -> url
        }
        val playerHtml =
            getRequest("https://p2.cfnode1.xyz/ty4.php?url=$data&next=$nextLink&id=$animeId&nid=${cfg["nid"]}").bodyString()
        val videoUrl = getStringVariableValue(html = playerHtml, variableName = "urls")
            ?: throw RuntimeException("未获取到视频链接")
        return Resource.Success(AnimationSource.VideoUrlResult(url = videoUrl))
    }

    private fun getStringVariableValue(html: String, variableName: String): String? {
        val keyword = "var $variableName"
        val index = html.indexOf(keyword)
        if (index == -1) {
            return null
        }
        var startIndex = -1
        var quote = '"'
        var endIndex = -1
        for (i in (index + keyword.length) until html.length) {
            val c = html[i]
            if (startIndex == -1 && (c == '\'' || c == '"')) {
                startIndex = i + 1
                quote = c
                continue
            }
            if (startIndex != -1 && c == quote) {
                endIndex = i
                break
            }
        }
        if (startIndex < endIndex) {
            return html.substring(startIndex, endIndex)
        }
        return null
    }

    override fun supportSearch(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        return getRequest("/search/${keyword.encodeUrl()}----------$page---.html").asDocument()
            .parseVideoPage(page)
    }

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        if (videoCategoryGroups == null) {
            videoCategoryLock.withLock {
                if (videoCategoryGroups == null) {
                    videoCategoryGroups = getVideoCategoriesFromWebPage()
                }
            }
        }
        return videoCategoryGroups!!
    }

    private suspend fun getVideoCategoriesFromWebPage(): List<VideoCategoryGroup> {
        val first = VideoCategoryGroup.NormalCategoryGroup(
            name = "按分类",
            key = "0",
            defaultValue = "1",
            categories = listOf(
                VideoCategory(label = "电影", value = "1"),
                VideoCategory(label = "剧集", value = "2"),
                VideoCategory(label = "动漫", value = "4")
            )
        )
        val result = mutableListOf<VideoCategoryGroup>(first)
        val subCategoriesGroupsOfKey = coroutineScope {
            val finalMap =
                mutableMapOf<String, MutableMap<String, VideoCategoryGroup.NormalCategoryGroup>>()
            first.categories.map {
                async {
                    it.value to getSubCategoriesOfType(type = it.value)
                }
            }
                .awaitAll()
                .forEach { (type, groups) ->
                    groups.forEach { group ->
                        val mapOfKey = if (finalMap.containsKey(group.key)) {
                            finalMap[group.key]!!
                        } else {
                            val map =
                                mutableMapOf<String, VideoCategoryGroup.NormalCategoryGroup>()
                            finalMap[group.key] = map
                            map
                        }
                        mapOfKey[type] = group
                    }
                }
            finalMap
        }
        if (!subCategoriesGroupsOfKey.containsKey("3")) {
            result.add(
                VideoCategoryGroup.DynamicCategoryGroup(
                    name = "按剧情",
                    key = "3",
                    dependsOnKey = listOf("0")
                ) {
                    VideoCategoryGroup.NormalCategoryGroup(
                        name = "按剧情",
                        key = "3",
                        defaultValue = "",
                        categories = getContentCategoryOfType(it[0].value)
                    )
                })
        }
        subCategoriesGroupsOfKey.forEach { (key, groupsOfType) ->
            val anyone = groupsOfType.values.first()
            VideoCategoryGroup.DynamicCategoryGroup(
                name = anyone.name,
                key = key,
                dependsOnKey = listOf("0")
            ) {
                val typeValue = it[0].value
                groupsOfType[typeValue] ?: VideoCategoryGroup.NormalCategoryGroup(
                    name = anyone.name,
                    key = key,
                    defaultValue = anyone.defaultValue,
                    categories = emptyList()
                )
            }.apply { result.add(this) }
        }
        return result
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val map = categories.associate { it.name to it.value }
        val param = List(12) { idx ->
            if (idx == 8) { // 第九位是页码
                page.toString()
            } else {
                map[idx.toString()] ?: ""
            }
        }.joinToString(separator = "-")
        return getRequest("/show/$param.html").asDocument().parseVideoPage(page)

    }

    private fun Document.parseVideoPage(page: Int): AnimePageData {
        val videos = select(".stui-vodlist .stui-vodlist__box").map { it.parseAnime() }
        return AnimePageData(page = page, hasNextPage = hasNextPage(), animeList = videos)
    }

    private fun getSubCategoriesOfType(type: String): List<VideoCategoryGroup.NormalCategoryGroup> {
        val doc = getRequest("/show/$type-----------.html").asDocument()
        return doc.select("#screenbox > ul").map { row ->
            val rowLabel = row.child(0).text().trim(':', ' ', '：')
            val items = row.children().let { it.subList(1, it.size) }
            val parts = items.map { li ->
                val linkEl = li.selectFirst("a")!!
                val url = linkEl.attr("href")
                val values =
                    url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.')).split('-')
                val label = linkEl.text().trim()
                label to values
            }
            val valueIndex = parts.last().second.indexOfLast { it.isNotEmpty() }
            VideoCategoryGroup.NormalCategoryGroup(
                name = rowLabel,
                key = valueIndex.toString(),
                defaultValue = parts.first().second[valueIndex],
                categories = parts.map {
                    VideoCategory(label = it.first, value = it.second[valueIndex])
                }
            )
        }
    }

    fun getContentCategoryOfType(type: String): List<VideoCategory> = when (type) {
        "1" -> listOf(
            VideoCategory(label = "全部", value = ""),
            VideoCategory(label = "喜剧", value = "%E5%96%9C%E5%89%A7"),
            VideoCategory(label = "爱情", value = "%E7%88%B1%E6%83%85"),
            VideoCategory(label = "恐怖", value = "%E6%81%90%E6%80%96"),
            VideoCategory(label = "动作", value = "%E5%8A%A8%E4%BD%9C"),
            VideoCategory(label = "科幻", value = "%E7%A7%91%E5%B9%BB"),
            VideoCategory(label = "剧情", value = "%E5%89%A7%E6%83%85"),
            VideoCategory(label = "战争", value = "%E6%88%98%E4%BA%89"),
            VideoCategory(label = "警匪", value = "%E8%AD%A6%E5%8C%AA"),
            VideoCategory(label = "犯罪", value = "%E7%8A%AF%E7%BD%AA"),
            VideoCategory(label = "动画", value = "%E5%8A%A8%E7%94%BB"),
            VideoCategory(label = "奇幻", value = "%E5%A5%87%E5%B9%BB"),
            VideoCategory(label = "武侠", value = "%E6%AD%A6%E4%BE%A0"),
            VideoCategory(label = "冒险", value = "%E5%86%92%E9%99%A9"),
            VideoCategory(label = "枪战", value = "%E6%9E%AA%E6%88%98"),
            VideoCategory(label = "恐怖", value = "%E6%81%90%E6%80%96"),
            VideoCategory(label = "悬疑", value = "%E6%82%AC%E7%96%91"),
            VideoCategory(label = "惊悚", value = "%E6%83%8A%E6%82%9A"),
            VideoCategory(label = "经典", value = "%E7%BB%8F%E5%85%B8"),
            VideoCategory(label = "青春", value = "%E9%9D%92%E6%98%A5"),
            VideoCategory(label = "文艺", value = "%E6%96%87%E8%89%BA"),
            VideoCategory(label = "微电影", value = "%E5%BE%AE%E7%94%B5%E5%BD%B1"),
            VideoCategory(label = "古装", value = "%E5%8F%A4%E8%A3%85"),
            VideoCategory(label = "历史", value = "%E5%8E%86%E5%8F%B2"),
            VideoCategory(label = "运动", value = "%E8%BF%90%E5%8A%A8"),
            VideoCategory(label = "农村", value = "%E5%86%9C%E6%9D%91"),
            VideoCategory(label = "儿童", value = "%E5%84%BF%E7%AB%A5"),
            VideoCategory(label = "网络电影", value = "%E7%BD%91%E7%BB%9C%E7%94%B5%E5%BD%B1")
        )

        "2" -> listOf(
            VideoCategory(label = "全部", value = ""),
            VideoCategory(label = "古装", value = "%E5%8F%A4%E8%A3%85"),
            VideoCategory(label = "战争", value = "%E6%88%98%E4%BA%89"),
            VideoCategory(label = "青春偶像", value = "%E9%9D%92%E6%98%A5%E5%81%B6%E5%83%8F"),
            VideoCategory(label = "喜剧", value = "%E5%96%9C%E5%89%A7"),
            VideoCategory(label = "家庭", value = "%E5%AE%B6%E5%BA%AD"),
            VideoCategory(label = "犯罪", value = "%E7%8A%AF%E7%BD%AA"),
            VideoCategory(label = "动作", value = "%E5%8A%A8%E4%BD%9C"),
            VideoCategory(label = "奇幻", value = "%E5%A5%87%E5%B9%BB"),
            VideoCategory(label = "剧情", value = "%E5%89%A7%E6%83%85"),
            VideoCategory(label = "历史", value = "%E5%8E%86%E5%8F%B2"),
            VideoCategory(label = "经典", value = "%E7%BB%8F%E5%85%B8"),
            VideoCategory(label = "乡村", value = "%E4%B9%A1%E6%9D%91"),
            VideoCategory(label = "情景", value = "%E6%83%85%E6%99%AF"),
            VideoCategory(label = "商战", value = "%E5%95%86%E6%88%98"),
            VideoCategory(label = "网剧", value = "%E7%BD%91%E5%89%A7"),
            VideoCategory(label = "其他", value = "%E5%85%B6%E4%BB%96")
        )

        "4" -> listOf(
            VideoCategory(label = "全部", value = ""),
            VideoCategory(label = "情感", value = "%E6%83%85%E6%84%9F"),
            VideoCategory(label = "科幻", value = "%E7%A7%91%E5%B9%BB"),
            VideoCategory(label = "热血", value = "%E7%83%AD%E8%A1%80"),
            VideoCategory(label = "推理", value = "%E6%8E%A8%E7%90%86"),
            VideoCategory(label = "搞笑", value = "%E6%90%9E%E7%AC%91"),
            VideoCategory(label = "冒险", value = "%E5%86%92%E9%99%A9"),
            VideoCategory(label = "萝莉", value = "%E8%90%9D%E8%8E%89"),
            VideoCategory(label = "校园", value = "%E6%A0%A1%E5%9B%AD"),
            VideoCategory(label = "动作", value = "%E5%8A%A8%E4%BD%9C"),
            VideoCategory(label = "机战", value = "%E6%9C%BA%E6%88%98"),
            VideoCategory(label = "运动", value = "%E8%BF%90%E5%8A%A8"),
            VideoCategory(label = "战争", value = "%E6%88%98%E4%BA%89"),
            VideoCategory(label = "少年", value = "%E5%B0%91%E5%B9%B4"),
            VideoCategory(label = "少女", value = "%E5%B0%91%E5%A5%B3"),
            VideoCategory(label = "社会", value = "%E7%A4%BE%E4%BC%9A"),
            VideoCategory(label = "原创", value = "%E5%8E%9F%E5%88%9B"),
            VideoCategory(label = "亲子", value = "%E4%BA%B2%E5%AD%90"),
            VideoCategory(label = "益智", value = "%E7%9B%8A%E6%99%BA"),
            VideoCategory(label = "励志", value = "%E5%8A%B1%E5%BF%97"),
            VideoCategory(label = "其他", value = "%E5%85%B6%E4%BB%96")
        )

        else -> emptyList()
    }

    private fun Document.hasNextPage(): Boolean {
        val pageEls = select(".stui-pannel__ft > .stui-page__item > li")
        val currentIndex = pageEls.indexOfFirst { it.hasClass("active") }
        return currentIndex != -1 && currentIndex < pageEls.size - 4
    }

    private fun getRequest(url: String, block: Request.Builder.() -> Unit = {}): Response =
        okHttpClient.newRequest(FunCDNHelper) {
            if (url.startsWith("http:") || url.startsWith("https:")) {
                url(url)
            } else {
                url(BASE_URL + url)
            }
            get()
            header("referer", "$BASE_URL/")
            block()
        }

    companion object {
        const val BASE_URL = "https://www.libvio.me"
    }
}