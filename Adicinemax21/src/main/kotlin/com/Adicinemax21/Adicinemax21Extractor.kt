package com.Adicinemax21

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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import org.json.JSONObject 
import java.net.URLDecoder
import com.Adicinemax21.Adicinemax21.Companion.cinemaOSApi
import com.Adicinemax21.Adicinemax21.Companion.Player4uApi
import com.Adicinemax21.Adicinemax21.Companion.idlixAPI
import com.Adicinemax21.Adicinemax21.Companion.RiveStreamAPI
// Import tambahan untuk Adimoviebox2
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import android.net.Uri

object Adicinemax21Extractor : Adicinemax21() {

    // ================== IDLIX SOURCE (UPDATED) ==================
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
            val document = response.document
            val directUrl = getBaseUrl(response.url)

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
                ).parsedSafe<ResponseHash>() ?: return@amap

                val metrix = parseJson<AesData>(json.embed_url).m
                val password = createIdlixKey(json.key, metrix)
                val decrypted = AesHelper.cryptoAESHandler(json.embed_url, password.toByteArray(), false)
                    ?.fixUrlBloat() ?: return@amap

                when {
                    decrypted.contains("jeniusplay", true) -> {
                        val finalUrl = if (decrypted.startsWith("//")) "https:$decrypted" else decrypted
                        Jeniusplay().getUrl(finalUrl, "$directUrl/", subtitleCallback, callback)
                    }
                    !decrypted.contains("youtube") -> {
                        loadExtractor(decrypted, directUrl, subtitleCallback, callback)
                    }
                    else -> return@amap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                val selectors = listOf("a.btn-watch", "a.watch-now", ".watch-button a", "div.last-episode a", ".film-buttons a.btn-primary")
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
            val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/") ?: return
            
            val jsonResponseText = app.get(signedUrl, referer = targetUrl, headers = AdiDewasaHelper.headers).text
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            videoSource.forEach { (quality, url) ->
                 if (url.isNotEmpty()) {
                    callback.invoke(newExtractorLink("AdiDewasa", "AdiDewasa ($quality)", url, INFER_TYPE))
                }
            }
             
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 } ?: return
             val subJson = jsonObject["sub"] as? Map<String, Any>
             val subs = subJson?.get(bestQualityKey) as? List<String>
             subs?.forEach { subPath ->
                 val subUrl = fixUrl(subPath, baseUrl)
                 subtitleCallback.invoke(newSubtitleFile("English", subUrl))
             }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================== KISSKH SOURCE ==================
    suspend fun invokeKisskh(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainUrl = "https://kisskh.ovh"
        val KISSKH_API = "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val KISSKH_SUB_API = "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        try {
            val searchRes = app.get("$mainUrl/api/DramaList/Search?q=$title&type=0").text
            val searchList = tryParseJson<ArrayList<KisskhMedia>>(searchRes) ?: return
            val matched = searchList.find { it.title.equals(title, true) } 
                ?: searchList.firstOrNull { it.title?.contains(title, true) == true } ?: return
            val dramaId = matched.id ?: return
            val detailRes = app.get("$mainUrl/api/DramaList/Drama/$dramaId?isq=false").parsedSafe<KisskhDetail>() ?: return
            val episodes = detailRes.episodes ?: return
            val targetEp = if (season == null) episodes.lastOrNull() else episodes.find { it.number?.toInt() == episode }
            val epsId = targetEp?.id ?: return
            val kkeyVideo = app.get("$KISSKH_API$epsId&version=2.8.10").parsedSafe<KisskhKey>()?.key ?: ""
            val videoUrl = "$mainUrl/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkeyVideo"
            val sources = app.get(videoUrl).parsedSafe<KisskhSources>()

            listOfNotNull(sources?.video, sources?.thirdParty).forEach { link ->
                if (link.contains(".m3u8")) {
                    M3u8Helper.generateM3u8("Kisskh", link, referer = "$mainUrl/", headers = mapOf("Origin" to mainUrl)).forEach(callback)
                } else if (link.contains(".mp4")) {
                    callback.invoke(newExtractorLink("Kisskh", "Kisskh", link, ExtractorLinkType.VIDEO) { this.referer = mainUrl })
                }
            }

            val kkeySub = app.get("$KISSKH_SUB_API$epsId&version=2.8.10").parsedSafe<KisskhKey>()?.key ?: ""
            val subJson = app.get("$mainUrl/api/Sub/$epsId?kkey=$kkeySub").text
            tryParseJson<List<KisskhSubtitle>>(subJson)?.forEach { sub ->
                subtitleCallback.invoke(newSubtitleFile(sub.label ?: "Unknown", sub.src ?: return@forEach))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class KisskhMedia(@JsonProperty("id") val id: Int?, @JsonProperty("title") val title: String?)
    private data class KisskhDetail(@JsonProperty("episodes") val episodes: ArrayList<KisskhEpisode>?)
    private data class KisskhEpisode(@JsonProperty("id") val id: Int?, @JsonProperty("number") val number: Double?)
    private data class KisskhKey(@JsonProperty("key") val key: String?)
    private data class KisskhSources(@JsonProperty("Video") val video: String?, @JsonProperty("ThirdParty") val thirdParty: String?)
    private data class KisskhSubtitle(@JsonProperty("src") val src: String?, @JsonProperty("label") val label: String?)
    // ================== ADIMOVIEBOX SOURCE (FIXED) ==================
    suspend fun invokeAdimoviebox(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiUrl = "https://filmboom.top" // API baru
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
            Adicinemax21.gomoviesAPI,
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
            "${Adicinemax21.vidsrcccAPI}/v2/embed/movie/$tmdbId"
        } else {
            "${Adicinemax21.vidsrcccAPI}/v2/embed/tv/$tmdbId/$season/$episode"
        }

        val script =
            app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return

        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")

        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")

        val serverUrl = if (season == null) {
            "${Adicinemax21.vidsrcccAPI}/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId"
        } else {
            "${Adicinemax21.vidsrcccAPI}/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"
        }

        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.amap {
            val sources =
                app.get("${Adicinemax21.vidsrcccAPI}/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data
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
                            this.referer = "${Adicinemax21.vidsrcccAPI}/"
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
                        referer = "${Adicinemax21.vidsrcccAPI}/"
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
                                this.referer = "${Adicinemax21.vidsrcccAPI}/"
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
            "${Adicinemax21.vidSrcAPI}/embed/movie?imdb=$imdbId"
        } else {
            "${Adicinemax21.vidSrcAPI}/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
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
                else -> return@amap
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
                    "${Adicinemax21.xprimeAPI}/${servers.first()}?id=$tmdbId"
                } else {
                    "${Adicinemax21.xprimeAPI}/${servers.first()}?id=$tmdbId&season=$season&episode=$episode"
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
                    "${Adicinemax21.xprimeAPI}/${servers.last()}?name=$title&fallback_year=$year"
                } else {
                    "${Adicinemax21.xprimeAPI}/${servers.last()}?name=$title&fallback_year=$year&season=$season&episode=$episode"
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
            "${Adicinemax21.watchSomuchAPI}/Watch/ajMovieTorrents.aspx", data = mapOf(
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
            "${Adicinemax21.watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "${Adicinemax21.watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.label?.substringBefore("&nbsp")?.trim() ?: "",
                    fixUrl(sub.url ?: return@map null, Adicinemax21.watchSomuchAPI)
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
            "${Adicinemax21.mappleAPI}/watch/$mediaType/$tmdbId"
        } else {
            "${Adicinemax21.mappleAPI}/watch/$mediaType/$season-$episode/$tmdbId"
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
                this.referer = "${Adicinemax21.mappleAPI}/"
                this.headers = mapOf(
                    "Accept" to "*/*"
                )
            }
        )

        val subRes = app.get(
            "${Adicinemax21.mappleAPI}/api/subtitles?id=$tmdbId&mediaType=$mediaType${if (season == null) "" else "&season=1&episode=1"}",
            referer = "${Adicinemax21.mappleAPI}/"
        ).text
        tryParseJson<ArrayList<MappleSubtitle>>(subRes)?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.display ?: "",
                    fixUrl(subtitle.url ?: return@map, Adicinemax21.mappleAPI)
                )
            )
        }
    }
    // ================== ADIMOVIEBOX 2 SOURCE (FIXED 2004 & SUBTITLE) ==================
    suspend fun invokeAdimoviebox2(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiUrl = "https://api.inmoviebox.com"
        
        // 1. Cari Film/Series berdasarkan Judul
        val searchUrl = "$apiUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$title"}"""
        
        val headersSearch = Adimoviebox2Helper.getHeaders(searchUrl, jsonBody)
        
        val searchRes = app.post(
            searchUrl,
            headers = headersSearch,
            requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
        ).parsedSafe<Adimoviebox2SearchResponse>()

        // 2. Filter hasil pencarian (Cocokkan Judul & Tahun)
        val matchedSubject = searchRes?.data?.results?.flatMap { it.subjects ?: arrayListOf() }?.find { subject ->
            val subjectYear = subject.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val isTitleMatch = subject.title?.contains(title, true) == true
            val isYearMatch = year == null || subjectYear == year
            // Filter tipe konten (Movie=1, Series=2, Variety/Adult=3)
            val isTypeMatch = if (season != null) subject.subjectType == 2 else (subject.subjectType == 1 || subject.subjectType == 3)
            
            isTitleMatch && isYearMatch && isTypeMatch
        } ?: return

        val subjectId = matchedSubject.subjectId ?: return
        val s = season ?: 0
        val e = episode ?: 0

        // 3. Ambil Link Streaming
        val playUrl = "$apiUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$s&ep=$e"
        val headersPlay = Adimoviebox2Helper.getHeaders(playUrl, null, "GET")

        val playRes = app.get(playUrl, headers = headersPlay).parsedSafe<Adimoviebox2PlayResponse>()
        val streams = playRes?.data?.streams ?: return

        streams.forEach { stream ->
            val streamUrl = stream.url ?: return@forEach
            val quality = getQualityFromName(stream.resolutions)
            val signCookie = stream.signCookie

            // FIX ERROR 2004:
            // Ambil header dasar (User-Agent, dll)
            val baseHeaders = Adimoviebox2Helper.getHeaders(streamUrl, null, "GET").toMutableMap()
            
            // Jika ada signCookie dari respon JSON, tambahkan ke Header "Cookie"
            if (!signCookie.isNullOrEmpty()) {
                baseHeaders["Cookie"] = signCookie
            }

            callback.invoke(
                newExtractorLink(
                    "Adimoviebox2",
                    "Adimoviebox2",
                    streamUrl,
                    if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                ) {
                    this.quality = quality
                    // Inject header + cookie yang sudah disiapkan
                    this.headers = baseHeaders
                }
            )

            // 4. LOGIKA SUBTITLE (DIGABUNGKAN)
            if (stream.id != null) {
                // A. API Subtitle Bawaan (Internal)
                val subUrlInternal = "$apiUrl/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=${stream.id}"
                val headersSubInternal = Adimoviebox2Helper.getHeaders(subUrlInternal, null, "GET")
                
                app.get(subUrlInternal, headers = headersSubInternal).parsedSafe<Adimoviebox2SubtitleResponse>()?.data?.extCaptions?.forEach { cap ->
                    val lang = cap.language ?: cap.lanName ?: cap.lan ?: "Unknown"
                    val capUrl = cap.url ?: return@forEach
                    subtitleCallback.invoke(
                        newSubtitleFile(lang, capUrl)
                    )
                }

                // B. API Subtitle Eksternal (FIX UTAMA UNTUK BAHASA INDONESIA)
                // Biasanya parameter episode=0 cukup, tapi untuk keamanan kita coba sesuaikan
                val subUrlExternal = "$apiUrl/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=${stream.id}&episode=0"
                val headersSubExternal = Adimoviebox2Helper.getHeaders(subUrlExternal, null, "GET")

                app.get(subUrlExternal, headers = headersSubExternal).parsedSafe<Adimoviebox2SubtitleResponse>()?.data?.extCaptions?.forEach { cap ->
                    val lang = cap.lan ?: cap.lanName ?: cap.language ?: "Unknown" // Prioritas field sering berbeda di API ini
                    val capUrl = cap.url ?: return@forEach
                    
                    // Kita tambahkan suffix [Ext] atau biarkan saja agar Cloudstream yang merge
                    subtitleCallback.invoke(
                        newSubtitleFile(lang, capUrl)
                    )
                }
            }
        }
    }

    // Helper Object untuk Enkripsi Adimoviebox2 (Private di dalam Extractor)
    private object Adimoviebox2Helper {
        private val secretKeyDefault = base64Decode("NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==")
        
        fun getHeaders(url: String, body: String? = null, method: String = "POST"): Map<String, String> {
            val timestamp = System.currentTimeMillis()
            val xClientToken = generateXClientToken(timestamp)
            val xTrSignature = generateXTrSignature(method, "application/json", if(method=="POST") "application/json; charset=utf-8" else "application/json", url, body, timestamp)

            return mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to xTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                "x-client-status" to "0"
            )
        }

        private fun md5(input: ByteArray): String {
            return MessageDigest.getInstance("MD5").digest(input)
                .joinToString("") { "%02x".format(it) }
        }

        private fun generateXClientToken(timestamp: Long): String {
            val tsStr = timestamp.toString()
            val reversed = tsStr.reversed()
            val hash = md5(reversed.toByteArray())
            return "$tsStr,$hash"
        }

        private fun generateXTrSignature(
            method: String, accept: String?, contentType: String?, url: String, body: String?, timestamp: Long
        ): String {
            val parsed = Uri.parse(url)
            val path = parsed.path ?: ""
            val query = if (parsed.queryParameterNames.isNotEmpty()) {
                parsed.queryParameterNames.sorted().joinToString("&") { key ->
                    parsed.getQueryParameters(key).joinToString("&") { "$key=$it" }
                }
            } else ""
            
            val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path
            val bodyBytes = body?.toByteArray(Charsets.UTF_8)
            val bodyHash = if (bodyBytes != null) md5(if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes) else ""
            val bodyLength = bodyBytes?.size?.toString() ?: ""
            
            val canonical = "${method.uppercase()}\n${accept ?: ""}\n${contentType ?: ""}\n$bodyLength\n$timestamp\n$bodyHash\n$canonicalUrl"
            
            val secretBytes = base64DecodeArray(secretKeyDefault)
            val mac = Mac.getInstance("HmacMD5")
            mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
            val signature = base64Encode(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))

            return "$timestamp|2|$signature"
        }
        
        private fun base64DecodeArray(str: String): ByteArray {
             return android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        }
    }
}
