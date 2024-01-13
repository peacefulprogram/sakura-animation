package com.jing.sakura.repo

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class WedmSource(
    private val okHttpClient: OkHttpClient
) : AnimationSource {

    private var baseUrl: String? = null

    private val baseUrlLock = Mutex()

    override val sourceId: String
        get() = "wedm"
    override val name: String
        get() = "WE动漫"
    override val pageSize: Int
        get() = 36

    override suspend fun fetchHomePageData(): HomePageData {
        val document = okHttpClient.getDocument(toAbsolute("/"))
        val animeSeries = mutableListOf<NamedValue<List<AnimeData>>>()
        document.selectFirst(".flickity-slider")?.let { container ->
            animeSeries.add(
                NamedValue(
                    "热门推荐",
                    container.getElementsByClass("myui-vodlist__box").map { it.parseAnime() }
                )
            )
        }
        document.getElementsByClass("myui-panel").forEach { panelEl ->
            val videos = panelEl.getElementsByClass("myui-vodlist__box").map { it.parseAnime() }
            if (videos.isNotEmpty()) {
                animeSeries.add(
                    NamedValue(
                        name = panelEl.selectFirst(".title")!!.text(),
                        value = videos
                    )
                )
            }
        }
        return HomePageData(sourceId = sourceId, animeSeries)
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val document = okHttpClient.getDocument(toAbsolute("/video/$animeId.html"))
        val imageUrl = document.selectFirst(".myui-vodlist__thumb img")!!.dataset()["original"]!!
        val detailContainer = document.selectFirst(".myui-content__detail")!!
        val title = detailContainer.selectFirst(".title")!!.text().trim()
        val currentEpisode =
            document.selectFirst("div.myui-content__thumb  pic-text.text-right")?.text()?.trim()
                ?: ""
        val infoList = detailContainer.getElementsByClass("data").map { it.text().trim() }.run {
            if (currentEpisode.isEmpty()) {
                this
            } else {
                listOf(currentEpisode) + this
            }
        }
        val desc = detailContainer.selectFirst(".desc .sketch")?.text()?.trim() ?: ""
        val playlistNames =
            document.selectFirst("ul.nav-tabs")?.children()?.map { it.text().trim() } ?: emptyList()
        val episodesGroup = document.select(".tab-content > .tab-pane").asSequence()
            .filter { it.id().startsWith("playlist") }
            .map { epContainer ->
                epContainer.select("a").map {
                    AnimePlayListEpisode(
                        episodeId = it.attr("href").extractIdFromUrl(),
                        episode = it.text().trim()
                    )
                }
            }
            .toList()
        val playlists = List(playlistNames.size.coerceAtMost(episodesGroup.size)) {
            AnimePlayList(
                playlistNames[it],
                episodesGroup[it]
            )
        }
        return AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            description = desc,
            imageUrl = imageUrl,
            playLists = playlists,
            infoList = infoList,
            otherAnimeList = document.getElementsByClass("myui-vodlist__box")
                .asSequence()
                .map { it.parseAnime() }
                .distinctBy { it.id }
                .toList()
        )
    }

    override fun supportSearch(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val document =
            okHttpClient.getDocument(toAbsolute("/search/${keyword.encodeUrl()}----------$page---.html"))
        val videos = document.getElementById("searchList")?.children()?.map { it.parseAnime() }
            ?: emptyList()
        return AnimePageData(
            page = page,
            hasNextPage = hasNextPage(document = document),
            animeList = videos
        )
    }

    private fun hasNextPage(document: Document): Boolean =
        document.selectFirst(".myui-page")?.getElementsByTag("a")
            ?.asSequence()
            ?.filter { it.attr("href").isNotBlank() }
            ?.toList()
            ?.run {
                indexOfLast { it.hasClass("btn-warm") } < size - 3
            } ?: false

    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val newHtml =
            okHttpClient.getHtml(
                "https://danmu.yhdmjx.com/m3u8.php?url=" + extractPlayerParam(
                    okHttpClient.getHtml(
                        toAbsolute(
                            "/play/$episodeId.html"
                        )
                    )
                )
            )
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
        return Resource.Success(AnimationSource.VideoUrlResult(plainUrl))
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


    private fun Element.parseAnime(): AnimeData {
        val linkEl = selectFirst("a")!!
        val url = linkEl.absUrl("href")
        val imageUrl = linkEl.dataset()["original"] ?: linkEl.attr("style").run {
            val openBracketIndex = indexOf('(', indexOf("url"))
            val closeBracketIndex = indexOf(')', openBracketIndex)
            try {
                substring(openBracketIndex + 1, closeBracketIndex).trim()
            } catch (ex: Exception) {
                Log.e(TAG, "parseAnime: $this", ex)
                ""
            }
        }
        val episode = linkEl.children().last()?.text()?.trim() ?: ""
        val title =
            selectFirst(".myui-vodlist__detail .title")?.text()?.trim() ?: linkEl.attr("title")
                .trim()
        return AnimeData(
            id = url.extractIdFromUrl(),
            url = url,
            title = title,
            currentEpisode = episode,
            imageUrl = imageUrl,
            sourceId = sourceId
        )
    }

    private fun String.extractIdFromUrl(): String =
        this.substring(this.lastIndexOf('/') + 1, this.lastIndexOf('.'))


    private suspend fun toAbsolute(url: String): String = requireBaseUrl() + url

    private suspend fun requireBaseUrl(): String {
        if (baseUrl == null) {
            baseUrlLock.withLock {
                if (baseUrl != null) {
                    return baseUrl!!
                }
                val doc = okHttpClient.getDocument(PUBLISH_PAGE_URL)
                val website = doc.selectFirst(".main .item p")?.text()?.trim()?.let { text ->
                    val colonIndex =
                        text.lastIndexOf('：').takeIf { it >= 0 } ?: text.lastIndexOf(':')
                    if (colonIndex >= 0) {
                        text.substring(colonIndex + 1).trim()
                    } else {
                        null
                    }
                }
                baseUrl = if (website?.isNotBlank() == true) {
                    "https://$website"
                } else {
                    "www.886dm.tv"
                }
            }
        }
        return baseUrl!!
    }

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        val document = okHttpClient.getDocument(toAbsolute("/type/ribendongman.html"))
        val elements =
            document.select(".container .row .myui-panel_bd ul.myui-screen__list.nav-slide")
        val ignoreKey = "ribendongman"
        // 忽略第一个
        val result = List(elements.size - 1) { elementIndex ->
            val row = elements[elementIndex + 1]
            val linkList = row.getElementsByTag("a")
            val name = linkList[0].text().trim()
            val valueIndex = linkList.last()!!.attr("href").run {
                substring(lastIndexOf('/') + 1, lastIndexOf('.'))
            }
                .split('-')
                .indexOfFirst { it.isNotEmpty() && it != ignoreKey }
            // 忽略第一个
            val categories = List(linkList.size - 1) {
                val el = linkList[it + 1]
                val value = el.attr("href").run {
                    substring(lastIndexOf('/') + 1, lastIndexOf('.'))
                }
                    .split('-')[valueIndex]
                VideoCategory(label = el.text().trim(), value = value)
            }
            VideoCategoryGroup.NormalCategoryGroup(
                name = name,
                key = valueIndex.toString(),
                defaultValue = categories[0].value,
                categories = categories
            )
        }
        val channel = VideoCategoryGroup.NormalCategoryGroup(
            name = "频道",
            key = "0",
            defaultValue = "ribendongman",
            categories = listOf(
                VideoCategory(label = "日漫", value = "ribendongman"),
                VideoCategory(label = "国漫", value = "guochandongman"),
                VideoCategory(label = "动漫电影", value = "dongmandianying"),
                VideoCategory(label = "欧美动漫", value = "oumeidongman")
            )
        )
        return listOf(channel) + result
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val params = MutableList(12) { "" }
        params[8] = page.toString()
        categories.forEach { params[it.name.toInt()] = it.value }
        val document =
            okHttpClient.getDocument(toAbsolute("/show/${params.joinToString("-")}.html"))
        val videos = document.getElementsByClass("myui-vodlist__box").map { it.parseAnime() }
        return AnimePageData(
            page = page,
            hasNextPage = hasNextPage(document = document),
            animeList = videos
        )
    }

    companion object {
        const val PUBLISH_PAGE_URL = "https://wedm.cc/"
        private const val TAG = "WedmSource"
    }


}