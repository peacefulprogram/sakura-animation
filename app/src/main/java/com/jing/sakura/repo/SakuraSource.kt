package com.jing.sakura.repo

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.getDocument
import okhttp3.OkHttpClient
import java.util.Calendar
import java.util.Date

class SakuraSource(private val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = SOURCE_ID
    override val name: String
        get() = "樱花动漫"

    override val pageSize: Int
        get() = 15

    private fun String.extractId() =
        this.substring(this.lastIndexOf('/') + 1, this.lastIndexOf('.'))

    override suspend fun fetchHomePageData(): HomePageData {
        val document = okHttpClient.getDocument(SAKURA_URL)
        val seriesTitleEls = document.select(".firs.l .dtit h2")
        val seriesAnimeEls = document.select(".firs.l .img")
        val seriesCount = seriesAnimeEls.size.coerceAtMost(seriesTitleEls.size)

        val seriesList = ArrayList<NamedValue<List<AnimeData>>>(seriesCount)

        for (i in 0 until seriesCount) {
            val title = seriesTitleEls[i].text()
            val seriesData = seriesAnimeEls[i].select("li").map { li ->
                val imgSrc = li.select("img")[0].absUrl("src")
                val animeName: String
                val animeUrl: String
                li.select(".tname a")[0].let { el ->
                    animeName = el.text()
                    animeUrl = el.absUrl("href")
                }

                val episode = li.select("a").run {
                    last()?.text() ?: ""
                }
                AnimeData(
                    url = animeUrl,
                    title = animeName,
                    currentEpisode = episode,
                    imageUrl = imgSrc,
                    sourceId = sourceId,
                    id = animeUrl.extractId()
                )
            }
            seriesList.add(NamedValue(title, seriesData))
        }
        return HomePageData(
            seriesList = seriesList
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val url = "$SAKURA_URL/show/$animeId.html"
        val document = okHttpClient.getDocument(url)
        val animeName = document.select("div.rate.r > h1").text()
        val infoList = document.select("div.rate.r>.sinfo>span").map { it.text().trim() }
        val imageUrl = document.select(".thumb.l img")[0].absUrl("src")
        val otherAnimeList = document.select(".sido.r .pics li").map { li ->
            val otherUrl = li.select("a")[0].absUrl("href")
            AnimeData(
                url = otherUrl,
                title = li.select("h2")[0].text(),
                imageUrl = li.select("img")[0].absUrl("src"),
                sourceId = sourceId,
                id = otherUrl.extractId()
            )
        }
        val description = document.select(".info")[0].text()
        val defaultPlayListIndex =
            document.getElementById("DEF_PLAYINDEX")?.html()?.toIntOrNull() ?: 0
        val playLists: MutableList<AnimePlayList> = mutableListOf()
        document.select(".tabs")[0].apply {
            val nameList = select(".menu0 li").mapIndexed { index, li ->
                li.text()
            }
            val episodeGroup = select(".main0 ul").map { ul ->
                ul.children().mapIndexed { episodeIndex, li ->
                    li.child(0).run {
                        val epUrl = absUrl("href")
                        AnimePlayListEpisode(
                            episode = text(),
                            episodeId = epUrl.substring(
                                epUrl.lastIndexOf('/') + 1,
                                epUrl.lastIndexOf('.')
                            )
                        )
                    }
                }
            }
            for (i in 0 until nameList.size.coerceAtMost(episodeGroup.size)) {
                if (episodeGroup[i].isNotEmpty()) {
                    playLists.add(
                        AnimePlayList(
                            name = nameList[i],
                            episodeList = episodeGroup[i],
                            defaultPlayList = i == defaultPlayListIndex
                        )
                    )
                }
            }
        }

        return AnimeDetailPageData(
            animeId = animeId,
            animeName = animeName,
            description = description,
            imageUrl = imageUrl,
            otherAnimeList = otherAnimeList,
            playLists = playLists,
            defaultPlayListIndex = defaultPlayListIndex,
            infoList = infoList
        )
    }

    override fun supportSearch(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {

        var searchUrl = "${SAKURA_URL}/search/${keyword.encodeUrl()}/"
        if (page > 1) {
            searchUrl += "?page=${page}"
        }
        val document = okHttpClient.getDocument(searchUrl)
        val noNextPage =
            document.select(".pages").takeIf { it.isNotEmpty() }?.let { it[0].children() }
                ?.let { pages ->
                    pages.indexOfFirst { it.tagName() == "span" } in arrayOf(
                        -1, pages.size - 2
                    )
                } ?: true

        val animeList = document.select(".lpic >ul > li").map { li ->
            val url = li.child(0).absUrl("href")
            val animeName = li.child(1).text()
            val episode = li.child(2).text()
            val tags = li.child(3).text()
            val description = li.child(4).text()

            AnimeData(
                url = url,
                title = animeName,
                currentEpisode = episode,
                imageUrl = li.select("img")[0].absUrl("src"),
                description = description,
                tags = tags,
                sourceId = sourceId,
                id = url.extractId()
            )
        }

        return AnimePageData(
            page = page,
            hasNextPage = !noNextPage,
            animeList = animeList
        )
    }

    override suspend fun fetchVideoUrl(episodeId: String): Resource<AnimationSource.VideoUrlResult> {
        return try {
            okHttpClient.getDocument("$SAKURA_URL/v/$episodeId.html").select(".bofang > div")
                .takeIf { it.size > 0 }
                ?.first()
                ?.attr("data-vid")?.let {
                    val dollarIndex = it.lastIndexOf('$')
                    if (dollarIndex == -1) {
                        it
                    } else {
                        it.substring(0 until dollarIndex)
                    }
                }?.let { Resource.Success(AnimationSource.VideoUrlResult(it)) }
                ?: Resource.Error("加载视频链接失败")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "")
        }
    }

    override fun supportTimeline(): Boolean = true

    override suspend fun fetchUpdateTimeline(): UpdateTimeLine {
        val home = okHttpClient.getDocument(SAKURA_URL)
        val container =
            home.selectFirst(".side.r>.bg") ?: throw RuntimeException("未找到更新时间表")
        val names = container.selectFirst(".tag")!!.children().mapIndexed() { index, el ->
            el.text().trim()
        }
        val timeline = ArrayList<Pair<String, List<AnimeData>>>(names.size)
        container.select(".tlist>ul").forEachIndexed { index, ul ->
            if (index < names.size) {
                val anime = ul.select("li>a").map {
                    val animeUrl = it.absUrl("href")
                    AnimeData(
                        url = animeUrl,
                        title = it.text().trim(),
                        sourceId = sourceId,
                        id = animeUrl.extractId()
                    )
                }
                timeline.add(Pair(names[index], anime))
            }
        }

        return UpdateTimeLine(
            current = Calendar.getInstance().run {
                time = Date()
                (get(Calendar.DAY_OF_WEEK) + 5) % 7
            },
            timeline = timeline
        )
    }

    companion object {
        const val SAKURA_URL = "http://www.iyinghua.io"

        const val SOURCE_ID = "yinghua"
    }
}