package com.AdiManuLateri3

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.newSubtitleFile
import com.AdiManuLateri3.Lateri3Play.Companion.PrimeSrcApi
import com.AdiManuLateri3.Lateri3Play.Companion.RiveStreamAPI
import com.AdiManuLateri3.Lateri3Play.Companion.SubtitlesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.WyZIESUBAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

object Lateri3PlayExtractor {

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // ================= HELPER FUNCTIONS =================
    
    private suspend fun loadSource(
        sourceName: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
        size: String = ""
    ) {
        val fixSize = if (size.isNotEmpty()) " $size" else ""
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        "$sourceName[${link.source}$fixSize]",
                        "$sourceName[${link.source}$fixSize]",
                        link.url,
                    ) {
                        this.quality = link.quality
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

    private fun getLanguage(code: String): String {
        return Locale(code).displayLanguage
    }

    // ================= SUBTITLES =================

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
        
        val response = app.get(url, timeout = 100L)
        if (response.code != 200) return
        
        try {
            val res = app.parseJson<SubtitlesAPI>(response.text)
            res.subtitles.forEach { sub ->
                val lang = getLanguage(sub.lang)
                subtitleCallback.invoke(
                    newSubtitleFile(lang, sub.url)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        if (response.code != 200) return

        try {
            val subtitles = Gson().fromJson<List<WyZIESUB>>(
                response.text, 
                object : TypeToken<List<WyZIESUB>>() {}.type
            ) ?: emptyList()

            subtitles.forEach {
                val language = it.display.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
                subtitleCallback(newSubtitleFile(language, it.url))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================= VIDEO EXTRACTORS =================

    suspend fun invokePrimeSrc(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "accept" to "*/*",
            "referer" to if (season == null) "$PrimeSrcApi/embed/movie?imdb=$imdbId" else "$PrimeSrcApi/embed/tv?imdb=$imdbId&season=$season&episode=$episode",
            "user-agent" to USER_AGENT
        )
        
        val url = if (season == null) {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&type=movie"
        } else {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&season=$season&episode=$episode&type=tv"
        }

        try {
            val serverList = app.get(url, timeout = 30, headers = headers).parsedSafe<PrimeSrcServerList>()
            serverList?.servers?.forEach { server ->
                val rawServerJson = app.get("$PrimeSrcApi/api/v1/l?key=${server.key}", timeout = 30, headers = headers).text
                val jsonObject = JSONObject(rawServerJson)
                val link = jsonObject.optString("link", "")
                
                if (link.isNotBlank()) {
                    loadSource(
                        "PrimeWire", 
                        link, 
                        PrimeSrcApi, 
                        subtitleCallback, 
                        callback, 
                        size = server.fileSize ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("User-Agent" to USER_AGENT)

        try {
            // 1. Get Source List
            val sourceApiUrl = "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
            val sourceList = app.get(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() ?: return

            // 2. Get Secret Key logic (Simplified)
            // Note: In a real scenario, this key logic changes often. 
            // Using a static fetch attempt similar to original code.
            val document = app.get(RiveStreamAPI, headers, timeout = 20).document
            val appScript = document.select("script")
                .firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

            val js = app.get("$RiveStreamAPI$appScript").text
            val keyList = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
                .findAll(js).firstOrNull { it.groupValues[1].length > 2 }?.groupValues?.get(1)
                ?.let { array ->
                    Regex("\"([^\"]+)\"").findAll(array).map { it.groupValues[1] }.toList()
                } ?: emptyList()

            // Fetch Secret Key from worker
            val secretKey = app.get(
                "https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}"
            ).text

            // 3. Loop through sources
            sourceList.data.forEach { source ->
                val streamUrl = if (season == null) {
                    "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val responseString = app.get(streamUrl, headers, timeout = 10).text
                val json = JSONObject(responseString)
                val sourcesArray = json.optJSONObject("data")?.optJSONArray("sources") ?: return@forEach

                for (i in 0 until sourcesArray.length()) {
                    val src = sourcesArray.getJSONObject(i)
                    val label = "RiveStream ${src.optString("source")}"
                    val url = src.optString("url")

                    if (url.contains("proxy?url=")) {
                        // Handle Proxy URL
                        val fullyDecoded = URLDecoder.decode(url, "UTF-8")
                        val decodedUrl = URLDecoder.decode(
                            fullyDecoded.substringAfter("proxy?url=").substringBefore("&headers="),
                            "UTF-8"
                        )
                        val headersJson = fullyDecoded.substringAfter("&headers=")
                        val headerMap = try {
                            val hJson = JSONObject(URLDecoder.decode(headersJson, "UTF-8"))
                            hJson.keys().asSequence().associateWith { hJson.getString(it) }
                        } catch (e: Exception) { mapOf<String,String>() }

                        callback.invoke(newExtractorLink(
                            label, label, decodedUrl, 
                            if (decodedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            this.headers = headerMap
                            this.quality = Qualities.P1080.value
                        })

                    } else {
                        // Handle Direct URL
                        callback.invoke(newExtractorLink(
                            label, label, url, 
                            if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            this.quality = Qualities.P1080.value
                        })
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
