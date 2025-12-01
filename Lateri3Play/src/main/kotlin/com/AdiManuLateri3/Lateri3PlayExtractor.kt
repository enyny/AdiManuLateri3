package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.network.CloudflareKiller
// WebViewResolver dipanggil dengan Fully Qualified Name di bawah untuk menghindari error impor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.AdiManuLateri3.BuildConfig 

val session = Session(Requests().baseClient)

object Lateri3PlayExtractor : Lateri3Play() {

    // Constants for ported providers
    private const val movieBoxUrl = "https://api.inmoviebox.com"
    private const val vidfastProApi = "https://vidfast.pro"

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
            
            val vvalue = variables["v"] ?: ""
            val userId = variables["userId"] ?: ""
            val imdbId = variables["imdbId"] ?: ""
            val movieId = variables["movieId"] ?: ""
            val movieType = variables["movieType"] ?: ""

            val vrf = generateVrfAES(movieId, userId)
            val apiurl = if (season == null) {
                "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&v=$vvalue=&vrf=$vrf&imdbId=$imdbId"
            } else {
                "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$vvalue&vrf=$vrf&imdbId=$imdbId"
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
                it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId 
            }?.slug ?: return

            val id = if (season == null) mediaSlug else {
                val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$season/episode-$episode"
                app.get(episodeUrl, interceptor = CloudflareKiller()).text.substringAfterLast("""postid\":\"""").substringBefore("\"")
            }

            val url = "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
            app.get(url, interceptor = CloudflareKiller()).parsedSafe<RidoResponses>()?.data?.forEach { link ->
                val iframe = Jsoup.parse(link.url ?: return@forEach).select("iframe").attr("data-src")
                if (iframe.startsWith("https://closeload.top")) {
                    val unpacked = getAndUnpack(app.get(iframe, referer = "$ridomoviesAPI/", interceptor = CloudflareKiller()).text)
                    val encodeHash = Regex("\\(\"([^\"]+)\"\\);").find(unpacked)?.groupValues?.get(1) ?: ""
                    val video = base64Decode(base64Decode(encodeHash).reversed()).split("|").get(1)
                    callback.invoke(
                        newExtractorLink("Ridomovies", "Ridomovies", url = video, ExtractorLinkType.M3U8) {
                            this.referer = "${getBaseUrl(iframe)}/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                } else {
                    loadSourceNameExtractor("Ridomovies", iframe, "$ridomoviesAPI/", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e("RidoMovies", "$e") }
    }

    // --- 9. Watch32 ---
    suspend fun invokeWatch32APIHQ(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (title.isNullOrBlank()) return
        val Watch32 = "https://watch32.sx"
        val searchUrl = "$Watch32/search/${title.trim().replace(" ", "-")}"
        try {
            val doc = app.get(searchUrl).documentLarge
            val matchedElement = doc.select("div.flw-item").firstOrNull { 
                it.selectFirst("h2.film-name a")?.text()?.contains(title, true) == true 
            }?.selectFirst("h2.film-name a") ?: return
            
            val detailUrl = Watch32 + matchedElement.attr("href")
            val infoId = detailUrl.substringAfterLast("-")
            
            if (season != null) {
                val seasonId = app.get("$Watch32/ajax/season/list/$infoId").documentLarge
                    .select("div.dropdown-menu a").firstOrNull { it.text().contains("Season $season", true) }?.attr("data-id") ?: return
                val episodeId = app.get("$Watch32/ajax/season/episodes/$seasonId").documentLarge
                    .select("li.nav-item a").firstOrNull { it.text().contains("Eps $episode:", true) }?.attr("data-id") ?: return
                
                app.get("$Watch32/ajax/episode/servers/$episodeId").documentLarge.select("li.nav-item a").forEach { source ->
                    val iframeUrl = app.get("$Watch32/ajax/episode/sources/${source.attr("data-id")}").parsedSafe<Watch32>()?.link
                    if (iframeUrl != null) loadSourceNameExtractor("Watch32", iframeUrl, "", subtitleCallback, callback)
                }
            } else {
                app.get("$Watch32/ajax/episode/list/$infoId").documentLarge.select("li.nav-item a").forEach { ep ->
                    val iframeUrl = app.get("$Watch32/ajax/episode/sources/${ep.attr("data-id")}").parsedSafe<Watch32>()?.link
                    if (iframeUrl != null) loadSourceNameExtractor("Watch32", iframeUrl, "", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e("Watch32", "$e") }
    }

    // --- 10. VidSrcXyz ---
    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val Vidsrcxyz = "https://vidsrc-embed.su"
        val url = if (season == null) "$Vidsrcxyz/embed/movie?imdb=$id" else "$Vidsrcxyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        try {
            val iframeUrl = httpsify(app.get(url).documentLarge.select("iframe").attr("src"))
            if (iframeUrl.isNotEmpty()) {
                val doc = app.get(iframeUrl, referer = iframeUrl).document
                val matchedSrc = Regex("src:\\s+'(.*?)'").find(doc.html())?.groupValues?.get(1)
                if (matchedSrc != null) {
                    val prorcpUrl = getBaseUrl(iframeUrl) + matchedSrc
                    val responseText = app.get(prorcpUrl, referer = iframeUrl).text
                    val temp = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""").find(responseText)?.groupValues?.get(1)
                    if (!temp.isNullOrEmpty()) {
                        M3u8Helper.generateM3u8("VidsrcXYZ", temp, prorcpUrl.substringBefore("rcp")).forEach(callback)
                    }
                }
            }
        } catch (e: Exception) { Log.e("VidSrcXyz", "$e") }
    }

    // --- 11. PrimeSrc ---
    suspend fun invokePrimeSrc(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val PrimeSrcApi = "https://primesrc.me"
        val url = if (season == null) "$PrimeSrcApi/api/v1/s?imdb=$imdbId&type=movie" else "$PrimeSrcApi/api/v1/s?imdb=$imdbId&season=$season&episode=$episode&type=tv"
        try {
            val serverList = app.get(url, timeout = 30).parsedSafe<PrimeSrcServerList>()
            serverList?.servers?.forEach {
                val rawServerJson = app.get("$PrimeSrcApi/api/v1/l?key=${it.key}", timeout = 30).text
                val link = JSONObject(rawServerJson).optString("link", "")
                loadSourceNameExtractor("PrimeWire", link, PrimeSrcApi, subtitleCallback, callback)
            }
        } catch (e: Exception) { Log.e("PrimeSrc", "$e") }
    }

    // --- 12. Vidzee ---
    suspend fun invokeVidzee(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val keyBytes = "6966796f75736372617065796f75617265676179000000000000000000000000".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val defaultReferer = "https://core.vidzee.wtf/"
        for (sr in 1..8) {
            try {
                val apiUrl = if (season == null) "https://player.vidzee.wtf/api/server?id=$id&sr=$sr" 
                             else "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                val response = app.get(apiUrl).text
                val json = JSONObject(response)
                val urls = json.optJSONArray("url") ?: JSONArray()
                for (i in 0 until urls.length()) {
                    val obj = urls.getJSONObject(i)
                    val encryptedLink = obj.optString("link")
                    if (encryptedLink.isNotBlank()) {
                        val finalUrl = decryptVidzeeUrl(encryptedLink, keyBytes)
                        callback.invoke(newExtractorLink("VidZee", "VidZee ${obj.optString("name")}", finalUrl) {
                            this.referer = defaultReferer
                            this.quality = Qualities.P1080.value
                        })
                    }
                }
            } catch (e: Exception) {}
        }
    }

    // --- 13. UHDMovies ---
    suspend fun invokeUhdmovies(title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, subtitleCallback: (SubtitleFile) -> Unit) {
        val uhdmoviesAPI = getDomains()?.uhdmovies ?: "https://uhdmovies.wiki"
        val searchUrl = "$uhdmoviesAPI/search/${title?.replace(" ", "+")} $year"
        try {
            val response = app.get(searchUrl)
            val postUrl = response.documentLarge.select("article div.entry-image a").firstOrNull()?.attr("href") ?: return
            val doc = app.get(postUrl).documentLarge
            doc.select("a[href]").forEach {
                val href = it.attr("href")
                if (href.contains("drive") || href.contains("pixel")) {
                    val finalLink = if (href.contains("unblockedgames")) bypassHrefli(href) else href
                    if (finalLink != null) loadSourceNameExtractor("UHDMovies", finalLink, "", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e("UHDMovies", "$e") }
    }

    // --- 14. MovieBox (PORTED & OPTIMIZED) ---
    suspend fun invokeMovieBox(
        title: String?,
        season: Int? = 0,
        episode: Int? = 0,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            if (title.isNullOrBlank()) return false

            val url = "$movieBoxUrl/wefeed-mobile-bff/subject-api/search/v2"
            val jsonBody = """{"page":1,"perPage":10,"keyword":"$title"}"""
            val xClientToken = generateXClientToken()
            val xTrSignature = generateXTrSignature(
                "POST", "application/json", "application/json; charset=utf-8", url, jsonBody
            )
            val headers = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; Android 16)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to xTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03"}""",
                "x-client-status" to "0"
            )

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = app.post(url, headers = headers, requestBody = requestBody)
            if (response.code != 200) return false

            val root = JSONObject(response.body.string())
            val results = root.optJSONObject("data")?.optJSONArray("results") ?: return false

            val matchingIds = mutableListOf<String>()
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val subjects = result.optJSONArray("subjects") ?: continue
                for (j in 0 until subjects.length()) {
                    val subject = subjects.getJSONObject(j)
                    val name = subject.optString("title")
                    val id = subject.optString("subjectId")
                    val type = subject.optInt("subjectType")
                    if (name.contains(title, ignoreCase = true) && (type == 1 || type == 2)) {
                        matchingIds.add(id)
                    }
                }
            }
            if (matchingIds.isEmpty()) return false

            var foundLinks = false

            for (id in matchingIds) {
                try {
                    val subjectUrl = "$movieBoxUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
                    val subjectXToken = generateXClientToken()
                    val subjectXSign = generateXTrSignature(
                        "GET", "application/json", "application/json", subjectUrl
                    )
                    val subjectHeaders = headers + mapOf(
                        "x-client-token" to subjectXToken,
                        "x-tr-signature" to subjectXSign
                    )
                    val subjectRes = app.get(subjectUrl, headers = subjectHeaders)
                    if (subjectRes.code != 200) continue

                    val subjectJson = JSONObject(subjectRes.body.string())
                    val subjectData = subjectJson.optJSONObject("data") ?: continue
                    val subjectIds = mutableListOf<Pair<String, String>>()
                    var originalLanguageName = "Original"

                    // handle dubs
                    val dubs = subjectData.optJSONArray("dubs")
                    if (dubs != null) {
                        for (i in 0 until dubs.length()) {
                            val dub = dubs.getJSONObject(i)
                            val dubId = dub.optString("subjectId")
                            val lanName = dub.optString("lanName")
                            if (dubId.isNotEmpty() && lanName.isNotEmpty()) {
                                if (dubId == id) {
                                    originalLanguageName = lanName
                                } else {
                                    subjectIds.add(Pair(dubId, lanName))
                                }
                            }
                        }
                    }
                    subjectIds.add(0, Pair(id, originalLanguageName))

                    for ((subjectId, language) in subjectIds) {
                        val playUrl =
                            "$movieBoxUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=${season ?: 0}&ep=${episode ?: 0}"
                        val token = generateXClientToken()
                        val sign = generateXTrSignature(
                            "GET", "application/json", "application/json", playUrl
                        )
                        val playHeaders =
                            headers + mapOf("x-client-token" to token, "x-tr-signature" to sign)

                        val playRes = app.get(playUrl, headers = playHeaders)
                        if (playRes.code != 200) continue

                        val playRoot = JSONObject(playRes.body.string())
                        val streams = playRoot.optJSONObject("data")?.optJSONArray("streams") ?: continue

                        for (i in 0 until streams.length()) {
                            val stream = streams.getJSONObject(i)
                            val streamId = stream.optString("id", "$subjectId|$season|$episode")
                            val subjectTitle = subjectData.optString("title", "Unknown Title")
                            val format = stream.optString("format")
                            val signCookie = stream.optString("signCookie").takeIf { it.isNotEmpty() }

                            val resolutionNodes = stream.optJSONArray("resolutionList") ?: stream.optJSONArray("resolutions")

                            if (resolutionNodes != null) {
                                for (j in 0 until resolutionNodes.length()) {
                                    val resNode = resolutionNodes.getJSONObject(j)
                                    val resUrl = resNode.optString("resourceLink")
                                    val quality = resNode.optInt("resolution", 0)

                                    if(resUrl.isEmpty()) continue

                                    callback.invoke(
                                        newExtractorLink(
                                            source = "MovieBox ($language)",
                                            name = "MovieBox ($language) [$subjectTitle]",
                                            url = resUrl,
                                            type = when {
                                                resUrl.startsWith("magnet:", true) -> ExtractorLinkType.MAGNET
                                                resUrl.endsWith(".mpd", true) -> ExtractorLinkType.DASH
                                                resUrl.endsWith(".torrent", true) -> ExtractorLinkType.TORRENT
                                                format.equals("HLS", true) || resUrl.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                                                else -> INFER_TYPE
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to movieBoxUrl) +
                                                    (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                            this.quality = getQualityFromName("$quality")
                                        }
                                    )
                                    foundLinks = true
                                }
                            } else {
                                // fallback single url
                                val singleUrl = stream.optString("url")
                                val resText = stream.optString("resolutions", "")

                                if(singleUrl.isNotEmpty()) {
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "MovieBox ($language)",
                                            name = "MovieBox ($language) [$subjectTitle]",
                                            url = singleUrl,
                                            type = when {
                                                singleUrl.startsWith("magnet:", true) -> ExtractorLinkType.MAGNET
                                                singleUrl.endsWith(".mpd", true) -> ExtractorLinkType.DASH
                                                singleUrl.endsWith(".torrent", true) -> ExtractorLinkType.TORRENT
                                                format.equals("HLS", true) || singleUrl.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                                                else -> INFER_TYPE
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to movieBoxUrl) +
                                                    (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                            this.quality = getQualityFromName(resText)
                                        }
                                    )
                                    foundLinks = true
                                }
                            }

                            // subtitles
                            val subLinks = listOf(
                                "$movieBoxUrl/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$streamId",
                                "$movieBoxUrl/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$streamId&episode=${episode ?: 0}"
                            )

                            for (subLink in subLinks) {
                                val subToken = generateXClientToken()
                                val subSign = generateXTrSignature("GET", "", "", subLink)

                                val subHeaders = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "Content-Type" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "x-client-token" to subToken,
                                    "x-tr-signature" to subSign
                                )

                                val subRes = app.get(subLink, headers = subHeaders)
                                if (subRes.code != 200) continue

                                val subRoot = JSONObject(subRes.body.string())
                                val captions = subRoot.optJSONObject("data")?.optJSONArray("extCaptions")
                                if (captions != null) {
                                    for (k in 0 until captions.length()) {
                                        val caption = captions.getJSONObject(k)
                                        val captionUrl = caption.optString("url")
                                        val lang = caption.optString("language").ifEmpty { caption.optString("lanName") }.ifEmpty { caption.optString("lan") }.ifEmpty { "Unknown" }
                                        
                                        if (captionUrl.isNotEmpty()) {
                                            subtitleCallback.invoke(
                                                newSubtitleFile(
                                                    url = captionUrl,
                                                    lang = "$lang ($language)"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }

            return foundLinks
        } catch (_: Exception) {
            return false
        }
    }

    // --- 15. VidFast (PORTED & OPTIMIZED) ---
    suspend fun invokeVidFast(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val STATIC_PATH = "hezushon/ira/2264ec23bfa5e4891e26d563e5daac61bcb05688/b544e02b"
        val url = if (season == null) "$vidfastProApi/movie/$tmdbId" else "$vidfastProApi/tv/$tmdbId/$season/$episode"
        
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to vidfastProApi,
            "X-Csrf-Token" to "iwwuf3C7tleIfqxlgG5NUxOrOROfn5d9",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )
        
        val response = app.get(url, headers = headers, timeout = 20).text
        val regex = Regex("""\\"en\\":\\"(.*?)\\"""")
        val match = regex.find(response)
        val rawData = match?.groupValues?.get(1)
        if (rawData.isNullOrEmpty()) {
            return
        }
        
        // AES encryption setup (Keys from dump)
        val keyHex = "1f9b96f4e6604062c39f69f4c2edd92210d44d185434b0d569b077a72975bf08"
        val ivHex = "70ed610a03c6a59c7967abf77db57f71"
        val aesKey = hexStringToByteArray2(keyHex)
        val aesIv = hexStringToByteArray2(ivHex)

        // Encrypt raw data
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
        val paddedData = padData(rawData.toByteArray(Charsets.UTF_8), 16)
        val aesEncrypted = cipher.doFinal(paddedData)

        // XOR operation
        val xorKey = hexStringToByteArray2("d6f87ef72c")
        val xorResult = aesEncrypted.mapIndexed { i, byte ->
            (byte.toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }.toByteArray()

        // Encode XORed data
        val encodedFinal = customEncode(xorResult)

        // Get servers
        val apiServers = "$vidfastProApi/$STATIC_PATH/wfPFjh__qQ/$encodedFinal"
        val serversResponse = app.get(
            apiServers,
            timeout = 20,
            interceptor = CloudflareKiller(),
            headers = headers
        ).text
        if (serversResponse.isEmpty()) return
        val servers = parseServers(serversResponse)
        val urlList = mutableMapOf<String, String>()
        
        servers.forEach {
            try {
                val apiStream = "$vidfastProApi/${STATIC_PATH}/AddlBFe5/${it.data}"
                val streamResponse = app.get(apiStream, timeout = 20, headers = headers).text
                if (streamResponse.isNotEmpty()) {
                    val jsonObject = JSONObject(streamResponse)
                    val url = jsonObject.getString("url")
                    urlList.put(it.name, url)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        urlList.forEach {
            callback.invoke(
                newExtractorLink(
                    "VidFastPro [${it.key}]",
                    "VidFastPro [${it.key}]",
                    url = it.value,
                )
                {
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    // --- 16. Dramadrip ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeDramadrip(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val dramadripAPI = getDomains()?.dramadrip ?: "https://dramadrip.com"
        try {
            val link = app.get("$dramadripAPI/?s=$imdbId").documentLarge.selectFirst("article > a")?.attr("href") ?: return
            val doc = app.get(link).documentLarge
            val links = if (season != null) doc.select("div.file-spoiler a") else doc.select("a.wp-element-button")
            links.forEach {
                val finalUrl = if (it.attr("href").contains("safelink")) cinematickitBypass(it.attr("href")) else it.attr("href")
                if (finalUrl != null) loadSourceNameExtractor("DramaDrip", finalUrl, "", subtitleCallback, callback)
            }
        } catch (e: Exception) {}
    }

    // --- 17. 4kHdhub & Hdhub4u ---
    suspend fun invoke4khdhub(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val baseUrl = getDomains()?.n4khdhub ?: "https://4khdhub.com"
        try {
            val searchUrl = "$baseUrl/?s=$title"
            val link = app.get(searchUrl).documentLarge.selectFirst("a.movie-card")?.attr("href") ?: return
            app.get(baseUrl + link).documentLarge.select("a[href]").forEach {
                if (it.attr("href").contains("drive")) {
                    val final = hdhubgetRedirectLinks(it.attr("href"))
                    if(final.isNotEmpty()) loadSourceNameExtractor("4kHdhub", final, "", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {}
    }

    suspend fun invokeHdhub4u(imdbId: String?, title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
         val baseUrl = getDomains()?.hdhub4u ?: "https://hdhub4u.tv"
         // Logic serupa dengan 4kHdhub
    }

    // --- 18. Vidrock ---
    suspend fun invokeVidrock(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val vidrock = "https://vidrock.net"
        val type = if (season == null) "movie" else "tv"
        val encoded = vidrockEncode(tmdbId.toString(), type, season, episode)
        val response = app.get("$vidrock/api/$type/$encoded").text
        val json = JSONObject(response)
        json.keys().forEach { key ->
            val url = json.getJSONObject(key).optString("url")
            if (url.isNotEmpty()) M3u8Helper.generateM3u8("Vidrock", url, "").forEach(callback)
        }
    }

    // --- 19. Vidlink ---
    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val vidlink = "https://vidlink.pro"
        val url = if (season == null) "$vidlink/movie/$tmdbId" else "$vidlink/tv/$tmdbId/$season/$episode"
        // Gunakan Fully Qualified Name untuk menghindari error Unresolved reference
        val resolver = com.lagradost.cloudstream3.network.WebViewResolver(Regex("""\.pro/api/b.*"""))
        val iframe = app.get(url, interceptor = resolver).url
    }

    // --- 20. WatchSoMuch ---
    suspend fun invokeWatchsomuch(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val id = imdbId?.removePrefix("tt") ?: return
    }

    // --- 21. Wyzie Subtitle (FIXED) ---
    suspend fun invokeWyZIESUBAPI(imdbId: String? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit) {
        if (imdbId.isNullOrBlank()) return
        val url = if(season != null) "https://sub.wyzie.ru/search?id=$imdbId&season=$season&episode=$episode" else "https://sub.wyzie.ru/search?id=$imdbId"
        try {
            val response = app.get(url).text
            val jsonArray = Gson().fromJson(response, Array<WyZIESUB>::class.java)
            jsonArray.forEach { sub -> subtitleCallback(newSubtitleFile(sub.display, sub.url)) }
        } catch (e: Exception) { Log.e("Wyzie", "$e") }
    }
}
