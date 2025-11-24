package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

// 1. OBJECT ADIHYBRID (KUMPULAN EXTRACTOR DARI ADIDRAKOR)
object AdiHybrid {

    // ================== ADIMOVIEBOX SOURCE ==================
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
        
        val searchBody = mapOf(
            "keyword" to title,
            "page" to 1,
            "perPage" to 10,
            "subjectType" to 0
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        try {
            val searchRes = app.post(searchUrl, requestBody = searchBody).text
            val items = tryParseJson<AdimovieboxSearch>(searchRes)?.data?.items ?: return
            
            val matchedMedia = items.find { item ->
                val itemYear = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
                (item.title.equals(title, true)) || 
                (item.title?.contains(title, true) == true && itemYear == year)
            } ?: return

            val subjectId = matchedMedia.subjectId ?: return
            val se = season ?: 0
            val ep = episode ?: 0
            
            val playUrl = "$streamApi/wefeed-h5-bff/web/subject/play?subjectId=$subjectId&se=$se&ep=$ep"
            val validReferer = "$streamApi/spa/videoPlayPage/movies/${matchedMedia.detailPath}?id=$subjectId&type=/movie/detail&lang=en"

            val playRes = app.get(playUrl, referer = validReferer).text
            val streams = tryParseJson<AdimovieboxStreams>(playRes)?.data?.streams ?: return

            streams.reversed().forEach { source ->
                 callback.invoke(
                    newExtractorLink(
                        "Adimoviebox",
                        "Adimoviebox",
                        source.url ?: return@forEach,
                        INFER_TYPE 
                    ) {
                        this.referer = validReferer
                        this.quality = getQualityFromName(source.resolutions)
                    }
                )
            }

            val id = streams.firstOrNull()?.id
            val format = streams.firstOrNull()?.format
            if (id != null) {
                val subUrl = "$streamApi/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$subjectId"
                app.get(subUrl, referer = validReferer).parsedSafe<AdimovieboxCaptions>()?.data?.captions?.forEach { sub ->
                    subtitleCallback.invoke(
                        newSubtitleFile(sub.lanName ?: "Unknown", sub.url ?: return@forEach)
                    )
                }
            }
        } catch (e: Exception) { }
    }

    data class AdimovieboxSearch(val data: AdimovieboxData?)
    data class AdimovieboxData(val items: List<AdimovieboxItem>?)
    data class AdimovieboxItem(val subjectId: String?, val title: String?, val releaseDate: String?, val detailPath: String?)
    data class AdimovieboxStreams(val data: AdimovieboxStreamData?)
    data class AdimovieboxStreamData(val streams: List<AdimovieboxStreamItem>?)
    data class AdimovieboxStreamItem(val id: String?, val format: String?, val url: String?, val resolutions: String?)
    data class AdimovieboxCaptions(val data: AdimovieboxCaptionData?)
    data class AdimovieboxCaptionData(val captions: List<AdimovieboxCaptionItem>?)
    data class AdimovieboxCaptionItem(val lanName: String?, val url: String?)

    // ================== IDLIX SOURCE ==================
    suspend fun invokeIdlix(
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val idlixAPI = "https://tv6.idlixku.com"
        val fixTitle = title?.filter { it.isLetterOrDigit() || it.isWhitespace() }?.trim()?.replace(" ", "-")?.lowercase()
        val url = if (season == null) {
            "$idlixAPI/movie/$fixTitle-$year"
        } else {
            "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        
        try {
            val res = app.get(url)
            val document = res.document
            document.select("ul#playeroptionsul > li").map {
                Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
            }.forEach { (id, nume, type) ->
                val json = app.post(
                    url = "$idlixAPI/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                    referer = url
                ).text
                
                val sourceUrl = tryParseJson<ResponseHash>(json)?.embed_url ?: return@forEach
                
                // INI YANG SEBELUMNYA ERROR: SEKARANG KELAS AdiJenius SUDAH ADA DI BAWAH
                if (sourceUrl.contains("jeniusplay")) {
                    AdiJenius().getUrl(sourceUrl, "$idlixAPI/", subtitleCallback, callback)
                } else {
                    loadExtractor(sourceUrl, "$idlixAPI/", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { }
    }
    
    data class ResponseHash(val embed_url: String?, val type: String?)

    // ================== VIDLINK SOURCE ==================
    suspend fun invokeVidlink(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val vidlinkAPI = "https://vidlink.pro"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"

        try {
            val videoLink = app.get(url).parsedSafe<VidlinkSources>()?.stream?.playlist
            if (videoLink != null) {
                callback.invoke(
                    newExtractorLink("Vidlink", "Vidlink", videoLink, ExtractorLinkType.M3U8) {
                        this.referer = "$vidlinkAPI/"
                    }
                )
            }
        } catch (e: Exception) { }
    }
    data class VidlinkSources(val stream: VidlinkStream?)
    data class VidlinkStream(val playlist: String?)

    // ================== SUPEREMBED SOURCE ==================
    suspend fun invokeSuperembed(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val superembedAPI = "https://multiembed.mov"
        val path = if (season == null) "" else "&s=$season&e=$episode"
        val url = "$superembedAPI/directstream.php?video_id=$tmdbId&tmdb=1$path"
        
        try {
            val token = app.get(url).url.substringAfter("?play=")
            val (server, id) = app.post(
                "https://streamingnow.mov/response.php", 
                data = mapOf("token" to token), 
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).document.select("ul.sources-list li:contains(vipstream-S)").let { it.attr("data-server") to it.attr("data-id") }

            val playUrl = "https://streamingnow.mov/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
            val iframe = app.get(playUrl).document.selectFirst("iframe.source-frame")?.attr("src") ?: return
            
            val json = app.get(iframe).text.substringAfter("Playerjs(").substringBefore(");")
            val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)

            if (video != null) {
                callback.invoke(newExtractorLink("Superembed", "Superembed", video, INFER_TYPE))
            }
        } catch (e: Exception) { }
    }
}

// 2. CLASS ADIJENIUS (DIBUTUHKAN OLEH PLUGIN DAN IDLIX)
open class AdiJenius : ExtractorApi() {
    override val name = "AdiJenius"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = "$mainUrl/").document
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                m3uLink,
                ExtractorLinkType.M3U8
            ) {
                this.referer = url
            }
        )

        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            getLanguage(subtitle.label ?: ""),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str
                .contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )
}
