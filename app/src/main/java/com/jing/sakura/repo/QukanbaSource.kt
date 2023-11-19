package com.jing.sakura.repo

import android.util.Base64
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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class QukanbaSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "qukanba"
    override val name: String
        get() = "去看吧"


    override suspend fun fetchHomePageData(): HomePageData {
        val document = okHttpClient.getDocument(toAbsolute("/"))
        val animeParts = setOf(
            "新番推薦",
            "新番推荐",
            "高清原碟",
            "日漫",
            "女頻",
            "劇場",
            "剧场",
            "美漫"
        )
        val series = mutableListOf<NamedValue<List<AnimeData>>>()
        for (layoutContainer in document.getElementsByClass("fed-part-layout")) {
            val name =
                layoutContainer.selectFirst(".fed-list-head > .fed-font-xvi")?.text()?.trim() ?: ""
            if (!animeParts.contains(name)) {
                continue
            }
            val videos = layoutContainer.select(".fed-list-info > li").map { it.parseAnime() }
            if (videos.isNotEmpty()) {
                series.add(
                    NamedValue(
                        name = name,
                        value = videos
                    )
                )
            }
        }
        return HomePageData(seriesList = series)
    }

    private fun Element.parseAnime(): AnimeData {
        val linkEl = selectFirst(".fed-list-pics")!!
        val url = linkEl.absUrl("href")
        val imageUrl = linkEl.dataset()["original"] ?: ""
        val episode = linkEl.selectFirst(".fed-list-remarks")?.text()?.trim() ?: ""
        val title = selectFirst(".fed-list-title")?.text()?.trim() // 首页和详情页推荐视频
            ?: selectFirst("dd.fed-deta-content .fed-part-eone")?.text()?.trim() // 搜索页逻辑
        return AnimeData(
            id = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.')),
            url = url,
            title = title!!,
            currentEpisode = episode,
            imageUrl = imageUrl,
            sourceId = sourceId
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val document =
            okHttpClient.getDocument(toAbsolute("/index.php/vod/detail/id/$animeId.html"))
        val detailContainer = document.selectFirst(".fed-deta-info")!!
        val imageUrl =
            detailContainer.selectFirst(".fed-list-pics")?.dataset()?.get("original") ?: ""
        val title = detailContainer.selectFirst(".fed-deta-content > h1")!!.text().trim()
        val episode = detailContainer.selectFirst(".fed-list-pics .fed-list-remarks")?.text() ?: ""
        val infoRows = detailContainer.select(".fed-part-rows > li")
        val infoList = infoRows.slice(0..<infoRows.size - 1).map { it.text().trim() }.run {
            if (episode.isNotBlank()) {
                this
            } else {
                listOf(episode) + this
            }
        }
        val desc = infoRows.last()?.textNodes()?.last()?.text() ?: ""
        val otherAnimations =
            document.select(".fed-part-layout.fed-back-whits .fed-list-head")
                .last()
                ?.nextElementSibling()
                ?.children()
                ?.map { it.parseAnime() }
                ?: emptyList()

        val playlistContainer = document.selectFirst(".fed-tabs-info .fed-tabs-boxs .fed-tabs-item")
        val playlistNames = playlistContainer?.select(".fed-part-rows > li")
            ?.map { it.text().trim() }
            ?: emptyList()

        val episodeGroup = playlistContainer?.select(".fed-play-item")
            ?.map {
                it.children().last()
                    ?.getElementsByTag("a")
                    ?.map { epEl ->
                        val url = epEl.attr("href")
                        val key = "/id/"
                        val parts = url.substring(
                            url.lastIndexOf(key) + key.length,
                            url.lastIndexOf('.')
                        )
                            .split('/')
                        AnimePlayListEpisode(
                            episode = epEl.text().trim(),
                            episodeId = "${parts[0]}-${parts[2]}-${parts[4]}"
                        )
                    } ?: emptyList()
            } ?: emptyList()

        return AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            description = desc,
            imageUrl = imageUrl,
            infoList = infoList,
            otherAnimeList = otherAnimations,
            playLists = List(size = playlistNames.size.coerceAtMost(episodeGroup.size)) {
                AnimePlayList(
                    name = playlistNames[it],
                    episodeList = episodeGroup[it]
                )
            }
                .sortedBy {
                    if (it.name.contains("异世界") || it.name.contains("異世界")) Int.MAX_VALUE else 0
                }
        )
    }

    override fun supportSearch(): Boolean = true
    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val document =
            okHttpClient.getDocument(toAbsolute("/index.php/vod/search/page/$page/wd/${keyword.encodeUrl()}.html"))
        val videos = document.getElementsByClass("fed-deta-info").map { it.parseAnime() }
        return AnimePageData(page = page, hasNextPage = hasNextPage(document), animeList = videos)
    }

    private fun hasNextPage(document: Document): Boolean {
        return document.selectFirst(".fed-page-info")
            ?.children()
            ?.run {
                val nextPageDisabled =
                    findLast {
                        it.text().trim().run { this == "下頁" || this == "下页" }
                    }?.hasClass("fed-btns-disad") ?: true
                !nextPageDisabled
            } ?: false
    }

    override suspend fun fetchVideoUrl(episodeId: String): Resource<AnimationSource.VideoUrlResult> {
        val parts = episodeId.split('-')
        val playPageUrl =
            toAbsolute("/index.php/vod/play/id/${parts[0]}/sid/${parts[1]}/nid/${parts[2]}.html")
        val document = okHttpClient.getDocument(playPageUrl)
        val iframeEl = document.getElementById("fed-play-iframe")!!
        val plain = decode(iframeEl.dataset()["play"]!!)
        if (plain.startsWith("https://") || plain.startsWith("http://")) {
            return Resource.Success(AnimationSource.VideoUrlResult(plain))
        }
        val html =
            okHttpClient.getHtml(toAbsolute(iframeEl.dataset()["pars"] + plain.encodeUrl())) {
                header("referer", playPageUrl)
            }
        val anotherPageUrl =
            extractKeywordValue(html, "purl") ?: throw RuntimeException("未获取到播放器链接")
        val newHtml = okHttpClient.getHtml(anotherPageUrl) {
            header("referer", "$BASE_URL/")
        }
        val leToken = extractKeywordValue(newHtml, "le_token")
        if (leToken != null) {
            val encryptedUrl = extractEncryptedUrl(newHtml)
            val plainUrl = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec("A42EAC0C2B408472".toByteArray(), "AES"),
                    IvParameterSpec(leToken.toByteArray(Charsets.UTF_8))
                )
                doFinal(Base64.decode(encryptedUrl, Base64.DEFAULT))
            }.toString(Charsets.UTF_8)
            return Resource.Success(
                AnimationSource.VideoUrlResult(
                    url = plainUrl,
                    headers = mapOf("referer" to anotherPageUrl.run {
                        substring(0, indexOf('/', indexOf("://") + 3) + 1)
                    })
                )
            )
        }
        val videoUrl = extractKeywordValue(newHtml, "url")
        if (videoUrl != null) {
            return Resource.Success(AnimationSource.VideoUrlResult(videoUrl))
        }
        throw RuntimeException("未获取到视频链接")

    }

    private fun extractEncryptedUrl(html: String): String {
        val key = "getVideoInfo("
        val i1 = html.indexOf(key)
        val i2 = html.indexOf('"', i1 + key.length)
        val i3 = html.indexOf('"', i2 + 1)
        return html.substring(i2 + 1, i3)
    }

    private fun extractKeywordValue(html: String, keyword: String): String? {
        val i1 = html.indexOf(keyword)
        if (i1 < 0) {
            return null
        }
        var i2 = -1
        var quote = '"'
        for (i in (i1 + keyword.length + 1)..<html.length) {
            if (html[i] == '\'' || html[i] == '"') {
                i2 = i
                quote = html[i]
                break
            }
        }
        if (i2 < 0) {
            return null
        }
        val i3 = html.indexOf(quote, i2 + 1)
        return html.substring(i2 + 1, i3)
    }


    private fun toAbsolute(url: String): String = BASE_URL + url


    private fun decode(source: String): String {
        val key = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
        var d = 0
        var e = source
        var b: Int
        var p: Int
        var m: Int
        var w: Int
        var o: Int
        var c: Int
        var y: Int
        var i = ""
        e = e.toCharArray().filter {
            (it in '0'..'9') || it in 'a'..'z' || it in 'A'..'Z' || it == '+' || it == '/' || it == '='
        }.joinToString(separator = "")
        e = e.substring(3)
        while (d < e.length) {
            b = key.indexOf(e[d++])
            p = key.indexOf(e[d++])
            m = key.indexOf(e[d++])
            w = key.indexOf(e[d++])
            o = b shl 2 or (p.shr(4))
            c = 15 and p shl 4 or (m shr 2)
            y = 3 and m shl 6 or w
            i += Char(o)
            if (m != 64) {
                i += Char(c)
            }
            if (w != 64) {
                i += Char(y)
            }
        }

        return i
    }

    override fun supportSearchByCategory(): Boolean = true


    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        val document = okHttpClient.getDocument(toAbsolute("/index.php/vod/type/id/33.html"))
        val idKeyword = "/id/"
        val typeCategories =
            document.select(".fed-head-info > .fed-part-case > .fed-navs-info > .fed-menu-info > li > a")
                .asSequence()
                .filter {
                    it.attr("href").contains(idKeyword)
                }
                .map {
                    val value = it.attr("href").run {
                        substring(indexOf(idKeyword) + idKeyword.length, lastIndexOf('.'))
                    }
                    VideoCategory(label = it.text().trim(), value = value)
                }
                .toList()
        val result = mutableListOf(
            VideoCategoryGroup(
                name = "频道",
                key = "id",
                defaultValue = "21",
                categories = typeCategories
            )
        )
        document.select(".fed-scre-list > dl").forEach { groupEl ->
            val name = groupEl.child(0).text().trim()
            val videoCategories = mutableListOf(VideoCategory(label = "全部", value = ""))
            var key = ""
            var valueIndex = -1
            groupEl.getElementsByTag("a").forEach { linkEl ->
                val keyword = "/show/"
                val parts = linkEl.attr("href").run {
                    substring(indexOf(keyword) + keyword.length, lastIndexOf('.'))
                }.split('/')
                val value = if (valueIndex >= 0) {
                    parts[valueIndex]
                } else {
                    var v = ""
                    for (i in parts.indices step 2) {
                        if (parts[i] == "id") {
                            continue
                        }
                        key = parts[i]
                        valueIndex = i + 1
                        v = parts[valueIndex]
                        break
                    }
                    if (v.isEmpty()) {
                        throw RuntimeException("解析筛选项失败: " + linkEl.attr("href"))
                    }
                    v
                }

                videoCategories.add(VideoCategory(label = linkEl.text().trim(), value = value))
            }
            result.add(
                VideoCategoryGroup(
                    name = name,
                    key = key,
                    defaultValue = "",
                    categories = videoCategories
                )
            )
        }
        result.add(
            VideoCategoryGroup(
                name = "排序",
                key = "by",
                defaultValue = "time",
                categories = listOf(
                    VideoCategory(label = "按时间", value = "time"),
                    VideoCategory(label = "按人气", value = "hits"),
                    VideoCategory(label = "按评分", value = "score"),
                )
            )
        )
        return result
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val urlBuilder = StringBuilder("$BASE_URL/index.php/vod/show")
        val valueMap =
            categories.filter { it.value.isNotBlank() }.associate { it.name to it.value }
        arrayOf("area", "by", "class", "id", "year").forEach { key ->
            val value = valueMap[key]
            if (value?.isNotBlank() == true) {
                urlBuilder.append('/')
                    .append(key)
                    .append('/')
                    .append(value)
            }
        }
        urlBuilder.append(".html")
        val document = okHttpClient.getDocument(urlBuilder.toString())
        val videos = document.getElementsByClass("fed-list-item").map { it.parseAnime() }
        return AnimePageData(
            page = page,
            hasNextPage = hasNextPage(document),
            animeList = videos
        )
    }

    companion object {
        const val BASE_URL = "https://k6dm.com"

        private const val TAG = "QukanbaSource"
    }
}