package com.AdiManu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup

object AboruFilmExtractor : AboruFilm() {

    private val videoHeaders = mapOf(
        "User-Agent" to "okhttp/3.12.1",
        "Referer" to "https://www.febbox.com/",
        "Accept" to "*/*"
    )

    suspend fun invokeInternalSource(id: Int?, type: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val query = if (type == 1) 
            """{"childmode":"0","app_version":"11.7","appid":"$appId","module":"Movie_downloadurl_v3","mid":"$id","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","uid":"$HARDCODED_TOKEN"}"""
        else 
            """{"childmode":"0","app_version":"11.7","appid":"$appId","module":"TV_downloadurl_v3","episode":"$episode","season":"$season","tid":"$id","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","uid":"$HARDCODED_TOKEN"}"""

        try {
            val response = queryApiParsed<LinkDataProp>(query)
            response.data?.list?.forEach { link ->
                val path = link.path ?: return@forEach
                callback.invoke(newExtractorLink("Aboru Internal", "Aboru Internal ${link.quality ?: ""}", path, INFER_TYPE) {
                    this.quality = getQualityFromName(link.quality); this.headers = videoHeaders
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun invokeExternalSource(mediaId: Int?, type: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        try {
            val shareRes = app.get("$thirdAPI/mbp/to_share_page?box_type=$type&mid=$mediaId&json=1").parsedSafe<ExternalResponse>()?.data ?: return
            val shareKey = shareRes.link ?: return
            val fileList = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey").parsedSafe<ExternalResponse>()?.data?.file_list ?: return
            
            fileList.forEach { file ->
                val player = app.get("$thirdAPI/console/video_quality_list?fid=${file.fid}&share_key=$shareKey", headers = mapOf("Cookie" to "ui=$HARDCODED_TOKEN")).text
                val html = JSONObject(player).optString("html")
                Jsoup.parse(html).select("div.file_quality").forEach { el ->
                    val url = el.attr("data-url")
                    if (url.isNotEmpty()) {
                        callback.invoke(newExtractorLink("Aboru External", "Server External ${el.attr("data-quality")}", url, INFER_TYPE) {
                            this.headers = videoHeaders
                        })
                    }
                }
            }
        } catch (e: Exception) { }
    }

    suspend fun invokeExternalM3u8Source(mediaId: Int?, type: Int?, s: Int?, e: Int?, callback: (ExtractorLink) -> Unit) { /* Logika M3U8 mirip dengan External Source */ }

    suspend fun invokeOpenSubs(imdbId: String?, s: Int?, e: Int?, callback: (SubtitleFile) -> Unit) {
        val slug = if (s == null) "movie/$imdbId" else "series/$imdbId:$s:$e"
        app.get("$openSubAPI/subtitles/$slug.json").parsedSafe<OsResult>()?.subtitles?.forEach {
            callback.invoke(newSubtitleFile(it.lang ?: "English", it.url ?: return@forEach))
        }
    }
}
