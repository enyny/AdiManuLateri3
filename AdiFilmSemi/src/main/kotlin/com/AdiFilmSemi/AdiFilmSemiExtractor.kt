package com.AdiFilmSemi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import org.json.JSONObject
import com.AdiFilmSemi.AdiFilmSemi.Companion.cinemaOSApi
import com.AdiFilmSemi.AdiFilmSemi.Companion.Player4uApi
import com.AdiFilmSemi.AdiFilmSemi.Companion.idlixAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.gomoviesAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidsrcccAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidSrcAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.xprimeAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.watchSomuchAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.mappleAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidlinkAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidfastAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.wyzieAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vixsrcAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidsrccxAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.superembedAPI
import com.AdiFilmSemi.AdiFilmSemi.Companion.vidrockAPI
import java.net.URI

object AdiFilmSemiExtractor : AdiFilmSemi() {

    // ================== ADIDEWASA / DRAMAFULL SOURCE ==================
    @Suppress("UNCHECKED_CAST")
    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = "https://dramafull.cc"
        
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
        val searchUrl = "$baseUrl/api/live-search/$encodedQuery"

        try {
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers).parsedSafe<AdiDewasaSearchResponse>()
            
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
            } ?: searchRes?.data?.firstOrNull()

            if (matchedItem == null) return 

            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"

            val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document

            if (season != null && episode != null) {
                val episodeHref = doc.select("div.episode-item a, .episode-list a").find { 
                    val text = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")

                if (episodeHref == null) return
                targetUrl = fixUrl(episodeHref, baseUrl)
            } else {
                val selectors = listOf(
                    "a.btn-watch", 
                    "a.watch-now", 
                    ".watch-button a", 
                    "div.last-episode a",
                    ".film-buttons a.btn-primary"
                )
                
                var foundUrl: String? = null
                for (selector in selectors) {
                    val el = doc.selectFirst(selector)
                    if (el != null) {
                        val href = el.attr("href")
                        if (href.isNotEmpty() && !href.contains("javascript") && href != "#") {
                            foundUrl = fixUrl(href, baseUrl)
                            break
                        }
                    }
                }
                if (foundUrl != null) targetUrl = foundUrl
            }

            val docPage = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
            val allScripts = docPage.select("script").joinToString(" ") { it.data() }
            
            val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return
            
            val jsonResponseText = app.get(signedUrl, referer = targetUrl, headers = AdiDewasaHelper.headers).text
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            videoSource.forEach { (quality, url) ->
                 if (url.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            url,
                            INFER_TYPE 
                        )
                    )
                }
            }
             
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 } ?: return
             val subJson = jsonObject["sub"] as? Map<String, Any>
             val subs = subJson?.get(bestQualityKey) as? List<String>
             subs?.forEach { subPath ->
                 val subUrl = fixUrl(subPath, baseUrl)
                 subtitleCallback.invoke(
                     newSubtitleFile(
                         "English", subUrl
                     )
                 )
             }
             
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================== ADIMOVIEBOX SOURCE (FIXED) ==================
    suspend fun invokeAdimoviebox(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiUrl = "https://filmboom.top" // API Baru
        val searchUrl = "$apiUrl/wefeed-h5-bff/web/subject/search"
        
        val searchBody = mapOf(
            "keyword" to title,
            "page" to "1",
            "perPage" to "0",
            "subjectType" to "0"
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val searchRes = app.post(searchUrl, requestBody = searchBody).text
        val items = tryParseJson<AdimovieboxResponse>(searchRes)?.data?.items ?: return
        
        val matchedMedia = items.find { item ->
            val itemYear = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            (item.title.equals(title, true)) || 
            (item.title?.contains(title, true) == true && itemYear == year)
        } ?: return

        val subjectId = matchedMedia.subjectId ?: return
        val detailPath = matchedMedia.detailPath
        val se = if (season == null) 0 else season
        val ep = if (episode == null) 0 else episode
        
        val playUrl = "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=$subjectId&se=$se&ep=$ep"
        val validReferer = "$apiUrl/spa/videoPlayPage/movies/$detailPath?id=$subjectId&type=/movie/detail&lang=en"

        val playRes = app.get(playUrl, referer = validReferer).text
        val streams = tryParseJson<AdimovieboxResponse>(playRes)?.data?.streams ?: return

        streams.reversed().distinctBy { it.url }.forEach { source ->
             callback.invoke(
                newExtractorLink(
                    "Adimoviebox",
                    "Adimoviebox",
                    source.url ?: return@forEach,
                    INFER_TYPE 
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams.firstOrNull()?.id
        val format = streams.firstOrNull()?.format
        if (id != null) {
            val subUrl = "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$subjectId"
            app.get(subUrl, referer = validReferer).parsedSafe<AdimovieboxResponse>()?.data?.captions?.forEach { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.lanName ?: "Unknown",
                        sub.url ?: return@forEach
                    )
                )
            }
        }
    }

    // ================== GOMOVIES SOURCE ==================
    suspend fun invokeGomovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        invokeGpress(
            title,
            year,
            season,
            episode,
            callback,
            gomoviesAPI,
            "Gomovies",
            base64Decode("X3NtUWFtQlFzRVRi"),
            base64Decode("X3NCV2NxYlRCTWFU"),
        )
    }

    private suspend fun invokeGpress(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        api: String,
        name: String,
        mediaSelector: String,
        episodeSelector: String,
    ) {
        fun String.decrypt(key: String): List<GpressSources>? {
            return tryParseJson<List<GpressSources>>(base64Decode(this).xorDecrypt(key))
        }

        val slug = getEpisodeSlug(season, episode)
        val query = if (season == null) {
            title
        } else {
            "$title Season $season"
        }

        var cookies = mapOf(
            "_identitygomovies7" to """5a436499900c81529e3740fd01c275b29d7e2fdbded7d760806877edb1f473e0a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identitygomovies7%22%3Bi%3A1%3Bs%3A52%3A%22%5B2800906%2C%22L2aGGTL9aqxksKR0pLvL66TunKNe1xXb%22%2C2592000%5D%22%3B%7D""",
        )

        var res = app.get("$api/search/$query", cookies = cookies)
        cookies = gomoviesCookies ?: res.cookies.filter { it.key == "advanced-frontendgomovies7" }
            .also { gomoviesCookies = it }
        val doc = res.document
        val media = doc.select("div.$mediaSelector").map {
            Triple(it.attr("data-filmName"), it.attr("data-year"), it.select("a").attr("href"))
        }.let { el ->
            if (el.size == 1) {
                el.firstOrNull()
            } else {
                el.find {
                    if (season == null) {
                        (it.first.equals(title, true) || it.first.equals(
                            "$title ($year)",
                            true
                        )) && it.second.equals("$year")
                    } else {
                        it.first.equals("$title - Season $season", true)
                    }
                }
            } ?: el.find { it.first.contains("$title", true) && it.second.equals("$year") }
        } ?: return

        val iframe = if (season == null) {
            media.third
        } else {
            app.get(
                fixUrl(
                    media.third,
                    api
                )
            ).document.selectFirst("div#$episodeSelector a:contains(Episode ${slug.second})")
                ?.attr("href")
        } ?: return

        res = app.get(fixUrl(iframe, api), cookies = cookies)
        val url = res.document.select("meta[property=og:url]").attr("content")
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val qualities = intArrayOf(2160, 1440, 1080, 720, 480, 360)

        val (serverId, episodeId) = if (season == null) {
            url.substringAfterLast("/") to "0"
        } else {
            url.substringBeforeLast("/").substringAfterLast("/") to url.substringAfterLast("/")
                .substringBefore("-")
        }
        val serverRes = app.get(
            "$api/user/servers/$serverId?ep=$episodeId",
            cookies = cookies,
            headers = headers
        )
        val script = getAndUnpack(serverRes.text)
        val key = """key\s*="\s*(\d+)"""".toRegex().find(script)?.groupValues?.get(1) ?: return
        serverRes.document.select("ul li").amap { el ->
            val server = el.attr("data-value")
            val encryptedData = app.get(
                "$url?server=$server&_=$unixTimeMS",
                cookies = cookies,
                referer = url,
                headers = headers
            ).text
            val links = encryptedData.decrypt(key)
            links?.forEach { video ->
                qualities.filter { it <= video.max.toInt() }.forEach {
                    callback.invoke(
                        newExtractorLink(
                            name,
                            name,
                            video.src.split("360", limit = 3).joinToString(it.toString()),
                            ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = "$api/"
                            this.quality = it
                        }
                    )
                }
            }
        }

    }

    // ================== IDLIX SOURCE ==================
    suspend fun invokeIdlix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) {
            "$idlixAPI/movie/$fixTitle-$year"
        } else {
            "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        }

        try {
            val response = app.get(url)
            val directUrl = getBaseUrl(response.url)
            val document = response.document

            val scriptRegex = """window\.idlixNonce=['"]([a-f0-9]+)['"].*?window\.idlixTime=(\d+).*?""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val script = document.select("script:containsData(window.idlix)").toString()
            val match = scriptRegex.find(script)
            val idlixNonce = match?.groups?.get(1)?.value ?: ""
            val idlixTime = match?.groups?.get(2)?.value ?: ""

            document.select("ul#playeroptionsul > li").map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->
                val json = app.post(
                    url = "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type,
                        "_n" to idlixNonce,
                        "_p" to id,
                        "_t" to idlixTime
                    ),
                    referer = url,
                    headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest")
                ).parsedSafe<IdlixResponseHash>() ?: return@amap

                val metrix = parseJson<IdlixAesData>(json.embed_url).m
                val password = createIdlixKey(json.key, metrix)
                val decrypted = AesHelper.cryptoAESHandler(json.embed_url, password.toByteArray(), false)
                        ?.fixBloat() ?: return@amap

                when {
                    decrypted.contains("jeniusplay", ignoreCase = true) -> {
                        Jeniusplay().getUrl(decrypted, directUrl, subtitleCallback, callback)
                    }
                    !decrypted.contains("youtube") -> {
                        loadExtractor(decrypted, directUrl, subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private fun createIdlixKey(r: String, m: String): String {
        val rList = r.split("\\x").filter { it.isNotEmpty() }.toTypedArray()
        var n = ""
        var reversedM = m.split("").reversed().joinToString("")
        while (reversedM.length % 4 != 0) reversedM += "="
        val decodedBytes = try {
            base64Decode(reversedM)
        } catch (_: Exception) {
            return ""
        }
        val decodedM = String(decodedBytes.toCharArray())
        for (s in decodedM.split("|")) {
            try {
                val index = Integer.parseInt(s)
                if (index in rList.indices) {
                    n += "\\x" + rList[index]
                }
            } catch (_: Exception) {
            }
        }
        return n
    }

    private fun String.fixBloat(): String {
        return this.replace("\"", "").replace("\\", "")
    }

    data class IdlixResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String,
    )

    data class IdlixAesData(
        @JsonProperty("m") val m: String,
    )

    // ================== VIDSRCCC SOURCE ==================
    suspend fun invokeVidsrccc(
        tmdbId: Int?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {

        val url = if (season == null) {
            "$vidsrcccAPI/v2/embed/movie/$tmdbId"
        } else {
            "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        }

        val script =
            app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return

        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")

        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")

        val serverUrl = if (season == null) {
            "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId"
        } else {
            "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"
        }

        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.amap {
            val sources =
                app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data
                    ?: return@amap

            when {
                it.name.equals("VidPlay") -> {

                    callback.invoke(
                        newExtractorLink(
                            "VidPlay",
                            "VidPlay",
                            sources.source ?: return@amap,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$vidsrcccAPI/"
                        }
                    )

                    sources.subtitles?.map {
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                it.label ?: return@map,
                                it.file ?: return@map
                            )
                        )
                    }
                }

                it.name.equals("UpCloud") -> {
                    val scriptData = app.get(
                        sources.source ?: return@amap,
                        referer = "$vidsrcccAPI/"
                    ).document.selectFirst("script:containsData(source =)")?.data()
                    val iframe = Regex("source\\s*=\\s*\"([^\"]+)").find(
                        scriptData ?: return@amap
                    )?.groupValues?.get(1)?.fixUrlBloat()

                    val iframeRes =
                        app.get(iframe ?: return@amap, referer = "https://lucky.vidbox.site/").text

                    val id = iframe.substringAfterLast("/").substringBefore("?")
                    val key = Regex("\\w{48}").find(iframeRes)?.groupValues?.get(0) ?: return@amap

                    app.get(
                        "${iframe.substringBeforeLast("/")}/getSources?id=$id&_k=$key",
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                        ),
                        referer = iframe
                    ).parsedSafe<UpcloudResult>()?.sources?.amap file@{ source ->
                        callback.invoke(
                            newExtractorLink(
                                "UpCloud",
                                "UpCloud",
                                source.file ?: return@file,
                                ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$vidsrcccAPI/"
                            }
                        )
                    }

                }

                else -> {
                    return@amap
                }
            }
        }

    }

    // ================== VIDSRC SOURCE ==================
    suspend fun invokeVidsrc(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = "https://cloudnestra.com"
        val url = if (season == null) {
            "$vidSrcAPI/embed/movie?imdb=$imdbId"
        } else {
            "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        }

        app.get(url).document.select(".serversList .server").amap { server ->
            when {
                server.text().equals("CloudStream Pro", ignoreCase = true) -> {
                    val hash =
                        app.get("$api/rcp/${server.attr("data-hash")}").text.substringAfter("/prorcp/")
                            .substringBefore("'")
                    val res = app.get("$api/prorcp/$hash").text
                    val m3u8Link = Regex("https:.*\\.m3u8").find(res)?.value

                    callback.invoke(
                        newExtractorLink(
                            "Vidsrc",
                            "Vidsrc",
                            m3u8Link ?: return@amap,
                            ExtractorLinkType.M3U8
                        )
                    )
                }

                server.text().equals("2Embed", ignoreCase = true) -> {
                    return@amap
                }

                server.text().equals("Superembed", ignoreCase = true) -> {
                    return@amap
                }

                else -> {
                    return@amap
                }
            }
        }

    }

    // ================== XPRIME SOURCE ==================
    suspend fun invokeXprime(
        tmdbId: Int?,
        title: String? = null,
        year: Int? = null,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val servers = listOf("rage", "primebox")
        val serverName = servers.map { it.capitalize() }
        val referer = "https://xprime.tv/"
        runAllAsync(
            {
                val url = if (season == null) {
                    "$xprimeAPI/${servers.first()}?id=$tmdbId"
                } else {
                    "$xprimeAPI/${servers.first()}?id=$tmdbId&season=$season&episode=$episode"
                }

                val source = app.get(url).parsedSafe<RageSources>()?.url

                callback.invoke(
                    newExtractorLink(
                        serverName.first(),
                        serverName.first(),
                        source ?: return@runAllAsync,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer
                    }
                )
            },
            {
                val url = if (season == null) {
                    "$xprimeAPI/${servers.last()}?name=$title&fallback_year=$year"
                } else {
                    "$xprimeAPI/${servers.last()}?name=$title&fallback_year=$year&season=$season&episode=$episode"
                }

                val sources = app.get(url).parsedSafe<PrimeboxSources>()

                sources?.streams?.map { source ->
                    callback.invoke(
                        newExtractorLink(
                            serverName.last(),
                            serverName.last(),
                            source.value,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = referer
                            this.quality = getQualityFromName(source.key)
                        }
                    )
                }

                sources?.subtitles?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            subtitle.label ?: "",
                            subtitle.file ?: return@map
                        )
                    )
                }

            }
        )
    }

    // ================== WATCHSOMUCH SOURCE ==================
    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post(
            "${watchSomuchAPI}/Watch/ajMovieTorrents.aspx", data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val subUrl = if (season == null) {
            "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.label?.substringBefore("&nbsp")?.trim() ?: "",
                    fixUrl(sub.url ?: return@map null, watchSomuchAPI)
                )
            )
        }

    }

    // ================== MAPPLE SOURCE ==================
    suspend fun invokeMapple(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val mediaType = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$mappleAPI/watch/$mediaType/$tmdbId"
        } else {
            "$mappleAPI/watch/$mediaType/$season-$episode/$tmdbId"
        }

        val data = if (season == null) {
            """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        } else {
            """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"$season-$episode","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        }

        val headers = mapOf(
            "Next-Action" to "403f7ef15810cd565978d2ac5b7815bb0ff20258a5",
        )

        val res = app.post(
            url,
            requestBody = data.toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull()),
            headers = headers
        ).text
        val videoLink =
            tryParseJson<MappleSources>(res.substringAfter("1:").trim())?.data?.stream_url

        callback.invoke(
            newExtractorLink(
                "Mapple",
                "Mapple",
                videoLink ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mappleAPI/"
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

        val subRes = app.get(
            "$mappleAPI/api/subtitles?id=$tmdbId&mediaType=$mediaType${if (season == null) "" else "&season=1&episode=1"}",
            referer = "$mappleAPI/"
        ).text
        tryParseJson<ArrayList<MappleSubtitle>>(subRes)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.display ?: "",
                    fixUrl(subtitle.url ?: return@map, mappleAPI)
                )
            )
        }

    }

    // ================== VIDLINK SOURCE ==================
    suspend fun invokeVidlink(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vidlinkAPI/$type/$tmdbId"
        } else {
            "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        }

        val videoLink = app.get(
            url, interceptor = WebViewResolver(
                Regex("""$vidlinkAPI/api/b/$type/A{32}"""), timeout = 15_000L
            )
        ).parsedSafe<VidlinkSources>()?.stream?.playlist

        callback.invoke(
            newExtractorLink(
                "Vidlink",
                "Vidlink",
                videoLink ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidlinkAPI/"
            }
        )

    }

    // ================== VIDFAST SOURCE ==================
    suspend fun invokeVidfast(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val module = "hezushon/1000076901076321/0b0ce221/cfe60245-021f-5d4d-bacb-0d469f83378f/uva/jeditawev/b0535941d898ebdb81f575b2cfd123f5d18c6464/y/APA91zAOxU2psY2_BvBqEmmjG6QvCoLjgoaI-xuoLxBYghvzgKAu-HtHNeQmwxNbHNpoVnCuX10eEes1lnTcI2l_lQApUiwfx2pza36CZB34X7VY0OCyNXtlq-bGVCkLslfNksi1k3B667BJycQ67wxc1OnfCc5PDPrF0BA8aZRyMXZ3-2yxVGp"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vidfastAPI/$type/$tmdbId"
        } else {
            "$vidfastAPI/$type/$tmdbId/$season/$episode"
        }

        val res = app.get(
            url, interceptor = WebViewResolver(
                Regex("""$vidfastAPI/$module/JEwECseLZdY"""),
                timeout = 15_000L
            )
        ).text

        tryParseJson<ArrayList<VidFastServers>>(res)?.filter { it.description?.contains("Original audio") == true }
            ?.amapIndexed { index, server ->
                val source =
                    app.get("$vidfastAPI/$module/Sdoi/${server.data}", referer = "$vidfastAPI/")
                        .parsedSafe<VidFastSources>()

                callback.invoke(
                    newExtractorLink(
                        "Vidfast",
                        "Vidfast [${server.name}]",
                        source?.url ?: return@amapIndexed,
                        INFER_TYPE
                    )
                )

                if (index == 1) {
                    source.tracks?.map { subtitle ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                subtitle.label ?: return@map,
                                subtitle.file ?: return@map
                            )
                        )
                    }
                }

            }

    }

    // ================== WYZIE SOURCE ==================
    suspend fun invokeWyzie(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "$wyzieAPI/search?id=$tmdbId"
        } else {
            "$wyzieAPI/search?id=$tmdbId&season=$season&episode=$episode"
        }

        val res = app.get(url).text

        tryParseJson<ArrayList<WyzieSubtitle>>(res)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.display ?: return@map,
                    subtitle.url ?: return@map,
                )
            )
        }

    }

    // ================== VIXSRC SOURCE ==================
    suspend fun invokeVixsrc(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val proxy = "https://proxy.heistotron.uk"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) {
            "$vixsrcAPI/$type/$tmdbId"
        } else {
            "$vixsrcAPI/$type/$tmdbId/$season/$episode"
        }

        val res =
            app.get(url).document.selectFirst("script:containsData(window.masterPlaylist)")?.data()
                ?: return

        val video1 =
            Regex("""'token':\s*'(\w+)'[\S\s]+'expires':\s*'(\w+)'[\S\s]+url:\s*'(\S+)'""").find(res)
                ?.let {
                    val (token, expires, path) = it.destructured
                    "$path?token=$token&expires=$expires&h=1&lang=en"
                } ?: return

        val video2 =
            "$proxy/p/${base64Encode("$proxy/api/proxy/m3u8?url=${encode(video1)}&source=sakura|ananananananananaBatman!".toByteArray())}"

        listOf(
            VixsrcSource("Vixsrc [Alpha]", video1, url),
            VixsrcSource("Vixsrc [Beta]", video2, "$mappleAPI/"),
        ).map {
            callback.invoke(
                newExtractorLink(
                    it.name,
                    it.name,
                    it.url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = it.referer
                    this.headers = mapOf(
                        "Accept" to "*/*"
                    )
                }
            )
        }

    }

    // ================== VIDSRCCX SOURCE ==================
    suspend fun invokeVidsrccx(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val filePath =
            if (season == null) "/media/$tmdbId/master.m3u8" else "/media/$tmdbId-$season-$episode/master.m3u8"
        val video = app.post(
            "https://8ball.piracy.cloud/api/generate-secure-url", requestBody = mapOf(
                "filePath" to filePath
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<VidsrccxSource>()?.secureUrl

        callback.invoke(
            newExtractorLink(
                "VidsrcCx",
                "VidsrcCx",
                video ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidsrccxAPI/"
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

    }

    // ================== SUPEREMBED SOURCE ==================
    suspend fun invokeSuperembed(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String = "https://streamingnow.mov"
    ) {
        val path = if (season == null) "" else "&s=$season&e=$episode"
        val token = app.get("$superembedAPI/directstream.php?video_id=$tmdbId&tmdb=1$path").url.substringAfter(
            "?play="
        )

        val (server, id) = app.post(
            "$api/response.php", data = mapOf(
                "token" to token
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document.select("ul.sources-list li:contains(vipstream-S)")
            .let { it.attr("data-server") to it.attr("data-id") }

        val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
        val playRes = app.get(playUrl).document
        val iframe = playRes.selectFirst("iframe.source-frame")?.attr("src") ?: run {
            val captchaId = playRes.select("input[name=captcha_id]").attr("value")
            app.post(playUrl, requestBody = "captcha_id=TEduRVR6NmZ3Sk5Jc3JpZEJCSlhTM25GREs2RCswK0VQN2ZsclI5KzNKL2cyV3dIaFEwZzNRRHVwMzdqVmoxV0t2QlBrNjNTY04wY2NSaHlWYS9Jc09nb25wZTV2YmxDSXNRZVNuQUpuRW5nbkF2dURsQUdJWVpwOWxUZzU5Tnh0NXllQjdYUG83Y0ZVaG1XRGtPOTBudnZvN0RFK0wxdGZvYXpFKzVNM2U1a2lBMG40REJmQ042SA%3D%3D&captcha_answer%5B%5D=8yhbjraxqf3o&captcha_answer%5B%5D=10zxn5vi746w&captcha_answer%5B%5D=gxfpe17tdwub".toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull())
            ).document.selectFirst("iframe.source-frame")?.attr("src")
        }
        val json = app.get(iframe ?: return).text.substringAfter("Playerjs(").substringBefore(");")

        val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)

        callback.invoke(
            newExtractorLink(
                "Superembed",
                "Superembed",
                video ?: return,
                INFER_TYPE
            ) {
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

        """subtitle:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)?.split(",")?.map {
            val (subLang, subUrl) = Regex("""\[(\w+)](http\S+)""").find(it)?.destructured
                ?: return@map
            subtitleCallback.invoke(
                newSubtitleFile(
                    subLang.trim(),
                    subUrl.trim()
                )
            )
        }

    }

    // ================== VIDROCK SOURCE ==================
    suspend fun invokeVidrock(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        subAPI: String = "https://sub.vdrk.site"
    ) {

        val type = if (season == null) "movie" else "tv"
        val url = "$vidrockAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}"
        val encryptData = VidrockHelper.encrypt(tmdbId, type, season, episode)

        app.get("$vidrockAPI/api/$type/$encryptData", referer = url).parsedSafe<LinkedHashMap<String, HashMap<String, String>>>()
            ?.map { source ->
                if (source.key == "source2") {
                    val json = app.get(source.value["url"] ?: return@map, referer = "${vidrockAPI}/").text
                    tryParseJson<ArrayList<VidrockSource>>(json)?.reversed()?.map mirror@{ it ->
                        callback.invoke(
                            newExtractorLink(
                                "Vidrock",
                                "Vidrock [Source2]",
                                it.url ?: return@mirror,
                                INFER_TYPE
                            ) {
                                this.quality = it.resolution ?: Qualities.Unknown.value
                                this.headers = mapOf(
                                    "Range" to "bytes=0-",
                                    "Referer" to "${vidrockAPI}/"
                                )
                            }
                        )
                    }
                } else {
                    callback.invoke(
                        newExtractorLink(
                            "Vidrock",
                            "Vidrock [${source.key.capitalize()}]",
                            source.value["url"] ?: return@map,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "${vidrockAPI}/"
                            this.headers = mapOf(
                                "Origin" to vidrockAPI
                            )
                        }
                    )
                }
            }

        val subUrl = "$subAPI/$type/$tmdbId${if (type == "movie") "" else "/$season/$episode"}"
        val res = app.get(subUrl).text
        tryParseJson<ArrayList<VidrockSubtitle>>(res)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.label?.replace(Regex("\\d"), "")?.replace(Regex("\\s+Hi"), "")?.trim()
                        ?: return@map,
                    subtitle.file ?: return@map,
                )
            )
        }

    }

    // ================== CINEMAOS SOURCE ==================
    suspend fun invokeCinemaOS(
        imdbId: String? = null,
        tmdbId: Int? = null,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val sourceHeaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to cinemaOSApi,
            "Origin" to cinemaOSApi,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "sec-ch-ua" to "\"Google Chrome\";v=\"123\", \"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"123\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "Content-Type" to "application/json"
        )

        val fixTitle = title?.replace(" ", "+")
        val cinemaOsSecretKeyRequest = CinemaOsSecretKeyRequest(
            tmdbId = tmdbId.toString(), 
            seasonId = season?.toString() ?: "", 
            episodeId = episode?.toString() ?: ""
        )
        val secretHash = cinemaOSGenerateHash(cinemaOsSecretKeyRequest, season != null)
        val type = if (season == null) "movie" else "tv"
        
        val sourceUrl = if (season == null) {
            "$cinemaOSApi/api/fuckit?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&t=$fixTitle&ry=$year&secret=$secretHash"
        } else {
            "$cinemaOSApi/api/fuckit?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&seasonId=$season&episodeId=$episode&t=$fixTitle&ry=$year&secret=$secretHash"
        }

        try {
            val sourceResponse = app.get(sourceUrl, headers = sourceHeaders, timeout = 60).parsedSafe<CinemaOSReponse>()
            val decryptedJson = cinemaOSDecryptResponse(sourceResponse?.data)
            val json = parseCinemaOSSources(decryptedJson.toString())
            
            val blockedServers = listOf(
                "Maphisto", "Noah", "Bolt", "Zeus", "Nexus", 
                "Apollo", "Kratos", "Flick", "Hollywood", 
                "Flash", "Ophim", "Bollywood", "Apex", "Universe", 
                "Hindi", "Bengali", "Tamil", "Telugu" 
            )
            val finalBlocked = blockedServers.filter { !it.equals("Rizz", ignoreCase = true) }

            val sortedSources = json.filter {
                val serverName = it["server"] ?: ""
                val isBlocked = finalBlocked.any { blocked -> serverName.contains(blocked, ignoreCase = true) }
                !isBlocked
            }.sortedByDescending { 
                (it["server"] ?: "").contains("Rizz", ignoreCase = true)
            }

            sortedSources.forEach {
                val extractorLinkType = when {
                    it["type"]?.contains("hls", true) == true -> ExtractorLinkType.M3U8
                    it["type"]?.contains("dash", true) == true -> ExtractorLinkType.DASH
                    it["type"]?.contains("mp4", true) == true -> ExtractorLinkType.VIDEO
                    else -> INFER_TYPE
                }
                
                val quality = if (it["quality"]?.isNotEmpty() == true && it["quality"]?.toIntOrNull() != null) {
                    getQualityFromName(it["quality"])
                } else {
                    Qualities.P1080.value
                }

                callback.invoke(
                    newExtractorLink(
                        "CinemaOS [${it["server"]}]",
                        "CinemaOS [${it["server"]}] ${it["bitrate"]}",
                        url = it["url"].toString(),
                        type = extractorLinkType
                    ) {
                        this.headers = mapOf("Referer" to cinemaOSApi)
                        this.quality = quality
                    }
                )
            }
        } catch (e: Exception) {
            // Error handling
        }
    }

    // ================== PLAYER4U SOURCE ==================
    suspend fun invokePlayer4U(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        val queryWithEpisode = season?.let { "$title S${"%02d".format(it)}E${"%02d".format(episode)}" }
        val baseQuery = queryWithEpisode ?: title.orEmpty()
        val encodedQuery = baseQuery.replace(" ", "+")

        val pageRange = 0..4
        val deferredPages = pageRange.map { page ->
            async {
                val url = "$Player4uApi/embed?key=$encodedQuery" + if (page > 0) "&page=$page" else ""
                runCatching { app.get(url, timeout = 20).document }.getOrNull()?.let { doc ->
                    extractPlayer4uLinks(doc, season, episode, title.toString(), year)
                } ?: emptyList()
            }
        }

        val allLinks = deferredPages.awaitAll().flatten().toMutableSet()

        if (allLinks.isEmpty() && season == null) {
            val fallbackUrl = "$Player4uApi/embed?key=${title?.replace(" ", "+")}"
            val fallbackDoc = runCatching { app.get(fallbackUrl, timeout = 20).document }.getOrNull()
            if (fallbackDoc != null) {
                allLinks += extractPlayer4uLinks(fallbackDoc, season, episode, title.toString(), year)
            }
        }

        allLinks.distinctBy { it.name }.map { link ->
            async {
                try {
                    val namePart = link.name.split("|").lastOrNull()?.trim().orEmpty()
                    val displayName = buildString {
                        append("Player4U")
                        if (namePart.isNotEmpty()) append(" {$namePart}")
                    }

                    val qualityMatch = Regex(
                        """(\d{3,4}p|4K|CAM|HQ|HD|SD|WEBRip|DVDRip|BluRay|HDRip|TVRip|HDTC|PREDVD)""",
                        RegexOption.IGNORE_CASE
                    ).find(displayName)?.value?.uppercase() ?: "UNKNOWN"

                    val quality = getPlayer4UQuality(qualityMatch)
                    val subPath = Regex("""go\('(.*?)'\)""").find(link.url)?.groupValues?.get(1) ?: return@async null

                    val iframeSrc = runCatching {
                        app.get("$Player4uApi$subPath", timeout = 10, referer = Player4uApi)
                            .document.selectFirst("iframe")?.attr("src")
                    }.getOrNull() ?: return@async null

                    getPlayer4uUrl(
                        displayName,
                        quality,
                        "https://uqloads.xyz/e/$iframeSrc",
                        Player4uApi,
                        callback
                    )
                } catch (_: Exception) { null }
            }
        }.awaitAll()
    }

    private fun extractPlayer4uLinks(document: Document, season:Int?, episode:Int?, title:String, year:Int?): List<Player4uLinkData> {
        return document.select(".playbtnx").mapNotNull { element ->
            val titleText = element.text()?.split(" | ")?.lastOrNull() ?: return@mapNotNull null
            if (season == null && episode == null) {
                if (year != null && (titleText.startsWith("$title $year", ignoreCase = true) ||
                            titleText.startsWith("$title ($year)", ignoreCase = true))) {
                    Player4uLinkData(name = titleText, url = element.attr("onclick"))
                } else null
            } else {
                if (season != null && episode != null &&
                    titleText.startsWith("$title S${"%02d".format(season)}E${"%02d".format(episode)}", ignoreCase = true)) {
                    Player4uLinkData(name = titleText, url = element.attr("onclick"))
                } else null
            }
        }
    }
}
