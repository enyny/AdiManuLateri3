package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.AdiManuLateri3.Lateri3Play.Companion.PrimeSrcApi
import com.AdiManuLateri3.Lateri3Play.Companion.RiveStreamAPI
import com.AdiManuLateri3.Lateri3Play.Companion.SubtitlesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.UqloadsAPI
import com.AdiManuLateri3.Lateri3Play.Companion.WyZIESUBAPI
import com.AdiManuLateri3.Lateri3PlayUtils.loadSourceNameExtractor
import com.AdiManuLateri3.Lateri3PlayUtils.runLimitedAsync
import org.json.JSONObject

object Lateri3PlayExtractor {

    // --- Subtitle Extractor ---

    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "$SubtitlesAPI/subtitles/movie/$id.json"
        } else {
            "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf("User-Agent" to "Mozilla/5.0")
        val response = app.get(url, headers = headers, timeout = 5000) // Reduced timeout for speed
        if (!response.isSuccessful) return

        response.parsedSafe<SubtitlesAPIResponse>()?.subtitles?.forEach {
            val lang = Lateri3PlayUtils.getLanguage(it.lang)
            subtitleCallback.invoke(newSubtitleFile(lang, it.url))
        }
    }

    suspend fun invokeWyZIESUBAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (id.isNullOrBlank()) return

        val url = buildString {
            append("$WyZIESUBAPI/search?id=$id")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }

        val response = app.get(url)
        if (!response.isSuccessful) return

        response.parsedSafe<List<WyZIESUB>>()?.forEach {
            val language = Lateri3PlayUtils.getLanguage(it.display)
            subtitleCallback(newSubtitleFile(language, it.url))
        }
    }
    
    // --- Video Extractor ---

    // Extractor 1: PrimeSrc (PrimeWire)
    suspend fun invokePrimeSrc(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "accept" to "*/*",
            "user-agent" to "Mozilla/5.0"
        )
        val url = if (season == null) {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&type=movie"
        } else {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&season=$season&episode=$episode&type=tv"
        }

        val serverList =
            app.get(url, timeout = 10000, headers = headers).parsedSafe<PrimeSrcServerList>()
        
        serverList?.servers?.forEach {
            val rawServerJson =
                app.get("$PrimeSrcApi/api/v1/l?key=${it.key}", timeout = 10000, headers = headers).text
            
            val jsonObject = runCatching { JSONObject(rawServerJson) }.getOrNull()
            
            loadSourceNameExtractor(
                "PrimeSrc",
                jsonObject?.optString("link", "") ?: return@forEach,
                PrimeSrcApi,
                subtitleCallback,
                callback,
                getQualityFromName(it.fileName),
                it.fileSize ?: ""
            )
        }
    }

    // Extractor 2: Uqloadsxyz (Sangat sederhana)
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeUqloadsXyz(
        imdbId: String? = null, // Uqloads biasanya menggunakan ID dari embedder, tapi kita akan gunakan IMDB untuk skema ini
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Uqloadsxyz digunakan sebagai extractor oleh 2embed. Karena kita tidak menggunakan 2embed,
        // kita akan menggunakan UqloadsAPI + ID IMDB untuk simulasi sederhana.
        val tmdbId = Lateri3PlayUtils.getTMDBIdFromIMDB(imdbId) ?: return 
        val id = if (season == null) tmdbId.toString() else Lateri3PlayUtils.getEpisodeLinkHash(tmdbId, season, episode)

        // Asumsi: UqloadsAPI memiliki endpoint sederhana yang mengembalikan link embed
        val url = "$UqloadsAPI/embed/$id"
        
        val response = app.get(url, timeout = 10000)
        val embedUrl = response.document.select("iframe").attr("src")
        
        if (embedUrl.isNotBlank()) {
            loadSourceNameExtractor(
                "UqloadsXyz",
                embedUrl,
                UqloadsAPI,
                subtitleCallback,
                callback,
                Qualities.P1080.value
            )
        }
    }


    // Extractor 3: RiveStream
    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (id == null) return

        // RiveStream menggunakan hash dan API untuk mendapatkan link.
        val headers = mapOf("User-Agent" to "Mozilla/5.0")
        
        val appScript = app.get(RiveStreamAPI).document.select("script")
            .firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

        val js = app.get("$RiveStreamAPI$appScript").text
        val keyList = Lateri3PlayUtils.extractKeyList(js)
        
        val secretKey = app.get(
            "https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}"
        ).text

        val sourceApiUrl =
            "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = app.get(sourceApiUrl, headers).parsedSafe<RiveStreamSource>()

        sourceList?.data?.forEach { source ->
            val streamUrl = if (season == null) {
                "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
            } else {
                "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
            }

            val responseString = app.get(streamUrl, headers, timeout = 10000).text
            
            val json = runCatching { JSONObject(responseString) }.getOrNull()
            val sourcesArray = json?.optJSONObject("data")?.optJSONArray("sources") ?: return@forEach

            for (i in 0 until sourcesArray.length()) {
                val src = sourcesArray.getJSONObject(i)
                val url = src.optString("url")
                val label = "RiveStream ${src.optString("source")}"

                if (url.isNotBlank()) {
                    M3u8Helper.generateM3u8(label, url, RiveStreamAPI).forEach(callback)
                }
            }
        }
    }

    // --- Parser Data Classes (Minimal) ---

    // Subtitle API
    data class SubtitlesAPIResponse(val subtitles: List<SubtitleApiItem>)
    data class SubtitleApiItem(val url: String, val lang: String)
    data class WyZIESUB(val url: String, val display: String)
    
    // PrimeSrc
    data class PrimeSrcServerList(val servers: List<PrimeSrcServer>)
    data class PrimeSrcServer(
        val name: String,
        val key: String,
        val fileSize: String?,
        val fileName: String?,
    )

    // RiveStream (minimal classes)
    data class RiveStreamSource(val data: List<String>)
}
