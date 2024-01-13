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
import com.jing.sakura.extend.CloudFlareHelper
import com.jing.sakura.extend.asDocument
import com.jing.sakura.extend.bodyString
import com.jing.sakura.extend.encodeUrl
import com.jing.sakura.extend.newGetRequest
import com.jing.sakura.extend.newRequest
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Stack
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.ExperimentalEncodingApi

class ChangZhangSource(val okHttpClient: OkHttpClient) : AnimationSource {
    override val sourceId: String
        get() = "changzhang"
    override val name: String
        get() = "厂长资源"
    override val pageSize: Int
        get() = 25

    override suspend fun fetchHomePageData(): HomePageData {
        val doc = okHttpClient.newGetRequest {
            url(BASE_URL)
        }.asDocument()
        val series = mutableListOf<NamedValue<List<AnimeData>>>()
        doc.getElementsByClass("mi_btcon").forEach { groupEl ->
            val name = groupEl.selectFirst(".bt_tit a")?.text()?.trim()
            val videos = groupEl.select(".bt_img > ul > li").map { it.parseAnime() }
            if (name != null && videos.isNotEmpty()) {
                series.add(NamedValue(name, value = videos))
            }

        }
        return HomePageData(seriesList = series)
    }

    private fun Element.parseAnime(): AnimeData {
        val url = selectFirst("a")!!.absUrl("href")
        val image = selectFirst("img")!!.let {
            it.dataset()["original"] ?: it.attr("src")
        }
        val ep = selectFirst(".jidi")?.text()?.trim()
        val title = selectFirst(".dytit")!!.text().trim()
        return AnimeData(
            id = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.')),
            url = url,
            title = title,
            currentEpisode = ep ?: "",
            imageUrl = image,
            sourceId = sourceId
        )
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData {
        val doc = okHttpClient.newGetRequest {
            url("$BASE_URL/movie/$animeId.html")
        }.asDocument()
        val episodes = doc.select(".paly_list_btn > a").map { epEl ->
            val url = epEl.attr("href")
            AnimePlayListEpisode(
                episode = epEl.text().trim(),
                episodeId = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))
            )
        }
        val container = doc.selectFirst(".dyxingq")!!
        val image = container.selectFirst(".dyimg > img")!!.let {
            it.dataset()["original"] ?: it.attr("src")
        }
        val title = container.selectFirst(".dytext > .moviedteail_tt > h1")!!.text()
        val infoList = container.select(".dytext > .moviedteail_list > li").map { it.text().trim() }
        val desc = doc.selectFirst(".yp_context")?.text()?.trim()
        return AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            description = desc ?: "",
            imageUrl = image,
            playLists = if (episodes.isEmpty()) emptyList() else listOf(
                AnimePlayList(
                    name = "在线播放",
                    episodeList = episodes
                )
            ),
            infoList = infoList,
            otherAnimeList = doc.select(".cai_list > .bt_img > ul > li").map { it.parseAnime() }
        )
    }

    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        val html = okHttpClient.newGetRequest {
            url("$BASE_URL/v_play/$episodeId.html")
        }.bodyString()
        return Resource.Success(
            data = AnimationSource.VideoUrlResult(
                url = getVideoPlainUrlFromHtml(
                    html
                )
            )
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun getVideoPlainUrlFromHtml(html: String): String {
        val iframeSrc = Jsoup.parse(html).selectFirst(".videoplay > iframe")?.attr("src") ?: ""
        if (iframeSrc.isNotEmpty()) {
            return getVideoUrlFromIframe(iframeSrc)
        }
        val variableName = extractVariableName(html)
        val source = extractVariableValue(html, variableName)
        val (key, iv) = extractKeyAndIv(html)

        val code = with(Cipher.getInstance("AES/CBC/PKCS5Padding")) {
            init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
            )
            doFinal(kotlin.io.encoding.Base64.decode(source)).toString(Charsets.UTF_8)
        }
        val urlIndex = code.indexOf("url")
        val startIndex = code.indexOf(':', startIndex = urlIndex + 3) + 1
        var quoteStartIndex = -1
        for (i in startIndex until code.length) {
            val c = code[i]
            if (c == '\'' || c == '"') {
                if (quoteStartIndex == -1) {
                    quoteStartIndex = i
                } else {
                    return code.substring(quoteStartIndex + 1, i)
                }
            }
        }
        throw RuntimeException()
    }

    private fun getStringVariableValue(html: String, variableName: String): String? {
        val keyword = " $variableName"
        val idx = html.indexOf(keyword)
        if (idx == -1) {
            return null
        }
        var start = -1
        var quote = '"'
        for (i in (idx + keyword.length) until html.length) {
            val c = html[i]
            if (start == -1 && (c == '\'' || c == '"')) {
                start = i
                quote = c
                continue
            }
            if (start > 0 && c == quote) {
                return html.substring(start + 1, i)
            }
        }
        return null

    }

    private suspend fun getVideoUrlFromIframe(iframeSrc: String): String {
        val resp = okHttpClient.newGetRequest {
            url(iframeSrc)
            header("referer", "https://www.czzy88.com/")
            header("sec-fetch-dest", "iframe")
            header("Sec-Fetch-Mode", "navigate")
            header("Sec-Fetch-Site", "cross-site")
        }
        val html = resp.body!!.string()
        val keyword = "\"data\""
        val idx = html.indexOf(keyword)
        if (idx == -1) {
            return getVideoUrlOfPlayerAndRand(html)
        }
        val startIndex = html.indexOf('"', startIndex = idx + keyword.length)
        var end = -1
        for (i in (startIndex + 1) until html.length) {
            if (html[i] == '"') {
                end = i;
                break
            }
        }
        if (end <= startIndex) {
            throw RuntimeException("未获取到data值")
        }
        val dataValue = html.substring(startIndex + 1, end)
        val newStr = dataValue.reversed().chunked(2)
            .map { it.toInt(16).toChar() }
            .joinToString(separator = "")
        val splitIndex = (newStr.length - 7) / 2
        return newStr.substring(0, splitIndex) + newStr.substring(splitIndex + 0x7)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getVideoUrlOfPlayerAndRand(html: String): String {
        val rand = getStringVariableValue(html, "rand") ?: throw RuntimeException("未找到rand变量")
        val player =
            getStringVariableValue(html, "player") ?: throw RuntimeException("未找到player变量")
        val key = "VFBTzdujpR9FWBhe"
        val config = with(Cipher.getInstance("AES/CBC/PKCS5Padding")) {
            init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(rand.toByteArray(Charsets.UTF_8))
            )
            doFinal(kotlin.io.encoding.Base64.decode(player)).toString(Charsets.UTF_8)
        }
        val map = Gson().fromJson<Map<String, Any>>(config, Map::class.java)
        return map["url"]?.let { it as String } ?: throw RuntimeException("未获取到url")
    }

    private fun extractKeyAndIv(html: String): Pair<String, String> {
        var start = 0
        val keyAndIv = mutableListOf<String>()
        val keyword = "Utf8.parse("
        while (start < html.length) {
            val idx = html.indexOf(keyword, startIndex = start)
            if (idx == -1) {
                break
            }
            val valueStart = idx + keyword.length
            for (i in valueStart until html.length) {
                if (html[i] == ')') {
                    val value = html.substring(valueStart, i)
                    keyAndIv.add(value.trim('"', '\''))
                    if (keyAndIv.size == 2) {
                        return keyAndIv[0] to keyAndIv[1]
                    }
                    start = i + 1
                    break
                }
            }
        }
        throw RuntimeException("未获取到key和iv")
    }

    private fun extractVariableValue(html: String, name: String): String {
        val idx = html.indexOf(" $name")
        if (idx == -1) {
            throw RuntimeException()
        }
        var quoteStart = -1
        for (i in (idx + name.length + 1) until html.length) {
            val c = html[i]
            if (c == '\'' || c == '"') {
                if (quoteStart == -1) {
                    quoteStart = i
                } else {
                    return html.substring(quoteStart + 1, i)
                }
            }
        }
        throw RuntimeException()
    }

    private fun extractVariableName(html: String): String {
        val index = html.indexOf("eval(")
        if (index == -1) {
            throw RuntimeException("未找到eval函数调用")
        }
        val bracketStack = Stack<Int>()
        for (i in (index + 4) until html.length) {
            val c = html[i]
            if (c == '(') {
                bracketStack.add(i)
            } else if (c == ')') {
                return html.substring(bracketStack.peek() + 1, i)
            }
        }
        throw RuntimeException("数据不存在")
    }

    override fun supportSearch(): Boolean = true

    override fun supportSearchByCategory(): Boolean = true

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val doc = okHttpClient.newRequest(CloudFlareHelper) {
            url("$BASE_URL/xssearch?q=${keyword.encodeUrl()}&f=_all&p=$page")
            get()
        }.asDocument()
        val videos = doc.select(".search_list > ul > li").map { it.parseAnime() }
        return AnimePageData(
            page = page,
            hasNextPage = doc.hasNextPage(),
            animeList = videos
        )
    }

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        return listOf(
            VideoCategoryGroup.NormalCategoryGroup(
                name = "频道",
                key = "movie_bt_series",
                defaultValue = "",
                categories = listOf(
                    VideoCategory(label = "全部", value = ""),
                    VideoCategory(label = "电影", value = "dyy"),
                    VideoCategory(label = "电视剧", value = "dianshiju"),
                    VideoCategory(label = "国产剧", value = "guochanju"),
                    VideoCategory(label = "动画", value = "dohua"),
                    VideoCategory(label = "韩剧", value = "hj"),
                    VideoCategory(label = "韩国电影", value = "hanguodianying"),
                    VideoCategory(label = "美剧", value = "mj"),
                    VideoCategory(label = "俄罗斯电影", value = "eluosidianying"),
                    VideoCategory(label = "加拿大电影", value = "jianadadianying"),
                    VideoCategory(label = "华语电影", value = "huayudianying"),
                    VideoCategory(label = "印度电影", value = "yindudianying"),
                    VideoCategory(label = "日剧", value = "rj"),
                    VideoCategory(label = "日本电影", value = "ribendianying"),
                    VideoCategory(label = "欧美电影", value = "meiguodianying"),
                    VideoCategory(label = "海外剧", value = "hwj"),
                    VideoCategory(label = "站长推荐", value = "zhanchangtuijian"),
                    VideoCategory(label = "会员专区", value = "huiyuanzhuanqu"),
                )
            ),
            VideoCategoryGroup.NormalCategoryGroup(
                name = "分类",
                key = "movie_bt_tags",
                defaultValue = "",
                categories = listOf(
                    VideoCategory(label = "全部", value = ""),
                    VideoCategory(label = "冒险", value = "maoxian"),
                    VideoCategory(label = "科幻", value = "kh"),
                    VideoCategory(label = "剧情", value = "juqing"),
                    VideoCategory(label = "动作", value = "dozuo"),
                    VideoCategory(label = "动漫", value = "doman"),
                    VideoCategory(label = "动画", value = "dhh"),
                    VideoCategory(label = "历史", value = "lishi"),
                    VideoCategory(label = "古装", value = "guzhuang"),
                    VideoCategory(label = "喜剧", value = "xiju"),
                    VideoCategory(label = "奇幻", value = "qihuan"),
                    VideoCategory(label = "家庭", value = "jiating"),
                    VideoCategory(label = "恐怖", value = "kubu"),
                    VideoCategory(label = "悬疑", value = "xuanyi"),
                    VideoCategory(label = "情色", value = "qingse"),
                    VideoCategory(label = "惊悚", value = "kingsong"),
                    VideoCategory(label = "战争", value = "zhanzheng"),
                    VideoCategory(label = "歌舞", value = "gw"),
                    VideoCategory(label = "武侠", value = "wuxia"),
                    VideoCategory(label = "灾难", value = "zainan"),
                    VideoCategory(label = "爱情", value = "aiqing"),
                    VideoCategory(label = "犯罪", value = "fanzui"),
                    VideoCategory(label = "真人秀", value = "zrx"),
                    VideoCategory(label = "短片", value = "dp"),
                    VideoCategory(label = "纪录片", value = "jlpp"),
                    VideoCategory(label = "西部", value = "xb"),
                    VideoCategory(label = "儿童", value = "etet"),
                    VideoCategory(label = "运动", value = "yd"),
                    VideoCategory(label = "音乐", value = "yy"),
                    VideoCategory(label = "传记", value = "chuanji"),
                    VideoCategory(label = "鬼怪", value = "%e9%ac%bc%e6%80%aa"),
                    VideoCategory(label = "同性", value = "tongxing"),
                )
            ),
        )
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val urlBuilder = StringBuilder(BASE_URL)
            .append("/movie_bt")
        listOf("movie_bt_tags", "movie_bt_series").forEach { k ->
            categories.find { it.name == k && it.value.isNotEmpty() }
                ?.let { urlBuilder.append('/').append(k).append('/').append(it.value) }
        }
        if (page > 1) {
            urlBuilder.append("/page/").append(page)
        }
        val doc = okHttpClient.newGetRequest {
            url(urlBuilder.toString())
        }.asDocument()
        val videos = doc.select(".bt_img > ul > li").map { it.parseAnime() }
        return AnimePageData(
            page = page,
            hasNextPage = doc.hasNextPage(),
            animeList = videos
        )
    }

    private fun Document.hasNextPage(): Boolean {
        val pageItems = select(".pagenavi_txt > a")
        if (pageItems.isEmpty()) {
            return false
        }
        return !pageItems.last()!!.hasClass("current")
    }


    companion object {
        const val BASE_URL = "https://www.czzy88.com"
    }
}