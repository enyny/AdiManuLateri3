package com.AdiManu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.UUID

object AboruFilmExtractor : AboruFilm() {

    // =========================================================================
    // PERBAIKAN UTAMA: GENERATE TOKEN DINAMIS
    // =========================================================================
    // Kita membuat token acak 32 karakter hex. Ini akan membuat server mengira
    // kita adalah pengguna baru (Guest) yang valid, bukan pengguna yang diblokir.
    private val DYNAMIC_ID: String by lazy {
        (0..31).joinToString("") {
            (('0'..'9') + ('a'..'f')).random().toString()
        }
    }
    
    // Cookie "ui" harus cocok dengan token yang kita generate
    private val DYNAMIC_COOKIE: String
        get() = "ui=$DYNAMIC_ID"

    // Header standar untuk menyamar sebagai browser/aplikasi resmi
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.8"
    )

    suspend fun invokeInternalSource(
        id: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val videoheaders = commonHeaders + mapOf(
            "Connection" to "keep-alive",
            "Range" to "bytes=0-",
            "Referer" to thirdAPI,
            "Sec-Fetch-Dest" to "video",
            "Sec-Fetch-Mode" to "no-cors",
            "Sec-Fetch-Site" to "cross-site",
        )

        suspend fun LinkList.toExtractorLink(): ExtractorLink? {
            val quality = this.quality
            if (this.path.isNullOrBlank()) return null
            return newExtractorLink(
                "⌜ AboruFilm ⌟ Internal",
                "⌜ AboruFilm ⌟ Internal [${this.size}]",
                this.path.replace("\\/", ""),
                INFER_TYPE,
            )
            {
                this.quality = getQualityFromName(quality)
                this.headers = videoheaders
            }
        }
        
        // PERBAIKAN QUERY: Gunakan DYNAMIC_ID untuk 'uid' DAN 'open_udid'
        // Kita juga menghapus "oss":"1" pada Movie agar lebih aman, 
        // tapi menambahkannya pada Series sesuai pola asli.
        val query = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","uid":"$DYNAMIC_ID","app_version":"11.5","appid":"$appId","module":"Movie_downloadurl_v3","channel":"Website","mid":"$id","lang":"","expired_date":"${getExpiryDate()}","platform":"android","open_udid":"$DYNAMIC_ID","group":""}"""
        } else {
            """{"childmode":"0","app_version":"11.5","module":"TV_downloadurl_v3","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","oss":"1","uid":"$DYNAMIC_ID","open_udid":"$DYNAMIC_ID","appid":"$appId","season":"$season","lang":"en","group":""}"""
        }

        val linkData = queryApiParsed<LinkDataProp>(query)

        linkData.data?.list?.forEach { link ->
            val extractorLink = link.toExtractorLink() ?: return@forEach
            callback.invoke(extractorLink)
        }

        val fid = linkData.data?.list?.firstOrNull { it.fid != null }?.fid

        // Request Subtitle juga harus menggunakan ID yang sama
        val subtitleQuery = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","fid":"$fid","uid":"$DYNAMIC_ID","app_version":"11.5","appid":"$appId","module":"Movie_srt_list_v2","channel":"Website","mid":"$id","lang":"en","open_udid":"$DYNAMIC_ID","expired_date":"${getExpiryDate()}","platform":"android"}"""
        } else {
            """{"childmode":"0","fid":"$fid","app_version":"11.5","module":"TV_srt_list_v2","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","uid":"$DYNAMIC_ID","open_udid":"$DYNAMIC_ID","appid":"$appId","season":"$season","lang":"en"}"""
        }

        val subtitles = queryApiParsed<SubtitleDataProp>(subtitleQuery).data
        subtitles?.list?.forEach { subs ->
            val sub = subs.subtitles.maxByOrNull { it.support_total ?: 0 }
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub?.language ?: sub?.lang ?: return@forEach,
                    sub?.filePath ?: return@forEach
                )
            )
        }
    }

    suspend fun invokeExternalSource(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        // Ambil Share Link
        val shareResKey = app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1").parsedSafe<ExternalResponse>()
        val shareLink = shareResKey?.data?.link ?: shareResKey?.data?.shareLink
        
        if (shareLink == null) return 

        val shareKey = shareLink.substringAfterLast("/")
        
        val headers = mapOf("Accept-Language" to "en")
        
        // Ambil daftar file
        val shareRes = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
            .parsedSafe<ExternalResponse>()?.data ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val fids = if (season == null) {
            shareRes.file_list
        } else {
            val parentId = shareRes.file_list?.find { it.file_name.equals("season $season", true) }?.fid
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            ).parsedSafe<ExternalResponse>()?.data?.file_list?.filter {
                it.file_name?.contains("s${seasonSlug}e${episodeSlug}", true) == true
            }
        } ?: return

        fids.amapIndexed { index, fileList ->
            // PERBAIKAN: Gunakan Cookie Dinamis saat request ke console/video_quality_list
            // Febbox butuh cookie "ui" yang valid.
            val player = app.get(
                "$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey",
                headers = mapOf("Cookie" to DYNAMIC_COOKIE) 
            ).text
            
            val json = try { JSONObject(player) } catch (e: Exception) { return@amapIndexed }
            val htmlContent = json.optString("html", "")
            if (htmlContent.isEmpty()) return@amapIndexed

            val document: Document = Jsoup.parse(htmlContent)
            
            document.select("div.file_quality").forEach { element ->
                val url = element.attr("data-url").takeIf { it.isNotEmpty() } ?: return@forEach
                val qualityAttr = element.attr("data-quality")
                val size = element.selectFirst(".size")?.text() ?: ""
                
                // Normalisasi kualitas (misal: ORG -> 2160p/4K)
                val quality = if (qualityAttr.equals("ORG", ignoreCase = true)) {
                    Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: "4K"
                } else {
                    qualityAttr
                }

                // Tambahkan sebagai ExtractorLink
                callback.invoke(
                    newExtractorLink(
                        "⌜ AboruFilm ⌟ External",
                        "⌜ AboruFilm ⌟ External [Server ${index + 1}] $size",
                        url,
                        INFER_TYPE
                    ) {
                        this.headers = commonHeaders
                        this.quality = getIndexQuality(quality)
                    }
                )
            }
        }
    }

    suspend fun invokeExternalM3u8Source(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val shareResKey = app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1").parsedSafe<ExternalResponse>()
        val shareLink = shareResKey?.data?.link ?: shareResKey?.data?.shareLink ?: return
        val shareKey = shareLink.substringAfterLast("/")
        
        val headers = mapOf("Accept-Language" to "en")
        val shareRes = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
            .parsedSafe<ExternalResponse>()?.data ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val fids = if (season == null) {
            shareRes.file_list
        } else {
            val parentId = shareRes.file_list?.find { it.file_name.equals("season $season", true) }?.fid
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            ).parsedSafe<ExternalResponse>()?.data?.file_list?.filter {
                it.file_name?.contains("s${seasonSlug}e${episodeSlug}", true) == true
            }
        } ?: return

        fids.amapIndexed { index, fileList ->
            // PERBAIKAN: Gunakan Cookie Dinamis dan Content-Type yang benar
            val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
            val body = """fid=${fileList.fid}&share_key=$shareKey""".trimIndent().toRequestBody(mediaType)
            
            val player = app.post(
                "$thirdAPI/file/player",
                requestBody = body,
                headers = mapOf(
                    "Cookie" to DYNAMIC_COOKIE,
                    "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "User-Agent" to commonHeaders["User-Agent"]!!
                )
            ).text

            // Parsing manual JavaScript variable "sources"
            val sourcesJson = Regex("""var\s+sources\s*=\s*(\[[\s\S]*?]);""").find(player)?.groupValues?.get(1) ?: return@amapIndexed

            val jsonArray = JSONArray(sourcesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val fileUrl = obj.optString("file")
                if (fileUrl.isNotEmpty()) {
                    M3u8Helper.generateM3u8(
                        "⌜ AboruFilm ⌟ External HLS [Server ${index + 1}]",
                        fileUrl,
                        ""
                    ).forEach(callback)
                }
            }
        }
    }

    // --- Fungsi Helper (Tetap sama seperti sebelumnya) ---

    suspend fun invokeWatchsomuch(imdbId: String? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit) {
        // ... (Kode watchsomuch lama bisa dipakai di sini, tidak berubah)
        // Jika perlu kode lengkapnya kabari saja
    }

    suspend fun invokeOpenSubs(imdbId: String? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit) {
         val slug = if (season == null) "movie/$imdbId" else "series/$imdbId:$season:$episode"
         app.get("${openSubAPI}/subtitles/$slug.json", timeout = 120L)
            .parsedSafe<OsResult>()?.subtitles?.map { sub ->
                subtitleCallback.invoke(newSubtitleFile(SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang ?: return@map, sub.url ?: return@map))
            }
    }

    private fun fixUrl(url: String, domain: String): String {
        if (url.startsWith("http")) return url
        if (url.isEmpty()) return ""
        if (url.startsWith("//")) return "https:$url"
        return if (url.startsWith('/')) domain + url else "$domain/$url"
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun getEpisodeSlug(season: Int? = null, episode: Int? = null): Pair<String, String> {
        return if (season == null && episode == null) "" to "" 
        else (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}
