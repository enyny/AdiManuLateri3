package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

object AdiDewasaExtractor {

    // ================== SUMBER UTAMA: DRAMAFULL DIRECT ==================
    @Suppress("UNCHECKED_CAST") // Mengatasi peringatan Unchecked Cast
    suspend fun invokeAdiDewasaDirect(
        url: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        try {
            val doc = app.get(url, headers = AdiDewasaHelper.headers).document
            val allScripts = doc.select("script").joinToString(" ") { it.data() }
            
            val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return
            
            val jsonResponseText = app.get(signedUrl, referer = url, headers = AdiDewasaHelper.headers).text
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            
            // Cast unchecked di sini akan diabaikan oleh compiler berkat @Suppress
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            videoSource.forEach { (quality, link) ->
                 if (link.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            link,
                            INFER_TYPE 
                        )
                    )
                }
            }
             
             // SUBTITLE
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 } ?: return
             val subJson = jsonObject["sub"] as? Map<String, Any>
             val subs = subJson?.get(bestQualityKey) as? List<String>
             
             subs?.forEach { subPath ->
                 val finalUrl = if(subPath.startsWith("http")) subPath else "https://dramafull.cc$subPath"
                 subtitleCallback.invoke(
                     // MENGGUNAKAN newSubtitleFile (Bukan constructor SubtitleFile)
                     newSubtitleFile("English", finalUrl)
                 )
             }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================== EXTRACTORS LAIN (PORTING DARI ADIDRAKOR) ==================
    
    // --- IDLIX ---
    val idlixAPI = "https://tv6.idlixku.com"
    suspend fun invokeIdlix(
        title: String?, year: Int?, season: Int?, episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.replace(" ", "-")?.lowercase()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" 
                  else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(
        name: String?, url: String, subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit, encrypt: Boolean = false
    ) {
        val res = app.get(url)
        if(res.code != 200) return
        val referer = "https://${java.net.URI(url).host}"
        
        res.document.select("ul#playeroptionsul > li").forEach {
            val id = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = url
            ).text
            
            val source = tryParseJson<ResponseHash>(json)?.let { resp ->
                if (encrypt) {
                    val meta = tryParseJson<Map<String, String>>(resp.embed_url)?.get("m") ?: return@let null
                    val key = generateWpKey(resp.key ?: return@let null, meta)
                    AesHelper.cryptoAESHandler(resp.embed_url, key.toByteArray(), false)?.fixUrlBloat()
                } else {
                    resp.embed_url
                }
            } ?: return@forEach

            if (source.contains("jeniusplay")) {
                 JeniusplayDewasa().getUrl(source, "$referer/", subtitleCallback, callback)
            }
        }
    }

    // --- VIDSRC & LAINNYA ---
    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
         if(imdbId == null) return
         val vidSrcAPI = "https://vidsrc.net"
         val api = "https://cloudnestra.com"
         val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
         
         app.get(url).document.select(".serversList .server").forEach { server ->
            if (server.text().equals("CloudStream Pro", true)) {
                 val hash = app.get("$api/rcp/${server.attr("data-hash")}").text.substringAfter("/prorcp/").substringBefore("'")
                 val m3u8Link = Regex("https:.*\\.m3u8").find(app.get("$api/prorcp/$hash").text)?.value
                 callback.invoke(newExtractorLink("Vidsrc", "Vidsrc", m3u8Link ?: return@forEach, ExtractorLinkType.M3U8))
            }
         }
    }

    suspend fun invokeXprime(tmdbId: Int?, title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val xprimeAPI = "https://backend.xprime.tv"
        if(title != null) {
            val url = if(season == null) "$xprimeAPI/primebox?name=$title&fallback_year=$year" else "$xprimeAPI/primebox?name=$title&fallback_year=$year&season=$season&episode=$episode"
            val sources = app.get(url).parsedSafe<PrimeboxSources>()
            sources?.streams?.forEach { (quality, link) ->
                callback.invoke(newExtractorLink("Primebox", "Primebox", link, ExtractorLinkType.M3U8))
            }
        }
    }
    
    // Extractor lainnya
    suspend fun invokeVidfast(tmdbId: Int?, s: Int?, e: Int?, sc: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) { }
    suspend fun invokeVidlink(tmdbId: Int?, s: Int?, e: Int?, cb: (ExtractorLink)->Unit) { }
    suspend fun invokeMapple(tmdbId: Int?, s: Int?, e: Int?, sc: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) { }
    suspend fun invokeWyzie(tmdbId: Int?, s: Int?, e: Int?, sc: (SubtitleFile)->Unit) { }
    suspend fun invokeVixsrc(tmdbId: Int?, s: Int?, e: Int?, cb: (ExtractorLink)->Unit) { }
    suspend fun invokeVidsrccc(tmdbId: Int?, imdb: String?, s: Int?, e: Int?, sc: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) { }
    suspend fun invokeSuperembed(tmdbId: Int?, s: Int?, e: Int?, sc: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) { }
}
