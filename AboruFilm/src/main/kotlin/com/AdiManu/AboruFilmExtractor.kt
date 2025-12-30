package com.AdiManu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup

object AboruFilmExtractor : AboruFilm() {

    private val vHeaders = mapOf("User-Agent" to "okhttp/3.12.1", "Referer" to "https://www.febbox.com/")

    suspend fun invokeInternalSource(id: Int?, type: Int?, s: Int?, e: Int?, subCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val q = if (type == 1) """{"module":"Movie_downloadurl_v3","mid":"$id","uid":"$HARDCODED_TOKEN","platform":"android","appid":"$appId"}"""
                else """{"module":"TV_downloadurl_v3","tid":"$id","season":"$s","episode":"$e","uid":"$HARDCODED_TOKEN","platform":"android","appid":"$appId"}"""
        
        try {
            val res = queryApiParsed<LinkDataProp>(q)
            res.data?.list?.forEach { link ->
                callback.invoke(newExtractorLink("Aboru Internal", "Internal ${link.quality ?: ""}", link.path ?: return@forEach, INFER_TYPE) { this.headers = vHeaders })
            }
        } catch (err: Exception) { }
    }

    suspend fun invokeExternalSource(mid: Int?, type: Int?, s: Int?, e: Int?, callback: (ExtractorLink) -> Unit) {
        try {
            val share = app.get("$thirdAPI/mbp/to_share_page?box_type=$type&mid=$mid&json=1").parsedSafe<ExternalResponse>()?.data?.link ?: return
            val files = app.get("$thirdAPI/file/file_share_list?share_key=$share").parsedSafe<ExternalResponse>()?.data?.file_list ?: return
            
            files.amap { file ->
                val p = app.get("$thirdAPI/console/video_quality_list?fid=${file.fid}&share_key=$share", headers = mapOf("Cookie" to "ui=$HARDCODED_TOKEN")).text
                val html = JSONObject(p).optString("html")
                Jsoup.parse(html).select("div.file_quality").forEach { el ->
                    val url = el.attr("data-url")
                    if (url.isNotEmpty()) callback.invoke(newExtractorLink("Aboru External", "Server ${el.attr("data-quality")}", url, INFER_TYPE) { this.headers = vHeaders })
                }
            }
        } catch (err: Exception) { }
    }

    suspend fun invokeWatchsomuch(imdb: String?, s: Int?, e: Int?, subCallback: (SubtitleFile) -> Unit) {
        try {
            val id = imdb?.removePrefix("tt")
            val res = app.post("$watchSomuchAPI/Watch/ajMovieTorrents.aspx", data = mapOf("mid" to "$id", "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45")).parsedSafe<WatchsomuchResponses>()
            val tid = res?.movie?.torrents?.find { if (s == null) true else it.season == s && it.episode == e }?.id ?: return
            val subUrl = "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$tid"
            app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.forEach { sub ->
                subCallback.invoke(newSubtitleFile(sub.label ?: "English", sub.url ?: return@forEach))
            }
        } catch (err: Exception) { }
    }

    suspend fun invokeOpenSubs(imdb: String?, s: Int?, e: Int?, subCallback: (SubtitleFile) -> Unit) {
        val slug = if (s == null) "movie/$imdb" else "series/$imdb:$s:$e"
        app.get("$openSubAPI/subtitles/$slug.json").parsedSafe<OsResult>()?.subtitles?.forEach {
            subCallback.invoke(newSubtitleFile(it.lang ?: "English", it.url ?: return@forEach))
        }
    }
}
