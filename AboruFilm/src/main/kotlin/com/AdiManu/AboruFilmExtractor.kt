package com.AdiManu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup

object AboruFilmExtractor : AboruFilm() {

    private val videoheaders = mapOf(
        "User-Agent" to "okhttp/3.12.1",
        "Referer" to "https://www.febbox.com/",
        "Accept" to "*/*"
    )

    // Token Hardcoded agar sinkron
    private const val HARDCODED_TOKEN = "59e139fd173d9045a2b5fc13b40dfd87"
    private const val HARDCODED_COOKIE_TOKEN = "ui=59e139fd173d9045a2b5fc13b40dfd87"

    suspend fun invokeInternalSource(
        id: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        // Menggunakan ResponseTypes global dari Parser
        val query = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","app_version":"11.7","appid":"$appId","module":"Movie_downloadurl_v3","channel":"Website","mid":"$id","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","uid":"$HARDCODED_TOKEN","open_udid":"$HARDCODED_TOKEN"}"""
        } else {
            """{"childmode":"0","app_version":"11.7","module":"TV_downloadurl_v3","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","uid":"$HARDCODED_TOKEN","open_udid":"$HARDCODED_TOKEN","appid":"$appId","season":"$season","lang":"en"}"""
        }

        try {
            val linkData = queryApiParsed<LinkDataProp>(query)
            linkData.data?.list?.forEach { link ->
                // Membersihkan path URL
                val path = link.path?.replace("\\", "") ?: return@forEach
                callback.invoke(
                    newExtractorLink(
                        "Aboru Internal",
                        "Aboru Internal [${link.quality ?: ""}]",
                        path,
                        INFER_TYPE,
                    ) {
                        this.quality = getQualityFromName(link.quality)
                        this.headers = videoheaders
                    }
                )
            }
            
            val fid = linkData.data?.list?.firstOrNull { it.fid != null }?.fid
            if (fid != null) {
                fetchInternalSubtitles(id, fid, type, season, episode, subtitleCallback)
            }
        } catch (e: Exception) { }
    }

    private suspend fun fetchInternalSubtitles(
        id: Int?, fid: Int?, type: Int?, season: Int?, episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val subQuery = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","fid":"$fid","app_version":"11.7","appid":"$appId","module":"Movie_srt_list_v2","channel":"Website","mid":"$id","lang":"en","uid":"$HARDCODED_TOKEN"}"""
        } else {
            """{"childmode":"0","fid":"$fid","app_version":"11.7","module":"TV_srt_list_v2","channel":"Website","episode":"$episode","tid":"$id","uid":"$HARDCODED_TOKEN","appid":"$appId","season":"$season"}"""
        }

        try {
            val subtitles = queryApiParsed<SubtitleDataProp>(subQuery).data
            subtitles?.list?.forEach { subs ->
                val sub = subs.subtitles.maxByOrNull { it.support_total ?: 0 }
                if (sub?.filePath != null) {
                    subtitleCallback.invoke(
                        newSubtitleFile(sub.language ?: sub.lang ?: "English", sub.filePath)
                    )
                }
            }
        } catch (e: Exception) { }
    }

    suspend fun invokeExternalSource(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val shareRes = app.get("$thirdAPI/mbp/to_share_page?box_type=$type&mid=$mediaId&json=1").parsedSafe<ExternalResponse>()?.data
            
            // PERBAIKAN: Menggunakan share_link yang sudah didefinisikan di Parser
            val shareKey = shareRes?.link ?: shareRes?.share_link?.substringAfterLast("/") ?: return
            
            val fileList = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey").parsedSafe<ExternalResponse>()?.data?.file_list ?: return
            
            fileList.forEach { file ->
                val playerRes = app.get("$thirdAPI/console/video_quality_list?fid=${file.fid}&share_key=$shareKey", headers = mapOf("Cookie" to HARDCODED_COOKIE_TOKEN)).text
                val html = JSONObject(playerRes).optString("html")
                if (html.isNotEmpty()) {
                    Jsoup.parse(html).select("div.file_quality").forEach { el ->
                        val url = el.attr("data-url")
                        if (url.isNotEmpty()) {
                            callback.invoke(newExtractorLink("Aboru External", "Server External ${el.attr("data-quality")}", url, INFER_TYPE) {
                                this.headers = videoheaders
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) { }
    }

    suspend fun invokeOpenSubs(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val slug = if (season == null) "movie/$imdbId" else "series/$imdbId:$season:$episode"
        try {
            app.get("$openSubAPI/subtitles/$slug.json").parsedSafe<OsResult>()?.subtitles?.forEach {
                subtitleCallback.invoke(newSubtitleFile(it.lang ?: "English", it.url ?: return@forEach))
            }
        } catch (e: Exception) { }
    }
}
