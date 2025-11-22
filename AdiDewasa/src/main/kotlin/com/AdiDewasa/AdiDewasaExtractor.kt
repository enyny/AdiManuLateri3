package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

object AdiDewasaExtractor {
    const val idlixAPI = "https://tv6.idlixku.com"
    const val vidsrcccAPI = "https://vidsrc.cc"
    const val vidSrcAPI = "https://vidsrc.net"
    const val xprimeAPI = "https://backend.xprime.tv"
    const val watchSomuchAPI = "https://watchsomuch.tv"
    const val mappleAPI = "https://mapple.uk"
    const val vidlinkAPI = "https://vidlink.pro"
    const val vidfastAPI = "https://vidfast.pro"
    const val wyzieAPI = "https://sub.wyzie.ru"
    const val superembedAPI = "https://multiembed.mov"
    const val vidrockAPI = "https://vidrock.net"
    const val gomoviesAPI = "https://gomovies-online.cam"

    suspend fun invokeAdimoviebox(title: String, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val searchBody = mapOf("keyword" to title, "page" to 1, "perPage" to 10, "subjectType" to 0).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        val items = app.post("https://moviebox.ph/wefeed-h5-bff/web/subject/search", requestBody = searchBody).parsedSafe<AdimovieboxSearch>()?.data?.items ?: return
        val matched = items.find { (it.title.equals(title, true)) || (it.title?.contains(title, true) == true && it.releaseDate?.contains("$year") == true) } ?: return
        val se = season ?: 0; val ep = episode ?: 0
        val playUrl = "https://fmoviesunblocked.net/wefeed-h5-bff/web/subject/play?subjectId=${matched.subjectId}&se=$se&ep=$ep"
        val validRef = "https://fmoviesunblocked.net/spa/videoPlayPage/movies/${matched.detailPath}?id=${matched.subjectId}&type=/movie/detail&lang=en"
        app.get(playUrl, referer = validRef).parsedSafe<AdimovieboxStreams>()?.data?.streams?.forEach {
            callback.invoke(newExtractorLink("Adimoviebox", "Adimoviebox", it.url ?: return@forEach, INFER_TYPE) { this.referer = validRef })
        }
    }

    suspend fun invokeIdlix(title: String, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val slug = title.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$slug-$year" else "$idlixAPI/episode/$slug-season-$season-episode-$episode"
        try {
            val res = app.get(url).document
            res.select("ul#playeroptionsul > li").map { Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type")) }.forEach { (id, nume, type) ->
                val json = app.post("$idlixAPI/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type), referer = url).text
                val source = tryParseJson<ResponseHash>(json)?.embed_url
                if (source?.contains("jeniusplay") == true) Jeniusplay2().getUrl(source, "$idlixAPI/", {}, callback)
            }
        } catch (e: Exception) {}
    }

    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        if (tmdbId == null) return
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val link = app.get(url, interceptor = WebViewResolver(Regex("""$vidlinkAPI/api/b/$type/A{32}"""))).parsedSafe<VidlinkSources>()?.stream?.playlist
        if (link != null) callback.invoke(newExtractorLink("Vidlink", "Vidlink", link, ExtractorLinkType.M3U8) { this.referer = vidlinkAPI })
    }

    suspend fun invokeVidsrc(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        if (tmdbId == null) return
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        try {
            val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
            val userId = script.substringAfter("userId = \"").substringBefore("\";")
            val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
            val v = script.substringAfter("v = \"").substringBefore("\";")
            val serverUrl = if(season == null) "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf" else "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&season=$season&episode=$episode"
            
            app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.forEach {
                if (it.name == "VidPlay") {
                    val src = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data?.source
                    if (src != null) callback.invoke(newExtractorLink("VidPlay", "VidPlay", src, ExtractorLinkType.M3U8) { this.referer = vidsrcccAPI })
                }
            }
        } catch (e: Exception) {}
    }
    
    suspend fun invokeMapple(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        if(tmdbId == null) return
        val mediaType = if (season == null) "movie" else "tv"
        val url = if (season == null) "$mappleAPI/watch/$mediaType/$tmdbId" else "$mappleAPI/watch/$mediaType/$season-$episode/$tmdbId"
        val data = if (season == null) """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"","source":"mapple","sessionId":"1"}]""" else """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"$season-$episode","source":"mapple","sessionId":"1"}]"""
        try {
            val res = app.post(url, requestBody = data.toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull()), headers = mapOf("Next-Action" to "403f7ef15810cd565978d2ac5b7815bb0ff20258a5")).text
            val videoLink = tryParseJson<MappleSources>(res.substringAfter("1:").trim())?.data?.stream_url
            if(videoLink != null) callback.invoke(newExtractorLink("Mapple", "Mapple", videoLink, ExtractorLinkType.M3U8) { this.referer = "$mappleAPI/"; this.headers = mapOf("Accept" to "*/*") })
        } catch(e: Exception) {}
    }
}
