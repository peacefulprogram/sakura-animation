package com.jing.sakura.repo

import com.jing.sakura.Constants.SAKURA_URL
import com.jing.sakura.data.*
import com.jing.sakura.extend.encodeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

class WebPageRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    private val weekDays = arrayOf(
        "一",
        "二",
        "三",
        "四",
        "五",
        "六",
        "日"
    )

    suspend fun fetchHomePage(): HomePageData {
        val document = fetchDocument(SAKURA_URL)!!
        val timeLineList = document.select(".tlist ul").mapIndexed { index, ul ->
            val name = "周" + weekDays[index]
            val animeList = ul.children().map { li ->
                val episode: String
                val url: String
                val animeName = li.child(1).text()
                li.child(0) // span
                    .child(0) // a
                    .also { a ->
                        episode = a.text()
                        url = a.absUrl("href")
                    }
                AnimeData(
                    url = url,
                    title = animeName,
                    currentEpisode = episode
                )
            }
            NamedValue(name, animeList)
        }
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
                    imageUrl = imgSrc
                )
            }
            seriesList.add(NamedValue(title, seriesData))
        }
        return HomePageData(
            timeLineList = timeLineList,
            seriesList = seriesList
        )
    }

    suspend fun fetchDetailPage(url: String): AnimeDetailPageData {
        val document = fetchDocument(url)!!
        val animeName = document.select("div.rate.r > h1").text()
        val releaseDay: String
        val region: NamedValue<String>
        val tags: List<NamedValue<String>>
        val episode: String
        val animeAlias: String
        document.select("div.rate.r>.sinfo")[0].apply {
            animeAlias = child(0).text().removePrefix("别名:").trim()
            releaseDay = child(1).text().removePrefix("上映:").trim()
            region = child(2)
                .child(1) // a
                .run {
                    NamedValue(text(), absUrl("href"))
                }
            tags = child(3).select("a").map {
                NamedValue(it.text(), it.absUrl("href"))
            }
            episode = child(6).text().removePrefix("更新至：").trim()

        }
        val imageUrl = document.select(".thumb.l img")[0].absUrl("src")
        val otherAnimeList = document.select(".sido.r .pics li").map { li ->
            AnimeData(
                url = li.select("a")[0].absUrl("href"),
                title = li.select("h2")[0].text(),
                imageUrl = li.select("img")[0].absUrl("src")
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
                ul.children().map { li ->
                    li.child(0).run {
                        AnimePlayListEpisode(
                            episode = text(),
                            url = absUrl("href")
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
            animeName = animeName,
            releaseDay = releaseDay,
            region = region,
            tags = tags,
            description = description,
            imageUrl = imageUrl,
            otherAnimeList = otherAnimeList,
            playLists = playLists,
            animeAlias = animeAlias,
            latestEpisode = episode,
            defaultPlayListIndex = defaultPlayListIndex
        )

    }

    suspend fun searchAnimation(keyword: String, page: Int, pageSize: Int = 24): SearchPageData =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf("kw=${keyword.encodeUrl()}")
            if (page > 1) {
                queryParts.add("pagesize=${pageSize}")
                queryParts.add("pageindex=${page}")
            } else {
                queryParts.add("ex=1")
            }
            val document =
                fetchDocument("$SAKURA_URL/s_all?${queryParts.joinToString("&")}")!!
            val hasNextPage = document.select(".pages")[0].children().find {
                it.text().contains("下一页")
            } != null

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
                    tags = tags
                )
            }

            SearchPageData(page = page, hasNextPage = hasNextPage, animeList = animeList)
        }


    private suspend fun fetchDocument(url: String): Document? {
        return okHttpClient.newCall(Request.Builder().url(url).get().build())
            .execute()
            .body
            ?.byteString()
            ?.string(Charsets.UTF_8)
            ?.run {
                Jsoup.parse(this).apply {
                    setBaseUri(SAKURA_URL)
                }
            }
    }
}