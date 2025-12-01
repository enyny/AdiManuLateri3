package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.ArrayList
import com.AdiManuLateri3.BuildConfig 

val session = Session(Requests().baseClient)

object Lateri3PlayExtractor : Lateri3Play() {

    // --- 1. MultiEmbed ---
    suspend fun invokeMultiEmbed(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "https://multiembed.mov/directstream.php?video_id=$imdbId"
        } else {
            "https://multiembed.mov/directstream.php?video_id=$imdbId&s=$season&e=$episode"
        }
        try {
            val res = app.get(url, referer = url).documentLarge
            val script = res.selectFirst("script:containsData(function(h,u,n,t,e,r))")?.data()
            if (script != null) {
                val file = Regex("file\":\"(.*?)\"").find(script)?.groupValues?.get(1)
                if (file != null) {
                    callback.invoke(
                        newExtractorLink(
                            "MultiEmbed",
                            "MultiEmbed",
                            url = file.replace("\\", ""),
                            type = INFER_TYPE
                        ) {
                            this.referer = ""
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
        } catch (e: Exception) { Log.e("MultiEmbed", "$e") }
    }

    // --- 2. MultiMovies ---
    suspend fun invokeMultimovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val multimoviesApi = getDomains()?.multiMovies ?: "https://multimovies.cloud"
        val fixTitle = title.createSlug()

        val url = if (season == null) {
            "$multimoviesApi/movies/$fixTitle"
        } else {
            "$multimoviesApi/episodes/$fixTitle-${season}x${episode}"
        }

        try {
            val response = app.get(url, interceptor = CloudflareKiller())
            if (response.code != 200) return
            val req = response.documentLarge
            
            val playerOptions = req.select("ul#playeroptionsul li").map {
                Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
            }

            playerOptions.forEach { (postId, nume, type) ->
                if (!nume.contains("trailer", ignoreCase = true)) {
                    val postResponse = app.post(
                        url = "$multimoviesApi/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to postId,
                            "nume" to nume,
                            "type" to type
                        ),
                        referer = url,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    )
                    if (postResponse.code == 200) {
                        val responseData = postResponse.parsed<ResponseHash>()
                        val embedUrl = responseData.embed_url
                        val link = embedUrl.substringAfter("\"").substringBefore("\"")
                        if (!link.contains("youtube", ignoreCase = true)) {
                            loadCustomExtractor(
                                "Multimovies",
                                link,
                                "$multimoviesApi/",
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("MultiMovies", "$e") }
    }

    // --- 3. KissKH ---
    suspend fun invokeKisskh(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        lastSeason: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val kissKhAPI = BuildConfig.KissKh
        val slug = title.createSlug() ?: return
        val type = if (season == null) "2" else "1"
        try {
            val searchResponse = app.get("$kissKhAPI/api/DramaList/Search?q=$title&type=$type", referer = "$kissKhAPI/")
            val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
            
            val (id, contentTitle) = if (res.size == 1) {
                res.first().id to res.first().title
            } else {
                val data = res.find { it.title.createSlug() == slug } ?: res.find { it.title.equals(title) }
                data?.id to data?.title
            }
            if (id == null) return

            val detailResponse = app.get("$kissKhAPI/api/DramaList/Drama/$id?isq=false")
            val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
            val epsId = if (season == null) resDetail.episodes?.first()?.id else resDetail.episodes?.find { it.number == episode }?.id ?: return

            val sourcesResponse = app.get("$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=", referer = "$kissKhAPI/")
            sourcesResponse.parsedSafe<KisskhSources>()?.let { source ->
                listOf(source.video, source.thirdParty).forEach { link ->
                    val safeLink = link ?: return@forEach
                    if (safeLink.contains(".m3u8") || safeLink.contains(".mp4")) {
                        callback.invoke(
                            newExtractorLink("Kisskh", "Kisskh", fixUrl(safeLink, kissKhAPI), INFER_TYPE) {
                                referer = kissKhAPI
                                quality = Qualities.P720.value
                                headers = mapOf("Origin" to kissKhAPI)
                            }
                        )
                    } else {
                        val cleanedLink = safeLink.substringBefore("?")
                        loadSourceNameExtractor("Kisskh", fixUrl(cleanedLink, kissKhAPI), "$kissKhAPI/", subtitleCallback, callback, Qualities.P720.value)
                    }
                }
            }
            
            val subResponse = app.get("$kissKhAPI/api/Sub/$epsId")
            tryParseJson<List<KisskhSubtitle>>(subResponse.text)?.forEach { sub ->
                subtitleCallback.invoke(newSubtitleFile(getLanguage(sub.label ?: "Unknown"), sub.src ?: return@forEach))
            }
        } catch (e: Exception) { Log.e("KissKH", "$e") }
    }

    // --- 4. KissKH Asia ---
    suspend fun invokeKisskhAsia(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("Referer" to "https://hlscdn.xyz/", "X-Requested-With" to "XMLHttpRequest")
        if (tmdbId == null) return
        val url = if (season != null && season > 1) "https://hlscdn.xyz/e/$tmdbId-$season-${episode.toString().padStart(2, '0')}"
                  else "https://hlscdn.xyz/e/$tmdbId-${episode.toString().padStart(2, '0')}"

        try {
            val responseText = app.get(url, headers = headers).text
            val token = Regex("window\\.kaken=\"(.*?)\"").find(responseText)?.groupValues?.getOrNull(1) ?: return
            
            val apiResponse = app.post("https://hlscdn.xyz/api", headers = headers, requestBody = token.toRequestBody("text/plain".toMediaType())).text
            val json = JSONObject(apiResponse)
            
            val sources = json.optJSONArray("sources")
            if (sources != null) {
                for (i in 0 until sources.length()) {
                    val srcObj = sources.getJSONObject(i)
                    val videoUrl = srcObj.optString("file")
                    if (videoUrl.isNotEmpty()) {
                        callback.invoke(newExtractorLink("KisskhAsia", "KisskhAsia", videoUrl) {
                            this.referer = "https://hlscdn.xyz/"
                            this.quality = Qualities.P720.value
                        })
                    }
                }
            }
        } catch (e: Exception) { Log.e("KissKHAsia", "$e") }
    }

    // --- 5. Player4U ---
    suspend fun invokePlayer4U(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        val Player4uApi = "https://player4u.xyz"
        val query = (if (season != null) "$title S${"%02d".format(season)}E${"%02d".format(episode)}" else "$title $year").replace(" ", "+")
        
        val deferredPages = (0..1).map { page ->
            async {
                try {
                    val url = "$Player4uApi/embed?key=$query" + if (page > 0) "&page=$page" else ""
                    app.get(url, timeout = 20).documentLarge.select(".playbtnx").mapNotNull { element ->
                        val titleText = element.text().split(" | ").lastOrNull()
                        if (titleText != null) titleText to element.attr("onclick") else null
                    }
                } catch (e: Exception) { emptyList() }
            }
        }
        
        deferredPages.awaitAll().flatten().distinctBy { it.first }.forEach { (name, url) ->
            try {
                val subPath = Regex("""go\('(.*?)'\)""").find(url)?.groupValues?.get(1) ?: return@forEach
                val iframeSrc = app.get("$Player4uApi$subPath", timeout = 10, referer = Player4uApi).documentLarge.selectFirst("iframe")?.attr("src")
                if (iframeSrc != null) {
                    val displayName = "Player4U ${name.split("|").lastOrNull()?.trim() ?: ""}"
                    val quality = getPlayer4UQuality(displayName)
                    getPlayer4uUrl(displayName, quality, "https://uqloads.xyz/e/$iframeSrc", Player4uApi, callback)
                }
            } catch (_: Exception) {}
        }
    }

    // --- 6. Vidsrccc ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val vidsrctoAPI = BuildConfig.Vidsrccc
        val url = if (season == null) "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false" else "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        
        try {
            val doc = app.get(url).documentLarge.toString()
            val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
            val variables = mutableMapOf<String, String>()
            regex.findAll(doc).forEach { match ->
                variables[match.groupValues[1]] = match.groupValues[2].ifEmpty { match.groupValues[3] }
            }
            
            val vrf = generateVrfAES(variables["movieId"] ?: "", variables["userId"] ?: "")
            val apiurl = if (season == null) {
                "$vidsrctoAPI/api/$id/servers?id=$id&type=${variables["movieType"]}&v=${variables["v"]}=&vrf=$vrf&imdbId=${variables["imdbId"]}"
            } else {
                "$vidsrctoAPI/api/$id/servers?id=$id&type=${variables["movieType"]}&season=$season&episode=$episode&v=${variables["v"]}&vrf=$vrf&imdbId=${variables["imdbId"]}"
            }
            
            app.get(apiurl).parsedSafe<Vidsrcccservers>()?.data?.forEach {
                val iframe = app.get("$vidsrctoAPI/api/source/${it.hash}").parsedSafe<Vidsrcccm3u8>()?.data?.source
                if (iframe != null && !iframe.contains(".vidbox")) {
                    callback.invoke(newExtractorLink("Vidsrc", "Vidsrc | [${it.name}]", iframe) {
                        this.quality = if (it.name.contains("4K")) Qualities.P2160.value else Qualities.P1080.value
                        this.referer = vidsrctoAPI
                    })
                }
            }
        } catch (e: Exception) { Log.e("Vidsrccc", "$e") }
    }

    // --- 7. Top Movies ---
    suspend fun invokeTopMovies(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val topmoviesAPI = getDomains()?.topMovies ?: "https://topmovies.se"
        val url = if (season == null) "$topmoviesAPI/search/${imdbId.orEmpty()} ${year ?: ""}" else "$topmoviesAPI/search/${imdbId.orEmpty()} Season $season ${year ?: ""}"

        try {
            val hrefpattern = app.get(url).documentLarge.select("#content_box article a").firstOrNull()?.attr("href") ?: return
            val res = app.get(hrefpattern, interceptor = CloudflareKiller()).documentLarge

            if (season == null) {
                res.select("a.maxbutton-download-links").forEach {
                    val detailDoc = app.get(it.attr("href")).documentLarge
                    detailDoc.select("a.maxbutton-fast-server-gdrive").forEach { link ->
                        val finalLink = if (link.attr("href").contains("unblockedgames")) bypassHrefli(link.attr("href")) else link.attr("href")
                        if (finalLink != null) loadSourceNameExtractor("TopMovies", finalLink, "$topmoviesAPI/", subtitleCallback, callback)
                    }
                }
            } else {
                res.select("a.maxbutton-g-drive").forEach {
                    val detailDoc = app.get(it.attr("href")).documentLarge
                    val episodeLink = detailDoc.select("span strong").firstOrNull { el -> 
                        el.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE)) 
                    }?.parent()?.closest("a")?.attr("href")
                    
                    if (episodeLink != null) {
                        val finalLink = if (episodeLink.contains("unblockedgames")) bypassHrefli(episodeLink) else episodeLink
                        if (finalLink != null) loadSourceNameExtractor("TopMovies", finalLink, "$topmoviesAPI/", subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) { Log.e("TopMovies", "$e") }
    }

    // --- 8. RidoMovies ---
    suspend fun invokeRidomovies(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val ridomoviesAPI = BuildConfig.RidomoviesAPI
        try {
            val searchResponse = app.get("$ridomoviesAPI/core/api/search?q=$imdbId", interceptor = CloudflareKiller())
            val mediaSlug = searchResponse.parsedSafe<RidoSearch>()?.data?.items?.find { 
                it.contentable?.tmdbId == tmdbId || it.contentable?.imdb
