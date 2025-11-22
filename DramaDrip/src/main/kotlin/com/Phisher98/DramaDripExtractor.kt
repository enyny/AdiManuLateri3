package com.Phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.extractors.helper.AesHelper

object DramaDripExtractor : DramaDrip() {

    // ================== 0. ADIMOVIEBOX SOURCE (ADDED) ==================
    // Prioritas: Tertinggi (Direct Source)
    suspend fun invokeAdimoviebox(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchUrl = "https://moviebox.ph/wefeed-h5-bff/web/subject/search"
        val streamApi = "https://fmoviesunblocked.net"
        
        // 1. Cari Film/Serial berdasarkan judul
        val searchBody = mapOf(
            "keyword" to title,
            "page" to 1,
            "perPage" to 10,
            "subjectType" to 0
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val searchRes = app.post(searchUrl, requestBody = searchBody).text
        // Parsing manual agar tidak merusak DramaDripParser utama
        val items = tryParseJson<AdimovieboxSearch>(searchRes)?.data?.items ?: return
        
        // 2. Filter hasil pencarian yang cocok
        val matchedMedia = items.find { item ->
            val itemYear = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            // Logika pencocokan: Judul persis ATAU (Judul mengandung kata kunci & Tahun sama)
            (item.title.equals(title, true)) || 
            (item.title?.contains(title, true) == true && itemYear == year)
        } ?: return

        // 3. Request Link Stream
        val subjectId = matchedMedia.subjectId ?: return
        val se = if (season == null) 0 else season
        val ep = if (episode == null) 0 else episode
        
        val playUrl = "$streamApi/wefeed-h5-bff/web/subject/play?subjectId=$subjectId&se=$se&ep=$ep"
        // Referer spesifik wajib ada
        val validReferer = "$streamApi/spa/videoPlayPage/movies/${matchedMedia.detailPath}?id=$subjectId&type=/movie/detail&lang=en"

        val playRes = app.get(playUrl, referer = validReferer).text
        val streams = tryParseJson<AdimovieboxStreams>(playRes)?.data?.streams ?: return

        // 4. Ekstrak Link Video
        streams.reversed().forEach { source ->
             callback.invoke(
                newExtractorLink(
                    "Adimoviebox",
                    "Adimoviebox",
                    source.url ?: return@forEach,
                    // Gunakan INFER_TYPE agar otomatis deteksi MP4/M3U8
                    INFER_TYPE 
                ) {
                    this.referer = validReferer
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        // 5. Ekstrak Subtitle
        val id = streams.firstOrNull()?.id
        val format = streams.firstOrNull()?.format
        if (id != null) {
            val subUrl = "$streamApi/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$subjectId"
            app.get(subUrl, referer = validReferer).parsedSafe<AdimovieboxCaptions>()?.data?.captions?.forEach { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.lanName ?: "Unknown",
                        sub.url ?: return@forEach
                    )
                )
            }
        }
    }

    // --- Internal Data Classes untuk Adimoviebox ---
    data class AdimovieboxSearch(val data: AdimovieboxData?)
    data class AdimovieboxData(val items: List<AdimovieboxItem>?)
    data class AdimovieboxItem(val subjectId: String?, val title: String?, val releaseDate: String?, val detailPath: String?)
    data class AdimovieboxStreams(val data: AdimovieboxStreamData?)
    data class AdimovieboxStreamData(val streams: List<AdimovieboxStreamItem>?)
    data class AdimovieboxStreamItem(val id: String?, val format: String?, val url: String?, val resolutions: String?)
    data class AdimovieboxCaptions(val data: AdimovieboxCaptionData?)
    data class AdimovieboxCaptionData(val captions: List<AdimovieboxCaptionItem>?)
    data class AdimovieboxCaptionItem(val lanName: String?, val url: String?)
    // ================== END ADIMOVIEBOX ==================


    // --- 1. JENIUSPLAY (VIA IDLIX) ---
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
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        val res = app.get(url ?: return, interceptor = if (hasCloudflare) interceptor else null)
        val referer = getBaseUrl(res.url)
        val document = res.document
        
        document.select("ul#playeroptionsul > li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.amap { (id, nume, type) ->
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type
                ),
                headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"),
                referer = url,
                interceptor = if (hasCloudflare) interceptor else null
            ).text
            
            val source = tryParseJson<ResponseHash>(json)?.let {
                when {
                    encrypt -> {
                        val meta = tryParseJson<Map<String, String>>(it.embed_url)?.get("m")
                            ?: return@amap
                        val key = generateWpKey(it.key ?: return@amap, meta)
                        AesHelper.cryptoAESHandler(
                            it.embed_url,
                            key.toByteArray(),
                            false
                        )?.fixUrlBloat()
                    }
                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@amap

            when {
                source.startsWith("https://jeniusplay.com") -> {
                    // Memanggil Extractor Jeniusplay
                    Jeniusplay().getUrl(source, "$referer/", subtitleCallback, callback)
                }
                !source.contains("youtube") -> {
                    loadExtractor(source, "$referer/", subtitleCallback, callback)
                }
                else -> {
                    return@amap
                }
            }
        }
    }

    // --- 2. VIDLINK ---
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

    // --- 3. VIDPLAY (VIA VIDSRCCC) ---
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

                    sources.subtitles?.map { sub ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                sub.label ?: return@map,
                                sub.file ?: return@map
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
            }
        }
    }

    // --- 4. VIXSRC (ALPHA) ---
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
}
