package com.jing.sakura.repo

import android.util.Base64
import com.google.gson.Gson
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.data.SearchPageData
import com.jing.sakura.data.UpdateTimeLine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MxdmSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = SOURCE_ID
    override val name: String
        get() = "MX动漫"

    override suspend fun fetchHomePageData(): HomePageData {
        val document = getDocument(BASE_URL)
        val contents = document.select(".content .module .module-list>.module-items").iterator()
        val titles = document.select(".content .module .module-title").iterator()
        val seriesList: MutableList<NamedValue<List<AnimeData>>> = mutableListOf()
        while (contents.hasNext() && titles.hasNext()) {
            val contentEl = contents.next()
            val titleEl = titles.next()
            val videos = contentEl.select(".module-item")
            if (videos.isEmpty()) {
                continue
            }
            if (videos[0].classNames().size > 1) {
                continue
            }
            val videoList = videos.map { videoEl -> videoEl.parseToAnime() }
            seriesList.add(NamedValue(titleEl.text().trim(), value = videoList))
        }
        return HomePageData(
            seriesList = seriesList
        )
    }


    private fun Element.parseToAnime(): AnimeData {
        val coverUrl = extractImageSrc(this.selectFirst("img")!!)
        val linkEl = this.selectFirst("a")!!
        val url = linkEl.absUrl("href")
        val id = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))
        val videoTitle = this.selectFirst(".video-name")!!.text().trim()
        val tags = LinkedHashSet<String>()
        val episode = this.selectFirst(".module-item-text")?.text()?.trim() ?: ""
        if (episode.isEmpty()) {
            tags.add(episode)
        }
        this.selectFirst(".module-item-caption")?.children()?.forEach {
            val text = it.text().trim()
            if (text.isNotEmpty()) {
                tags.add(text)
            }
        }
        return AnimeData(
            id = id,
            url = url,
            title = videoTitle,
            sourceId = sourceId,
            currentEpisode = episode,
            imageUrl = coverUrl,
            description = selectFirst(".module-item-cover .module-item-content .video-text")?.text()
                ?.trim() ?: ""
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val document = getDocument("/dongman/$animeId.html")
        val videoTitle = document.selectFirst(".page-title")!!.text().trim()
        val tags = document.select(".video-info-aux a").joinToString(" | ") { it.text().trim() }
        val desc = document.selectFirst(".video-info-content")?.text()
        val coverUrl = document.selectFirst(".module-item-pic img")?.run {
            extractImageSrc(this)
        }

        val playlistNames = document.select(".module-player-tab .module-tab-item").map { el ->
            if (el.childrenSize() > 0) {
                el.child(0).text().trim()
            } else {
                el.text().trim()
            }
        }
        val playLists = mutableListOf<AnimePlayList>()
        document.select(".module-player-list > .module-blocklist")
            .forEachIndexed { index, container ->
                if (index >= playlistNames.size) {
                    return@forEachIndexed
                }
                val episodeElements = container.getElementsByTag("a")
                if (episodeElements.isEmpty()) {
                    return@forEachIndexed
                }
                playLists.add(
                    AnimePlayList(
                        name = playlistNames[index],
                        episodeList = episodeElements.mapIndexed { epIndex, el ->
                            val epUrl = el.absUrl("href")
                            AnimePlayListEpisode(
                                episode = el.text().trim(),
                                episodeId = epUrl.substring(
                                    epUrl.lastIndexOf('/') + 1,
                                    epUrl.lastIndexOf('.')
                                )
                            )
                        }
                    )
                )
            }

        return AnimeDetailPageData(
            animeId = animeId,
            animeName = videoTitle,
            description = desc ?: "",
            imageUrl = coverUrl ?: "",
            defaultPlayListIndex = 0,
            playLists = playLists,
            otherAnimeList = document.select(".module-lines-list .module-item")
                .map { it.parseToAnime() },
            infoList = listOf(tags)
        )
    }

    override suspend fun searchAnimation(keyword: String, page: Int): SearchPageData {
        val document = getDocument("/search/${encodeUrlComponent(keyword)}----------$page---.html")
        val videos = document.select(".module-search-item").map { videoElement ->
            val link = videoElement.selectFirst("a")!!
            val url = link.absUrl("href")
            val videoTitle = videoElement.selectFirst("h3")!!.text()
            val coverUrl = extractImageSrc(videoElement.selectFirst("img")!!)
            val desc = videoElement.select(".video-info-item").last()?.text()
            val videoId = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))
            AnimeData(
                id = videoId,
                sourceId = sourceId,
                url = url,
                title = videoTitle,
                description = desc ?: "",
                imageUrl = coverUrl,
                currentEpisode = videoElement.selectFirst(".video-info .video-info-header .video-serial")
                    ?.text()?.trim() ?: ""
            )
        }
        return SearchPageData(
            page = page,
            hasNextPage = hasNextPage(document),
            animeList = videos
        )
    }


    private fun encodeUrlComponent(text: String): String =
        URLEncoder.encode(text, Charsets.UTF_8.name())

    override suspend fun fetchVideoUrl(episodeId: String): Resource<String> {
        val html = getHtml("/dongmanplay/$episodeId.html")

        val newHtml =
            getHtml("https://danmu.yhdmjx.com/m3u8.php?url=" + extractPlayerParam(html))

        val btToken = extractBtToken(newHtml)
        val encryptedUrl = extractEncryptedUrl(newHtml)

        val plainUrl = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec("57A891D97E332A9D".toByteArray(), "AES"),
                IvParameterSpec(btToken.toByteArray(Charsets.UTF_8))
            )
            doFinal(Base64.decode(encryptedUrl, Base64.DEFAULT))
        }.toString(Charsets.UTF_8)
        return Resource.Success(plainUrl)
    }

    fun extractEncryptedUrl(html: String): String {
        val key = "getVideoInfo("
        val i1 = html.indexOf(key)
        val i2 = html.indexOf('"', i1 + key.length)
        val i3 = html.indexOf('"', i2 + 1)
        return html.substring(i2 + 1, i3)
    }

    fun extractBtToken(html: String): String {
        val i1 = html.indexOf("bt_token")
        val i2 = html.indexOf('"', i1)
        val i3 = html.indexOf('"', i2 + 1)
        return html.substring(i2 + 1, i3)
    }

    private fun extractPlayerParam(html: String): String {
        val startIndex = html.indexOf("player_aaaa")
        val startBracketIndex = html.indexOf('{', startIndex + 1)
        var endIndex = -1
        var bracketCount = 0
        for (i in (startBracketIndex + 1)..<html.length) {
            val char = html[i]
            if (char == '{') {
                bracketCount++
            } else if (char == '}') {
                if (bracketCount == 0) {
                    endIndex = i
                    break
                }
                bracketCount--
            }
        }
        if (endIndex <= startIndex) {
            throw RuntimeException("未找到播放信息")
        }
        return Gson().fromJson<Map<String, String>>(
            html.substring(startBracketIndex, endIndex + 1),
            Map::class.java
        )["url"] ?: throw RuntimeException("未获取到播放信息")
    }

    override suspend fun fetchUpdateTimeline(): UpdateTimeLine {
        val document = getDocument(BASE_URL)
        val tabs = document.selectFirst(".mxoneweek-tabs") ?: return UpdateTimeLine(
            current = -1,
            timeline = emptyList()
        )
        val result = mutableListOf<Pair<String, List<AnimeData>>>()
        var activeTabIndex = 0
        val tabNames = tabs.children().mapIndexed { index, el ->
            if (el.hasClass("active")) {
                activeTabIndex = index
            }
            el.text().trim()
        }
        val videoGroups = document.select(".mxoneweek-list").map { el ->
            el.getElementsByTag("a").map { link ->
                val title =
                    if (link.childrenSize() > 0) link.child(0).text().trim() else link.text()
                        .trim()
                val episodeText =
                    if (link.childrenSize() > 1) link.child(1).text().trim() else ""
                val url = link.absUrl("href")
                val videoId = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))
                AnimeData(
                    id = videoId,
                    sourceId = sourceId,
                    url = url,
                    title = title,
                    currentEpisode = episodeText
                )
            }

        }
        for (i in 0..<tabNames.size.coerceAtMost(videoGroups.size)) {
            result.add(tabNames[i] to videoGroups[i])
        }
        return UpdateTimeLine(current = activeTabIndex, result)
    }

    fun getHtml(url: String): String {
        val actualUrl = if (url.startsWith("http")) {
            url
        } else {
            BASE_URL + url
        }
        val req = Request.Builder()
            .url(actualUrl)
            .get()
            .build()
        return okHttpClient.newCall(req).execute().body?.string()
            ?: throw RuntimeException("响应为空, url: $actualUrl")
    }

    fun getDocument(url: String): Document {
        val actualUrl = if (url.startsWith("http")) {
            url
        } else {
            BASE_URL + url
        }
        return getHtml(actualUrl).run {
            Jsoup.parse(this).apply {
                setBaseUri(actualUrl)
            }
        }
    }


    fun hasNextPage(document: Document): Boolean {
        val page = document.getElementById("page") ?: return false
        val currentPageIndex = page.children().indexOfFirst { it.hasClass("page-current") }
        return currentPageIndex != -1 && currentPageIndex < page.childrenSize() - 3
    }

    fun extractImageSrc(imageElement: Element): String {
        var img = imageElement.dataset()["src"] ?: ""
        if (img.isEmpty() && imageElement.hasAttr("src")) {
            img = imageElement.attr("src")
        }
        return img
    }

    companion object {
        const val SOURCE_ID = "mxdm"
        const val BASE_URL = "http://www.mxdm9.com"
    }
}